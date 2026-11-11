package io.github.masteryoga1.bleach.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The density function {@code bleach:radial}: how far a block's centre lies from the centre column, or from the nearer
 * axis, in blocks. Soul Society's rings and gate gaps are cut from it, as are Hueco Mundo's level ground, slope and biomes.
 */
public record RadialDensity(Mode mode) implements DensityFunction.SimpleFunction {
    /** The type registered as {@code bleach:radial}: its one field is the mode. */
    public static final MapCodec<RadialDensity> DATA_CODEC =
            Mode.CODEC.fieldOf("mode").xmap(RadialDensity::new, RadialDensity::mode);
    public static final KeyDispatchDataCodec<RadialDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    /** Further than any block inside the world border can lie from the centre. */
    public static final double FARTHEST = 4.25E7;

    @Override
    public double compute(FunctionContext context) {
        return mode == Mode.RADIUS ? SeireiteiPlan.radius(context.blockX(), context.blockZ())
                : SeireiteiPlan.axisDistance(context.blockX(), context.blockZ());
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public double maxValue() {
        return FARTHEST;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    /** What the distance is measured from. */
    public enum Mode implements StringRepresentable {
        /** The centre column, x = z = 0. */
        RADIUS("radius"),
        /** The nearer of the lines x = 0 and z = 0. */
        AXIS("axis");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
