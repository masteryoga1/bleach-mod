package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Getsuga Tenshō: a crescent of reiatsu swung out from Zangetsu's edge. It flies straight at
 * {@link #SPEED} blocks a tick for a set number of ticks, cuts through a few creatures in its path
 * (a Hollow it kills is purified), knocks other souls' arrows out of the air, carves soft blocks
 * where griefing and building rights allow and stops at anything harder. The server moves it and
 * decides everything it does; a client only slides it along. It goes when its owner does, and is
 * never saved.
 */
public class GetsugaTensho extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("getsuga_tensho"));
    /** Blocks a tick. */
    public static final float SPEED = 1.6F;
    /** How far in front of where the swing starts the crescent appears, in blocks. */
    public static final double SWING_REACH = 1.0;
    /** The colour bit that makes a crescent the black one with a red edge, above the 24 bits of the blade's colour. */
    public static final int BLACK = 1 << 24;

    private static final EntityDataAccessor<Float> DATA_CHARGE =
            SynchedEntityData.defineId(GetsugaTensho.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOUR =
            SynchedEntityData.defineId(GetsugaTensho.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(GetsugaTensho.class, EntityDataSerializers.INT);

    /** The life of a crescent at the default range of 40 blocks, until {@link #launch} sets its own. */
    private static final int DEFAULT_LIFE_TICKS = 25;
    /** How far apart, in blocks, the crescent looks for blocks to carve along its path. */
    private static final double CARVE_STEP = 0.5;
    /** Drawn out to the distance it is tracked at: eight chunks. */
    private static final double RENDER_DISTANCE = 128.0;

    private float damage;
    private int pierceLeft;
    private int blocksLeft;
    private int age;
    private ItemStack blade = ItemStack.EMPTY;
    /** Where the swing started: nothing wholly behind it is ever touched. Unset on a crescent not launched. */
    @Nullable
    private Vec3 root;
    /**
     * The way the swing faced on the level, for judging what is behind the wielder as well as the
     * path does. Zero on a crescent not launched.
     */
    private Vec3 facing = Vec3.ZERO;
    /** The creatures already cut, so each is struck once however many ticks it stays in the path. */
    private final IntOpenHashSet struck = new IntOpenHashSet();

    public GetsugaTensho(EntityType<? extends GetsugaTensho> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * Swings a crescent out along {@code direction} from {@code root}, where the swing starts (for a
     * wielder, just below the eyes), and adds it to the level: it appears {@link #SWING_REACH} on, and
     * never touches anything wholly behind {@code root}. The charge, from 0 to 1, sets how wide it is
     * and how many creatures it cuts through; the damage and life (in ticks) are the caller's, so a
     * test can pin them. {@code blade} is the zanpakutō it came from, copied, and {@code colour} tints it; with
     * {@link #BLACK} it is the black crescent with a red edge.
     */
    public static GetsugaTensho launch(Level level, @Nullable LivingEntity owner, Vec3 root, Vec3 direction,
                                       float charge, float damage, int lifeTicks, ItemStack blade, int colour) {
        GetsugaTensho crescent = new GetsugaTensho(ModEntities.GETSUGA_TENSHO.get(), level);
        crescent.setOwner(owner);
        Vec3 heading = direction.normalize();
        Vec3 motion = heading.scale(SPEED);
        Vec3 from = root.add(heading.scale(SWING_REACH));
        // Turned the way a mob faces when it looks along the path; the renderer draws it that way.
        float yRot = (float) (Mth.atan2(-motion.x, motion.z) * Mth.RAD_TO_DEG);
        float xRot = (float) (-Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG);
        crescent.moveTo(from.x, from.y, from.z, yRot, xRot);
        crescent.setDeltaMovement(motion);
        crescent.root = root;
        Vec3 facing = new Vec3(heading.x, 0.0, heading.z).normalize();
        if (facing.lengthSqr() < 0.5 && owner != null) {
            // Aimed straight up or down, the path faces no way on the level, but the wielder still does.
            facing = Vec3.directionFromRotation(0.0F, owner.getYRot());
        }
        crescent.facing = facing;
        crescent.damage = damage;
        crescent.pierceLeft = pierceFor(charge);
        crescent.blocksLeft = BleachConfig.get(BleachConfig.GETSUGA_BLOCK_BUDGET);
        crescent.blade = blade.copy();
        crescent.entityData.set(DATA_CHARGE, charge);
        crescent.entityData.set(DATA_COLOUR, colour);
        crescent.entityData.set(DATA_LIFE, lifeTicks);
        level.addFreshEntity(crescent);
        return crescent;
    }

    /** How many creatures a crescent of this charge cuts through: 3 to 5. */
    public static int pierceFor(float charge) {
        return 3 + Math.round(2.0F * charge);
    }

    /** Half the crescent's width, across its path, in blocks. */
    public static float halfWidthFor(float charge) {
        return 1.2F + charge;
    }

    /** Half the crescent's height, in blocks. */
    public static float halfHeightFor(float charge) {
        return 0.6F + 0.4F * charge;
    }

    /** How many ticks a crescent takes to fly this far. */
    public static int lifeTicksFor(double range) {
        return Mth.ceil(range / SPEED);
    }

    /**
     * Whether the crescent can cut this block: soft enough (between 0 and {@code maxHardness}, so
     * never an unbreakable one), holding nothing (no block entity) and not one even a Wither leaves
     * alone.
     */
    public static boolean cuttable(BlockState state, BlockGetter level, BlockPos pos, double maxHardness) {
        if (state.isAir() || state.hasBlockEntity() || state.is(BlockTags.WITHER_IMMUNE)) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && hardness <= maxHardness;
    }

    /**
     * Whether the crescent may break this block here: block breaking is on in the server config, mob
     * griefing allows it, the block lets the breaker destroy it, and a player owner could break it
     * themselves (outside spawn protection, allowed to build, and no protection mod objects).
     */
    public static boolean permitted(Level level, BlockPos pos, BlockState state, @Nullable Entity breaker,
                                    @Nullable Entity owner, boolean breaksBlocks) {
        if (!breaksBlocks || !EventHooks.canEntityGrief(level, breaker)) {
            return false;
        }
        if (breaker != null && !state.canEntityDestroy(level, pos, breaker)) {
            return false;
        }
        if (owner instanceof Player player) {
            if (!player.mayInteract(level, pos) || !player.mayBuild()) {
                return false;
            }
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }
        return true;
    }

    /**
     * Whether the crescent cuts this creature: never its owner, anything riding with them or their
     * tamed animals, never a Plus or a Shinigami, nor a player in creative or spectator mode or one
     * the owner may not harm (a teammate, with friendly fire off). See {@link SpiritProjectiles#mayStrike}.
     */
    public boolean affects(LivingEntity target) {
        return SpiritProjectiles.mayStrike(getOwner(), target);
    }

    /** From 0 for a tap to 1 for a full gather. */
    public float charge() {
        return entityData.get(DATA_CHARGE);
    }

    /** The blade's colour, which tints the crescent: its low 24 bits; the {@link #BLACK} bit above them. */
    public int colour() {
        return entityData.get(DATA_COLOUR);
    }

    /** Whether this is the black crescent with a red edge: a masked Visored's. */
    public boolean isBlack() {
        return (colour() & BLACK) != 0;
    }

    /** How many ticks the crescent flies for. */
    public int lifeTicks() {
        return entityData.get(DATA_LIFE);
    }

    /** Ticks flown so far; a client counts from when it first saw the crescent. */
    public int age() {
        return age;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CHARGE, 0.0F);
        builder.define(DATA_COLOUR, ZanpakutoBlade.DEFAULT_COLOUR);
        builder.define(DATA_LIFE, DEFAULT_LIFE_TICKS);
    }

    /**
     * Server side, each tick: the crescent goes once its owner has gone or left this level, its time
     * is up or its path runs into unloaded ground. Otherwise it sweeps its path for projectiles and
     * creatures, carves along it, and moves on. A client just moves it along its course.
     */
    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (level().isClientSide()) {
            setPos(position().add(getDeltaMovement()));
            age++;
            return;
        }
        Entity owner = getOwner();
        Vec3 from = position();
        Vec3 motion = getDeltaMovement();
        Vec3 to = from.add(motion);
        if (owner == null || owner.level() != level() || age >= lifeTicks() || !level().isLoaded(BlockPos.containing(to))) {
            discard();
            return;
        }
        if (!sweep(owner, from, motion) || !carve(owner, from, to)) {
            return;
        }
        setPos(to);
        age++;
    }

    /**
     * Sweeps this tick's path, as wide and tall as the crescent: knocks every projectile that is not
     * the owner's (nor another crescent) out of the air, then cuts the creatures in it, nearest first,
     * each costing one of its pierces. It never touches what is at the back of the one who fired it
     * (see {@link #clearOfWielder}), yet still cuts what is pressed against their front. A shield that
     * stops it, or the last pierce, ends it. Returns whether the crescent is still flying.
     */
    private boolean sweep(Entity owner, Vec3 from, Vec3 motion) {
        float charge = charge();
        double halfWidth = halfWidthFor(charge);
        double halfHeight = halfHeightFor(charge);
        AABB path = new AABB(from.x - halfWidth, from.y - halfHeight, from.z - halfWidth,
                from.x + halfWidth, from.y + halfHeight, from.z + halfWidth).expandTowards(motion);
        Vec3 heading = motion.normalize();
        Vec3 rear = root != null ? root : from;
        // As far along the path from the root as the first sweep reaches: the swing itself.
        double beside = SWING_REACH + SPEED + halfWidth * (Math.abs(heading.x) + Math.abs(heading.z))
                + halfHeight * Math.abs(heading.y);
        for (Projectile projectile : level().getEntitiesOfClass(Projectile.class, path,
                other -> other != this && !(other instanceof GetsugaTensho) && other.getOwner() != owner
                        && clearOfWielder(other, rear, heading, beside))) {
            // An arrow or trident someone could have picked up is knocked down, not destroyed.
            if (projectile instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
                arrow.spawnAtLocation(arrow.getPickupItemStackOrigin().copy(), 0.1F);
            }
            projectile.discard();
        }
        // A giant counts by its parts too, however far below the path it stands.
        List<LivingEntity> targets = SpiritProjectiles.creaturesIn(level(), path,
                target -> !struck.contains(target.getId()) && affects(target) && clearOfWielder(target, rear, heading, beside));
        if (targets.isEmpty()) {
            return true;
        }
        targets.sort(Comparator.comparingDouble(target -> target.distanceToSqr(from)));
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE), this, owner);
        for (LivingEntity target : targets) {
            struck.add(target.getId());
            boolean hurt = target.hurt(source, damage);
            pierceLeft--;
            if ((!hurt && target.isBlocking()) || pierceLeft <= 0) {
                discard();
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the entity is clear of the wielder's back. Some part of it must lie on or in front of
     * the plane through where the swing started ({@code rear}) square to the path. While it is still
     * beside the wielder, reaching back to within {@code beside} of {@code rear} along the path (as far
     * as the first sweep goes, which takes in the wielder's body and the ground about their feet), some
     * part must also lie in front of the upright plane through {@code rear} that the swing faces, so a
     * swing aimed up or down does not tilt "behind" through the wielder's body or what stands at their
     * heels. Out along the path beyond that, only the first plane counts.
     */
    private boolean clearOfWielder(Entity entity, Vec3 rear, Vec3 heading, double beside) {
        AABB box = entity.getBoundingBox();
        Vec3 offset = box.getCenter().subtract(rear);
        double depth = offset.dot(heading);
        double reach = halfDepth(box, heading);
        if (depth < -reach) {
            return false;
        }
        return depth - reach >= beside || offset.dot(facing) >= -halfDepth(box, facing);
    }

    /** Half of how far the box reaches along a direction: zero for a zero direction. */
    private static double halfDepth(AABB box, Vec3 direction) {
        return (box.getXsize() * Math.abs(direction.x) + box.getYsize() * Math.abs(direction.y)
                + box.getZsize() * Math.abs(direction.z)) * 0.5;
    }

    /**
     * Carves along the middle of this tick's path, every half block from where it starts to where it
     * ends: at the middle and the block above it, a soft block is cut while the budget lasts, and
     * anything else solid at the middle stops the crescent. What is above the middle never stops it.
     * Air and liquids are passed through. Returns whether the crescent is still flying.
     */
    private boolean carve(Entity owner, Vec3 from, Vec3 to) {
        Vec3 motion = to.subtract(from);
        int steps = Math.max(1, Mth.ceil(motion.length() / CARVE_STEP));
        boolean breaksBlocks = BleachConfig.get(BleachConfig.GETSUGA_BREAKS_BLOCKS);
        double maxHardness = BleachConfig.get(BleachConfig.GETSUGA_MAX_HARDNESS);
        Level level = level();
        BlockPos previous = null;
        for (int i = 0; i <= steps; i++) {
            BlockPos middle = BlockPos.containing(from.add(motion.scale((double) i / steps)));
            if (middle.equals(previous)) {
                continue;
            }
            previous = middle;
            for (int up = 0; up <= 1; up++) {
                BlockPos pos = up == 0 ? middle : middle.above();
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                    continue;
                }
                if (blocksLeft > 0 && cuttable(state, level, pos, maxHardness)
                        && permitted(level, pos, state, this, owner, breaksBlocks)) {
                    level.destroyBlock(pos, true, this);
                    blocksLeft--;
                } else if (up == 0 && !state.getCollisionShape(level, pos).isEmpty()) {
                    discard();
                    return false;
                }
            }
        }
        return true;
    }

    /** The blade it came from, so a Hollow it purifies counts as purified by that zanpakutō. */
    @Override
    @Nullable
    public ItemStack getWeaponItem() {
        return blade;
    }

    /**
     * Keeps the turn it was launched with. Vanilla would turn a newly seen projectile the way arrows
     * face, which is not how the crescent is drawn.
     */
    @Override
    public void lerpMotion(double x, double y, double z) {
        setDeltaMovement(x, y, z);
    }

    /** A client that starts watching takes the launch turn as last tick's too, so the first frame does not swing round. */
    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        yRotO = getYRot();
        xRotO = getXRot();
    }

    /** The drawn crescent is much wider than the hitbox, so it is culled by what is drawn. */
    @Override
    public AABB getBoundingBoxForCulling() {
        float charge = charge();
        return getBoundingBox().inflate(halfWidthFor(charge), halfHeightFor(charge), halfWidthFor(charge));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    /** Nothing pushes it off course: not water, explosions or pistons. */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }
}
