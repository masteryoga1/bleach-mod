package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.client.ClientPayloads;
import io.github.masteryoga1.bleach.soul.Pesquisa;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the souls an Arrancar's Pesquisa felt, by entity id (at most {@link Pesquisa#MAX_MARKS},
 * the nearest), to outline through walls for this many ticks. Each pulse's marks replace the last one's.
 * {@link BleachNetwork#sendPesquisa} cuts a longer list short before sending.
 */
public record PesquisaPayload(List<Integer> ids, int ticks) implements CustomPacketPayload {
    public static final Type<PesquisaPayload> TYPE = new Type<>(BleachMod.id("pesquisa"));
    public static final StreamCodec<ByteBuf, PesquisaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(Pesquisa.MAX_MARKS)), PesquisaPayload::ids,
            ByteBufCodecs.VAR_INT, PesquisaPayload::ticks,
            PesquisaPayload::new);

    /** Keeps a copy, so a list the sender goes on changing cannot change what is sent. */
    public PesquisaPayload {
        ids = List.copyOf(ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(PesquisaPayload payload, IPayloadContext context) {
        ClientPayloads.pesquisa(payload);
    }
}
