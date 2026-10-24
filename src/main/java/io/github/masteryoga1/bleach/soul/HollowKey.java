package io.github.masteryoga1.bleach.soul;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What a Hollow (or a Plus) does with the Devour key. The key sends one message when it goes down and
 * another when it comes up, so a hold can last as long as the key does. The server checks everything
 * again (see {@link DevourEvents#onKey}). Sent by ordinal, so new keys go last (Cero comes next).
 */
public enum HollowKey {
    /** The key went down, with the creature under the crosshair: devour it, or gnaw at the chain as a Plus. */
    PRESS,
    /** The key came up, a screen opened or the player died: let go. */
    RELEASE,
    /** The key pressed while sneaking: the Hollow sight on or off. */
    SIGHT;

    private static final IntFunction<HollowKey> BY_ID =
            ByIdMap.continuous(HollowKey::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, HollowKey> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, HollowKey::ordinal);
}
