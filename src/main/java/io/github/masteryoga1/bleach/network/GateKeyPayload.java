package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ByIdMap;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the Gate key went down, came up, or a screen opened while it was held. The server decides
 * everything ({@link TravelEvents#onKey}), and a later Garganta can use the same three.
 */
public record GateKeyPayload(GateKey key) implements CustomPacketPayload {
    public static final Type<GateKeyPayload> TYPE = new Type<>(BleachMod.id("gate_key"));
    public static final StreamCodec<ByteBuf, GateKeyPayload> STREAM_CODEC =
            GateKey.STREAM_CODEC.map(GateKeyPayload::new, GateKeyPayload::key);

    /** Sent by ordinal, so new keys go last: an unknown one reads as a press, which the server checks like any other. */
    public enum GateKey {
        /** The key went down: begin the turn. */
        PRESS,
        /** The key came up: open the gate if the turn is done, or end it. */
        RELEASE,
        /** A screen opened while it was held: end the turn. */
        CANCEL;

        private static final IntFunction<GateKey> BY_ID =
                ByIdMap.continuous(GateKey::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, GateKey> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, GateKey::ordinal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(GateKeyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TravelEvents.onKey(player, payload.key(), player.level().getGameTime());
        }
    }
}
