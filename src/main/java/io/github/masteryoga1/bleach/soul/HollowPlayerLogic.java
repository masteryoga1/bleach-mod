package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.BleachTags;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The rules for playing as a Hollow: the bands of soul hunger, what the claws, hide and regeneration
 * are worth at a soul's reiryoku, what a death costs, and what a Hollow cannot wear, eat or trade
 * for. Like {@link SoulLogic}, these read only their arguments and the config, send nothing and are
 * safe on both sides, so game tests can call them directly. What they do to a player is in
 * {@link HollowBody}. Every rule for a Hollow player asks {@link #isHollow}, so the states that count
 * as one are decided in one place (the Arrancar keep today's rules for now).
 */
public final class HollowPlayerLogic {
    /** How hungry a Hollow is. Lower in the list is hungrier. Never sent or saved. */
    public enum Band {
        /** Fed: full regeneration and reiryoku. */
        FED,
        /** Below the ravenous mark: slower healing and reiryoku, sharper claws. */
        RAVENOUS,
        /** No soul hunger left: no healing, no sprinting, and hurt as far as the difficulty allows. */
        STARVING
    }

    // The SoulAppearance.hollowLook bits. 8 and up are kept for the Menos tiers and Cero.
    /** The jaw of the mask hangs open: the Hollow is devouring. */
    public static final int LOOK_DEVOURING = 1;
    /** The mask is cracked from a blow. */
    public static final int LOOK_MASK_CRACKED = 2;
    /** The eyes burn red: the Hollow is ravenous or starving. */
    public static final int LOOK_RAVENOUS = 4;
    /** How many mask shapes there are: 0 skull, 1 fanged, 2 horned, 3 beaked. */
    public static final int MASK_VARIANTS = 4;
    /** The food level a starving Hollow is held at: exactly the client's own limit for sprinting. */
    public static final int STARVING_FOOD_LEVEL = 6;
    /** Souls held within for the Legion Within advancement. */
    public static final int SOULS_WITHIN = 100;
    /** Soul counts at which the souls within stir, and the Hollow is told so. */
    public static final int[] SOUL_STIRRINGS = {25, 50, 100, 200};
    /** A hungry Hollow smells no soul nearer than this, which it could see for itself. */
    public static final double SCENT_MIN_DISTANCE = 12.0;

    private HollowPlayerLogic() {
    }

    /** Whether this soul is a Hollow, for every rule of the Hollow body and hunger. */
    public static boolean isHollow(SoulData data) {
        return data.state() == SoulState.HOLLOW;
    }

    /** Whether this player is a Hollow. Never gives a player without one a soul. */
    public static boolean isHollowPlayer(Player player) {
        return SoulLogic.hasSoul(player) && isHollow(SoulLogic.get(player));
    }

    /** Whether the player plays by survival's rules: in survival or adventure mode. */
    public static boolean survivalRules(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    /** The band for this much soul hunger. A hunger not yet started counts as fed. */
    public static Band band(float satiety) {
        if (satiety < 0.0F) {
            return Band.FED;
        }
        if (satiety <= 0.0F) {
            return Band.STARVING;
        }
        return satiety < BleachConfig.get(BleachConfig.RAVENOUS_BELOW) ? Band.RAVENOUS : Band.FED;
    }

    /** The band of a soul's saved hunger. */
    private static Band band(SoulData data) {
        return band(data.hollow().satiety());
    }

    /**
     * Damage the claws add to a bare-handed blow: a base and a share of the square root of maximum
     * reiryoku, up to a cap, and sharper still while ravenous or starving.
     */
    public static float clawBonus(SoulData data) {
        double root = Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        double claws = Math.min(BleachConfig.get(BleachConfig.CLAW_DAMAGE_MAX),
                BleachConfig.get(BleachConfig.CLAW_DAMAGE_BASE) + BleachConfig.get(BleachConfig.CLAW_DAMAGE_PER_SQRT) * root);
        if (band(data) != Band.FED) {
            claws *= BleachConfig.get(BleachConfig.RAVENOUS_CLAW_MULTIPLIER);
        }
        return (float) claws;
    }

    /** The attack speed modifier that makes bare-handed blows come at the configured rate, from vanilla's four a second. */
    public static double clawSpeedModifier() {
        return BleachConfig.get(BleachConfig.CLAW_ATTACKS_PER_SECOND) / 4.0 - 1.0;
    }

    /** Armour the hide gives: a base and a share of the square root of maximum reiryoku, up to a cap. */
    public static float hideArmour(SoulData data) {
        double root = Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        return (float) Math.min(BleachConfig.get(BleachConfig.HIDE_ARMOR_MAX),
                BleachConfig.get(BleachConfig.HIDE_ARMOR_BASE) + BleachConfig.get(BleachConfig.HIDE_ARMOR_PER_SQRT) * root);
    }

    /**
     * Health a Hollow regenerates a second: a base and more for every hundred maximum reiryoku, up to a
     * cap, slower while ravenous and none while starving.
     */
    public static float regenPerSecond(SoulData data) {
        Band band = band(data);
        if (band == Band.STARVING) {
            return 0.0F;
        }
        double perSecond = Math.min(BleachConfig.get(BleachConfig.HOLLOW_REGEN_MAX),
                BleachConfig.get(BleachConfig.HOLLOW_REGEN_BASE)
                        + BleachConfig.get(BleachConfig.HOLLOW_REGEN_PER_100) * Math.max(0.0F, data.maxReiryoku()) / 100.0);
        if (band == Band.RAVENOUS) {
            perSecond *= BleachConfig.get(BleachConfig.RAVENOUS_REGEN_MULTIPLIER);
        }
        return (float) perSecond;
    }

    /** How fast reiryoku refills for this soul (see ShinigamiLogic#regenMultiplier): slower for a hungry Hollow. */
    public static double reiryokuRegenMultiplier(SoulData data) {
        if (isHollow(data) && data.hollow().hungerStarted() && band(data) != Band.FED) {
            return BleachConfig.get(BleachConfig.HUNGRY_REIRYOKU_MULTIPLIER);
        }
        return 1.0;
    }

    /**
     * Whether starving hurts at this health, with vanilla's floors for starving: never on Peaceful,
     * down to half the hearts on Easy and to half a heart on Normal, and to death on Hard.
     */
    public static boolean starves(Difficulty difficulty, float health) {
        return switch (difficulty) {
            case PEACEFUL -> false;
            case EASY -> health > 10.0F;
            case NORMAL -> health > 1.0F;
            case HARD -> true;
        };
    }

    /** Soul hunger lost each tick doing nothing. */
    public static float drainPerTick() {
        return (float) (BleachConfig.get(BleachConfig.SATIETY_DRAIN_PER_MINUTE) / 1200.0);
    }

    /** Soul hunger the effort behind this much vanilla exhaustion costs. */
    public static float satietyFromExhaustion(float exhaustion) {
        return (float) (exhaustion * BleachConfig.get(BleachConfig.SATIETY_PER_EXHAUSTION));
    }

    /** The hunger a Hollow comes back from death with: what it had, but never below the floor; the start if it had none. */
    public static float respawnSatiety(HollowProgress old) {
        double had = old.hungerStarted() ? old.satiety() : BleachConfig.get(BleachConfig.SATIETY_START);
        return (float) Math.max(had, BleachConfig.get(BleachConfig.SATIETY_RESPAWN_FLOOR));
    }

    /** The souls a Hollow loses when it dies, rounded down: those its devourer takes, if anything devoured it. */
    public static int soulsLostOnDeath(int souls) {
        return (int) Math.floor(Math.max(0, souls) * BleachConfig.get(BleachConfig.DEATH_SOUL_LOSS_FRACTION));
    }

    /** The highest of {@link #SOUL_STIRRINGS} passed in going from {@code before} to {@code after} souls, or 0. */
    public static int stirring(int before, int after) {
        int passed = 0;
        for (int mark : SOUL_STIRRINGS) {
            if (before < mark && mark <= after) {
                passed = Math.max(passed, mark);
            }
        }
        return passed;
    }

    /**
     * Whether a Hollow's hide refuses this item in this slot: armour, for a Hollow player under survival's
     * rules, unless the server lets Hollows wear it or the item is one a Hollow can always wear (elytra,
     * carved pumpkins and heads). Runs on both sides, so the inventory screen agrees with the server.
     */
    public static boolean refusesArmour(LivingEntity entity, ItemStack stack, @Nullable EquipmentSlot slot) {
        return slot != null && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && entity instanceof Player player
                && survivalRules(player) && !BleachConfig.get(BleachConfig.HOLLOW_WEARS_ARMOR)
                && !stack.is(BleachTags.HOLLOW_WEARABLE) && isHollowPlayer(player);
    }

    /** Whether a Hollow cannot eat or drink this: food of any kind, unless a pack lets Hollows have it. */
    public static boolean cannotConsume(ItemStack stack, LivingEntity eater) {
        return stack.getFoodProperties(eater) != null && !stack.is(BleachTags.HOLLOW_EDIBLE);
    }

    /** Whether villagers and wandering traders turn away from this player: a Hollow under survival's rules. */
    public static boolean shunnedByVillagers(Player player) {
        return !BleachConfig.get(BleachConfig.VILLAGERS_TRADE_WITH_HOLLOWS) && survivalRules(player)
                && isHollowPlayer(player);
    }

    /** Which shape of mask a Hollow player wears: the same on every client and in every session. */
    public static int maskVariant(UUID id) {
        return Math.floorMod(id.hashCode(), MASK_VARIANTS);
    }

    /** The look bits the soul's saved data gives, so its owner's client can work them out too: red eyes while hungry. */
    public static int hollowLook(SoulData data) {
        return isHollow(data) && data.hollow().hungerStarted() && band(data) != Band.FED ? LOOK_RAVENOUS : 0;
    }

    /**
     * Every look bit, with those only the server knows: the jaw open while devouring and the mask
     * cracked after a blow. On a client's copy those are at their defaults and add nothing.
     */
    public static int hollowLook(SoulData data, long now) {
        int look = hollowLook(data);
        if (data.devourTargetId >= 0) {
            look |= LOOK_DEVOURING;
        }
        if (now < data.hollowCrackedUntil) {
            look |= LOOK_MASK_CRACKED;
        }
        return look;
    }

    /** The soul screen's next step for a Hollow: how to feed before the first meal, and what feeding builds after. */
    public static String nextStepKey(SoulData data) {
        return data.hollow().meals() > 0 ? "hollow.fed" : "hollow";
    }
}
