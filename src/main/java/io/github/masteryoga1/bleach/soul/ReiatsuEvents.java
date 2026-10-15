package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/** Reiatsu as a force: weak attackers barely scratch a strong soul, and released reiatsu crushes the weak. */
public final class ReiatsuEvents {
    private static final int PULSE_INTERVAL_TICKS = 10;
    /** How long a soul must wait after holding its reiatsu in before releasing it again. */
    public static final int RELEASE_COOLDOWN_TICKS = 40;
    /** Presses closer together than this are ignored, so a held or repeated key cannot spam the toggle. */
    private static final int MIN_TOGGLE_INTERVAL_TICKS = 5;
    /** How long one pulse keeps a creature running. */
    private static final int FRIGHT_TICKS = SoulLogic.PRESSURE_EFFECT_TICKS;
    private static final DustParticleOptions REIATSU_PARTICLE =
            new DustParticleOptions(new Vector3f(0.56F, 0.85F, 1.0F), 1.5F);

    private ReiatsuEvents() {
    }

    public static void register(IEventBus bus) {
        // After Zanjutsu and Hakuda have strengthened the hit.
        bus.addListener(EventPriority.LOW, ReiatsuEvents::onIncomingDamage);
        bus.addListener(ReiatsuEvents::onPlayerTick);
        bus.addListener(ReiatsuEvents::onEntityJoin);
        bus.addListener(ReiatsuEvents::onChangeTarget);
    }

    /** Every creature that walks by pathfinding can be frightened into running. */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof PathfinderMob mob
                && mob.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof ReiatsuFleeGoal)) {
            mob.goalSelector.addGoal(0, new ReiatsuFleeGoal(mob));
        }
    }

    /**
     * A creature running from released reiatsu does not pick a new target. Brains would otherwise pick
     * one straight back each tick and flicker between fighting and idling (see {@link ReiatsuFleeGoal}).
     */
    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getTargetType() == LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET
                && event.getNewAboutToBeSetTarget() != null
                && event.getEntity() instanceof PathfinderMob mob && ReiatsuFleeGoal.isFleeing(mob)) {
            event.setCanceled(true);
        }
    }

    /** Reiatsu difference: a soul whose reiatsu dwarfs its attacker's shrugs off most of the hit. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!SoulLogic.hasSoul(victim) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        float multiplier = SoulLogic.reiatsuDamageMultiplier(SoulLogic.reiatsu(attacker), SoulLogic.reiatsu(victim));
        if (multiplier < 1.0F) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (!data.isReiatsuReleased()) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            seal(player, data);
            return;
        }
        if (!SoulLogic.drain(player, data, SoulLogic.releaseDrainPerTick(data))) {
            seal(player, data);
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_exhausted")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (player.tickCount % PULSE_INTERVAL_TICKS == 0) {
            pulse(player, data);
        }
    }

    /** One beat of the aura: everyone weak enough in range is weighed down, and the weakest flee. */
    private static void pulse(ServerPlayer player, SoulData data) {
        float radius = SoulLogic.pressureRadius(data);
        double radiusSqr = radius * radius;
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && entity.distanceToSqr(player) <= radiusSqr && isAffected(player, entity));
        boolean crushedSomeone = false;
        long until = player.level().getGameTime() + FRIGHT_TICKS;
        for (LivingEntity target : nearby) {
            int weight = SoulLogic.applyPressure(target, data.maxReiryoku());
            if (weight > 0) {
                crushedSomeone = true;
            }
            if (weight >= 2 && target instanceof PathfinderMob mob) {
                frighten(mob, player, until);
            }
        }
        if (crushedSomeone) {
            Milestones.grant(player, Milestones.CRUSHING_PRESSURE);
        }
        player.serverLevel().sendParticles(REIATSU_PARTICLE, player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.6, 0.9, 0.6, 0.02);
    }

    /**
     * Who the aura reaches: not the releaser, what they ride or carry, or their own pets; not players
     * in creative or spectator mode; and not players they could not hurt (PvP off, or a team with
     * friendly fire off).
     */
    private static boolean isAffected(ServerPlayer releaser, LivingEntity entity) {
        if (entity.getRootVehicle() == releaser.getRootVehicle()) {
            return false;
        }
        if (entity instanceof OwnableEntity pet && releaser.getUUID().equals(pet.getOwnerUUID())) {
            return false;
        }
        if (entity instanceof Player other) {
            return !other.isCreative() && !other.isSpectator() && releaser.canHarmPlayer(other);
        }
        return true;
    }

    /** The creature drops its grudge against the releaser and runs (see {@link ReiatsuFleeGoal}). */
    public static void frighten(PathfinderMob mob, LivingEntity from, long until) {
        if (mob.getTarget() == from) {
            mob.setTarget(null);
        }
        // Creatures moved by a brain keep their target there instead.
        if (mob.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, from)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        // A hissing creeper lets its fuse die down, as when its target gets away; one lit with flint
        // and steel still blows.
        if (mob instanceof Creeper creeper && !creeper.isIgnited()) {
            creeper.setSwellDir(-1);
        }
        mob.setData(SoulAttachments.FRIGHT, new Fright(until, from.position()));
    }

    /**
     * Whether the soul's reiatsu is still settling after a release or seal. While it settles, the
     * reiatsu cannot be released again and reiryoku does not refill.
     */
    public static boolean isSettling(Player player, SoulData data) {
        return player.tickCount - data.lastReleaseChangeTick < RELEASE_COOLDOWN_TICKS;
    }

    /**
     * Called when the player presses the release key. The server decides whether it can happen.
     * Releasing costs as much reiryoku up front as one pulse's weight lasts and pulses at once, and
     * after holding it in the soul's reiatsu settles for {@link #RELEASE_COOLDOWN_TICKS}, with no
     * refilling and no new release (see {@link #isSettling}), so flicking the aura on and off is never
     * cheaper than holding it.
     */
    public static void toggleRelease(ServerPlayer player) {
        SoulData data = SoulLogic.get(player);
        int sinceLastChange = player.tickCount - data.lastReleaseChangeTick;
        if (sinceLastChange < MIN_TOGGLE_INTERVAL_TICKS) {
            return;
        }
        if (data.isReiatsuReleased()) {
            seal(player, data);
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_sealed")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (isSettling(player, data)) {
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_settling")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        float cost = SoulLogic.releaseDrainPerTick(data) * SoulLogic.PRESSURE_EFFECT_TICKS;
        if (!player.isAlive() || player.isSpectator() || !SoulLogic.canRelease(data)
                || data.reiryoku() - cost < 0.5F) {
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_too_weak")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        data.setReiryoku(data.reiryoku() - cost);
        data.setReiatsuReleased(true);
        data.lastReleaseChangeTick = player.tickCount;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.bleach.reiatsu_released")
                .withStyle(ChatFormatting.AQUA), true);
        pulse(player, data);
    }

    private static void seal(ServerPlayer player, SoulData data) {
        data.setReiatsuReleased(false);
        data.lastReleaseChangeTick = player.tickCount;
    }
}
