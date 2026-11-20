package io.github.masteryoga1.bleach.world.gen;

import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Lays Seireitei's streets in the chunk as the plan works them out column by column: stone brick paving for the top
 * block, the Senkaimon court's marked arrival and its lanterns, and lane walls of plaster under a course of tiles.
 */
public final class SeireiteiStreetsFeature extends Feature<NoneFeatureConfiguration> {
    public SeireiteiStreetsFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        BlockState paving = Blocks.STONE_BRICKS.defaultBlockState(), tiles = ModBlocks.ROOF_TILES.get().defaultBlockState();
        BlockState black = ModBlocks.BLACK_PLASTER.get().defaultBlockState();
        BlockState white = ModBlocks.WHITE_PLASTER.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // The lawn's top block: paving replaces it, so a soul still stands at the plateau's height.
        int top = SeireiteiPlan.PLATEAU_Y - 1;
        boolean laid = false;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                SeireiteiPlan.Street street = SeireiteiPlan.street(x, z);
                switch (street) {
                    case NONE -> {
                    }
                    case PAVING -> setBlock(level, pos.set(x, top, z), paving);
                    case MARK -> setBlock(level, pos.set(x, top, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                    case LANTERN -> {
                        setBlock(level, pos.set(x, top, z), paving);
                        setBlock(level, pos.set(x, top + 1, z), Blocks.STONE_BRICK_WALL.defaultBlockState());
                        setBlock(level, pos.set(x, top + 2, z), Blocks.LANTERN.defaultBlockState());
                    }
                    // On the lawn: a course of black plaster, then white, under a cap of tiles.
                    case WALL -> {
                        for (int y = 1, h = SeireiteiPlan.LANE_WALL_HEIGHT; y <= h; y++) {
                            setBlock(level, pos.set(x, top + y, z), y == 1 ? black : y < h ? white : tiles);
                        }
                    }
                }
                laid |= street != SeireiteiPlan.Street.NONE;
            }
        }
        return laid;
    }
}
