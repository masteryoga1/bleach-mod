package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Gate of Hell, which rises behind a sinner a zanpakutō has cut down, for no blade washes clean what a soul did in
 * life: two doors decked with bones rise out of the ground and swing open on the dark, a giant blade from the dark runs
 * the body through, the body is drawn in along it, and the doors slam and crumble away. Nothing is seen of whatever holds
 * the blade. It holds the body where the blow left it until it draws it in, about six seconds in all ({@link #END_AGE}).
 *
 * <p>It takes only its own body (see {@link Hollow#isHellbound}); anyone else in its way is pushed aside, never taken.
 * It is a spirit, seen, heard and felt only by the aware, with no body to strike, never saved and never summoned. Its
 * time is the world's, from a synced birth, so a soul that comes near late sees it where it is. It
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

    /** Its timeline, in ticks from the killing blow: it rises, opens, the blade comes, the body is drawn in, it slams. */
    public static final int RISE_TICKS = 40;
    public static final int OPEN_AT = RISE_TICKS;
    public static final int OPEN_TICKS = 15;
    public static final int BLADE_AT = OPEN_AT + OPEN_TICKS;
    public static final int BLADE_TICKS = 8;
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
     * Server side, each tick: it lets go of a body that went or lives again; holds the body, then draws it in; pushes
     * anyone else out of its way until it is shut; runs each step of its timeline the age has crossed, once and in order,
     * its sounds and dust and the taking, so a late tick still takes the body; and ends.
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
        if (age < TAKEN_AT) {
            pushAside(server);
        }
        if (crossed(0, age)) {
            cue(ModSounds.HELL_GATE_RISE.get());
        }
        if (age < RISE_TICKS && age % 4 == 0) {
            BlockState ground = server.getBlockState(BlockPos.containing(getX(), getY() - 0.5, getZ()));
            dust(server, ground.isAir() ? Blocks.BONE_BLOCK.defaultBlockState() : ground, 0.0, 0.1);
        }
        if (crossed(OPEN_AT, age)) {
            cue(ModSounds.HELL_GATE_OPEN.get());
        }
        if (crossed(BLADE_AT, age)) {
            cue(ModSounds.HELL_GATE_BLADE.get());
        }
        if (crossed(BLADE_AT + BLADE_TICKS, age) && body != null) {
            SpiritSenses.particles(server, ParticleTypes.SQUID_INK, body.getX(), body.getY(0.5), body.getZ(), 12, 0.3, 0.3,
                    0.3, 0.05);
        }
        if (crossed(DRAW_AT, age)) {
            cue(ModSounds.HELL_GATE_LAUGH.get());
        }
        if (crossed(SLAM_AT, age)) {
            cue(ModSounds.HELL_GATE_SLAM.get());
        }
        if (crossed(TAKEN_AT, age)) {
            if (body != null) {
                // Taken as vanilla's death takes a body. A Shrieker's leeches fell apart already, the tick after its death
                // at die, as on the purify path: they go with its life (ShriekerLeech reads !isAlive()), so the take adds
                // nothing.
                body.remove(RemovalReason.KILLED);
                body = null;
                entityData.set(DATA_BODY, -1);
            }
            cue(ModSounds.HELL_GATE_CRUMBLE.get());
        }
        if (age >= TAKEN_AT && age % 3 == 0) {
            dust(server, Blocks.BONE_BLOCK.defaultBlockState(), HEIGHT * scale() * 0.5, HEIGHT * scale() * 0.4);
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
            // The steps it jumps over make no sound.
            entityData.set(DATA_BORN_AT, server.getGameTime() - SLAM_AT);
            lastAge = SLAM_AT - 1;
        }
    }

    private boolean crossed(long step, long age) {
        return lastAge < step && step <= age;
    }

    private void cue(SoundEvent sound) {
        SpiritSenses.play(this, sound, SoundSource.HOSTILE, 1.0F, 1.0F, false);
    }

    /** Dust of this block over its whole width, this high over its foot and spread this far up and down. */
    private void dust(ServerLevel server, BlockState block, double up, double spread) {
        double half = WIDTH * scale() * 0.4;
        SpiritSenses.particles(server, new BlockParticleOption(ParticleTypes.BLOCK, block), getX(), getY() + up + 0.1,
                getZ(), 16, half, spread, half, 0.1);
    }

    /**
     * Pushes every creature but its body out of the Gate by its nearest side, as a Visored's ring pushes souls out: the
     * spirits it can touch and the souls who see spirits, never a spectator or a player in creative, and never off a
     * ledge or into lava.
     */
    private void pushAside(ServerLevel server) {
        for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, volume(), creature -> creature != body
                && creature.isAlive() && !creature.isSpectator()
                && !(creature instanceof Player player && player.isCreative())
                && SpiritSenses.bodiesTouch(this, creature))) {
            Vec3 out = outOf(other);
            if (out != null && VisoredEvents.safeOutward(server, other, out)) {
                VisoredEvents.push(other, out);
            }
        }
    }

    /** The way out of the Gate by its nearest side for a creature that stands in it, or null for one outside it. */
    @Nullable
    public Vec3 outOf(Entity entity) {
        double s = scale();
        Vec3 away = away();
        Vec3 side = new Vec3(-away.z, 0.0, away.x);
        Vec3 rel = entity.position().subtract(position());
        double reach = entity.getBbWidth() * 0.5;
        double along = rel.dot(away);
        double across = rel.dot(side);
        double deep = DEPTH * s * 0.5 + reach - Math.abs(along);
        double wide = WIDTH * s * 0.5 + reach - Math.abs(across);
        if (deep <= 0.0 || wide <= 0.0 || entity.getY() >= getY() + HEIGHT * s || entity.getBoundingBox().maxY <= getY()) {
            return null;
        }
        return deep < wide ? away.scale(along < 0.0 ? -1.0 : 1.0) : side.scale(across < 0.0 ? -1.0 : 1.0);
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

    /** Where the blade comes from, at the back of the dark, level with where the body's middle goes. */
    public Vec3 bladeFrom(Hollow taken) {
        Vec3 back = position().add(away().scale(DEPTH * scale() * 0.4));
        return new Vec3(back.x, middleFor(taken), back.z);
    }

    /** Ticks since the killing blow, on both sides, by the world's clock. */
    public long age() {
        return level().getGameTime() - entityData.get(DATA_BORN_AT);
    }

    /** How far it has risen, from 0 to 1. */
    public float rise(float partialTick) {
        return ease(Mth.clamp((age() + partialTick) / RISE_TICKS, 0.0F, 1.0F));
    }

    /** How far its doors stand open, from 0 to 1: opening, then slamming. */
    public float openness(float partialTick) {
        float age = age() + partialTick;
        if (age < SLAM_AT) {
            return Mth.clamp((age - OPEN_AT) / OPEN_TICKS, 0.0F, 1.0F);
        }
        return 1.0F - Mth.clamp((age - SLAM_AT) / SLAM_TICKS, 0.0F, 1.0F);
    }

    /** How far out of the dark the blade has come, from 0 to 1, and 0 once the doors begin to slam. */
    public float bladeReach(float partialTick) {
        float age = age() + partialTick;
        return age >= SLAM_AT ? 0.0F : Mth.clamp((age - BLADE_AT) / BLADE_TICKS, 0.0F, 1.0F);
    }

    /** How far it has crumbled away, from 0 to 1. */
    public float crumble(float partialTick) {
        return Mth.clamp((age() + partialTick - TAKEN_AT) / (END_AGE - TAKEN_AT), 0.0F, 1.0F);
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

    /** Drawn by what it is drawn as, its body and the blade between them too. */
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
