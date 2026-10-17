package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.HollowLogic;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Picks the most tempting prey in sight rather than the nearest: strong souls, the soul that drew the
 * Hollow and, for some kinds, Pluses are worth going further for (see {@link HollowLogic#preyScore}).
 */
final class HollowHuntGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final Hollow hollow;

    HollowHuntGoal(Hollow hollow) {
        super(hollow, LivingEntity.class, 10, true, false, HollowLogic::isPrey);
        this.hollow = hollow;
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
