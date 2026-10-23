package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.EnumSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A Quincy NPC's archery with the Cross, as a skeleton fights with a bow: it keeps its target
 * between {@link QuincyNpc#minRange()} and {@link QuincyNpc#maxRange()}, walking in when farther or
 * out of sight, backing off when nearer, and strafing in between, changing direction now and then.
 * With the target in sight it draws for {@link QuincyNpc#DRAW_TICKS} ticks and looses a Heilig
 * Pfeil, then waits {@link QuincyNpc#shotInterval()} ticks before drawing again.
 * <p>
 * The draw goes through the item in hand, so it looks like a player's, but the shot is the NPC's
 * own: the goal reads how long it drew before it stops using the Cross, and never releases it the
 * way a player does, which only acts for players anyway.
 */
public final class QuincyBowGoal extends Goal {
    /** Ticks in sight before it stops walking in and starts strafing. */
    private static final int SEE_TICKS_TO_STAND = 20;
    /** How often it may change the way it strafes. */
    private static final int STRAFE_TURN_TICKS = 20;
    private static final float STRAFE_TURN_CHANCE = 0.3F;
    private static final float STRAFE_SPEED = 0.5F;
    /** Out of sight this long, it lowers the Cross. */
    private static final int LOST_SIGHT_TICKS = -60;
    private static final float LOOK_SPEED = 30.0F;
    /** Strafing farther out than this share of its reach, squared, it no longer backs away. */
    private static final float FAR_SHARE = 0.75F;

    private final QuincyNpc npc;
    private final double speed;
    private int attackTime = -1;
    private int seeTime;
    private int strafingTime = -1;
    private boolean strafingClockwise;
    private boolean strafingBackwards;

    public QuincyBowGoal(QuincyNpc npc, double speed) {
        this.npc = npc;
        this.speed = speed;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = npc.getTarget();
        return target != null && target.isAlive() && npc.wantsToShoot(target) && QuincyCrossItem.is(npc.getMainHandItem());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        npc.setAggressive(true);
    }

    @Override
    public void stop() {
        npc.setAggressive(false);
        npc.stopUsingItem();
        seeTime = 0;
        attackTime = -1;
        strafingTime = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = npc.getTarget();
        if (target == null) {
            return;
        }
        double distanceSqr = npc.distanceToSqr(target);
        boolean sees = npc.getSensing().hasLineOfSight(target);
        if (sees != seeTime > 0) {
            seeTime = 0;
        }
        seeTime += sees ? 1 : -1;
        double maxSqr = (double) npc.maxRange() * npc.maxRange();
        double minSqr = (double) npc.minRange() * npc.minRange();

        if (distanceSqr <= maxSqr && seeTime >= SEE_TICKS_TO_STAND) {
            npc.getNavigation().stop();
            strafingTime++;
        } else {
            npc.getNavigation().moveTo(target, speed);
            strafingTime = -1;
        }
        if (strafingTime >= STRAFE_TURN_TICKS) {
            if (npc.getRandom().nextFloat() < STRAFE_TURN_CHANCE) {
                strafingClockwise = !strafingClockwise;
            }
            if (npc.getRandom().nextFloat() < STRAFE_TURN_CHANCE) {
                strafingBackwards = !strafingBackwards;
            }
            strafingTime = 0;
        }
        if (strafingTime > -1) {
            if (distanceSqr > maxSqr * FAR_SHARE) {
                strafingBackwards = false;
            } else if (distanceSqr < minSqr) {
                strafingBackwards = true;
            }
            npc.getMoveControl().strafe(strafingBackwards ? -STRAFE_SPEED : STRAFE_SPEED,
                    strafingClockwise ? STRAFE_SPEED : -STRAFE_SPEED);
            npc.lookAt(target, LOOK_SPEED, LOOK_SPEED);
        } else {
            npc.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);
        }

        if (npc.isUsingItem()) {
            if (!sees && seeTime < LOST_SIGHT_TICKS) {
                npc.stopUsingItem();
            } else if (sees) {
                // Read before stopping: stopping forgets how long it was drawn.
                int drawn = npc.getTicksUsingItem();
                if (drawn >= QuincyNpc.DRAW_TICKS) {
                    npc.stopUsingItem();
                    npc.performRangedAttack(target, 1.0F);
                    attackTime = npc.shotInterval();
                }
            }
        } else if (--attackTime <= 0 && seeTime >= LOST_SIGHT_TICKS) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
            SpiritSenses.play(npc, ModSounds.QUINCY_CROSS_DRAW.get(), npc.getSoundSource(), 0.6F, 1.0F, false);
        }
    }
}
