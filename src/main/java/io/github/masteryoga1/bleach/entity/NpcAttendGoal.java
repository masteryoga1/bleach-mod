package io.github.masteryoga1.bleach.entity;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a visiting Quincy NPC near the soul it came for while it has nothing to shoot: once the
 * visitor is more than two blocks past its stopping distance it walks over, looking at them, until
 * it is within that distance again. The Elder stays within 4 blocks, the Hunter within 6.
 */
public final class NpcAttendGoal extends Goal {
    /** How often the path is found again, in goal ticks. */
    private static final int REPATH_TICKS = 10;
    /** It sets off once the visitor is this much farther than where it stops. */
    private static final double SLACK = 2.0;
    private static final float LOOK_SPEED = 30.0F;

    private final QuincyNpc npc;
    private final double speed;
    private final double stopSqr;
    private final double startSqr;
    @Nullable
    private ServerPlayer visitor;
    private int repathIn;

    public NpcAttendGoal(QuincyNpc npc, double speed) {
        this.npc = npc;
        this.speed = speed;
        // Made while the Mob is being built: only the class is read, never a field.
        double stop = npc instanceof QuincyElder ? 4.0 : 6.0;
        this.stopSqr = stop * stop;
        this.startSqr = (stop + SLACK) * (stop + SLACK);
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (npc.getTarget() != null) {
            return false;
        }
        ServerPlayer player = npc.visitorPlayer();
        if (player == null || npc.distanceToSqr(player) <= startSqr) {
            return false;
        }
        visitor = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        ServerPlayer player = npc.visitorPlayer();
        return npc.getTarget() == null && player != null && player == visitor && npc.distanceToSqr(player) > stopSqr;
    }

    @Override
    public void start() {
        repathIn = 0;
    }

    @Override
    public void stop() {
        visitor = null;
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (visitor == null) {
            return;
        }
        npc.getLookControl().setLookAt(visitor, LOOK_SPEED, (float) npc.getMaxHeadXRot());
        if (--repathIn <= 0) {
            repathIn = adjustedTickDelay(REPATH_TICKS);
            npc.getNavigation().moveTo(visitor, speed);
        }
    }
}
