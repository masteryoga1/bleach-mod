package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.soul.SoulData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BleachNetwork {
    /** Bump when a payload changes shape, so mismatched clients are refused instead of breaking. */
    private static final String VERSION = "1";

    private BleachNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToClient(SyncSoulPayload.TYPE, SyncSoulPayload.STREAM_CODEC, SyncSoulPayload::handle);
    }

    /** Sends a player their own soul data and marks it as up to date. */
    public static void syncSoul(ServerPlayer player, SoulData data) {
        PacketDistributor.sendToPlayer(player, SyncSoulPayload.of(data));
        data.clearDirty();
    }
}
