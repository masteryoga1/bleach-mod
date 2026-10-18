package io.github.masteryoga1.bleach.quincy;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * The answers to the bloodline question. The server rolls the blood itself from the answer, so a
 * client can only ask, never pick Echt. Sent by ordinal, so new answers go last.
 */
public enum BloodlineChoice {
    /** No Quincy blood. */
    HUMAN,
    /** Quincy blood: Echt now and then, Gemischt otherwise. */
    QUINCY,
    /** A small chance of Quincy blood. */
    FATE;

    private static final IntFunction<BloodlineChoice> BY_ID =
            ByIdMap.continuous(BloodlineChoice::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, BloodlineChoice> STREAM_CODEC =
            ByteBufCodecs.idMapper(BY_ID, BloodlineChoice::ordinal);
}
