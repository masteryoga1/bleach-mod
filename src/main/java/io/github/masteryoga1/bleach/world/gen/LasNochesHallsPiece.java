package io.github.masteryoga1.bleach.world.gen;

import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * {@code bleach:las_noches_halls}'s one piece: the halls over their box, laid cell by cell from
 * {@link HuecoMundoPlan#hallsAt} within each chunk's part of it, after the shell, so worldgen, a command and the stamp lay
 * the same.
 */
public final class LasNochesHallsPiece extends StructurePiece {
    /** The version of the halls' table this piece lays, saved with it, so a later table can tell the chunks this one laid. */
    public static final int PLAN = 1;
    /** Clients hear of each block; no neighbour's shape is updated, so a stair stays straight in a loaded world too. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final int plan;

    public LasNochesHallsPiece() {
        // A copy, as a piece may move its own box.
        super(ModWorldgen.LAS_NOCHES_HALLS_PIECE.get(), 0, HuecoMundoPlan.HALLS_BOX.moved(0, 0, 0));
        setOrientation(null);
        plan = PLAN;
    }

    /** As saved in a chunk: its box and its plan's version, the first if it names none; the plan gives every cell. */
    public LasNochesHallsPiece(CompoundTag tag) {
        super(ModWorldgen.LAS_NOCHES_HALLS_PIECE.get(), tag);
        plan = tag.contains("Plan") ? tag.getInt("Plan") : PLAN;
    }

    /** The version of the halls' table this piece was set out by. */
    public int plan() {
        return plan;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Plan", plan);
    }

    /** Lays the halls in this chunk's part of the box, air in the shell's cells they cut, and leaves every other cell. */
    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunk, ChunkPos pos, BlockPos pivot) {
        BoundingBox halls = getBoundingBox();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(chunk.minX(), halls.minX()); x <= Math.min(chunk.maxX(), halls.maxX()); x++) {
            for (int z = Math.max(chunk.minZ(), halls.minZ()); z <= Math.min(chunk.maxZ(), halls.maxZ()); z++) {
                for (int y = Math.max(chunk.minY(), halls.minY()); y <= Math.min(chunk.maxY(), halls.maxY()); y++) {
                    HuecoMundoPlan.Hall kind = HuecoMundoPlan.hallsAt(x, y, z);
                    if (kind != null) {
                        level.setBlock(at.set(x, y, z), state(kind), FLAGS);
                    }
                }
            }
        }
    }

    /** The block that lays this kind of the halls: a stair's high side faces the way its kind names. */
    public static BlockState state(HuecoMundoPlan.Hall kind) {
        return switch (kind) {
            case CUT -> Blocks.AIR.defaultBlockState();
            case STONE -> ModBlocks.LAS_NOCHES_STONE.get().defaultBlockState();
            case TILES -> ModBlocks.LAS_NOCHES_TILES.get().defaultBlockState();
            case TRIM -> ModBlocks.LAS_NOCHES_TRIM.get().defaultBlockState();
            case SKY -> ModBlocks.LAS_NOCHES_SKY.get().defaultBlockState();
            case STAIR_NORTH -> stair(Direction.NORTH);
            case STAIR_EAST -> stair(Direction.EAST);
            case STAIR_SOUTH -> stair(Direction.SOUTH);
            case STAIR_WEST -> stair(Direction.WEST);
            case SLAB -> Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
        };
    }

    /** A straight smooth quartz stair, its high side to this side, as one placed by a soul walking that way. */
    private static BlockState stair(Direction facing) {
        return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
    }
}
