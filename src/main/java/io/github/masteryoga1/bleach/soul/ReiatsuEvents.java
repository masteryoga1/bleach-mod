package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.GateKeeper;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.entity.Runuganga;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.TrainingDummy;
import io.github.masteryoga1.bleach.entity.VastoLorde;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
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
        // After Zanjutsu, Hakuda and a blow to a Hollow's mask have strengthened the hit.
        bus.addListener(EventPriority.LOW, ReiatsuEvents::onIncomingDamage);
        bus.addListener(ReiatsuEvents::onPlayerTick);
        bus.addListener(ReiatsuEvents::onEntityJoin);
        bus.addListener(ReiatsuEvents::onChangeTarget);
    }

    /**
     * Every creature that walks by pathfinding can be frightened into running, except a Shinigami NPC,
     * a Quincy NPC, who stands their ground, Fishbone D, who has come for one soul and does not back
     * down, an Arrancar NPC, who has come to fight, an inner Hollow, which is its host's own power, a
     * gatekeeper, who keeps his post, and a Shrieker, Grand Fisher, a Vasto Lorde or Runuganga, bosses that do not leave
     * the fight they came for, and a zanpakutō's spirit drawn out, which is its wielder's own power too.
     */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof PathfinderMob mob && !(mob instanceof ShinigamiNpc)
                && !(mob instanceof QuincyNpc) && !(mob instanceof FishboneD) && !(mob instanceof ArrancarNpc)
                && !(mob instanceof InnerHollow) && !(mob instanceof GateKeeper) && !(mob instanceof Shrieker)
                && !(mob instanceof GrandFisher) && !(mob instanceof VastoLorde) && !(mob instanceof Runuganga)
                && !(mob instanceof MaterialisedSpirit)
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

    /**
     * Reiatsu difference: a soul whose reiatsu dwarfs its attacker's shrugs off most of the hit. Blut
     * Arterie makes the attacker's reiatsu count for more (see {@link QuincyLogic#reiatsuFactor}). An
     * Arrancar's Hierro is weighed here too, and only the larger of the two cuts is made, as both are the
     * same gap in reiatsu (see {@link Hierro}); the Hierro rings when it is the one that held. A Vasto Lorde's hide
     * ({@link VastoLorde#hide}) is weighed against that cut and its armour, never on top of them: the blow lands as the
     * hide leaves it, or as the cut and the armour do when together they take more.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!SoulLogic.hasSoul(victim) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        float attackerReiatsu = SoulLogic.reiatsu(attacker) * QuincyLogic.reiatsuFactor(attacker);
        float victimReiatsu = SoulLogic.reiatsu(victim);
        float difference = SoulLogic.reiatsuDamageMultiplier(attackerReiatsu, victimReiatsu);
        float hierro = Hierro.multiplier(victim, source, attackerReiatsu, victimReiatsu);
        float multiplier = Math.min(difference, hierro);
        float hide = VastoLorde.hide(victim, source, attackerReiatsu, victimReiatsu);
        if (hide < multiplier) {
            // The armour meets the blow the cut left: of that, the hide lets through only this share.
            float kept = hide / multiplier;
            event.addReductionModifier(DamageContainer.Reduction.ARMOR,
                    (container, reduction) -> Math.max(reduction, container.getNewDamage() * (1.0F - kept)));
        }
        if (multiplier < 1.0F) {
            float before = event.getAmount();
            event.setAmount(before * multiplier);
            if (hierro < difference) {
                Hierro.ring(victim, source, before - before * multiplier);
            }
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

    /**
     * One beat of the aura: everyone weak enough in range is weighed down, and the weakest flee. A
     * Hollow's or an Arrancar's aura also poisons the Quincy in it.
     */
    private static void pulse(ServerPlayer player, SoulData data) {
        float radius = SoulLogic.pressureRadius(data);
        double radiusSqr = radius * radius;
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && entity.distanceToSqr(player) <= radiusSqr && isAffected(player, entity));
        boolean crushedSomeone = false;
        long until = player.level().getGameTime() + FRIGHT_TICKS;
        boolean poisons = QuincyLogic.isHollowSource(player);
        float poison = (float) (BleachConfig.get(BleachConfig.HOLLOW_POISON_AURA_PER_SECOND) * PULSE_INTERVAL_TICKS / 20.0);
        for (LivingEntity target : nearby) {
            if (poisons && target instanceof Player other) {
                QuincyEvents.onHollowAura(other, poison);
            }
            int weight = SoulLogic.applyPressure(target, data.maxReiryoku());
            if (weight > 0) {
                crushedSomeone = true;
            }
            // A gatekeeper is weighed down as any creature is, but keeps his post.
            if (weight >= 2 && target instanceof PathfinderMob mob && !(mob instanceof GateKeeper)) {
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
     * One beat of a creature's presence, which it never chooses to release: within {@code radius} blocks it weighs on
     * every creature weak enough as a released soul of this reiatsu would, Menos and bosses too, and the weakest run.
     * The small Hollows and the Pluses it weighs on hardest also wither by {@code harm}, with no one to blame, unless
     * named, tamed or a student's practice Hollow, or the world is Peaceful. It never reaches itself, another of its own
     * kind, what cannot be hurt, a training dummy or a player in creative or spectator mode, and withers no player. One
     * search a beat. Returns how many withered.
     */
    public static int presence(Mob source, float reiatsu, double radius, float harm) {
        double radiusSqr = radius * radius;
        List<LivingEntity> nearby = source.level().getEntitiesOfClass(LivingEntity.class,
                source.getBoundingBox().inflate(radius), entity -> entity != source && entity.getType() != source.getType()
                        && entity.isAlive() && entity.distanceToSqr(source) <= radiusSqr && !entity.isInvulnerable()
                        && !(entity instanceof TrainingDummy)
                        && !(entity instanceof Player player && (player.isCreative() || player.isSpectator())));
        long until = source.level().getGameTime() + FRIGHT_TICKS;
        boolean harms = harm > 0.0F && source.level().getDifficulty() != Difficulty.PEACEFUL;
        int withered = 0;
        for (LivingEntity target : nearby) {
            int weight = SoulLogic.applyPressure(target, reiatsu);
            if (weight >= 2 && target instanceof PathfinderMob mob && !(mob instanceof GateKeeper)) {
                frighten(mob, source, until);
            }
            if (harms && weight >= 3 && withers(target) && target.hurt(source.damageSources().wither(), harm)) {
                withered++;
            }
        }
        return withered;
    }

    /**
     * Whether a presence may wither this creature: a small Hollow of the kinds that come by chance, or a Plus spirit,
     * neither named nor tamed, nor a student's practice Hollow.
     */
    private static boolean withers(LivingEntity target) {
        boolean small = target instanceof Hollow hollow ? hollow.kind().arrivalWeight > 0 && !hollow.isPractice()
                : target instanceof PlusSpirit;
        return small && !target.hasCustomName() && !(target instanceof OwnableEntity pet && pet.getOwnerUUID() != null);
    }

    /**
     * Who the aura reaches: not the releaser, what they ride or carry, or their own pets; nothing that
     * cannot be hurt (an Academy instructor, the Quincy Elder) or a training dummy; not players in
     * creative or spectator mode; and not players they could not hurt (PvP off, or a team with friendly
     * fire off).
     */
    private static boolean isAffected(ServerPlayer releaser, LivingEntity entity) {
        if (entity.isInvulnerable() || entity instanceof TrainingDummy
                || entity.getRootVehicle() == releaser.getRootVehicle()) {
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
        // A Quincy whose power burned out has no reiatsu of their own to let loose; a Shinigami's power still can.
        if (QuincyArtsLogic.isSealed(data) && !ShinigamiLogic.hasShinigamiPower(data)) {
            player.displayClientMessage(Component.translatable("message.bleach.letzt_stil.sealed_release")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (isSettling(player, data)) {
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_settling")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (ShinigamiLogic.isPowerLent(data, player.level().getGameTime())) {
            player.displayClientMessage(Component.translatable("message.bleach.power_lent")
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

    /** Holds the released reiatsu in again, with no word to the player. Also called when a Quincy's power is sealed. */
    public static void seal(ServerPlayer player, SoulData data) {
        data.setReiatsuReleased(false);
        data.lastReleaseChangeTick = player.tickCount;
    }
}
