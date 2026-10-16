package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.ReishiFooting;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the player asked for reishi footing in mid-air. The server decides and always
 * answers with a {@link ReishiFootingStatePayload}.
 *
 * @param viaJump whether they pressed jump rather than the footing key
 */
public record ReishiFootingPayload(boolean viaJump) implements CustomPacketPayload {
    public static final Type<ReishiFootingPayload> TYPE = new Type<>(BleachMod.id("reishi_footing"));
    public static final StreamCodec<ByteBuf, ReishiFootingPayload> STREAM_CODEC = ByteBufCodecs.BOOL
            .map(ReishiFootingPayload::new, ReishiFootingPayload::viaJump);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ReishiFootingPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ReishiFooting.activate(player, payload.viaJump());
        }
    }
}
