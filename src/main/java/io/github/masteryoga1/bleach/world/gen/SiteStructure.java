package io.github.masteryoga1.bleach.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * {@code bleach:site}: one of Soul Society's planned buildings. In the chunk its site starts in, that site's template is
 * set at its corner and turn, at its own height or on the ground there; placed anywhere else by command, the group's
 * first site's template is set at that chunk's corner, on the ground.
 */
public final class SiteStructure extends Structure {
    public static final MapCodec<SiteStructure> CODEC = RecordCodecBuilder.<SiteStructure>mapCodec(instance ->
            instance.group(settingsCodec(instance), Codec.STRING.fieldOf("group").forGetter(SiteStructure::group))
                    .apply(instance, SiteStructure::new));

    private final String group;

    public SiteStructure(StructureSettings settings, String group) {
        super(settings);
        this.group = group;
    }

    /** The plan's group whose site this is. */
    public String group() {
        return group;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        SeireiteiPlan.Site planned = SeireiteiPlan.siteAt(group, chunk.x, chunk.z);
        List<SeireiteiPlan.Site> sites = SeireiteiPlan.sites(group);
        if (planned == null && sites.isEmpty()) {
            return Optional.empty();
        }
        SeireiteiPlan.Site site = planned != null ? planned : sites.get(0);
        int x = planned != null ? site.x() : chunk.getMinBlockX();
        int z = planned != null ? site.z() : chunk.getMinBlockZ();
        // The noise's ground, not the world's, so worldgen, a command and the stamp for old worlds all agree; a site with
        // its own height, under the ground, is set there.
        int y = planned != null && planned.y() != SeireiteiPlan.Site.GROUND ? planned.y()
                : context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
        BlockPos corner = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(corner, pieces -> pieces.addPiece(
                new SitePiece(context.structureTemplateManager(), site.template(), corner, site.rotation()))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.SITE.get();
    }
}
