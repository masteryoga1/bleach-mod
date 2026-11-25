package io.github.masteryoga1.bleach.world.gen;

import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * {@code bleach:las_noches}'s one piece: Las Noches' shell over the plan's whole box, laid cell by cell from
 * {@link HuecoMundoPlan#shellAt} within each chunk's part of it, so worldgen, a command and a test lay the same.
 */
public final class LasNochesPiece extends StructurePiece {
    /** The version of the plan's table this piece lays, saved with it, so a later table can tell the chunks this one laid. */
    public static final int PLAN = 1;
    private final int plan;

    public LasNochesPiece() {
        // A copy, as a piece may move its own box.
        super(ModWorldgen.LAS_NOCHES_PIECE.get(), 0, HuecoMundoPlan.SHELL_BOX.moved(0, 0, 0));
        setOrientation(null);
        plan = PLAN;
    }

    /** As saved in a chunk: its box and its plan's version, the first if it names none; the plan gives every cell. */
    public LasNochesPiece(CompoundTag tag) {
        super(ModWorldgen.LAS_NOCHES_PIECE.get(), tag);
        plan = tag.contains("Plan") ? tag.getInt("Plan") : PLAN;
    }

    /** The version of the plan's table this piece was set out by. */
    public int plan() {
        return plan;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Plan", plan);
    }

    /**
     * Lays the shell's blocks in this chunk's part of the box; air is never laid, so a gateway, the dome's inside and the
     * fifth tower's room keep what stands there, the open air of the level ground.
     */
    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunk, ChunkPos pos, BlockPos pivot) {
        BoundingBox shell = getBoundingBox();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(chunk.minX(), shell.minX()); x <= Math.min(chunk.maxX(), shell.maxX()); x++) {
            for (int z = Math.max(chunk.minZ(), shell.minZ()); z <= Math.min(chunk.maxZ(), shell.maxZ()); z++) {
                for (int y = Math.max(chunk.minY(), shell.minY()); y <= Math.min(chunk.maxY(), shell.maxY()); y++) {
                    HuecoMundoPlan.Shell kind = HuecoMundoPlan.shellAt(x, y, z);
                    if (kind != null) {
                        level.setBlock(at.set(x, y, z), state(kind), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** The block that lays this kind of the shell. */
    public static BlockState state(HuecoMundoPlan.Shell kind) {
        return switch (kind) {
            case STONE -> ModBlocks.LAS_NOCHES_STONE.get().defaultBlockState();
            case TILES -> ModBlocks.LAS_NOCHES_TILES.get().defaultBlockState();
            case TRIM -> ModBlocks.LAS_NOCHES_TRIM.get().defaultBlockState();
        };
    }
}
