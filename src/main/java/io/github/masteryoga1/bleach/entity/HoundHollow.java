package io.github.masteryoga1.bleach.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;

/**
 * A wolf-like Hollow that hunts in packs. Hurt one and the pack turns on whoever did it; once one has
 * found its prey, every hound near it without prey of its own joins in.
 */
public class HoundHollow extends Hollow {
    /** How close another hound must be to share the prey. */
    private static final double SHARE_RANGE = 12.0;
    private static final int SHARE_INTERVAL_TICKS = 20;

    public HoundHollow(EntityType<? extends HoundHollow> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.HOUND.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.HOUND;
    }

    /** Hurt one hound, and the hounds around it go for the attacker too. */
    @Override
    protected HurtByTargetGoal hurtByTargetGoal() {
        return super.hurtByTargetGoal().setAlertOthers();
    }

    /** Gives this hound's prey to every hound within {@link #SHARE_RANGE} that has none and may hunt it. */
    public void shareTarget() {
        LivingEntity prey = getTarget();
        if (prey == null || !prey.isAlive()) {
            return;
        }
        double rangeSqr = SHARE_RANGE * SHARE_RANGE;
        for (HoundHollow hound : level().getEntitiesOfClass(HoundHollow.class, getBoundingBox().inflate(SHARE_RANGE),
                other -> other != this && other.isAlive() && !other.isDeparting() && other.getTarget() == null
                        && other.distanceToSqr(this) <= rangeSqr)) {
            if (hound.canAttack(prey)) {
                hound.setTarget(prey);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % SHARE_INTERVAL_TICKS == 0) {
            shareTarget();
        }
    }
}
