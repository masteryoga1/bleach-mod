package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.FootingState;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: whether the player's reishi footing holds, and at what height. The client's
 * movement collides with the footing by this, and holds the player's fall at its top for a moment
 * while the plates themselves arrive.
 */
public record ReishiFootingStatePayload(boolean active, int anchorY) implements CustomPacketPayload {
    /** How long the client holds the fall after hearing from the server. */
    public static final int CLAMP_TICKS = 10;
    public static final Type<ReishiFootingStatePayload> TYPE = new Type<>(BleachMod.id("reishi_footing_state"));
    public static final StreamCodec<ByteBuf, ReishiFootingStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ReishiFootingStatePayload::active,
            ByteBufCodecs.VAR_INT, ReishiFootingStatePayload::anchorY,
            ReishiFootingStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ReishiFootingStatePayload payload, IPayloadContext context) {
        Player player = context.player();
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        footing.active = payload.active();
        footing.anchorY = payload.anchorY();
        footing.clampUntilTick = payload.active() ? player.tickCount + CLAMP_TICKS : -1;
    }
}
