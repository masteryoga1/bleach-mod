package io.github.masteryoga1.bleach.soul;

import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Makes a creature run while it is frightened by released reiatsu (see {@link Fright}). It sits at
 * the top priority, so it overrides attacking and wandering for as long as the fright lasts.
 */
public final class ReiatsuFleeGoal extends Goal {
    private static final double SPEED = 1.4;

    private final PathfinderMob mob;
    private Path path;

    public ReiatsuFleeGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Whether the creature is frightened right now. */
    public static boolean isFrightened(PathfinderMob mob) {
        return mob.hasData(SoulAttachments.FRIGHT)
                && mob.getData(SoulAttachments.FRIGHT).until() > mob.level().getGameTime();
    }

    @Override
    public boolean canUse() {
        if (!isFrightened(mob)) {
            return false;
        }
        Vec3 away = DefaultRandomPos.getPosAway(mob, 10, 5, mob.getData(SoulAttachments.FRIGHT).from());
        if (away == null) {
            return false;
        }
        path = mob.getNavigation().createPath(away.x, away.y, away.z, 0);
        return path != null;
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(path, SPEED);
    }

    @Override
    public void stop() {
        path = null;
    }

    @Override
    public boolean canContinueToUse() {
        return isFrightened(mob) && !mob.getNavigation().isDone();
    }
}
