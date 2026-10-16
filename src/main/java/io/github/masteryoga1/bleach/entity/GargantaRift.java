package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
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
 */
public class GargantaRift extends Entity {
    private static final EntityDataAccessor<Long> DATA_OPEN_AT =
            SynchedEntityData.defineId(GargantaRift.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_DEPARTURE =
            SynchedEntityData.defineId(GargantaRift.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(GargantaRift.class, EntityDataSerializers.FLOAT);

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

    private final List<HollowKind> pending = new ArrayList<>();
    /** The soul whose reiatsu drew the Hollows, if any. */
    @Nullable
    private UUID drawer;
    /** The Hollow a departure tear has come for, until it is taken. */
    @Nullable
    private UUID leaving;
    private boolean opened;
    private boolean dropped;

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

    /** A small tear that opens now around a Hollow and takes it. It is not yet in the world: the caller adds it. */
    public static GargantaRift departure(ServerLevel level, Hollow leaving) {
        GargantaRift rift = new GargantaRift(ModEntities.GARGANTA.get(), level);
        rift.moveTo(leaving.getX(), leaving.getY(), leaving.getZ(), 0.0F, 0.0F);
        rift.entityData.set(DATA_OPEN_AT, level.getGameTime());
        rift.entityData.set(DATA_DEPARTURE, true);
        rift.entityData.set(DATA_SIZE, DEPARTURE_SIZE);
        rift.leaving = leaving.getUUID();
        return rift;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OPEN_AT, 0L);
        builder.define(DATA_DEPARTURE, false);
        builder.define(DATA_SIZE, 1.0F);
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
        if (age < CLOSE_AGE) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - (age - CLOSE_AGE) / (END_AGE - CLOSE_AGE), 0.0F, 1.0F);
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
        if (age >= END_AGE) {
            discard();
        }
    }

    /**
     * Drops the Hollows, each a little way from the middle if there is room there, turned toward the
     * soul that drew them. They hunt that soul if it is here and still prey, and Fishbone D takes it as
     * its quarry. Hollows fall the few blocks to the ground unharmed.
     */
    private void drop(ServerLevel server) {
        ServerPlayer target = drawer != null ? server.getServer().getPlayerList().getPlayer(drawer) : null;
        if (target != null && target.level() != server) {
            target = null;
        }
        for (HollowKind kind : pending) {
            EntityType<? extends Hollow> type = ModEntities.hollowType(kind);
            Hollow hollow = type.create(server);
            if (hollow == null) {
                continue;
            }
            Vec3 at = dropSpot(server, type, kind.scaleMax);
            float yaw = target != null
                    ? (float) (Mth.atan2(target.getZ() - at.z, target.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F
                    : random.nextFloat() * 360.0F;
            hollow.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            hollow.setYHeadRot(yaw);
            hollow.setYBodyRot(yaw);
            EventHooks.finalizeMobSpawn(hollow, server, server.getCurrentDifficultyAt(BlockPos.containing(at)),
                    MobSpawnType.EVENT, null);
            hollow.setDrawer(drawer);
            FishboneD fishbone = hollow instanceof FishboneD fish && target != null ? fish : null;
            if (fishbone != null) {
                fishbone.hunt(target);
            }
            if (!server.addFreshEntity(hollow)) {
                continue;
            }
            if (fishbone != null) {
                GargantaEvents.onFirstHollowLanded(target, fishbone);
            }
            if (hollow instanceof FishboneD) {
                hollow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HEAVY_LANDING_TICKS, SLOWNESS_IV,
                        false, false));
            }
            if (target != null && HollowLogic.isPrey(target)) {
                hollow.setTarget(target);
            }
            SpiritSenses.particles(server, ParticleTypes.LARGE_SMOKE, hollow.getX(), hollow.getY() + hollow.getBbHeight() * 0.5,
                    hollow.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        }
        pending.clear();
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
