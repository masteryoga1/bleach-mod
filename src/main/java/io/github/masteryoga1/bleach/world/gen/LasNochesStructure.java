package io.github.masteryoga1.bleach.world.gen;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * {@code bleach:las_noches}: Las Noches' shell, one piece set out about Hueco Mundo's centre on its level ground, wherever
 * it is started; its set starts it in the centre's chunk, whose structure references reach every chunk it covers.
 */
public final class LasNochesStructure extends Structure {
    public static final MapCodec<LasNochesStructure> CODEC = simpleCodec(LasNochesStructure::new);

    public LasNochesStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // The biome is read here, at the dome's foot over the centre column: las_noches.
        BlockPos centre = new BlockPos(0, HuecoMundoPlan.LEVEL_Y, 0);
        return Optional.of(new GenerationStub(centre, pieces -> pieces.addPiece(new LasNochesPiece())));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.LAS_NOCHES.get();
    }
}
