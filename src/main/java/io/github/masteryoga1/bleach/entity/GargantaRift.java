package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BaitEvents;
import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SpawnSpots;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * A Garganta: a tear in the sky that Hollows come through. An arrival tear opens near the soul whose
 * reiatsu drew it, grows, drops its Hollows a second and a half later and closes; a departure tear opens
 * around a Hollow that is leaving and takes it. Its timeline runs on game time from when it opens, so a
 * client that starts watching late sees the same tear, and an arrival may wait, dormant, until its time.
 * Never saved: a tear whose chunk unloads is gone, and so are the Hollows it held.
 * <p>
 * A tear drawn by a Hollow Bait belongs to its site ({@link #baitSite()}): its Hollows belong to the
 * site too, and the site is told how many came through. Such a tear may be quiet, sounding to no
 * pager. The bait's finale opens a great tear in the sky ({@link #sky}) that stays open for a while
 * ({@link #hold()}) before it closes.
 * <p>
 * An arrival tear may also carry mobs made ready elsewhere ({@link #carrying}), such as Arrancar. They drop
 * after its Hollows, and like them never come if the tear is gone first.
 */
public class GargantaRift extends Entity {
    private static final EntityDataAccessor<Long> DATA_OPEN_AT =
            SynchedEntityData.defineId(GargantaRift.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_DEPARTURE =
            SynchedEntityData.defineId(GargantaRift.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(GargantaRift.class, EntityDataSerializers.FLOAT);
    /** Extra ticks the tear stays fully open before it closes: 0 but for the sky's. */
    private static final EntityDataAccessor<Integer> DATA_HOLD =
            SynchedEntityData.defineId(GargantaRift.class, EntityDataSerializers.INT);

    /** Ticks after opening: fully open, a departing Hollow taken, the Hollows dropped, closing, gone. */
    public static final int GROW_TICKS = 10;
    public static final int DEPART_AGE = 10;
    public static final int DROP_AGE = 30;
    public static final int CLOSE_AGE = 60;
    public static final int END_AGE = 80;
    /** A tear that would open more than this long from now is a mistake, and goes at once. */
    public static final int DORMANT_LIMIT = 1200;

    private static final float DEPARTURE_SIZE = 0.6F;
    /** How far from the middle of the tear each Hollow may drop, in blocks. */
    private static final double DROP_JITTER = 1.5;
    /** Fishbone D lands heavily: Slowness IV, hidden, for a moment. */
    private static final int HEAVY_LANDING_TICKS = 30;
    private static final int SLOWNESS_IV = 3;
    /** The size of the drawn tear at size 1, for culling: 2 blocks wide and 4 high. */
    private static final double DRAWN_WIDTH = 2.0;
    private static final double DRAWN_HEIGHT = 4.0;
    /** The sky's tear opens at least this far below the top of the world. */
    private static final int SKY_TOP_MARGIN = 8;

    private final List<HollowKind> pending = new ArrayList<>();
    /**
     * Mobs made ready elsewhere and dropped after the Hollows, each at its own spot; not in the world until then.
     * Server only, never saved.
     */
    private final List<Mob> carried = new ArrayList<>();
    /** The soul whose reiatsu drew the Hollows, if any. */
    @Nullable
    private UUID drawer;
    /** The creature (a Hollow or an Arrancar) a departure tear has come for, until it is taken. */
    @Nullable
    private UUID leaving;
    private boolean opened;
    private boolean dropped;
    /** The Hollow Bait site that drew this tear, if any. Server only. */
    @Nullable
    private UUID baitSite;
    /** Whether the tear opens without sounding to pagers or to the Quincy senses. Server only. */
    private boolean quiet;

    public GargantaRift(EntityType<? extends GargantaRift> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * A tear that opens at {@code openAt} and drops these Hollows, in order, for {@code drawer} to face.
     * It is not yet in the world: the caller adds it.
     */
    public static GargantaRift arrival(ServerLevel level, Vec3 pos, long openAt, List<HollowKind> kinds,
                                       @Nullable ServerPlayer drawer, float size) {
        GargantaRift rift = new GargantaRift(ModEntities.GARGANTA.get(), level);
        rift.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        rift.entityData.set(DATA_OPEN_AT, openAt);
        rift.entityData.set(DATA_SIZE, size);
        rift.pending.addAll(kinds);
        rift.drawer = drawer != null ? drawer.getUUID() : null;
        return rift;
    }

    /**
     * The great tear above a Hollow Bait in its finale: it opens at {@code openAt}, stays open for
     * {@code holdTicks} ticks more than any other and drops nothing. Opened no higher than eight blocks
     * below the top of the world. It is not yet in the world: the caller adds it.
     */
    public static GargantaRift sky(ServerLevel level, Vec3 pos, long openAt, float size, int holdTicks, UUID site) {
        double y = Math.min(pos.y, level.getMaxBuildHeight() - SKY_TOP_MARGIN);
        GargantaRift rift = arrival(level, new Vec3(pos.x, y, pos.z), openAt, List.of(), null, size);
        rift.entityData.set(DATA_HOLD, Math.max(0, holdTicks));
        rift.baitSite = site;
        return rift;
    }

    /** A small tear that opens now around a creature and takes it. It is not yet in the world: the caller adds it. */
    public static GargantaRift departure(ServerLevel level, Entity leaving) {
        GargantaRift rift = new GargantaRift(ModEntities.GARGANTA.get(), level);
        rift.moveTo(leaving.getX(), leaving.getY(), leaving.getZ(), 0.0F, 0.0F);
        rift.entityData.set(DATA_OPEN_AT, level.getGameTime());
        rift.entityData.set(DATA_DEPARTURE, true);
        rift.entityData.set(DATA_SIZE, DEPARTURE_SIZE);
        rift.leaving = leaving.getUUID();
        return rift;
    }

    /**
     * This tear also drops these mobs, set up by the caller but not yet added to the world, after any Hollows.
     * Returns this.
     */
    public GargantaRift carrying(List<? extends Mob> mobs) {
        carried.addAll(mobs);
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OPEN_AT, 0L);
        builder.define(DATA_DEPARTURE, false);
        builder.define(DATA_SIZE, 1.0F);
        builder.define(DATA_HOLD, 0);
    }

    public long openAt() {
        return entityData.get(DATA_OPEN_AT);
    }

    public boolean isDeparture() {
        return entityData.get(DATA_DEPARTURE);
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    /** Extra ticks this tear stays fully open: 0 but for the sky's. */
    public int hold() {
        return entityData.get(DATA_HOLD);
    }

    /** The age at which the tear starts to close. */
    public int closeAge() {
        return CLOSE_AGE + hold();
    }

    /** The age at which the tear is gone. */
    public int endAge() {
        return END_AGE + hold();
    }

    /** The Hollow Bait site that drew this tear, if any. Known on the server only. */
    @Nullable
    public UUID baitSite() {
        return baitSite;
    }

    public void setBaitSite(@Nullable UUID site) {
        baitSite = site;
    }

    /** Whether the tear opens without sounding to pagers or to the Quincy senses. Known on the server only. */
    public boolean isQuiet() {
        return quiet;
    }

    public void setQuiet(boolean value) {
        quiet = value;
    }

    /** Ticks since the tear opened; below 0 while it is still dormant. */
    public long age() {
        return level().getGameTime() - openAt();
    }

    /** How open the tear is, from 0 to 1: shut while dormant, growing, open, then closing. */
    public float openness(float partialTick) {
        float age = (float) age() + partialTick;
        if (age < 0.0F) {
            return 0.0F;
        }
        if (age < GROW_TICKS) {
            return age / GROW_TICKS;
        }
        int closeAge = closeAge();
        if (age < closeAge) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - (age - closeAge) / (endAge() - closeAge), 0.0F, 1.0F);
    }

    /** The Hollows still to drop, for tests. */
    public List<HollowKind> pending() {
        return Collections.unmodifiableList(pending);
    }

    /** The soul whose reiatsu drew this tear, if any. */
    @Nullable
    public UUID drawer() {
        return drawer;
    }

    /**
     * Server side: waits while dormant, opens (an arrival tells {@link GargantaEvents#onRiftOpened}),
     * takes its departing Hollow or drops its Hollows, and goes when it has closed. Ages are compared
     * with at-least, so a tear that missed ticks in a chunk that stopped ticking catches up.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        long age = age();
        if (age < 0) {
            if (age < -DORMANT_LIMIT) {
                discard();
            }
            return;
        }
        if (!opened) {
            opened = true;
            if (!isDeparture()) {
                GargantaEvents.onRiftOpened(server, this);
            }
        }
        if (isDeparture()) {
            if (age >= DEPART_AGE && leaving != null) {
                Entity hollow = server.getEntity(leaving);
                leaving = null;
                if (hollow != null) {
                    hollow.discard();
                }
            }
        } else if (age >= DROP_AGE && !dropped) {
            dropped = true;
            drop(server);
        }
        if (age >= endAge()) {
            discard();
        }
    }

    /**
     * Drops the Hollows, each a little way from the middle if there is room there, turned toward the
     * soul that drew them. They hunt that soul if it is here and still their prey (a Hollow that drew
     * them is their rival), and Fishbone D takes it as its quarry. Hollows fall the few blocks to the
     * ground unharmed. Those a Hollow Bait drew belong to its site, which is told how many came. Mobs it carries
     * (Arrancar) drop after them, each set on the drawer if it may attack it.
     */
    private void drop(ServerLevel server) {
        ServerPlayer target = drawer != null ? server.getServer().getPlayerList().getPlayer(drawer) : null;
        if (target != null && target.level() != server) {
            target = null;
        }
        int landed = 0;
        for (HollowKind kind : pending) {
            EntityType<? extends Hollow> type = ModEntities.hollowType(kind);
            Hollow hollow = type.create(server);
            if (hollow == null) {
                continue;
            }
            Vec3 at = dropSpot(server, type, kind.scaleMax);
            float yaw = yawToward(target, at);
            hollow.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            hollow.setYHeadRot(yaw);
            hollow.setYBodyRot(yaw);
            EventHooks.finalizeMobSpawn(hollow, server, server.getCurrentDifficultyAt(BlockPos.containing(at)),
                    MobSpawnType.EVENT, null);
            hollow.setDrawer(drawer);
            hollow.setBaitSite(baitSite);
            FishboneD fishbone = hollow instanceof FishboneD fish && target != null ? fish : null;
            if (fishbone != null) {
                fishbone.hunt(target);
            }
            if (!server.addFreshEntity(hollow)) {
                continue;
            }
            landed++;
            if (fishbone != null) {
                GargantaEvents.onFirstHollowLanded(target, fishbone);
            }
            if (hollow instanceof FishboneD) {
                hollow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HEAVY_LANDING_TICKS, SLOWNESS_IV,
                        false, false));
            }
            if (target != null && HollowLogic.isPreyFor(hollow, target)) {
                hollow.setTarget(target);
            }
            SpiritSenses.particles(server, ParticleTypes.LARGE_SMOKE, hollow.getX(), hollow.getY() + hollow.getBbHeight() * 0.5,
                    hollow.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        }
        // Carried mobs are not the bait's (no bait tear carries any), so they do not count as landed.
        for (Mob mob : carried) {
            Vec3 at = dropSpot(server, mob.getType(), mob.getScale());
            float yaw = yawToward(target, at);
            mob.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
            EventHooks.finalizeMobSpawn(mob, server, server.getCurrentDifficultyAt(BlockPos.containing(at)),
                    MobSpawnType.EVENT, null);
            if (!server.addFreshEntity(mob)) {
                continue;
            }
            if (target != null && mob.canAttack(target)) {
                mob.setTarget(target);
            }
            SpiritSenses.particles(server, ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5,
                    mob.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        }
        carried.clear();
        pending.clear();
        if (baitSite != null) {
            BaitEvents.onArrived(server, baitSite, landed);
        }
    }

    /** The yaw that faces the drawer from this spot, or a random one with no drawer here. */
    private float yawToward(@Nullable ServerPlayer target, Vec3 at) {
        return target != null
                ? (float) (Mth.atan2(target.getZ() - at.z, target.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F
                : random.nextFloat() * 360.0F;
    }

    /**
     * A spot within {@link #DROP_JITTER} of the middle with room for the Hollow at its biggest, or the
     * middle itself, which the Garganta checked when it opened.
     */
    private Vec3 dropSpot(ServerLevel server, EntityType<?> type, float scale) {
        double x = getX() + (random.nextDouble() * 2.0 - 1.0) * DROP_JITTER;
        double z = getZ() + (random.nextDouble() * 2.0 - 1.0) * DROP_JITTER;
        return server.noCollision(SpawnSpots.box(type, scale, x, getY(), z)) ? new Vec3(x, getY(), z) : position();
    }

    /** The drawn tear is bigger than the hitbox when the tear is large, so it is culled by what is drawn. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double halfWidth = DRAWN_WIDTH * size() * 0.5;
        return new AABB(getX() - halfWidth, getY(), getZ() - halfWidth,
                getX() + halfWidth, getY() + DRAWN_HEIGHT * size(), getZ() + halfWidth);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }
}
