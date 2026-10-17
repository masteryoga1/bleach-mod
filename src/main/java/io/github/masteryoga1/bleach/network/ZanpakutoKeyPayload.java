package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player pressed, or let go of, a zanpakutō key. The server decides what happens. */
public record ZanpakutoKeyPayload(ZanpakutoKey key) implements CustomPacketPayload {
    public static final Type<ZanpakutoKeyPayload> TYPE = new Type<>(BleachMod.id("zanpakuto_key"));
    public static final StreamCodec<ByteBuf, ZanpakutoKeyPayload> STREAM_CODEC = ZanpakutoKey.STREAM_CODEC
            .map(ZanpakutoKeyPayload::new, ZanpakutoKeyPayload::key);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ZanpakutoKeyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ShikaiEvents.onKey(player, payload.key());
        }
    }
}
