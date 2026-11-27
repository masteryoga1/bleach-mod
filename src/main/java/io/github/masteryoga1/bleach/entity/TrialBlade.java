package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BankaiSwords;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * A blade risen in a Bankai trial that tests with blades (Zangetsu's): a sword stood point down in its spirit's Shikai
 * look. A client is sent only that look and whether it is held, so the true one looks as the rest ({@link BankaiSwords}).
 * A spirit's thing with no body to strike, never saved, summoned or moved, it goes when its bout is over.
 */
public class TrialBlade extends Entity {
    /** Its box, in blocks: thin, so it stands in the way of few blows, and as tall as a sword stood in the ground. */
    public static final float WIDTH = 0.3F, HEIGHT = 1.2F;
    /** The custom model data of its look: its spirit's Shikai's, the same for every blade of a bout. */
    private static final EntityDataAccessor<Integer> DATA_MODEL =
            SynchedEntityData.defineId(TrialBlade.class, EntityDataSerializers.INT);
    /** Whether its soul holds it, so it is not drawn or aimed at on the ground. */
    private static final EntityDataAccessor<Boolean> DATA_HELD =
            SynchedEntityData.defineId(TrialBlade.class, EntityDataSerializers.BOOLEAN);

    /** The soul whose bout it stands in. Server side only. */
    @Nullable
    private UUID wielderId;

    public TrialBlade(EntityType<? extends TrialBlade> type, Level level) {
        super(type, level);
    }

    /** A blade for this soul's bout, at this spot on the ground, turned this way, in this look; not yet in the world. */
    public static TrialBlade at(ServerLevel level, Vec3 spot, float yaw, UUID wielderId, int model) {
        TrialBlade blade = new TrialBlade(ModEntities.TRIAL_BLADE.get(), level);
        blade.moveTo(spot, yaw, 0.0F);
        blade.wielderId = wielderId;
        blade.entityData.set(DATA_MODEL, model);
        return blade;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODEL, 0);
        builder.define(DATA_HELD, false);
    }

    @Nullable
    public UUID wielderId() {
        return wielderId;
    }

    public int model() {
        return entityData.get(DATA_MODEL);
    }

    public boolean held() {
        return entityData.get(DATA_HELD);
    }

    public void setHeld(boolean held) {
        entityData.set(DATA_HELD, held);
    }

    /**
     * On the server it goes once its bout no longer holds it ({@link BankaiSwords#stands}): a backstop, as a bout's end
     * clears its own blades, for one that ticks before a leaving end's blades go.
     */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !isRemoved() && !BankaiSwords.stands(this)) {
            discard();
        }
    }

    /** Only a blade on the ground can be aimed at; arrows and other shots pass through. */
    @Override
    public boolean isPickable() {
        return !held();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Nothing in the world counts it or moves it: no plate or wire, no piston, no portal, which would take it away. */
    @Override
    public boolean isIgnoringBlockTriggers() {
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
