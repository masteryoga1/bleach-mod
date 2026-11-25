package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Shrieker's leech, loosed at its prey a few at a time from the hole in its chest, the Shrieker hiding how many it still
 * holds (see {@link Shrieker#looseLeeches}). It flies at the creature it was loosed at and clings to its back, set there
 * each tick on both sides and never riding it. It is no creature: one blow from a soul who sees it, or from what such a
 * soul shot or threw, makes it fall apart, in flight or clinging; every other blow is refused, so an unaware soul's arrow
 * glances off it as off any spirit. It falls apart, harmless, when its Shrieker dies or goes, when what it flies at or
 * clings to dies, goes, leaves the world or may no longer be struck by its Shrieker, when it flies or clings too long,
 * and at the Shakonmaku. Only those who see spirits see it, hear it or can strike it. Never saved, never summoned.
 */
public final class ShriekerLeech extends Entity {
    /** Its speed in flight, the share of the gap to that speed it closes each tick, and how near it must come to cling. */
    private static final double FLY_SPEED = 0.4;
    private static final double STEER = 0.25;
    private static final double REACH = 0.2;
    /** How long it may fly before it falls apart, and cling before it does. */
    public static final int FLIGHT_TICKS = 100;
    public static final int CLING_TICKS = 300;
    /**
     * Its places on a back, across and up from the middle, one for each leech of its Shrieker there. A fifth would share
     * the last, deliberately: none comes, as a Shrieker looses at most four at once and more only once those are spent.
     */
    private static final double[] ACROSS = {0.0, -0.3, 0.3, 0.0};
    private static final double[] UP = {0.15, 0.0, 0.0, -0.25};
    /** How high up its bearer's body it clings, as a share of the body's height. */
    private static final double BACK_HEIGHT = 0.65;
    private static final int NO_BEARER = -1;
    /** What it clings to, by id, and its place there: synced, so every client sets it at the same back itself. */
    private static final EntityDataAccessor<Integer> DATA_BEARER =
            SynchedEntityData.defineId(ShriekerLeech.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_SLOT =
            SynchedEntityData.defineId(ShriekerLeech.class, EntityDataSerializers.BYTE);

    // Server side, never saved: a leech never is.
    @Nullable
    private Shrieker owner;
    @Nullable
    private LivingEntity mark;
    /** Ticks since it was loosed, or since it began to cling. */
    private int stateTicks;

    public ShriekerLeech(EntityType<? extends ShriekerLeech> type, Level level) {
        super(type, level);
    }

    /** A leech its Shrieker looses at this mark from {@code at}, thrown off this way to begin with; null if refused. */
    @Nullable
    static ShriekerLeech loose(ServerLevel level, Shrieker owner, LivingEntity mark, Vec3 at, Vec3 off) {
        ShriekerLeech leech = ModEntities.SHRIEKER_LEECH.get().create(level);
        if (leech == null) {
            return null;
        }
        leech.owner = owner;
        leech.mark = mark;
        leech.setPos(at);
        leech.setDeltaMovement(off);
        return level.addFreshEntity(leech) ? leech : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BEARER, NO_BEARER);
        builder.define(DATA_SLOT, (byte) 0);
    }

    /** What it clings to, by the synced id on both sides; null while it flies, or where that side has no such creature. */
    @Nullable
    public Entity bearer() {
        int id = entityData.get(DATA_BEARER);
        return id == NO_BEARER ? null : level().getEntity(id);
    }

    /** Whether it clings to a back now, on both sides. */
    public boolean isClinging() {
        return entityData.get(DATA_BEARER) != NO_BEARER;
    }

    /** Clings to this creature's back, in the first place there free of its Shrieker's other leeches; the tests too. */
    public void clingTo(LivingEntity creature) {
        int taken = 0;
        if (owner != null) {
            for (ShriekerLeech other : owner.leeches()) {
                if (other != this && !other.isRemoved() && other.bearer() == creature) {
                    taken |= 1 << other.entityData.get(DATA_SLOT);
                }
            }
        }
        entityData.set(DATA_SLOT, (byte) Math.min(Integer.numberOfTrailingZeros(~taken), ACROSS.length - 1));
        entityData.set(DATA_BEARER, creature.getId());
        stateTicks = 0;
        place(creature);
        playSound(ModSounds.SHRIEKER_LEECH_CLING.get(), 0.8F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** Set at its bearer's back each tick on both sides; only the server moves one in flight, or lets it fall apart. */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Entity held = bearer();
            if (held != null) {
                place(held);
            }
            return;
        }
        Shrieker shrieker = owner;
        if (shrieker == null || gone(shrieker)) {
            fallApart();
            return;
        }
        stateTicks++;
        if (isClinging()) {
            cling(shrieker);
        } else {
            fly(shrieker);
        }
    }

    /**
     * Steers at its mark's middle against blocks and clings on touching it, while its Shrieker may still strike the mark;
     * it falls apart crossing the Shakonmaku.
     */
    private void fly(Shrieker shrieker) {
        LivingEntity target = mark;
        if (target == null || gone(target) || !CeroBeam.mayStrike(shrieker, target) || stateTicks > FLIGHT_TICKS) {
            fallApart();
            return;
        }
        Vec3 wanted = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize().scale(FLY_SPEED);
        Vec3 motion = getDeltaMovement().add(wanted.subtract(getDeltaMovement()).scale(STEER));
        Vec3 from = position();
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (Shakonmaku.in(level()) && Shakonmaku.bodyCrosses(from, position())) {
            fallApart();
            return;
        }
        if (motion.horizontalDistanceSqr() > 1.0E-6) {
            setYRot((float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F);
        }
        if (getBoundingBox().inflate(REACH).intersects(target.getBoundingBox())) {
            clingTo(target);
        }
    }

    /** Kept at its bearer's back while the bearer lives, stays in its world and may still be struck by its Shrieker. */
    private void cling(Shrieker shrieker) {
        if (!(bearer() instanceof LivingEntity held) || gone(held) || !CeroBeam.mayStrike(shrieker, held)
                || stateTicks > CLING_TICKS) {
            fallApart();
            return;
        }
        place(held);
    }

    /** Whether this creature is dead, gone or in another world: a player who changed worlds is the same object. */
    private boolean gone(LivingEntity creature) {
        return !creature.isAlive() || creature.level() != level();
    }

    /** Sets it at this creature's back, in its place there, facing as the creature's body faces. */
    private void place(Entity creature) {
        float yaw = creature instanceof LivingEntity living ? living.yBodyRot : creature.getYRot();
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
        int slot = entityData.get(DATA_SLOT);
        setPos(creature.position().subtract(ahead.scale((creature.getBbWidth() + getBbWidth()) * 0.5))
                .add(right.scale(ACROSS[slot]))
                .add(0.0, creature.getBbHeight() * BACK_HEIGHT + UP[slot] - getBbHeight() * 0.5, 0.0));
        setYRot(yaw);
        setDeltaMovement(Vec3.ZERO);
    }

    /** Gone, harmless, in a puff only the aware see. */
    private void fallApart() {
        if (level() instanceof ServerLevel server) {
            Vec3 at = getBoundingBox().getCenter();
            SpiritSenses.particles(server, ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.02);
        }
        discard();
    }

    /** One blow from a soul who sees it, or from what such a soul shot or threw, makes it fall apart; others are refused. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || amount <= 0.0F || !(source.getEntity() instanceof Player player)
                || !SoulLogic.seesSpirits(player)) {
            return false;
        }
        fallApart();
        return true;
    }

    /** A blow can find it: the aware's only, as the crosshair of the unaware passes through spirits. */
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /** A client sets one that clings at the back itself, taking the server's place only while it knows no bearer. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (bearer() == null) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
        }
    }

    /** Its sounds reach only those who hear spirits. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent()) {
            SpiritSenses.play(this, sound, SoundSource.HOSTILE, volume, pitch, false);
        }
    }

    /** No steps, sounds or vibrations as it moves, which the unaware would hear or feel. */
    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    /** Water thrown up by nothing would show it to those who cannot see spirits. */
    @Override
    protected void doWaterSplashEffect() {
    }

    /** No vibration it is the source of reaches a sculk sensor or a Warden, as for any spirit. */
    @Override
    public boolean dampensVibrations() {
        return true;
    }

    /** Pressure plates and tripwires never feel it, flying or clinging, as they never feel a Hollow. */
    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    /** It comes only from its Shrieker, and what it was loosed with is never saved: a copy elsewhere would be empty. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    /** Never saved (see the type's noSave). */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
