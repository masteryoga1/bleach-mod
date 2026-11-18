package io.github.masteryoga1.bleach.soul;

import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Sekkiseki's hold on a soul it shuts in: with a block that undoes reiryoku near on every side, a player's reiryoku, and a
 * Quincy's reishi, stops refilling and drains away, so every art fails for want of it. Worked out again every look;
 * nothing is saved.
 */
public final class SekkisekiLogic {
    /** How far off a block that undoes reiryoku may lie in each direction from the soul's block, that one not counted. */
    public static final int REACH = 8;
    /** How often each player is looked at, in its own ticks. */
    public static final int CHECK_TICKS = 10;
    /** The ticks in which a full pool drains away. */
    public static final int DRAIN_TICKS = 200;
    /** The line a soul aware of spirits is told once it is shut in. */
    public static final String ENCLOSED = "message.bleach.sekkiseki.enclosed";
    /** Down first: nearly every soul stands on ground with none under it, so most looks end after 8 blocks. */
    private static final Direction[] ORDER = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST};

    private SekkisekiLogic() {
    }

    /** Whether blocks that undo reiryoku ({@link SpiritClip#negates}) shut this block in; an unloaded one counts as none. */
    public static boolean enclosed(Level level, BlockPos pos) {
        return enclosed(at -> level.isLoaded(at) && SpiritClip.negates(level.getBlockState(at)), pos);
    }

    /**
     * Whether {@code negates} holds for a block within {@link #REACH} of this one in each of the six directions, down
     * first, the first direction with none ending the look. Public for game tests, which judge a template by it.
     */
    public static boolean enclosed(Predicate<BlockPos> negates, BlockPos pos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Direction direction : ORDER) {
            boolean met = false;
            at.set(pos);
            for (int k = 0; k < REACH && !met; k++) {
                met = negates.test(at.move(direction));
            }
            if (!met) {
                return false;
            }
        }
        return true;
    }

    /** Whether this soul was shut in at its last look. */
    public static boolean held(SoulData data) {
        return data.enclosed;
    }

    /**
     * A living player's tick, before its reiryoku refills: every {@link #CHECK_TICKS} it is looked at, and told once if
     * newly shut in; while shut in its reiryoku and reishi drain by a {@link #DRAIN_TICKS}th of their most a tick.
     */
    static void tick(ServerPlayer player, SoulData data) {
        if (player.isCreative() || player.isSpectator()) {
            data.enclosed = false;
            return;
        }
        if (player.tickCount % CHECK_TICKS == 0) {
            boolean now = enclosed(player.level(), player.blockPosition());
            if (now && !data.enclosed && SoulLogic.isAware(data)) {
                player.displayClientMessage(Component.translatable(ENCLOSED).withStyle(ChatFormatting.GRAY), true);
            }
            data.enclosed = now;
        }
        if (data.enclosed) {
            data.setReiryoku(data.reiryoku() - data.maxReiryoku() / DRAIN_TICKS);
            float reishi = QuincyLogic.reishi(data);
            if (reishi > 0.0F) {
                QuincyLogic.setReishi(data, reishi - QuincyLogic.maxReishi(data) / DRAIN_TICKS);
            }
        }
    }
}
