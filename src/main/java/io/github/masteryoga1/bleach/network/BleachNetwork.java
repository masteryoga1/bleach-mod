package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulData;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BleachNetwork {
    /** Bump when a payload changes shape, so mismatched clients are refused instead of breaking. */
    private static final String VERSION = "4";

    private BleachNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToClient(SyncSoulPayload.TYPE, SyncSoulPayload.STREAM_CODEC, SyncSoulPayload::handle)
                .playToClient(SoulAppearancePayload.TYPE, SoulAppearancePayload.STREAM_CODEC,
                        SoulAppearancePayload::handle)
                .playToClient(OpenDialoguePayload.TYPE, OpenDialoguePayload.STREAM_CODEC, OpenDialoguePayload::handle)
                .playToServer(ToggleReiatsuPayload.TYPE, ToggleReiatsuPayload.STREAM_CODEC,
                        ToggleReiatsuPayload::handle)
                .playToServer(NpcActionPayload.TYPE, NpcActionPayload.STREAM_CODEC, NpcActionPayload::handle);
    }

    /** Sends a player their own soul data and marks it as up to date. */
    public static void syncSoul(ServerPlayer player, SoulData data) {
        PacketDistributor.sendToPlayer(player, SyncSoulPayload.of(data));
        data.clearDirty();
    }

    /** Tells everyone tracking this player how their soul looks now. */
    public static void broadcastAppearance(ServerPlayer player, SoulAppearance appearance) {
        PacketDistributor.sendToPlayersTrackingEntity(player, new SoulAppearancePayload(player.getId(), appearance));
    }

    /** Tells one player how another player's soul looks, when they start seeing them. */
    public static void sendAppearance(ServerPlayer viewer, Entity target, SoulAppearance appearance) {
        PacketDistributor.sendToPlayer(viewer, new SoulAppearancePayload(target.getId(), appearance));
    }

    /** Opens a Shinigami NPC's dialogue for one player. See {@link OpenDialoguePayload}. */
    public static void openDialogue(ServerPlayer visitor, ShinigamiNpc npc, DialogueView view, List<Integer> required,
                                    boolean asauchiReady) {
        PacketDistributor.sendToPlayer(visitor, new OpenDialoguePayload(npc.getId(), view, required, asauchiReady));
    }

    /** Sends the answer the player picked in a Shinigami NPC's dialogue. */
    public static void requestNpcAction(int entityId, NpcAction action) {
        PacketDistributor.sendToServer(new NpcActionPayload(entityId, action));
    }

    /** Asks the server to toggle the reiatsu release. */
    public static void requestReiatsuToggle() {
        PacketDistributor.sendToServer(ToggleReiatsuPayload.INSTANCE);
    }
}
