package io.github.masteryoga1.bleach.world.gen;

import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Lays the Seireiheki: every wall column of the chunk, in Sekkiseki from just above the bedrock to the wall's top. */
public final class SeireihekiFeature extends Feature<NoneFeatureConfiguration> {
    public SeireihekiFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        BlockState stone = ModBlocks.SEKKISEKI.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean laid = false;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                if (!SeireiteiPlan.inWall(x, z)) {
                    continue;
                }
                // Air too, so nothing generated before this can leave a hole.
                for (int y = level.getMinBuildHeight() + 1; y < SeireiteiPlan.WALL_TOP_Y; y++) {
                    setBlock(level, pos.set(x, y, z), stone);
                }
                laid = true;
            }
        }
        return laid;
    }
}
