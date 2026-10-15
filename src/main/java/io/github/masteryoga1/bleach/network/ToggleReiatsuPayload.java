package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.ReiatsuEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player pressed the reiatsu release key. The server decides whether it happens. */
public record ToggleReiatsuPayload() implements CustomPacketPayload {
    public static final ToggleReiatsuPayload INSTANCE = new ToggleReiatsuPayload();
    public static final Type<ToggleReiatsuPayload> TYPE = new Type<>(BleachMod.id("toggle_reiatsu"));
    public static final StreamCodec<ByteBuf, ToggleReiatsuPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ToggleReiatsuPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ReiatsuEvents.toggleRelease(player);
        }
    }
}
