package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Where a spirit art meets a block that undoes reiryoku (Sekkiseki, or whatever {@link BleachTags#NEGATES_REIRYOKU}
 * holds), or the Shakonmaku over Seireitei. Every art that traces blocks asks here, and one that meets either comes
 * undone there ({@link #fizzle}); Pesquisa and the gates ask here too.
 */
public final class SpiritClip {
    /** How far a box is shrunk before its blocks are read, so one it only touches from outside does not count. */
    private static final double EDGE = 1.0E-7;
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

    /** Whether this hit is on the Shakonmaku or a block that undoes reiryoku; a miss or a creature never is. */
    public static boolean negates(BlockGetter level, @Nullable HitResult hit) {
        return hit instanceof Membrane || hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && negates(level.getBlockState(block.getBlockPos()));
    }

    /**
     * An art's own block trace, as {@code level.clip} gives it unless the Shakonmaku comes first, and whether it ended
     * at the membrane or at a block that undoes reiryoku.
     */
    public static Hit clip(BlockGetter level, ClipContext context) {
        BlockHitResult hit = level.clip(context);
        Membrane membrane = level instanceof Level world ? membrane(world, context.getFrom(), hit.getLocation()) : null;
        return membrane != null ? new Hit(membrane, true) : new Hit(hit, negates(level, hit));
    }

    /** Where the line from {@code from} to {@code to} meets the Shakonmaku, in Soul Society; null if it does not. */
    @Nullable
    public static Membrane membrane(Level level, Vec3 from, Vec3 to) {
        BlockHitResult at = Shakonmaku.in(level) ? Shakonmaku.crossing(from, to) : null;
        return at == null ? null : new Membrane(at);
    }

    /**
     * Where the straight line from {@code from} to {@code to} first meets the Shakonmaku or enters a loaded block that
     * undoes reiryoku, taken as its whole cell, passing every other block; null if it does neither. It never loads a
     * chunk.
     */
    @Nullable
    public static BlockHitResult firstOn(Level level, Vec3 from, Vec3 to) {
        Membrane membrane = membrane(level, from, to);
        Vec3 end = membrane == null ? to : membrane.getLocation();
        BlockHitResult block = BlockGetter.traverseBlocks(from, end, level,
                (world, pos) -> world.isLoaded(pos) && negates(world.getBlockState(pos))
                        ? Shapes.block().clip(from, to, pos.immutable()) : null,
                world -> null);
        return block != null ? block : membrane;
    }

    /** Whether a loaded block that undoes reiryoku lies in this box; one the box only touches from outside does not. */
    public static boolean meets(Level level, AABB box) {
        return BlockPos.betweenClosedStream(box.deflate(EDGE))
                .anyMatch(pos -> level.isLoaded(pos) && negates(level.getBlockState(pos)));
    }

    /** Where a line meets the Shakonmaku: no block is there, but an art comes undone at it as at Sekkiseki. */
    public static final class Membrane extends BlockHitResult {
        private Membrane(BlockHitResult at) {
            super(at.getLocation(), at.getDirection(), at.getBlockPos(), false);
        }
    }

    /** An art coming undone here: a spray of pale light and a hiss, for those who see spirits. Server side. */
    public static void fizzle(ServerLevel level, Vec3 at, SoundSource category) {
        SpiritSenses.particles(level, ParticleTypes.WAX_OFF, at.x, at.y, at.z, SPRAY_COUNT, SPRAY_SPREAD, SPRAY_SPREAD,
                SPRAY_SPREAD, SPRAY_SPEED);
        SpiritSenses.play(level, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, category, CeroBeam.FIZZLE_VOLUME,
                CeroBeam.FIZZLE_PITCH, false);
    }
}
