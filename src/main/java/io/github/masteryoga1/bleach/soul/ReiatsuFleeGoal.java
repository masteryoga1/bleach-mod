package io.github.masteryoga1.bleach.soul;

import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
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
    /** Where the creature is running, as a walk target for creatures moved by a brain. */
    private WalkTarget flight;

    public ReiatsuFleeGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Whether the creature is frightened right now. */
    public static boolean isFrightened(PathfinderMob mob) {
        return mob.hasData(SoulAttachments.FRIGHT)
                && mob.getData(SoulAttachments.FRIGHT).until() > mob.level().getGameTime();
    }

    /**
     * Piglins, hoglins, villagers and other creatures moved by a brain walk to the brain's walk target
     * after the goals have run, so the flight becomes that walk target, and whatever they were
     * attacking is forgotten while they run. A brain ignores memories it does not use, so this leaves
     * creatures moved by goals alone.
     */
    public static void steerBrain(Brain<?> brain, WalkTarget flight) {
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (!brain.isMemoryValue(MemoryModuleType.WALK_TARGET, flight)) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, flight);
        }
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
        if (path == null) {
            return false;
        }
        flight = new WalkTarget(away, (float) SPEED, 0);
        return true;
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(path, SPEED);
        steerBrain(mob.getBrain(), flight);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        steerBrain(mob.getBrain(), flight);
    }

    @Override
    public void stop() {
        Brain<?> brain = mob.getBrain();
        if (flight != null && brain.isMemoryValue(MemoryModuleType.WALK_TARGET, flight)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        path = null;
        flight = null;
    }

    @Override
    public boolean canContinueToUse() {
        return isFrightened(mob) && !mob.getNavigation().isDone();
    }
}
