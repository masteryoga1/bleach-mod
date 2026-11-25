package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Gate of Hell, which rises behind a sinner a zanpakutō has cut down, for no blade washes clean what a soul did in
 * life. It holds the body where the blow left it, draws it in and takes it, about six seconds in all ({@link #END_AGE}).
 *
 * <p>It takes only its own body (see {@link Hollow#isHellbound}). It is a spirit with no body to strike, never saved and
 * never summoned. Its time is the world's, from a synced birth, so a soul that comes near late sees it where it is. It
 * slams shut at once if its body goes first (a chunk unloading, Peaceful), and a body whose Gate goes first goes by
 * itself ({@link #HOLD_LIMIT}).
 */
public class HellGate extends Entity {
    private static final EntityDataAccessor<Long> DATA_BORN_AT =
            SynchedEntityData.defineId(HellGate.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(HellGate.class, EntityDataSerializers.FLOAT);
    /** The entity id of the body it takes, so a client can find it; -1 once it has none. */
    private static final EntityDataAccessor<Integer> DATA_BODY =
            SynchedEntityData.defineId(HellGate.class, EntityDataSerializers.INT);

    /** Its timeline, in ticks from the killing blow: the body is held, drawn in, the doors slam on it and it is taken. */
    public static final int DRAW_AT = 65;
    public static final int DRAW_TICKS = 20;
    public static final int SLAM_AT = DRAW_AT + DRAW_TICKS;
    public static final int SLAM_TICKS = 5;
    public static final int TAKEN_AT = SLAM_AT + SLAM_TICKS;
    /** It crumbles away after the taking, and is gone at this age. */
    public static final int END_AGE = 120;
    /** A body still held this long after its death, its Gate gone, goes by itself. */
    public static final int HOLD_LIMIT = TAKEN_AT + 20;

    /** Its size in blocks at scale 1: across, high and deep, and the doorway's height inside its frame. */
    public static final double WIDTH = 5.0;
    public static final double HEIGHT = 7.0;
    public static final double DEPTH = 2.5;
    public static final double DOORWAY_HEIGHT = 6.5;
    /** A body taller or wider than this makes its Gate bigger in step, so it always fits the doorway. */
    private static final double FITS_HEIGHT = 4.5;
    private static final double FITS_WIDTH = 3.0;
    /** How far behind the body's back the doorway rises, and how far down it looks for ground to rise from. */
    private static final double GAP = 1.0;
    private static final int GROUND_REACH = 4;

    // Server side, never saved.
    @Nullable
    private Hollow body;
    private Vec3 heldAt = Vec3.ZERO;
    private Vec3 takenTo = Vec3.ZERO;
    private long lastAge = -1L;

    public HellGate(EntityType<? extends HellGate> type, Level level) {
        super(type, level);
    }

    /**
     * Raises a Gate behind this sinner, cut down by this killer: on the far side of it from the killer (behind its back
     * with no killer), facing the killer, its foot on the ground within a few blocks below, or level with the body's feet
     * over a drop. Holds the body from now. Returns whether it rose; one that cannot rise leaves the body as it was.
     */
    public static boolean open(ServerLevel level, Hollow body, @Nullable Entity killer) {
        HellGate gate = ModEntities.HELL_GATE.get().create(level);
        if (gate == null) {
            return false;
        }
        Vec3 away = killer == null ? Vec3.ZERO : new Vec3(body.getX() - killer.getX(), 0.0, body.getZ() - killer.getZ());
        if (away.lengthSqr() < 1.0E-6) {
            away = Vec3.directionFromRotation(0.0F, body.getYRot()).multiply(-1.0, 0.0, -1.0);
        }
        away = away.normalize();
        float scale = (float) Math.max(1.0, Math.max(body.getBbHeight() / FITS_HEIGHT, body.getBbWidth() / FITS_WIDTH));
        double back = body.getBbWidth() * 0.5 + GAP + DEPTH * scale * 0.5;
        double x = body.getX() + away.x * back;
        double z = body.getZ() + away.z * back;
        gate.moveTo(x, groundUnder(level, x, body.getY(), z), z, (float) (Mth.atan2(away.x, -away.z) * Mth.RAD_TO_DEG), 0.0F);
        gate.entityData.set(DATA_BORN_AT, level.getGameTime());
        gate.entityData.set(DATA_SCALE, scale);
        gate.entityData.set(DATA_BODY, body.getId());
        gate.body = body;
        gate.heldAt = body.position();
        gate.takenTo = new Vec3(x, gate.middleFor(body) - body.getBbHeight() * 0.5, z);
        if (!level.addFreshEntity(gate)) {
            return false;
        }
        body.bindForHell();
        return true;
    }

    /** The top of the first block with a body found within {@link #GROUND_REACH} below this foot, or the foot itself. */
    private static double groundUnder(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y - 1.0E-3, z).mutable();
        for (int i = 0; i <= GROUND_REACH; i++, pos.move(Direction.DOWN)) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return Math.min(y, pos.getY() + shape.max(Direction.Axis.Y));
            }
        }
        return y;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BORN_AT, 0L);
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_BODY, -1);
    }

    /**
     * Server side, each tick: it lets go of a body that went or lives again; holds the body, then draws it in; runs each
     * step of its timeline the age has crossed, once and in order, so a late tick still takes the body; and ends.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (body != null && (body.isRemoved() || body.level() != server || !body.isDeadOrDying())) {
            letGo(server);
        }
        long age = age();
        if (body != null) {
            Vec3 at = age < DRAW_AT ? heldAt
                    : heldAt.lerp(takenTo, ease(Math.min(1.0F, (age - DRAW_AT) / (float) DRAW_TICKS)));
            body.setPos(at.x, at.y, at.z);
            body.setDeltaMovement(Vec3.ZERO);
        }
        if (crossed(TAKEN_AT, age) && body != null) {
            // Taken as vanilla's death takes a body. A Shrieker's leeches fell apart already, the tick after its death at
            // die, as on the purify path: they go with its life (ShriekerLeech reads !isAlive()), so the take adds nothing.
            body.remove(RemovalReason.KILLED);
            body = null;
            entityData.set(DATA_BODY, -1);
        }
        lastAge = age;
        if (age >= END_AGE) {
            discard();
        }
    }

    /**
     * Its body went before it was taken, or its death was undone and it lives again, so it is let go: it slams shut now.
     */
    private void letGo(ServerLevel server) {
        if (body.isAlive()) {
            body.unbindFromHell();
        }
        body = null;
        entityData.set(DATA_BODY, -1);
        if (age() < SLAM_AT) {
            // Its birth jumps so that it slams at once, which is meant: the body is gone, so there is nothing to wait for.
            entityData.set(DATA_BORN_AT, server.getGameTime() - SLAM_AT);
        }
    }

    private boolean crossed(long step, long age) {
        return lastAge < step && step <= age;
    }

    /** What it stands in: square, as it may face any way. */
    private AABB volume() {
        double half = WIDTH * scale() * 0.5;
        return new AABB(getX() - half, getY(), getZ() - half, getX() + half, getY() + HEIGHT * scale(), getZ() + half);
    }

    /** The way from its doorway into its dark, flat: the way it faces, turned about. */
    public Vec3 away() {
        return Vec3.directionFromRotation(0.0F, getYRot()).multiply(-1.0, 0.0, -1.0);
    }

    /** How high a body's middle is held in the Gate's dark: its own height, kept within the doorway. */
    public double middleFor(Hollow taken) {
        double half = taken.getBbHeight() * 0.5;
        return Mth.clamp(taken.getY() + half, getY() + half, getY() + DOORWAY_HEIGHT * scale() - half);
    }

    /** Ticks since the killing blow, on both sides, by the world's clock. */
    public long age() {
        return level().getGameTime() - entityData.get(DATA_BORN_AT);
    }

    /** For game tests: moves its birth this many ticks earlier, as a chunk that stopped ticking that long would. */
    public void ageForTest(int ticks) {
        entityData.set(DATA_BORN_AT, entityData.get(DATA_BORN_AT) - ticks);
    }

    public float scale() {
        return entityData.get(DATA_SCALE);
    }

    /** The body it takes, while it has one: the server's own, or on a client the one its synced id names. */
    @Nullable
    public Hollow body() {
        if (!level().isClientSide) {
            return body;
        }
        return level().getEntity(entityData.get(DATA_BODY)) instanceof Hollow taken && !taken.isRemoved() ? taken : null;
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** Drawn by what it is drawn as, its body too. */
    @Override
    public AABB getBoundingBoxForCulling() {
        Hollow taken = body();
        return taken == null ? volume() : volume().minmax(taken.getBoundingBox());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    /** No steps, sounds or vibrations, which the unaware would hear or feel. */
    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    /** Water thrown up by nothing would show it to those who cannot see spirits. */
    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    /** It stands where its body died, and what it holds is never saved: a copy elsewhere would hold nothing. */
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
