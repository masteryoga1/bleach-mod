package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.client.ClientPayloads;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: open the dialogue with a Shinigami NPC.
 *
 * @param required     the level each examined art needs to graduate, in {@code ShinigamiNpc.EXAMINED_ARTS} order
 * @param asauchiReady whether the Academy would hand this visitor a new Asauchi now
 */
public record OpenDialoguePayload(int entityId, DialogueView view, List<Integer> required, boolean asauchiReady)
        implements CustomPacketPayload {
    public static final Type<OpenDialoguePayload> TYPE = new Type<>(BleachMod.id("open_dialogue"));
    public static final StreamCodec<ByteBuf, OpenDialoguePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
            DialogueView.STREAM_CODEC, OpenDialoguePayload::view,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), OpenDialoguePayload::required,
            ByteBufCodecs.BOOL, OpenDialoguePayload::asauchiReady,
            OpenDialoguePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(OpenDialoguePayload payload, IPayloadContext context) {
        ClientPayloads.openDialogue(payload);
    }
}
