package io.github.masteryoga1.bleach.registry;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.world.gen.DangaiLaneFeature;
import io.github.masteryoga1.bleach.world.gen.RadialDensity;
import io.github.masteryoga1.bleach.world.gen.SeireihekiFeature;
import io.github.masteryoga1.bleach.world.gen.SitePiece;
import io.github.masteryoga1.bleach.world.gen.SitePlacement;
import io.github.masteryoga1.bleach.world.gen.SiteStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's world-generation types: its density function, its features, and the planned sites' placement, structure and
 * piece.
 */
public final class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, BleachMod.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, BleachMod.MODID);
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, BleachMod.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, BleachMod.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, BleachMod.MODID);

    /** {@code bleach:radial}, which Soul Society's noise settings read. */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RadialDensity>> RADIAL =
            DENSITY_FUNCTION_TYPES.register("radial", () -> RadialDensity.DATA_CODEC);
    /** {@code bleach:seireiheki}, which the wall's biome places. */
    public static final DeferredHolder<Feature<?>, SeireihekiFeature> SEIREIHEKI =
            FEATURES.register("seireiheki", SeireihekiFeature::new);
    /** {@code bleach:dangai_lane}, which the Dangai's biome places. */
    public static final DeferredHolder<Feature<?>, DangaiLaneFeature> DANGAI_LANE =
            FEATURES.register("dangai_lane", DangaiLaneFeature::new);
    /** {@code bleach:site}, where Soul Society's planned sites start. Saved in no chunk; registered for good all the same. */
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<SitePlacement>> SITE_PLACEMENT =
            STRUCTURE_PLACEMENTS.register("site", () -> (StructurePlacementType<SitePlacement>) () -> SitePlacement.CODEC);
    /** {@code bleach:site}, a planned building. Its id is saved in chunks, so it is registered for good. */
    public static final DeferredHolder<StructureType<?>, StructureType<SiteStructure>> SITE =
            STRUCTURE_TYPES.register("site", () -> (StructureType<SiteStructure>) () -> SiteStructure.CODEC);
    /** {@code bleach:site}'s piece. Its id is saved in chunks, so it is registered for good. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> SITE_PIECE =
            STRUCTURE_PIECES.register("site", () -> (StructurePieceType.StructureTemplateType) SitePiece::new);

    private ModWorldgen() {
    }
}
