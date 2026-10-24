package io.github.masteryoga1.bleach.entity;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a Hollow that a Hollow Bait holds near the bait: one with no prey that has strayed more than
 * 16 blocks walks back until it is within 8. While the sky cracks it drops its prey and gathers
 * within 4 blocks of the spot below the tear, and stays there, so the tear can take them all up.
 * Distances are across only, as a site's are: a bait crushed below ground holds the Hollows on the
 * ground above it.
 */
public final class HollowGatherGoal extends Goal {
    /** Farther than this from the bait, squared, a Hollow with no prey heads back: 16 blocks. */
    private static final double STRAY_SQR = 16.0 * 16.0;
    /** It walks until it is this close, squared: 8 blocks. */
    private static final double BACK_SQR = 8.0 * 8.0;
    /** In the finale they gather within this many blocks of the spot below the tear. */
    private static final double FINALE_RADIUS = 4.0;
    /** How often the path is found again, in game ticks. */
    private static final int REPATH_TICKS = 20;
    private static final double SPEED = 1.0;

    private final Hollow hollow;
    private long nextPathAt;

    public HollowGatherGoal(Hollow hollow) {
        this.hollow = hollow;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        Vec3 centre = centre();
        if (centre == null) {
            return false;
        }
        return hollow.isBaitFinale() || hollow.getTarget() == null && acrossSqr(centre) > STRAY_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        Vec3 centre = centre();
        if (centre == null) {
            return false;
        }
        return hollow.isBaitFinale() || hollow.getTarget() == null && acrossSqr(centre) > BACK_SQR;
    }

    /** How far the Hollow is from this point across, leaving height out, squared. */
    private double acrossSqr(Vec3 point) {
        double dx = hollow.getX() - point.x;
        double dz = hollow.getZ() - point.z;
        return dx * dx + dz * dz;
    }

    /** Where the holding bait lies, or null when no bait holds this Hollow or it cannot walk now. */
    @Nullable
    private Vec3 centre() {
        if (!hollow.isBaitHeld() || hollow.isDeparting() || hollow.isPurifying() || hollow.isMaskForming()) {
            return null;
        }
        return hollow.baitCentre();
    }

    @Override
    public void start() {
        nextPathAt = hollow.level().getGameTime();
    }

    @Override
    public void stop() {
        hollow.getNavigation().stop();
    }

    @Override
    public void tick() {
        Vec3 centre = centre();
        if (centre == null) {
            return;
        }
        boolean finale = hollow.isBaitFinale();
        if (finale) {
            // The finale holds them from fighting (see Hollow.canAttack); this lets go of the prey at once.
            hollow.setTarget(null);
            if (acrossSqr(centre) <= FINALE_RADIUS * FINALE_RADIUS) {
                hollow.getNavigation().stop();
                return;
            }
        }
        long now = hollow.level().getGameTime();
        if (now < nextPathAt) {
            return;
        }
        nextPathAt = now + REPATH_TICKS;
        Vec3 to = centre;
        if (finale) {
            // Somewhere near the spot, so the crowd does not try to stand on one block.
            double angle = hollow.getRandom().nextDouble() * Math.PI * 2.0;
            double reach = hollow.getRandom().nextDouble() * (FINALE_RADIUS - 1.0);
            to = centre.add(Math.cos(angle) * reach, 0.0, Math.sin(angle) * reach);
        }
        hollow.getNavigation().moveTo(to.x, to.y, to.z, SPEED);
    }
}
