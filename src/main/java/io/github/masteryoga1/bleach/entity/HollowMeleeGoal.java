package io.github.masteryoga1.bleach.entity;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Chases prey at the kind's chase speed, even when it slips out of sight, and bites no faster than the
 * kind allows. The vanilla goal's own cooldown is private, so the kind's is checked on top of it.
 */
final class HollowMeleeGoal extends MeleeAttackGoal {
    private final Hollow hollow;

    HollowMeleeGoal(Hollow hollow) {
        super(hollow, hollow.kind().chaseSpeed, true);
        this.hollow = hollow;
    }

    @Override
    protected boolean isTimeToAttack() {
        return super.isTimeToAttack() && hollow.attackReady();
    }
}
