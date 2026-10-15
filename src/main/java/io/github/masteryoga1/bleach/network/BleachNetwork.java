package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BleachNetwork {
    /** Bump when a payload changes shape, so mismatched clients are refused instead of breaking. */
    private static final String VERSION = "3";

    private BleachNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToClient(SyncSoulPayload.TYPE, SyncSoulPayload.STREAM_CODEC, SyncSoulPayload::handle)
                .playToClient(SoulAppearancePayload.TYPE, SoulAppearancePayload.STREAM_CODEC,
                        SoulAppearancePayload::handle)
                .playToServer(ToggleReiatsuPayload.TYPE, ToggleReiatsuPayload.STREAM_CODEC,
                        ToggleReiatsuPayload::handle);
    }

    /** Sends a player their own soul data and marks it as up to date. */
    public static void syncSoul(ServerPlayer player, SoulData data) {
        PacketDistributor.sendToPlayer(player, SyncSoulPayload.of(data));
        data.clearDirty();
    }

    /** Tells everyone tracking this player how their soul looks now. */
    public static void broadcastAppearance(ServerPlayer player, SoulData data) {
        PacketDistributor.sendToPlayersTrackingEntity(player,
                new SoulAppearancePayload(player.getId(), SoulAppearance.of(data)));
    }

    /** Tells one player how another player's soul looks, when they start seeing them. */
    public static void sendAppearance(ServerPlayer viewer, Entity target, SoulData data) {
        PacketDistributor.sendToPlayer(viewer, new SoulAppearancePayload(target.getId(), SoulAppearance.of(data)));
    }

    /** Asks the server to toggle the reiatsu release. */
    public static void requestReiatsuToggle() {
        PacketDistributor.sendToServer(ToggleReiatsuPayload.INSTANCE);
    }
}
