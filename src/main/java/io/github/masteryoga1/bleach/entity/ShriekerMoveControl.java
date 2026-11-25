package io.github.masteryoga1.bleach.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Flies a Hollow by steering its motion. Each tick its motion closes a fifth of the gap to a straight line at the point
 * wanted, at its flying speed times the modifier, or slower when the point is nearer than that, so it slows as it
 * arrives; with nothing wanted it eases to a hover. Whatever cuts its walking speed (a mask blow's reel, a Bakudō's hold,
 * Gritz) cuts its flight by the same share, and what takes its flying speed stops it. It faces its prey when it has one
 * and its way when it has not. It never moves through blocks: the body moves as any mob's does, against what it meets.
 * Gravity is the flyer's own to switch (see {@link Shrieker#tick}).
 */
final class ShriekerMoveControl extends MoveControl {
    /** The share of the gap between its motion and the motion wanted that it closes each tick. */
    static final double STEER = 0.2;
    /** The most a quickened walking speed may quicken its flight, as a share. */
    private static final double MAX_SHARE = 1.5;
    /** The most it turns in a tick, in degrees. */
    private static final float TURN = 20.0F;
    /** Nearer than this to the point wanted, it is there. */
    private static final double THERE = 0.05;

    ShriekerMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        Vec3 motion = mob.getDeltaMovement();
        Vec3 wanted = Vec3.ZERO;
        Vec3 way = Vec3.ZERO;
        if (operation == Operation.MOVE_TO) {
            // Wished afresh each tick by its goal or its path; with no new wish it eases to a hover.
            operation = Operation.WAIT;
            way = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
            double distance = way.length();
            if (distance > THERE) {
                wanted = way.scale(Math.min(speed(), distance) / distance);
            }
        }
        mob.setDeltaMovement(motion.add(wanted.subtract(motion).scale(STEER)));
        LivingEntity prey = mob.getTarget();
        Vec3 face = prey != null ? prey.position().subtract(mob.position()) : way;
        if (face.horizontalDistanceSqr() > THERE * THERE) {
            float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, TURN));
        }
        // Nothing for the body to walk with: the motion above is all of it.
        mob.setSpeed(0.0F);
        mob.setYya(0.0F);
        mob.setXxa(0.0F);
    }

    /** Its speed now in blocks a tick at the modifier wanted: its flying speed, cut or quickened as its walking speed is. */
    double speed() {
        double flying = mob.getAttributeValue(Attributes.FLYING_SPEED);
        AttributeInstance walking = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        double share = walking == null || walking.getBaseValue() <= 0.0 ? 1.0
                : Mth.clamp(walking.getValue() / walking.getBaseValue(), 0.0, MAX_SHARE);
        return speedModifier * flying * share;
    }
}
