package io.github.masteryoga1.bleach.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The commonest Hollow: a great black spider with a bone mask. It climbs walls, leaps at its prey,
 * walks through cobwebs and leaves the one it bites reeling.
 */
public class SpiderHollow extends Hollow {
    public SpiderHollow(EntityType<? extends SpiderHollow> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.SPIDER.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.SPIDER;
    }

    @Override
    protected boolean leaps() {
        return true;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    /** Climbs whatever it walks into, as a spider does. */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            setFlag(FLAG_CLIMBING, horizontalCollision);
        }
    }

    @Override
    public boolean onClimbable() {
        return getFlag(FLAG_CLIMBING);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }
}
