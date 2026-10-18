package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.client.ClientPayloads;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: ask the player whether Quincy blood runs in them. The question waits until
 * nothing else is on screen (the world loading, say), and the answer comes back as a
 * {@link BloodlineChoicePayload}.
 */
public record OpenBloodlinePayload() implements CustomPacketPayload {
    public static final OpenBloodlinePayload INSTANCE = new OpenBloodlinePayload();
    public static final Type<OpenBloodlinePayload> TYPE = new Type<>(BleachMod.id("open_bloodline"));
    public static final StreamCodec<ByteBuf, OpenBloodlinePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(OpenBloodlinePayload payload, IPayloadContext context) {
        ClientPayloads.openBloodline();
    }
}
