package io.github.masteryoga1.bleach.zanpakuto;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * How far a zanpakutō is released: sealed, in Shikai or in Bankai. Each blade carries its own in its
 * {@link ZanpakutoBlade}, which every client that sees the stack is sent. Saved by name, but sent by ordinal, so a new
 * stage would go last; an ordinal it does not know reads as {@link #SEALED}.
 */
public enum ZanpakutoStage implements StringRepresentable {
    SEALED("sealed"),
    SHIKAI("shikai"),
    BANKAI("bankai");

    public static final Codec<ZanpakutoStage> CODEC = StringRepresentable.fromEnum(ZanpakutoStage::values);
    private static final IntFunction<ZanpakutoStage> BY_ID =
            ByIdMap.continuous(ZanpakutoStage::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, ZanpakutoStage> STREAM_CODEC =
            ByteBufCodecs.idMapper(BY_ID, ZanpakutoStage::ordinal);

    private final String name;

    ZanpakutoStage(String name) {
        this.name = name;
    }

    /** Whether a blade at this stage is released at all, in Shikai or in Bankai. */
    public boolean isReleased() {
        return this != SEALED;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
