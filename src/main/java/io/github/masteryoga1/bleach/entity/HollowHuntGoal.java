package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.HollowLogic;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.phys.AABB;

/**
 * Picks the most tempting prey in sight rather than the nearest: strong souls, the soul that drew the
 * Hollow and, for some kinds, Pluses are worth going further for (see {@link HollowLogic#preyScore}).
 * A Hollow player that drew this Hollow through a Garganta is its rival, and prey too (see
 * {@link HollowLogic#isPreyFor}). A Hollow that flies looks as far up and down as across; any other
 * within 4 blocks of its own height, as every hunter does.
 */
final class HollowHuntGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final Hollow hollow;
    private final boolean flies;

    HollowHuntGoal(Hollow hollow) {
        this(hollow, false);
    }

    HollowHuntGoal(Hollow hollow, boolean flies) {
        super(hollow, LivingEntity.class, 10, true, false, target -> HollowLogic.isPreyFor(hollow, target));
        this.hollow = hollow;
        this.flies = flies;
    }

    @Override
    protected AABB getTargetSearchArea(double distance) {
        return flies ? mob.getBoundingBox().inflate(distance) : super.getTargetSearchArea(distance);
    }

    @Override
    protected void findTarget() {
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity prey : mob.level().getEntitiesOfClass(LivingEntity.class, getTargetSearchArea(getFollowDistance()),
                candidate -> targetConditions.test(mob, candidate))) {
            double score = HollowLogic.preyScore(hollow, prey, mob.distanceTo(prey));
            if (score > bestScore) {
                bestScore = score;
                best = prey;
            }
        }
        target = best;
    }
}
