package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Bakudō of {@link KidoSpell.Shape#SMOKE}, such as #21 Sekienton: a cloud of smoke burst out where its caster
 * stood, which stays there for its ward's {@code seconds} while the caster, quickened, gets away. The cloud is an
 * upright cylinder of the spell's radius about the cast point, from a block below the caster's feet to as far above
 * them as its radius. It pulses at the cast and every {@link #PULSE_TICKS} ticks after: each creature whose eyes are in
 * it loses the caster ({@link #loseSight}), dropping it as a target and unable to pick it out again for the ward's
 * {@code lost_seconds} ({@link #lost}, asked through {@link SpiritEvents#cannotPick}), and each player in it whom the
 * caster may harm is blinded for the spell's {@code blind_seconds}, both cut by the reiatsu gap and both refreshed by
 * every pulse that finds them there. A caster duelling its Hollow raises a smoke that does nothing to anyone else, and
 * a duelling player is never blinded by another's.
 *
 * <p>A creature holds one mark, so a second caster's smoke replaces the first's. A Warden's anger, from scent and
 * sound, asks nothing of the mark, so a Warden may still find the caster in the smoke; and a caster standing in its
 * own cloud is not hidden from those outside it.
 *
 * <p>The cloud is a transient on the caster's soul, ticked from its caster's own tick ({@link KidoEvents}), and the
 * mark an attachment on the creature ({@link SoulAttachments#LOST}): neither is ever saved. The cloud ends with its
 * time, its caster's death, its caster leaving its world, and a relog; a caster bound, held or lent keeps it, since the
 * smoke has left the hands. Server side only. What it does anyone sees; the smoke itself only those who see spirits.
 */
public final class KidoSmoke {
    /** Ticks between pulses: twenty in a four-second cloud. */
    public static final int PULSE_TICKS = 4;
    /** How far below its caster's feet the cloud reaches, in blocks. */
    private static final double FLOOR = 1.0;
    /** Motes of the spell's colour, and puffs of its particle, thrown out at the cast and at each pulse. */
    private static final int BURST_MOTES = 48;
    private static final int BURST_PUFFS = 16;
    private static final int PULSE_MOTES = 10;
    private static final int PULSE_PUFFS = 3;
    /** A mote's size: large enough to live from one pulse to the next, so the cloud stays thick. */
    private static final float MOTE_SIZE = 3.5F;
    private static final double PUFF_SPEED = 0.02;
    /** How widely the smoke is drawn across the cloud and up it, as shares of its radius, about a point low in it. */
    private static final double SPREAD_ACROSS = 0.5;
    private static final double SPREAD_UP = 0.25;
    private static final float TICKS_PER_SECOND = 20.0F;

    private KidoSmoke() {
    }

    /**
     * A smoke standing where it was cast: its world and its centre (the caster's feet then), the spell and the share it
     * was cast at, the game time it is gone, and the game time of its next pulse.
     */
    public record Cloud(ResourceKey<Level> dimension, Vec3 centre, KidoSpell spell, float share, long endsAt,
                        long nextPulseAt) {
    }

    /** A creature lost in a Kidō smoke: it cannot pick out the soul with this UUID until this game time. */
    public record Lost(UUID from, long until) {
        /** No mark: lost to no one. */
        public static final Lost NONE = new Lost(Util.NIL_UUID, Long.MIN_VALUE);
    }

    // ---- The cloud ----

    /**
     * Bursts this smoke spell's cloud out where the caster stands, at this share, as {@link KidoEvents#cast} does once
     * the spell is paid for: the cloud stands from now for its ward's seconds, the hand swings, the spell's sound and
     * a burst of motes in its colour and of its particle go off for those who see spirits, the caster is quickened for
     * its ward's Speed, and the first pulse goes off at once. The cloud's own time is not cut by the share; what it
     * does to others, and the Speed, are. A second cloud replaces the first.
     */
    public static void raise(ServerLevel level, Player caster, SoulData data, KidoSpell spell, float share, long now) {
        KidoSpell.Ward ward = spell.effect().ward();
        Cloud cloud = new Cloud(level.dimension(), caster.position(), spell, share,
                now + Math.round(ward.seconds() * TICKS_PER_SECOND), now + PULSE_TICKS);
        data.kidoSmoke = cloud;
        caster.swing(InteractionHand.MAIN_HAND, true);
        KidoSpell.Look look = spell.look();
        Vec3 centre = cloud.centre();
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                centre.x, centre.y, centre.z, sound, SoundSource.PLAYERS, 1.0F, look.pitch(), false));
        draw(level, cloud, BURST_MOTES, BURST_PUFFS);
        int speedTicks = Math.round(ward.speedSeconds() * TICKS_PER_SECOND * share);
        if (ward.speedLevel() > 0 && speedTicks > 0) {
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedTicks, ward.speedLevel() - 1, false,
                    false, true));
        }
        pulse(level, caster, cloud, now);
    }

    /**
     * A smoke caster's tick: the cloud is dropped once its time is up, its caster is dead or its caster is in another
     * world, and otherwise pulses when its pulse is due, the next then due {@link #PULSE_TICKS} from now. Under a
     * frozen tick rate the clock stands still, so the cloud waits and never pulses twice at one time. Does nothing
     * with no cloud. Called by the tick listener and by tests.
     */
    public static void tick(Player caster, SoulData data, long now) {
        Cloud cloud = data.kidoSmoke;
        if (cloud == null) {
            return;
        }
        if (now >= cloud.endsAt() || !caster.isAlive() || !(caster.level() instanceof ServerLevel level)
                || !level.dimension().equals(cloud.dimension())) {
            data.kidoSmoke = null;
            return;
        }
        if (now >= cloud.nextPulseAt()) {
            Cloud next = new Cloud(cloud.dimension(), cloud.centre(), cloud.spell(), cloud.share(), cloud.endsAt(),
                    now + PULSE_TICKS);
            data.kidoSmoke = next;
            pulse(level, caster, next, now);
        }
    }

    /** The smoke this soul cast, while it stands, or null. Server only (the field is transient). */
    @Nullable
    public static Cloud cloud(SoulData data) {
        return data.kidoSmoke;
    }

    /**
     * Whether this creature is in the cloud: in its world, with its eyes within the cloud's radius across from where it
     * was cast, and between a block below its caster's feet then and its radius above them. A giant's eyes stand above
     * the smoke, and see over it.
     */
    public static boolean inside(Cloud cloud, LivingEntity creature) {
        if (!creature.level().dimension().equals(cloud.dimension())) {
            return false;
        }
        double radius = cloud.spell().effect().radius();
        Vec3 centre = cloud.centre();
        Vec3 eyes = creature.getEyePosition();
        double x = eyes.x - centre.x;
        double z = eyes.z - centre.z;
        return x * x + z * z <= radius * radius && eyes.y >= centre.y - FLOOR && eyes.y <= centre.y + radius;
    }

    /**
     * What one pulse does to one creature (see {@link #pulse}), which this does not ask is in the cloud: nothing to the
     * caster, to the dead, or to anyone while the caster duels its Hollow. A mob loses the caster for the ward's lost
     * seconds at the cloud's share ({@link #lostTicks}, {@link #loseSight}). A player the caster may strike
     * ({@link CeroBeam#mayStrike}), that Negación does not hold and that duels no Hollow is blinded for the spell's
     * blind seconds at the share ({@link KidoLogic#blindTicks}), with the caster as the source, as a gust blinds. Both
     * are cut by the reiatsu gap, without Hierro ({@link KidoLogic#gap(Player, LivingEntity)}). Returns whether it
     * took hold: the mob marked lost, or the player blinded. Public for tests, whose mock players no pulse can find.
     */
    public static boolean smokeOn(Player caster, Cloud cloud, LivingEntity target, long now) {
        // The duel's shield keeps the Hollow's fight to the two of them, as it keeps every blow out.
        if (target == caster || !target.isAlive() || VisoredEvents.isDuelling(caster)) {
            return false;
        }
        float gap = KidoLogic.gap(caster, target);
        if (target instanceof Mob mob) {
            int ticks = lostTicks(cloud.spell(), cloud.share(), gap);
            // The inner Hollow is spared here too, so it is never reported as lost.
            if (ticks <= 0 || mob instanceof InnerHollow) {
                return false;
            }
            loseSight(mob, caster, now + ticks);
            return true;
        }
        if (target instanceof Player player && CeroBeam.mayStrike(caster, player) && !Negacion.held(player)
                && !VisoredEvents.isDuelling(player)) {
            int ticks = KidoLogic.blindTicks(cloud.spell(), cloud.share(), gap);
            return ticks > 0 && player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false,
                    true), caster);
        }
        return false;
    }

    // ---- The lost mark ----

    /**
     * This mob loses sight of that creature until this game time ({@link #markLost}), so it cannot pick it out again
     * meanwhile. If that creature was its target, every target goal it was running is stopped, since a running one
     * would set its own remembered target straight back, and the target is cleared; a brain forgets it as its attack
     * target too. Once a target is dropped, a creeper's swell and a ranged attack, which keep a target of their own,
     * are stopped as well, and a creeper not lit by hand lets its fuse die down. An inner Hollow is spared, as the
     * binds spare it: its host's fight is its own. Returns whether a target was dropped.
     */
    public static boolean loseSight(Mob mob, LivingEntity from, long until) {
        if (mob instanceof InnerHollow) {
            return false;
        }
        markLost(mob, from, until);
        boolean dropped = false;
        if (mob.getTarget() == from) {
            stopRunning(mob.targetSelector, goal -> goal instanceof TargetGoal);
            if (mob.getTarget() == from) {
                mob.setTarget(null);
            }
            dropped = true;
        }
        // Creatures moved by a brain keep their target there instead.
        if (mob.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, from)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            dropped = true;
        }
        if (dropped) {
            // A running swell would hiss on at its own target, and a witch would go on throwing at hers.
            stopRunning(mob.goalSelector, goal -> goal instanceof SwellGoal || goal instanceof RangedAttackGoal);
            if (mob instanceof Creeper creeper && !creeper.isIgnited()) {
                creeper.setSwellDir(-1);
            }
        }
        return dropped;
    }

    /** Marks this creature lost to that one until this game time, in place of any mark it had: one caster at a time. */
    public static void markLost(LivingEntity looker, LivingEntity from, long until) {
        looker.setData(SoulAttachments.LOST, new Lost(from.getUUID(), until));
    }

    /**
     * Whether this creature is lost in a smoke the target cast, and so cannot pick it out: marked lost to it, and the
     * mark not yet run out. Every target search asks this, so it reads nothing from a creature never marked.
     */
    public static boolean lost(LivingEntity looker, @Nullable LivingEntity target) {
        if (target == null || !looker.hasData(SoulAttachments.LOST)) {
            return false;
        }
        Lost lost = looker.getData(SoulAttachments.LOST);
        return lost.until() > looker.level().getGameTime() && lost.from().equals(target.getUUID());
    }

    /**
     * Ticks a smoke of this spell leaves a creature lost at this share and gap: its ward's lost seconds at the share
     * and cut by the gap, rounded; 0 for none.
     */
    public static int lostTicks(KidoSpell spell, float share, float gap) {
        return Math.max(0, Math.round(spell.effect().ward().lostSeconds() * TICKS_PER_SECOND * share * gap));
    }

    // ---- Helpers ----

    /** One pulse: the smoke drawn again, then each creature whose eyes are in the cloud caught in it. */
    private static void pulse(ServerLevel level, Player caster, Cloud cloud, long now) {
        draw(level, cloud, PULSE_MOTES, PULSE_PUFFS);
        // A body's box holds its eyes, so the cylinder's own box finds every creature that may be in it.
        for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class, box(cloud),
                candidate -> inside(cloud, candidate))) {
            smokeOn(caster, cloud, creature, now);
        }
    }

    /** The box about the cloud's cylinder. */
    private static AABB box(Cloud cloud) {
        double radius = cloud.spell().effect().radius();
        Vec3 centre = cloud.centre();
        return new AABB(centre.x - radius, centre.y - FLOOR, centre.z - radius, centre.x + radius, centre.y + radius,
                centre.z + radius);
    }

    /**
     * The smoke, for those who see spirits: motes of the spell's colour, large enough to linger from one pulse to the
     * next, and puffs of its particle (none when it has none), spread over the lower cloud.
     */
    private static void draw(ServerLevel level, Cloud cloud, int motes, int puffs) {
        KidoSpell.Look look = cloud.spell().look();
        double radius = cloud.spell().effect().radius();
        double across = radius * SPREAD_ACROSS;
        double up = radius * SPREAD_UP;
        Vec3 at = cloud.centre().add(0.0, up, 0.0);
        SpiritSenses.particles(level, new DustParticleOptions(Vec3.fromRGB24(look.colour()).toVector3f(), MOTE_SIZE),
                at.x, at.y, at.z, motes, across, up, across, PUFF_SPEED);
        SimpleParticleType particle = KidoEvents.particleOf(look);
        if (particle != null) {
            SpiritSenses.particles(level, particle, at.x, at.y, at.z, puffs, across, up, across, PUFF_SPEED);
        }
    }

    /** Stops every goal of this selector that is running and matches, gathered first, as a stop changes what runs. */
    private static void stopRunning(GoalSelector selector, Predicate<Goal> which) {
        List<WrappedGoal> running = selector.getAvailableGoals().stream()
                .filter(goal -> goal.isRunning() && which.test(goal.getGoal()))
                .toList();
        running.forEach(WrappedGoal::stop);
    }
}
