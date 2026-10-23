package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.DialogueNpc;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player picked an answer in an NPC's dialogue (see {@link DialogueNpc}). The NPC checks it again. */
public record NpcActionPayload(int entityId, NpcAction action) implements CustomPacketPayload {
    public static final Type<NpcActionPayload> TYPE = new Type<>(BleachMod.id("npc_action"));
    public static final StreamCodec<ByteBuf, NpcActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcActionPayload::entityId,
            NpcAction.STREAM_CODEC, NpcActionPayload::action,
            NpcActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(NpcActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()
                && player.level().getEntity(payload.entityId()) instanceof DialogueNpc npc) {
            npc.perform(player, payload.action());
        }
    }
}
