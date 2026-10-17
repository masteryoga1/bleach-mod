package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the player's answer to the bloodline question. The server rolls the blood
 * itself, and only takes an answer to a question it asked this session that is still open.
 */
public record BloodlineChoicePayload(BloodlineChoice choice) implements CustomPacketPayload {
    public static final Type<BloodlineChoicePayload> TYPE = new Type<>(BleachMod.id("bloodline_choice"));
    public static final StreamCodec<ByteBuf, BloodlineChoicePayload> STREAM_CODEC = BloodlineChoice.STREAM_CODEC
            .map(BloodlineChoicePayload::new, BloodlineChoicePayload::choice);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BloodlineChoicePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuincyEvents.chooseBloodline(player, payload.choice());
        }
    }
}
