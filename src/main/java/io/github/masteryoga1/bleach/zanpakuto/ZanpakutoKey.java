package io.github.masteryoga1.bleach.zanpakuto;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What the zanpakutō keys ask of the server. The Technique key sends one message when it goes down
 * and another when it comes up, so a Getsuga Tenshō can gather for as long as it is held. A Visored's
 * Hollow Mask key rides along as {@link #MASK}. The server checks everything again. Sent by ordinal,
 * so new keys go last.
 */
public enum ZanpakutoKey {
    /** Call out the zanpakutō's name and release it. */
    RELEASE,
    SEAL,
    /** The Technique key went down: start gathering. */
    TECHNIQUE_PRESS,
    /** The Technique key came up: loose what was gathered. */
    TECHNIQUE_RELEASE,
    /** The held technique was interrupted on the client, by a slot change or a screen: let it go without firing. */
    TECHNIQUE_CANCEL,
    /** A Visored's Hollow mask, on and off; not a zanpakutō key but sent with them. */
    MASK;

    private static final IntFunction<ZanpakutoKey> BY_ID =
            ByIdMap.continuous(ZanpakutoKey::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, ZanpakutoKey> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, ZanpakutoKey::ordinal);
}
