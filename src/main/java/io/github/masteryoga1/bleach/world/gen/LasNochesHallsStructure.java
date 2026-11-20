package io.github.masteryoga1.bleach.world.gen;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * {@code bleach:las_noches_halls}: the halls under Las Noches' dome, one piece set out about Hueco Mundo's centre
 * wherever it is started; its set starts it in the centre's chunk, and its step lays it after the shell in each chunk.
 */
public final class LasNochesHallsStructure extends Structure {
    public static final MapCodec<LasNochesHallsStructure> CODEC = simpleCodec(LasNochesHallsStructure::new);

    public LasNochesHallsStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // The biome is read here, on the floor over the centre column, as the shell's is: las_noches.
        BlockPos centre = new BlockPos(0, HuecoMundoPlan.LEVEL_Y, 0);
        return Optional.of(new GenerationStub(centre, pieces -> pieces.addPiece(new LasNochesHallsPiece())));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.LAS_NOCHES_HALLS.get();
    }
}
