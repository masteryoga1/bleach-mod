package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.client.ClientPayloads;
import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: show this page of the world inside the blade. The first page of a sitting opens
 * the Jinzen screen; later ones, sent after each answer, replace what it shows.
 *
 * @param session which sitting the page belongs to, so a screen left over from an earlier one is replaced
 */
public record OpenJinzenPayload(int session, JinzenPage page) implements CustomPacketPayload {
    public static final Type<OpenJinzenPayload> TYPE = new Type<>(BleachMod.id("open_jinzen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenJinzenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenJinzenPayload::session,
            JinzenPage.STREAM_CODEC, OpenJinzenPayload::page,
            OpenJinzenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(OpenJinzenPayload payload, IPayloadContext context) {
        ClientPayloads.openJinzen(payload);
    }
}
