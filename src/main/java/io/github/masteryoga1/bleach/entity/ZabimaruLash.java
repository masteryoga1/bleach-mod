package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BankaiForms;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zabimaru's segmented blade lashing out in Shikai: drawn as a line of segments from its wielder's
 * hand to where a far blow landed, out and back in {@link #LIFE_TICKS} ticks. It only shows the blow,
 * which the wielder's longer reach has already struck (see {@code ShikaiEvents.lash}); it touches
 * nothing itself. Never saved. In Hihiō Zabimaru it is the serpent ({@link #isSerpent}), out for
 * {@link #SERPENT_LIFE_TICKS}, and gone the tick its wielder no longer holds the serpent
 * ({@link BankaiForms#serpentine}), whatever ended it.
 */
public class ZabimaruLash extends Entity {
    /** Ticks from the blade leaving the hand to it being back. */
    public static final int LIFE_TICKS = 8;
    /** Ticks from the serpent leaving the hand to it being back: longer, as it is longer. */
    public static final int SERPENT_LIFE_TICKS = 12;

    /** The wielder's entity id, so every client draws the line from the right hand. */
    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(ZabimaruLash.class, EntityDataSerializers.INT);
    /** Whether this is Hihiō Zabimaru's serpent, so every client draws it as the serpent, for as long. */
    private static final EntityDataAccessor<Boolean> DATA_SERPENT =
            SynchedEntityData.defineId(ZabimaruLash.class, EntityDataSerializers.BOOLEAN);
    private static final int NO_OWNER = -1;
    /** Drawn as far out as a lash can be seen. */
    private static final double RENDER_DISTANCE = 64.0;
    /** How much further than the line the serpent's sway and its head can reach, for culling. */
    private static final double SERPENT_CULL_MARGIN = 1.0;

    /** The wielder it was spawned for, on the server; clients look the wielder up by id. */
    @Nullable
    private Entity spawnedFor;
    private int age;

    public ZabimaruLash(EntityType<? extends ZabimaruLash> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Lashes out from the wielder to {@code at}, the middle of what the blow struck, and adds the lash to the level. */
    public static ZabimaruLash spawn(Level level, LivingEntity owner, Vec3 at) {
        ZabimaruLash lash = create(level, owner, at);
        level.addFreshEntity(lash);
        return lash;
    }

    /** Hihiō Zabimaru's serpent, lashing out from its wielder to {@code at}, added to the level. */
    public static ZabimaruLash serpent(Level level, Player owner, Vec3 at) {
        ZabimaruLash lash = create(level, owner, at);
        // Set before it is added, so it goes to every client with the lash itself.
        lash.entityData.set(DATA_SERPENT, true);
        level.addFreshEntity(lash);
        return lash;
    }

    private static ZabimaruLash create(Level level, LivingEntity owner, Vec3 at) {
        ZabimaruLash lash = new ZabimaruLash(ModEntities.ZABIMARU_LASH.get(), level);
        lash.moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
        lash.entityData.set(DATA_OWNER, owner.getId());
        lash.spawnedFor = owner;
        return lash;
    }

    /** Whether this is Hihiō Zabimaru's serpent rather than its Shikai's blade. */
    public boolean isSerpent() {
        return entityData.get(DATA_SERPENT);
    }

    /** Ticks from leaving the hand to being back. */
    public int lifeTicks() {
        return isSerpent() ? SERPENT_LIFE_TICKS : LIFE_TICKS;
    }

    /** The wielder the blade runs back to, or null if it is gone or this side cannot see it. */
    @Nullable
    public Entity owner() {
        if (spawnedFor != null && !spawnedFor.isRemoved()) {
            return spawnedFor;
        }
        int id = entityData.get(DATA_OWNER);
        return id == NO_OWNER ? null : level().getEntity(id);
    }

    /** Ticks since the lash began; a client counts from when it first saw it. */
    public int age() {
        return age;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, NO_OWNER);
        builder.define(DATA_SERPENT, false);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide()) {
            return;
        }
        // Sealed, stepped down, dead, gone, out of hand or in another world: it is never left out.
        if (age >= lifeTicks() || isSerpent() && !(owner() instanceof Player wielder && wielder.level() == level()
                && BankaiForms.serpentine(wielder, SoulLogic.get(wielder)))) {
            discard();
        }
    }

    /** The drawn line reaches back to the wielder, so it is culled by the box around both, and the serpent's sway. */
    @Override
    public AABB getBoundingBoxForCulling() {
        Entity owner = owner();
        AABB both = owner != null ? getBoundingBox().minmax(owner.getBoundingBox()) : getBoundingBox();
        return isSerpent() ? both.inflate(SERPENT_CULL_MARGIN) : both;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
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
