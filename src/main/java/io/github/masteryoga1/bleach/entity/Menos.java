package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * A Menos Grande: a great Hollow made of hundreds that devoured one another, far above any lesser
 * Hollow in power. The shared base of the Gillian and the Adjuchas.
 *
 * <p>Both gather a Cero at the mouth ({@link CeroGoal}): they stand, face their prey and let go a red
 * beam once it has gathered. A fresh crack in the mask, the jaws of a Hollow player, a retreat or a
 * reel breaks the charge, and the aim stops following the prey a little before it fires, so it can be
 * dodged. After a Cero comes a cooldown; a broken charge waits only a moment.
 *
 * <p>A Menos comes rarely: to a strong soul in place of an ordinary arrival ({@link #arrivalKind}),
 * and through the cracked sky at the end of a Hollow Bait. The server allows only so many at once,
 * spaced well apart ({@link #mayArrive}). None stays for ever: each goes back after its lifetime unless
 * it is named or kept. Whoever kills, purifies or devours one has bested a Menos Grande.
 */
public abstract class Menos extends Hollow implements CeroBeam.Caster {
    /** Synced Hollow flag (bit 16 is free after FLAG_PURIFYING 8): a Cero is gathering at the mouth. */
    public static final int FLAG_CERO_CHARGING = 16;
    /** A new Menos waits this long after its first step, and a Gillian after landing, before its first Cero. */
    public static final int CERO_FIRST_DELAY_TICKS = 80;
    /** Up to this many ticks are added at random to the cooldown, so a pack does not fire in step. */
    public static final int CERO_COOLDOWN_JITTER = 40;
    /** How long a broken charge waits before the next. */
    public static final int CERO_RETRY_TICKS = 40;
    /** How far in front of the mask's front face the Cero gathers, and how far below its middle, as shares of the mask. */
    public static final double MUZZLE_AHEAD = 0.3;
    public static final double MUZZLE_DROP = 0.2;

    private static final EntityTypeTest<Entity, Menos> ANY_MENOS = EntityTypeTest.forClass(Menos.class);
    private static final long NEVER = ShinigamiProgress.NEVER;
    private static final int TICKS_PER_MINUTE = 1200;
    private static final float LOOK_TURN = 30.0F;
    /** How far ahead the head looks along a locked aim when there is no prey to measure by. */
    private static final double LOCKED_LOOK_DISTANCE = 8.0;

    // Server-side, not saved: a Menos is never saved.
    private long ceroReadyAt = NEVER;    // set lazily to now + 80 on the first AI step (a Gillian resets it on landing)
    @Nullable
    private CeroBeam charging;           // set and cleared by CeroGoal, a nested class

    protected Menos(EntityType<? extends Menos> type, Level level) {
        super(type, level);
    }

    /** Whether a Cero is gathering at the mouth. Synced, so the model can tip the face up. */
    public boolean isChargingCero() {
        return getFlag(FLAG_CERO_CHARGING);
    }

    /** The Cero this Menos is gathering, if its charge goal started one. Known on the server only. */
    @Nullable
    public CeroBeam chargingBeam() {
        return charging;
    }

    /** Whether it is pulling itself back into the sky. Only a Gillian does. */
    public boolean isRetreating() {
        return false;
    }

    /** Whether it is still coming down from the sky. Only a Gillian does. */
    public boolean isDescending() {
        return false;
    }

    /** This kind's Cero, read from its config sub-section each time. */
    protected abstract CeroBeam.Spec ceroSpec();

    /** The closest, in blocks across the ground, the prey may be for a Cero. */
    protected abstract double ceroMinRange();

    /** The ticks between one Cero and the next. */
    protected abstract int ceroCooldownTicks();

    /** Whether nothing else keeps this Menos from gathering a Cero now. */
    protected boolean mayChargeNow() {
        return true;
    }

    /** It has stayed its lifetime: it goes back through a tear. */
    protected abstract void onLifetimeOver();

    /** No Cero for this many ticks from now. */
    protected void delayCero(int ticks) {
        ceroReadyAt = level().getGameTime() + ticks;
    }

    /** How long a Menos stays before it goes back, in ticks. */
    public static int lifetimeTicks() {
        return BleachConfig.get(BleachConfig.MENOS_LIFETIME_MINUTES) * TICKS_PER_MINUTE;
    }

    /**
     * Where the Cero gathers: just in front of the mask's face and a little below its middle, along
     * where the head faces.
     */
    @Override
    public Vec3 ceroMuzzle(float partialTick) {
        HollowKind.MaskSpot mask = kind().mask;
        double scale = getScale();
        float yaw = Mth.rotLerp(partialTick, yHeadRotO, yHeadRot);
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
        return getPosition(partialTick)
                .add(forward.scale((mask.forward() + mask.size() / 2.0 + MUZZLE_AHEAD) * scale))
                .add(0.0, (mask.up() - mask.size() * MUZZLE_DROP) * scale, 0.0);
    }

    /**
     * From the mouth to the middle of the prey, or null without live prey in sight. Sight is checked
     * afresh: a NoAI mob's cached sight is never cleared.
     */
    @Override
    @Nullable
    public Vec3 ceroAim() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !hasLineOfSight(target)) {
            return null;
        }
        Vec3 toward = target.getBoundingBox().getCenter().subtract(ceroMuzzle(1.0F));
        return toward.lengthSqr() < 1.0E-6 ? null : toward.normalize();
    }

    /** A charge holds while the Menos stands free: not held, reeling, leaving, dissolving or masking. */
    @Override
    public boolean keepsCharging() {
        return isAlive() && !isSeized() && !isRetreating() && !isPurifying() && !isDeparting() && !isMaskForming()
                && level().getGameTime() - lastReelAt >= REEL_TICKS;
    }

    @Override
    public void onCeroFired(CeroBeam.Shot shot) {
        ceroReadyAt = level().getGameTime() + ceroCooldownTicks() + random.nextInt(CERO_COOLDOWN_JITTER + 1);
        clearCharge();
    }

    @Override
    public void onCeroCancelled() {
        ceroReadyAt = level().getGameTime() + CERO_RETRY_TICKS;
        clearCharge();
    }

    private void clearCharge() {
        charging = null;
        setFlag(FLAG_CERO_CHARGING, false);
    }

    /**
     * Whether a natural arrival for a soul of this reiatsu brings a Menos instead, from two rolls in
     * [0, 1): null for none, else an Adjuchas pack for souls strong enough and the second roll, or a
     * Gillian.
     */
    @Nullable
    public static HollowKind arrivalKind(float reiatsu, double roll, double adjuchasRoll) {
        if (reiatsu < BleachConfig.get(BleachConfig.MENOS_GILLIAN_MIN_REIATSU)
                || roll >= BleachConfig.get(BleachConfig.MENOS_ARRIVAL_SHARE)) {
            return null;
        }
        if (reiatsu >= BleachConfig.get(BleachConfig.MENOS_ADJUCHAS_MIN_REIATSU)
                && adjuchasRoll < BleachConfig.get(BleachConfig.MENOS_ADJUCHAS_SHARE)) {
            return HollowKind.ADJUCHAS;
        }
        return HollowKind.GILLIAN;
    }

    /** How many Adjuchas a pack for a soul of this reiatsu brings: one more for the strongest, never past the room. */
    public static int packSize(float reiatsu, int room) {
        int size = BleachConfig.get(BleachConfig.MENOS_ADJUCHAS_PACK_SIZE)
                + (reiatsu >= BleachConfig.get(BleachConfig.MENOS_ADJUCHAS_EXTRA_AT_REIATSU) ? 1 : 0);
        return Mth.clamp(size, 1, Math.max(1, room));
    }

    /**
     * Whether a Menos may come here by chance: the server has fewer alive than it allows, and none is
     * within the spacing in this world. It looks through every entity, so it is asked only after the
     * rare roll for a Menos has passed.
     */
    public static boolean mayArrive(ServerLevel level, Vec3 at) {
        if (aliveCount(level.getServer()) >= BleachConfig.get(BleachConfig.MENOS_MAX_ALIVE)) {
            return false;
        }
        double spacing = BleachConfig.get(BleachConfig.MENOS_SPACING);
        double spacingSqr = spacing * spacing;
        return level.getEntities(ANY_MENOS, menos -> menos.isAlive() && menos.distanceToSqr(at) < spacingSqr).isEmpty();
    }

    /** The Menos alive in every world of the server. */
    public static int aliveCount(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(ANY_MENOS, Entity::isAlive).size();
        }
        return count;
    }

    /** The first step starts the wait for a first Cero; past its lifetime, a Menos nobody keeps goes back. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isRemoved() || !isAlive()) {
            return;
        }
        if (ceroReadyAt == NEVER) {
            delayCero(CERO_FIRST_DELAY_TICKS);
        }
        if (tickCount >= lifetimeTicks() && !hasCustomName() && !isPersistenceRequired()) {
            onLifetimeOver();
        }
    }

    /** A fresh crack in the mask also breaks a gathering Cero. */
    @Override
    public boolean onMaskStruck(long now) {
        boolean fresh = super.onMaskStruck(now);
        if (fresh && charging != null && charging.isCharging()) {
            charging.cancel();
        }
        return fresh;
    }

    /** A Menos goes when its lifetime ends, not when players are far. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** As every Hollow's, to the aware only; a Menos's cries also reach the shimmer band faintly. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent() && !level().isClientSide) {
            SpiritSenses.play(this, sound, getSoundSource(), volume, pitch, isScream(sound));
        }
    }

    /** Killed, purified, destroyed or devoured by a player: that player has bested a Menos Grande. */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // A cancelled death leaves it alive, and a second call does nothing: only a real first death counts.
        if (!wasDead && dead && source.getEntity() instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.MENOS_GRANDE);
        }
    }

    /** The cries of both kinds: what a soul just below awareness hears from far away. */
    private static boolean isScream(SoundEvent sound) {
        return sound == ModSounds.GILLIAN_AMBIENT.get() || sound == ModSounds.GILLIAN_HURT.get()
                || sound == ModSounds.GILLIAN_DEATH.get() || sound == ModSounds.ADJUCHAS_AMBIENT.get()
                || sound == ModSounds.ADJUCHAS_HURT.get() || sound == ModSounds.ADJUCHAS_DEATH.get();
    }

    /**
     * The charge: stand, face the prey, gather, and let go. It starts only at prey the beam would
     * strike (a Plus is eaten, not blasted), in sight, within range but not too close, with nothing
     * solid where the Cero gathers. Once started nothing interrupts it but the charge breaking: the
     * head follows the prey until the aim locks, then holds along the locked aim.
     */
    static final class CeroGoal extends Goal {
        private final Menos menos;
        @Nullable
        private CeroBeam beam;
        @Nullable
        private CeroBeam.Spec spec;
        /** Where the head looks once the aim has locked, so it no longer turns after the prey. */
        @Nullable
        private Vec3 lockedLook;

        CeroGoal(Menos menos) {
            this.menos = menos;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = menos.getTarget();
            if (target == null || !target.isAlive() || !menos.canAttack(target) || !CeroBeam.mayStrike(menos, target)) {
                return false;
            }
            long now = menos.level().getGameTime();
            if (!menos.onGround() || menos.isDescending() || !menos.keepsCharging() || menos.ceroReadyAt == NEVER
                    || now < menos.ceroReadyAt) {
                return false;
            }
            double minRange = menos.ceroMinRange();
            double dx = target.getX() - menos.getX();
            double dz = target.getZ() - menos.getZ();
            if (dx * dx + dz * dz < minRange * minRange) {
                return false;
            }
            CeroBeam.Spec next = menos.ceroSpec();
            Vec3 muzzle = menos.ceroMuzzle(1.0F);
            if (muzzle.distanceToSqr(target.getBoundingBox().getCenter()) > next.range() * next.range()
                    || !menos.hasLineOfSight(target)) {
                return false;
            }
            BlockPos mouth = BlockPos.containing(muzzle);
            if (!menos.level().getBlockState(mouth).getCollisionShape(menos.level(), mouth).isEmpty()
                    || !menos.mayChargeNow()) {
                return false;
            }
            spec = next;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return beam != null && !beam.isRemoved() && beam.isCharging();
        }

        @Override
        public void start() {
            lockedLook = null;
            CeroBeam.Spec use = spec != null ? spec : menos.ceroSpec();
            spec = use;
            beam = menos.level() instanceof ServerLevel level ? CeroBeam.charge(level, menos, use) : null;
            if (beam == null) {
                menos.onCeroCancelled();
                return;
            }
            menos.charging = beam;
            menos.setFlag(FLAG_CERO_CHARGING, true);
            menos.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (beam == null || spec == null) {
                return;
            }
            LivingEntity target = menos.getTarget();
            if (lockedLook == null && beam.tickCount >= spec.chargeTicks() - spec.aimLockTicks()) {
                Vec3 aim = menos.ceroAim();
                if (aim != null) {
                    Vec3 muzzle = menos.ceroMuzzle(1.0F);
                    double distance = target != null
                            ? Math.max(1.0, muzzle.distanceTo(target.getBoundingBox().getCenter())) : LOCKED_LOOK_DISTANCE;
                    lockedLook = muzzle.add(aim.scale(distance));
                }
            }
            if (lockedLook != null) {
                menos.getLookControl().setLookAt(lockedLook.x, lockedLook.y, lockedLook.z, LOOK_TURN, LOOK_TURN);
            } else if (target != null) {
                menos.getLookControl().setLookAt(target, LOOK_TURN, LOOK_TURN);
            }
        }

        @Override
        public void stop() {
            if (beam != null && beam.isCharging()) {
                beam.cancel();
            }
            menos.clearCharge();
            beam = null;
            spec = null;
            lockedLook = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
    }
}
