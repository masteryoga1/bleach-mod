package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: how another player's soul looks, sent to the players tracking them. */
public record SoulAppearancePayload(int entityId, SoulAppearance appearance) implements CustomPacketPayload {
    public static final Type<SoulAppearancePayload> TYPE = new Type<>(BleachMod.id("soul_appearance"));
    public static final StreamCodec<ByteBuf, SoulAppearancePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SoulAppearancePayload::entityId,
            SoulAppearance.STREAM_CODEC, SoulAppearancePayload::appearance,
            SoulAppearancePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SoulAppearancePayload payload, IPayloadContext context) {
        Entity entity = context.player().level().getEntity(payload.entityId());
        if (entity != null) {
            entity.setData(SoulAttachments.APPEARANCE, payload.appearance());
        }
    }
}
