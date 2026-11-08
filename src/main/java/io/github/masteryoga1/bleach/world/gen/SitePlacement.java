package io.github.masteryoga1.bleach.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** {@code bleach:site}: a structure set's starts are the chunks Soul Society's plan names for its group, and no others. */
public final class SitePlacement extends StructurePlacement {
    public static final MapCodec<SitePlacement> CODEC = RecordCodecBuilder.<SitePlacement>mapCodec(instance ->
            placementCodec(instance)
                    .and(Codec.STRING.fieldOf("group").forGetter(SitePlacement::group))
                    .apply(instance, SitePlacement::new));

    private final String group;

    @SuppressWarnings("deprecation")
    private SitePlacement(Vec3i locateOffset, FrequencyReductionMethod reduction, float frequency, int salt,
                          Optional<ExclusionZone> exclusionZone, String group) {
        super(locateOffset, reduction, frequency, salt, exclusionZone);
        this.group = group;
    }

    /** The plan's group whose sites these are. */
    public String group() {
        return group;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return SeireiteiPlan.siteAt(group, chunkX, chunkZ) != null;
    }

    @Override
    public StructurePlacementType<?> type() {
        return ModWorldgen.SITE_PLACEMENT.get();
    }
}
