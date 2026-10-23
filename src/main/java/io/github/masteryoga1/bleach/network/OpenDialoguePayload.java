package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.client.ClientPayloads;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.NpcAction;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: open the dialogue with an NPC (see {@code DialogueNpc}). The lists are capped, at
 * 8, 8 and 4 entries; {@link BleachNetwork#openDialogue} cuts longer ones short before sending.
 *
 * @param required the level each examined art needs to graduate, in {@code ShinigamiNpc.EXAMINED_ARTS}
 *                 order, for the Academy's exams; empty for everyone else
 * @param unready  the actions the view offers that this visitor cannot have yet, shown greyed out
 * @param notes    a few short lines under the speech, such as how many lessons are left or a tally
 */
public record OpenDialoguePayload(int entityId, DialogueView view, List<Integer> required, List<NpcAction> unready,
                                  List<Component> notes) implements CustomPacketPayload {
    public static final int MAX_REQUIRED = 8;
    public static final int MAX_UNREADY = 8;
    public static final int MAX_NOTES = 4;

    public static final Type<OpenDialoguePayload> TYPE = new Type<>(BleachMod.id("open_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
            DialogueView.STREAM_CODEC, OpenDialoguePayload::view,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_REQUIRED)), OpenDialoguePayload::required,
            NpcAction.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_UNREADY)), OpenDialoguePayload::unready,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NOTES)), OpenDialoguePayload::notes,
            OpenDialoguePayload::new);

    /** Keeps copies, so a list the sender goes on changing cannot change what is sent. */
    public OpenDialoguePayload {
        required = List.copyOf(required);
        unready = List.copyOf(unready);
        notes = List.copyOf(notes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(OpenDialoguePayload payload, IPayloadContext context) {
        ClientPayloads.openDialogue(payload);
    }
}
