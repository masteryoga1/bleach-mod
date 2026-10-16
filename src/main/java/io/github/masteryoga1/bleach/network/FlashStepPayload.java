package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.FlashStep;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the player pressed the flash step key while steering this way. The server
 * decides whether it happens and where it ends.
 *
 * @param forward forward (+) or back (-), from -1 to 1
 * @param left    left (+) or right (-), from -1 to 1
 */
public record FlashStepPayload(float forward, float left) implements CustomPacketPayload {
    public static final Type<FlashStepPayload> TYPE = new Type<>(BleachMod.id("flash_step"));
    public static final StreamCodec<ByteBuf, FlashStepPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, FlashStepPayload::forward,
            ByteBufCodecs.FLOAT, FlashStepPayload::left,
            FlashStepPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(FlashStepPayload payload, IPayloadContext context) {
        if (!Float.isFinite(payload.forward()) || !Float.isFinite(payload.left())) {
            return;
        }
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            FlashStep.perform(player, Mth.clamp(payload.forward(), -1.0F, 1.0F), Mth.clamp(payload.left(), -1.0F, 1.0F));
        }
    }
}
