package io.github.masteryoga1.bleach.world.gen;

import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.world.DangaiPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Carves a Dangai lane into its chunk: the plan's air and Kōryū for the lane's height, leaving the rock as it is. */
public final class DangaiLaneFeature extends Feature<NoneFeatureConfiguration> {
    public DangaiLaneFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        ChunkPos chunk = new ChunkPos(context.origin());
        if (chunk.x < 0 || chunk.x >= DangaiPlan.LANES || chunk.z < 0 || chunk.getMinBlockZ() >= DangaiPlan.END_Z) {
            return false;
        }
        WorldGenLevel level = context.level();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState koryu = ModBlocks.KORYU.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                for (int y = DangaiPlan.FLOOR_Y; y < DangaiPlan.FLOOR_Y + DangaiPlan.LANE_HEIGHT; y++) {
                    switch (DangaiPlan.cell(x, y, z)) {
                        case AIR -> setBlock(level, pos.set(x, y, z), air);
                        case KORYU -> setBlock(level, pos.set(x, y, z), koryu);
                        case ROCK -> {
                        }
                    }
                }
            }
        }
        return true;
    }
}
