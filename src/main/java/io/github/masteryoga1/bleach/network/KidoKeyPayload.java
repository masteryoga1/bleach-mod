package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ByIdMap;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the Kidō key, and later the Kidō page. The server decides everything (see
 * {@link KidoEvents#onKey}). The spell is only for {@link KidoKey#SELECT}, which names the spell to choose; the
 * key itself sends none. It already carries every message the Kidō round needs, so later spells that gather,
 * channel or are chosen from a page need no new payload.
 */
public record KidoKeyPayload(KidoKey key, Optional<ResourceLocation> spell) implements CustomPacketPayload {
    public static final Type<KidoKeyPayload> TYPE = new Type<>(BleachMod.id("kido_key"));
    public static final StreamCodec<ByteBuf, KidoKeyPayload> STREAM_CODEC = StreamCodec.composite(
            KidoKey.STREAM_CODEC, KidoKeyPayload::key,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), KidoKeyPayload::spell,
            KidoKeyPayload::new);

    /**
     * What the Kidō key, or the Kidō page, asks of the server. Sent by ordinal, so new keys go last: an old
     * server would read an unknown one as a press.
     */
    public enum KidoKey {
        /** The key went down: cast the chosen spell by name. */
        PRESS,
        /** The key came up (to loose a gathered Raikōhō, or end a channel, once those spells come). */
        RELEASE,
        /** A screen opened while it was held: let go without casting. */
        CANCEL,
        /** Sneak and the key: choose the next spell. */
        NEXT,
        /** Choose the spell named by the payload (the Kidō page's choice). */
        SELECT;

        private static final IntFunction<KidoKey> BY_ID =
                ByIdMap.continuous(KidoKey::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, KidoKey> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, KidoKey::ordinal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(KidoKeyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            KidoEvents.onKey(player, payload.key(), payload.spell(), player.level().getGameTime());
        }
    }
}
