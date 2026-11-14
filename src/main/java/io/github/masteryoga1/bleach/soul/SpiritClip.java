package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.registry.BleachTags;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Where a spirit art meets a block that undoes reiryoku (Sekkiseki, or whatever {@link BleachTags#NEGATES_REIRYOKU}
 * holds). Every art that traces blocks asks here, and one that meets such a block comes undone there ({@link #fizzle}).
 */
public final class SpiritClip {
    /** How many motes of pale light a fizzle sprays. */
    private static final int SPRAY_COUNT = 12;
    /** How far about the spot, in blocks, the motes start. */
    private static final double SPRAY_SPREAD = 0.2;
    /** How fast the motes fly. */
    private static final double SPRAY_SPEED = 0.05;

    private SpiritClip() {
    }

    /** A block trace as {@code level.clip} gives it, and whether the block it ended at undoes reiryoku. */
    public record Hit(BlockHitResult block, boolean negated) {
    }

    /** Whether this block undoes reiryoku: whether it is in {@link BleachTags#NEGATES_REIRYOKU}. */
    public static boolean negates(BlockState state) {
        return state.is(BleachTags.NEGATES_REIRYOKU);
    }

    /** Whether this hit is on a block that undoes reiryoku; a miss or a creature never is. */
    public static boolean negates(BlockGetter level, @Nullable HitResult hit) {
        return hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && negates(level.getBlockState(block.getBlockPos()));
    }

    /** An art's own block trace, exactly as {@code level.clip} gives it, and whether it ended at such a block. */
    public static Hit clip(BlockGetter level, ClipContext context) {
        BlockHitResult hit = level.clip(context);
        return new Hit(hit, negates(level, hit));
    }

    /**
     * Where the straight line from {@code from} to {@code to} first enters a loaded block that undoes reiryoku, taken
     * as its whole cell, passing every other block; null if it enters none. It never loads a chunk.
     */
    @Nullable
    public static BlockHitResult firstOn(Level level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level,
                (world, pos) -> world.isLoaded(pos) && negates(world.getBlockState(pos))
                        ? Shapes.block().clip(from, to, pos.immutable()) : null,
                world -> null);
    }

    /** An art coming undone here: a spray of pale light and a hiss, for those who see spirits. Server side. */
    public static void fizzle(ServerLevel level, Vec3 at, SoundSource category) {
        SpiritSenses.particles(level, ParticleTypes.WAX_OFF, at.x, at.y, at.z, SPRAY_COUNT, SPRAY_SPREAD, SPRAY_SPREAD,
                SPRAY_SPREAD, SPRAY_SPEED);
        SpiritSenses.play(level, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, category, CeroBeam.FIZZLE_VOLUME,
                CeroBeam.FIZZLE_PITCH, false);
    }
}
