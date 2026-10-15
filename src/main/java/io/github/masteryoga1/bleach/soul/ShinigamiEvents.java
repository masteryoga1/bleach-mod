package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-side hooks for the Shinigami path, and the things Shinigami do for others: enrolment,
 * the exams, Konsō and the Asauchi. The rules themselves are in {@link ShinigamiLogic}. Everything
 * here takes a {@link Player}, so game tests can use mock players.
 */
public final class ShinigamiEvents {
    private static final int KONSO_RING_POINTS = 16;
    private static final double KONSO_RING_RADIUS = 0.8;

    private ShinigamiEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ShinigamiEvents::onRespawn);
    }

    /** A Substitute who died is told what their power made of them. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (data.becameShinigami) {
            data.becameShinigami = false;
            player.sendSystemMessage(Component.translatable("message.bleach.became_shinigami")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    // ---- The Academy ----

    /** A Rukongai soul enrolls at the Academy and is handed an Asauchi. */
    public static boolean enroll(Player player) {
        SoulData data = SoulLogic.get(player);
        if (!ShinigamiLogic.canEnroll(data)) {
            return false;
        }
        ShinigamiLogic.enroll(data);
        giveAsauchi(player, data);
        playSound(player, SoundEvents.BOOK_PAGE_TURN);
        player.sendSystemMessage(Component.translatable("message.bleach.enrolled"));
        return true;
    }

    /** A student sits the exams: on a pass they become a Shinigami and receive the Shihakushō. */
    public static boolean graduate(Player player) {
        SoulData data = SoulLogic.get(player);
        if (!ShinigamiLogic.isStudent(data)) {
            return false;
        }
        List<Stat> missing = ShinigamiLogic.graduationShortfall(data);
        if (!missing.isEmpty()) {
            Component arts = ComponentUtils.formatList(missing.stream().map(Stat::displayName).toList(),
                    Component.literal(", "));
            player.displayClientMessage(Component.translatable("message.bleach.graduation_failed", arts), true);
            return false;
        }
        ShinigamiLogic.graduate(data);
        SoulLogic.applyStatModifiers(player, data);
        give(player, new ItemStack(ModItems.SHIHAKUSHO_KOSODE.get()));
        give(player, new ItemStack(ModItems.SHIHAKUSHO_HAKAMA.get()));
        give(player, new ItemStack(ModItems.WARAJI.get()));
        playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        player.sendSystemMessage(Component.translatable("message.bleach.graduated").withStyle(ChatFormatting.GOLD));
        if (player instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.GRADUATED);
        }
        return true;
    }

    /** Whether the Academy would hand this soul a new Asauchi: the wait is over and they carry no blade. */
    public static boolean mayReceiveAsauchi(Player player, SoulData data, long now) {
        return ShinigamiLogic.canReissueAsauchi(data, now) && !player.getInventory().contains(BleachTags.ZANPAKUTO);
    }

    /** The instructor replaces a lost Asauchi, at most once every ten minutes. */
    public static boolean reissueAsauchi(Player player) {
        SoulData data = SoulLogic.get(player);
        if (player.getInventory().contains(BleachTags.ZANPAKUTO)) {
            player.displayClientMessage(Component.translatable("message.bleach.asauchi_already"), true);
            return false;
        }
        if (!ShinigamiLogic.canReissueAsauchi(data, player.level().getGameTime())) {
            player.displayClientMessage(Component.translatable("message.bleach.asauchi_not_yet"), true);
            return false;
        }
        giveAsauchi(player, data);
        playSound(player, SoundEvents.ARMOR_EQUIP_IRON.value());
        player.displayClientMessage(Component.translatable("message.bleach.asauchi_reissued"), true);
        return true;
    }

    /** Hands over a new Asauchi and remembers when. */
    public static void giveAsauchi(Player player, SoulData data) {
        give(player, new ItemStack(ModItems.ASAUCHI.get()));
        data.setShinigami(data.shinigami().withAsauchiIssuedAt(player.level().getGameTime()));
    }

    /** Puts an item in the player's inventory, or at their feet if it is full. */
    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    // ---- Konsō ----

    /**
     * Konsō: the pommel touches the Plus's forehead, its chain falls away and its soul passes on. A
     * player who performs it grows a little stronger, up to a daily limit.
     *
     * @param performer who sends the Plus on: a player, a Shinigami NPC, or null
     * @param sendOn    whether the soul is sent on to the Academy
     * @return false, changing nothing, if the soul was not a Plus
     */
    public static boolean konso(@Nullable LivingEntity performer, Player plus, boolean sendOn) {
        SoulData data = SoulLogic.get(plus);
        if (!ShinigamiLogic.konso(data)) {
            return false;
        }
        konsoEffects(plus);
        plus.sendSystemMessage(Component.translatable("message.bleach.konso.received")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (performer instanceof Player player && SoulLogic.hasSoul(player)) {
            ShinigamiLogic.rewardKonso(SoulLogic.get(player), player.level().getGameTime());
            player.displayClientMessage(Component.translatable("message.bleach.konso.performed", plus.getDisplayName()), true);
            if (player instanceof ServerPlayer serverPlayer) {
                Milestones.grant(serverPlayer, Milestones.KONSO);
            }
        }
        return true;
    }

    /** A seal ring at the feet, soul wisps sinking into it, and one dark puff where the chain was. */
    private static void konsoEffects(Player plus) {
        if (!(plus.level() instanceof ServerLevel level)) {
            return;
        }
        double x = plus.getX();
        double y = plus.getY() + 0.05;
        double z = plus.getZ();
        for (int i = 0; i < KONSO_RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / KONSO_RING_POINTS;
            level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(angle) * KONSO_RING_RADIUS, y,
                    z + Math.sin(angle) * KONSO_RING_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.SOUL, x, plus.getY() + 1.0, z, 6, 0.25, 0.4, 0.25, 0.01);
        level.sendParticles(ParticleTypes.SQUID_INK, x, plus.getY() + 1.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ---- The Substitute route ----

    /** The wounded Shinigami pours her power into a Human, whose own power awakens. */
    public static boolean acceptPowerFrom(WoundedShinigami giver, Player human) {
        SoulData data = SoulLogic.get(human);
        if (giver.gavePower() || !ShinigamiLogic.canReceivePower(data)) {
            return false;
        }
        ShinigamiLogic.awaken(data, BleachConfig.get(BleachConfig.AWAKENING_GROWTH));
        SoulLogic.applyStatModifiers(human, data);
        giveAsauchi(human, data);
        giver.markGavePower();
        powerEffects(human);
        human.sendSystemMessage(Component.translatable("message.bleach.power.received").withStyle(ChatFormatting.AQUA));
        return true;
    }

    private static void powerEffects(Player human) {
        if (human.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, human.getX(), human.getY() + 1.0, human.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
        }
        playSound(human, SoundEvents.BEACON_POWER_SELECT);
    }

    private static void playSound(Player player, SoundEvent sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
