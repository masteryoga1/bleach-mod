package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.quincy.QuincyKey;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BleachNetwork {
    /** Bump when a payload changes shape, so mismatched clients are refused instead of breaking. */
    private static final String VERSION = "7";

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
                .playToClient(ReishiFootingStatePayload.TYPE, ReishiFootingStatePayload.STREAM_CODEC,
                        ReishiFootingStatePayload::handle)
                .playToServer(NpcActionPayload.TYPE, NpcActionPayload.STREAM_CODEC, NpcActionPayload::handle)
                .playToServer(ReishiFootingPayload.TYPE, ReishiFootingPayload.STREAM_CODEC, ReishiFootingPayload::handle)
                .playToServer(FlashStepPayload.TYPE, FlashStepPayload.STREAM_CODEC, FlashStepPayload::handle)
                .playToClient(OpenJinzenPayload.TYPE, OpenJinzenPayload.STREAM_CODEC, OpenJinzenPayload::handle)
                .playToServer(JinzenAnswerPayload.TYPE, JinzenAnswerPayload.STREAM_CODEC, JinzenAnswerPayload::handle)
                .playToServer(ZanpakutoKeyPayload.TYPE, ZanpakutoKeyPayload.STREAM_CODEC, ZanpakutoKeyPayload::handle)
                .playToClient(OpenBloodlinePayload.TYPE, OpenBloodlinePayload.STREAM_CODEC, OpenBloodlinePayload::handle)
                .playToServer(BloodlineChoicePayload.TYPE, BloodlineChoicePayload.STREAM_CODEC,
                        BloodlineChoicePayload::handle)
                .playToServer(QuincyKeyPayload.TYPE, QuincyKeyPayload.STREAM_CODEC, QuincyKeyPayload::handle);
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

    /** Tells a player whether their reishi footing holds, and at what height. */
    public static void sendFooting(ServerPlayer player, boolean active, int anchorY) {
        PacketDistributor.sendToPlayer(player, new ReishiFootingStatePayload(active, anchorY));
    }

    /** Asks the server for a flash step, steering this way. */
    public static void requestFlashStep(float forward, float left) {
        PacketDistributor.sendToServer(new FlashStepPayload(forward, left));
    }

    /** Asks the server for reishi footing. */
    public static void requestFooting(boolean viaJump) {
        PacketDistributor.sendToServer(new ReishiFootingPayload(viaJump));
    }

    /** Asks the server to toggle the reiatsu release. */
    public static void requestReiatsuToggle() {
        PacketDistributor.sendToServer(ToggleReiatsuPayload.INSTANCE);
    }

    /** Shows a player a page of the world inside their blade. See {@link OpenJinzenPayload}. */
    public static void openJinzen(ServerPlayer player, int session, JinzenPage page) {
        PacketDistributor.sendToPlayer(player, new OpenJinzenPayload(session, page));
    }

    /** Sends the soul's answer to a step of its spirit's test. */
    public static void answerJinzen(int session, int step, int value) {
        PacketDistributor.sendToServer(new JinzenAnswerPayload(session, step, value));
    }

    /** Tells the server a zanpakutō key was pressed or let go. */
    public static void requestZanpakutoKey(ZanpakutoKey key) {
        PacketDistributor.sendToServer(new ZanpakutoKeyPayload(key));
    }

    /** Asks a player whether Quincy blood runs in them. See {@link OpenBloodlinePayload}. */
    public static void openBloodline(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, OpenBloodlinePayload.INSTANCE);
    }

    /** Sends the player's answer to the bloodline question. */
    public static void requestBloodline(BloodlineChoice choice) {
        PacketDistributor.sendToServer(new BloodlineChoicePayload(choice));
    }

    /** Tells the server a Quincy key was pressed. */
    public static void requestQuincyKey(QuincyKey key) {
        PacketDistributor.sendToServer(new QuincyKeyPayload(key));
    }
}
