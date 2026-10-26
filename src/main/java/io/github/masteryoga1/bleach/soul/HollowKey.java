package io.github.masteryoga1.bleach.soul;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What a Hollow (or a Plus, or an Arrancar) does with the Devour and Cero keys. Each key sends one message when
 * it goes down and another when it comes up, so a hold, or a Cero gathering, can last as long as the key does.
 * The server checks everything again (see {@link DevourEvents#onKey}). Sent by ordinal, so new keys go
 * last: an old server would read an unknown one as a press of the Devour key.
 */
public enum HollowKey {
    /** The key went down, with the creature under the crosshair: devour it, or gnaw at the chain as a Plus. */
    PRESS,
    /** The key came up, a screen opened or the player died: let go. */
    RELEASE,
    /** The key pressed while sneaking: the Hollow sight on or off. */
    SIGHT,
    /** The Cero key went down: a Menos starts gathering a Cero at its mouth (see {@link HollowCero#start}). */
    CERO,
    /** The Cero key came up: fire the Cero, or let it fizzle if too little has gathered yet. */
    CERO_RELEASE,
    /** A screen opened, or the player died or stopped being a Hollow, with the Cero key held: let it fizzle. */
    CERO_CANCEL,
    /**
     * The Cero key went down while an Arrancar sneaks: it starts gathering a Gran Rey Cero (see
     * {@link GranReyCero#start}). It is let go or fizzles as a Cero is, by {@link #CERO_RELEASE} and {@link #CERO_CANCEL}.
     */
    GRAN_REY_CERO;

    private static final IntFunction<HollowKey> BY_ID =
            ByIdMap.continuous(HollowKey::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, HollowKey> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, HollowKey::ordinal);
}
