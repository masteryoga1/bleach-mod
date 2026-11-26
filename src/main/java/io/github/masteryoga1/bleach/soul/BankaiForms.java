package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a Bankai's form brings its wielder while it is held, on the server: the art its spirit's file names
 * ({@link ZanpakutoSpirit.BankaiAbility}). A soul holds its Bankai while its Bankai is released and its own blade is
 * in its main hand in Bankai; the two can disagree for up to the away time, so both are read. Tensa Zangetsu's art is
 * speed: its owner moves and swings faster, as transient modifiers on the player and never on the blade. Each art
 * puts its own on in its branch of {@link #keep} and lists their ids for {@link #clear}, so Hihiō Zabimaru's serpent
 * is a branch and a row to add. How it looks is the client's, read from the held stack alone, since a client knows no
 * spirits.
 */
public final class BankaiForms {
    /** The movement a Bankai of speed adds, as a share of the player's base speed, as Hohō's and the mask's are. */
    public static final ResourceLocation SPEED_ID = BleachMod.id("bankai_speed");
    /** The swings a second a Bankai of speed adds, as a share of the blade's own. */
    public static final ResourceLocation ATTACK_SPEED_ID = BleachMod.id("bankai_attack_speed");

    /** Every modifier an art puts on, with the attribute it is on. */
    private static final List<Worn> WORN = List.of(new Worn(Attributes.MOVEMENT_SPEED, SPEED_ID),
            new Worn(Attributes.ATTACK_SPEED, ATTACK_SPEED_ID));

    private BankaiForms() {
    }

    /** Whether the soul holds its Bankai: released, with its own blade in its main hand in Bankai. */
    public static boolean inBankai(Player player, SoulData data) {
        ItemStack held = player.getMainHandItem();
        return data.isBankaiActive() && ZanpakutoLogic.isOwnBlade(held, player)
                && ZanpakutoItem.blade(held).stage() == ZanpakutoStage.BANKAI;
    }

    /**
     * The art of the Bankai the soul holds (see {@link #inBankai}), as its spirit's file names it; empty when it holds
     * none, or its blade holds a spirit the soul no longer has. Server side only: a client knows no spirits.
     */
    public static Optional<ZanpakutoSpirit.BankaiAbility> held(Player player, SoulData data) {
        if (!inBankai(player, data)) {
            return Optional.empty();
        }
        ZanpakutoBlade blade = ZanpakutoItem.blade(player.getMainHandItem());
        if (!blade.spirit().equals(data.zanpakuto().spirit())) {
            return Optional.empty();
        }
        return ZanpakutoSpirits.get(player.level(), blade.spirit()).flatMap(ZanpakutoSpirit::bankai)
                .map(ZanpakutoSpirit.Bankai::ability);
    }

    /**
     * Puts on what the held Bankai's art gives the body, or takes it off when the soul holds none: called as a Bankai
     * is called and on each of the player's server ticks, so the away time, a copy in hand or a soul replaced without
     * a seal take it off by the next tick, and a blade taken up again puts it back. Writes only on a change.
     */
    public static void keep(Player player, SoulData data) {
        switch (held(player, data).orElse(ZanpakutoSpirit.BankaiAbility.NONE)) {
            case SPEED -> {
                wear(player, Attributes.MOVEMENT_SPEED, SPEED_ID, BleachConfig.get(BleachConfig.BANKAI_SPEED_MOVEMENT_BONUS));
                wear(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID,
                        BleachConfig.get(BleachConfig.BANKAI_SPEED_ATTACK_SPEED_BONUS));
            }
            default -> clear(player);
        }
    }

    /** Takes off everything a Bankai's art gave the body, at once: every end of a Bankai calls it. */
    public static void clear(Player player) {
        for (Worn worn : WORN) {
            // At nought it comes off, whatever it was filed under.
            HollowBody.setModifier(player, worn.attribute(), worn.id(), 0.0, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    /** A share of the base, as Hohō's and the mask's are, so the arts add to theirs. */
    private static void wear(Player player, Holder<Attribute> attribute, ResourceLocation id, double share) {
        HollowBody.setModifier(player, attribute, id, share, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    /** One modifier an art puts on the body. */
    private record Worn(Holder<Attribute> attribute, ResourceLocation id) {
    }
}
