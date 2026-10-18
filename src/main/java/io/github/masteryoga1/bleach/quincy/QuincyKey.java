package io.github.masteryoga1.bleach.quincy;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What the Quincy keys ask of the server. The server checks everything again. Sent by ordinal, so
 * new keys go last.
 */
public enum QuincyKey {
    /**
     * The Blut key: from off to Vene, from Vene to Arterie (or off while Arterie is still locked), and
     * from Arterie back to Vene. From a sneaking player it counts as {@link #BLUT_OFF}.
     */
    BLUT,
    /** The Blut key pressed while sneaking: let Blut go. */
    BLUT_OFF;

    private static final IntFunction<QuincyKey> BY_ID =
            ByIdMap.continuous(QuincyKey::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, QuincyKey> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, QuincyKey::ordinal);
}
