package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.DevourEvents;
import io.github.masteryoga1.bleach.soul.HollowKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the Devour key went down (with the id of the creature under the crosshair, or -1),
 * came up, or was pressed while sneaking. The id is only a hint: the server checks the creature again,
 * and ignores the id for anything but a press.
 */
public record HollowKeyPayload(HollowKey key, int target) implements CustomPacketPayload {
    public static final Type<HollowKeyPayload> TYPE = new Type<>(BleachMod.id("hollow_key"));
    public static final StreamCodec<ByteBuf, HollowKeyPayload> STREAM_CODEC = StreamCodec.composite(
            HollowKey.STREAM_CODEC, HollowKeyPayload::key,
            ByteBufCodecs.VAR_INT, HollowKeyPayload::target,
            HollowKeyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(HollowKeyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            DevourEvents.onKey(player, payload.key(), payload.target());
        }
    }
}
