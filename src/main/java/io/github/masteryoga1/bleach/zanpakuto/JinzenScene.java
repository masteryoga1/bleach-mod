package io.github.masteryoga1.bleach.zanpakuto;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What the world inside the blade shows a soul when Jinzen opens, chosen by the server from the
 * soul's bond (see {@code JinzenLogic.sceneFor}). Sent by ordinal, so new scenes go last.
 */
public enum JinzenScene {
    /** A named soul comes back to see its spirit. */
    VISIT,
    /** The spirit the soul bonded with is no longer in the world's data. */
    UNKNOWN,
    /** The bond is still too weak for the blade to answer. */
    SILENT,
    /** The spirit stirs and shows what it is becoming, but cannot speak yet. */
    STIRRING,
    /** The bond is full, but a student's soul cannot hold the name until it graduates. */
    STUDENT,
    /** The bond is full, but the soul's reiryoku is too weak to hear the name. */
    WEAK,
    /** The soul failed the spirit's test a short while ago, and must wait. */
    RESTING,
    /** The spirit sets its test, and tells its name to a soul that passes. */
    MEETING;

    private static final IntFunction<JinzenScene> BY_ID =
            ByIdMap.continuous(JinzenScene::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, JinzenScene> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, JinzenScene::ordinal);
}
