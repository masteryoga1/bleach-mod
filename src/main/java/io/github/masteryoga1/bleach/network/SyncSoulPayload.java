package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the player's own soul data, so the HUD and stats screen stay current. What the
 * player must not see, such as the hidden leaning that picks their zanpakutō's spirit, is left out
 * (see {@link SoulData#forClient}).
 */
public record SyncSoulPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncSoulPayload> TYPE = new Type<>(BleachMod.id("sync_soul"));
    public static final StreamCodec<ByteBuf, SyncSoulPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(SyncSoulPayload::new, SyncSoulPayload::data);

    public static SyncSoulPayload of(SoulData soul) {
        Tag tag = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, soul.forClient()).getOrThrow();
        return new SyncSoulPayload(tag instanceof CompoundTag compound ? compound : new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SyncSoulPayload payload, IPayloadContext context) {
        SoulData.CODEC.parse(NbtOps.INSTANCE, payload.data())
                .resultOrPartial(error -> BleachMod.LOGGER.warn("Ignoring bad soul data from the server: {}", error))
                .ifPresent(soul -> context.player().setData(SoulAttachments.SOUL, soul));
    }
}
