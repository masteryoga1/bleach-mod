package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.quincy.QuincyKey;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player pressed a Quincy key. The server decides what happens. */
public record QuincyKeyPayload(QuincyKey key) implements CustomPacketPayload {
    public static final Type<QuincyKeyPayload> TYPE = new Type<>(BleachMod.id("quincy_key"));
    public static final StreamCodec<ByteBuf, QuincyKeyPayload> STREAM_CODEC = QuincyKey.STREAM_CODEC
            .map(QuincyKeyPayload::new, QuincyKeyPayload::key);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(QuincyKeyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            QuincyEvents.onKey(player, payload.key());
        }
    }
}
