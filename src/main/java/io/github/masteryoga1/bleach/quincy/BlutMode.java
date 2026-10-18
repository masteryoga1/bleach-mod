package io.github.masteryoga1.bleach.quincy;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * Blut, reishi let into a Quincy's blood: at most one runs at a time. Saved by name, but sent by
 * ordinal (in {@code SoulAppearance}), so new modes go last.
 */
public enum BlutMode implements StringRepresentable {
    OFF("off"),
    /** Blut Vene, in the veins: defence. */
    VENE("vene"),
    /** Blut Arterie, in the arteries: attack. */
    ARTERIE("arterie");

    public static final Codec<BlutMode> CODEC = StringRepresentable.fromEnum(BlutMode::values);
    private static final IntFunction<BlutMode> BY_ID =
            ByIdMap.continuous(BlutMode::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, BlutMode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, BlutMode::ordinal);

    private final String name;

    BlutMode(String name) {
        this.name = name;
    }

    public Component displayName() {
        return Component.translatable("blut.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
