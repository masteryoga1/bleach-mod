package io.github.masteryoga1.bleach.entity;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One of a giant's hit boxes, for part of a body too tall for entity lookups to find by its feet: arrows, blades, beams
 * and the crosshair find it where they would miss the giant itself, and whatever strikes it strikes the giant. It lives
 * beside the giant, never saved and never ticked by the world: the giant moves it.
 */
public class GiantPart<T extends Entity> extends PartEntity<T> {
    private final EntityDimensions size;
    private float scale = 1.0F;
    private boolean placed;

    GiantPart(T parent, float width, float height) {
        super(parent);
        size = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public void kill() {
        getParent().kill();
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    /** Parts live outside the world's entity storage, and the giant saves itself if it is saved at all. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // Null only while the entity itself is being made, before the size is set.
        return size != null ? size.scale(scale) : super.getDimensions(pose);
    }

    @Override
    @Nullable
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    /** Grows with the giant, should its size ever change. */
    void resize(float parentScale) {
        if (parentScale != scale) {
            scale = parentScale;
            refreshDimensions();
        }
    }

    /** Moves to this bottom centre, starting from where it was, so it does not slide in from far away. */
    void placeAt(double x, double y, double z) {
        double fromX = placed ? getX() : x;
        double fromY = placed ? getY() : y;
        double fromZ = placed ? getZ() : z;
        setPos(x, y, z);
        xo = fromX;
        yo = fromY;
        zo = fromZ;
        xOld = fromX;
        yOld = fromY;
        zOld = fromZ;
        placed = true;
    }
}
