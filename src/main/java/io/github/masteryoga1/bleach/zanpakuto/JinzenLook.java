package io.github.masteryoga1.bleach.zanpakuto;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How a Jinzen page looks: the backdrop the client draws behind the text, and the colour it draws
 * it in.
 *
 * @param tint an RGB colour, such as {@code 0x9FC8E8}
 */
public record JinzenLook(JinzenBackdrop backdrop, int tint) {
    /** The dark of a blade that does not answer. */
    public static final JinzenLook VOID = new JinzenLook(JinzenBackdrop.VOID, 0x202028);

    public static final StreamCodec<ByteBuf, JinzenLook> STREAM_CODEC = StreamCodec.composite(
            JinzenBackdrop.STREAM_CODEC, JinzenLook::backdrop,
            ByteBufCodecs.INT, JinzenLook::tint,
            JinzenLook::new);

    /** Keeps only the colour, so a stray alpha byte never reaches the screen. */
    public JinzenLook {
        tint &= 0xFFFFFF;
    }
}
