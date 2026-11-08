package io.github.masteryoga1.bleach.registry;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.world.gen.DangaiLaneFeature;
import io.github.masteryoga1.bleach.world.gen.RadialDensity;
import io.github.masteryoga1.bleach.world.gen.SeireihekiFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's world-generation types: its density function and its features. */
public final class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, BleachMod.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, BleachMod.MODID);

    /** {@code bleach:radial}, which Soul Society's noise settings read. */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RadialDensity>> RADIAL =
            DENSITY_FUNCTION_TYPES.register("radial", () -> RadialDensity.DATA_CODEC);
    /** {@code bleach:seireiheki}, which the wall's biome places. */
    public static final DeferredHolder<Feature<?>, SeireihekiFeature> SEIREIHEKI =
            FEATURES.register("seireiheki", SeireihekiFeature::new);
    /** {@code bleach:dangai_lane}, which the Dangai's biome places. */
    public static final DeferredHolder<Feature<?>, DangaiLaneFeature> DANGAI_LANE =
            FEATURES.register("dangai_lane", DangaiLaneFeature::new);

    private ModWorldgen() {
    }
}
