package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.quincy.QuincyKey;
import io.github.masteryoga1.bleach.soul.HollowKey;
import io.github.masteryoga1.bleach.soul.Pesquisa;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BleachNetwork {
    /**
     * Bump when a payload changes shape, so mismatched clients are refused instead of breaking.
     * 10: the Devour key, and the Hollow look in SoulAppearance.
     * 11: the Cero keys, and the Menos record in HollowProgress.
     * 12: the Arrancar record in HollowProgress.
     * 13: the Pesquisa marks payload.
     * 14: the Gran Rey Cero key and the Arrancar record's Gran Rey Cero wait.
     * 15: the Arrancar record's rank, and the Arrancar's number in the Hollow look.
     * 16: the Visored record in ShinigamiProgress.
     * 17: {@code ZanpakutoKey.MASK} and the mask's four fields in the Visored record.
     * 18: the Kidō key, the synced Kidō spells and the Kidō record in ShinigamiProgress.
     * 19: the day of the instructor's Kidō lesson in the Kidō record, and the lesson in the student's dialogue.
     * 20: the Gate key.
     * 21: {@code NpcAction.BUTTERFLY}, the instructor's Jigokuchō, and academy_pos gone from ShinigamiProgress.
     * 22: the gatekeepers' dialogue views, and the passes won from them in ShinigamiProgress.
     * 23: the duty Shinigami's HOMEWARD view.
     * 24: the instructor's STUDENT_TRIAL view and {@code NpcAction.FIELD_TRIAL}, the Academy's field trial.
     * 25: Kisuke Urahara's SHOP_* views.
     * 26: {@code NpcAction.TRADE}, Kisuke Urahara's wares.
     * 27: {@code NpcAction.SENKAIMON}, Kisuke Urahara's Senkaimon.
     * 28: {@code NpcAction.GARGANTA}, Kisuke Urahara's Garganta.
     */
    private static final String VERSION = "28";

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
                .playToServer(QuincyKeyPayload.TYPE, QuincyKeyPayload.STREAM_CODEC, QuincyKeyPayload::handle)
                .playToServer(HollowKeyPayload.TYPE, HollowKeyPayload.STREAM_CODEC, HollowKeyPayload::handle)
                .playToServer(KidoKeyPayload.TYPE, KidoKeyPayload.STREAM_CODEC, KidoKeyPayload::handle)
                .playToServer(GateKeyPayload.TYPE, GateKeyPayload.STREAM_CODEC, GateKeyPayload::handle)
                .playToClient(PesquisaPayload.TYPE, PesquisaPayload.STREAM_CODEC, PesquisaPayload::handle);
    }

    /** Sends a player their own soul data and marks it as up to date. */
    public static void syncSoul(ServerPlayer player, SoulData data) {
        PacketDistributor.sendToPlayer(player, SyncSoulPayload.of(data));
        data.clearDirty();
    }

    /**
     * Tells everyone tracking this player how their soul looks now, and the player too: some of a
     * Hollow's look (the jaw open as it devours, the mask cracked) is known only to the server.
     */
    public static void broadcastAppearance(ServerPlayer player, SoulAppearance appearance) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SoulAppearancePayload(player.getId(), appearance));
    }

    /** Tells one player how another player's soul looks, when they start seeing them. */
    public static void sendAppearance(ServerPlayer viewer, Entity target, SoulAppearance appearance) {
        PacketDistributor.sendToPlayer(viewer, new SoulAppearancePayload(target.getId(), appearance));
    }

    /**
     * Opens an NPC's dialogue for one player. See {@link OpenDialoguePayload}. Lists longer than the
     * payload carries are cut short here, since the codec would refuse them on the way out.
     */
    public static void openDialogue(ServerPlayer visitor, Entity npc, DialogueView view, List<Integer> required,
                                    List<NpcAction> unready, List<Component> notes) {
        PacketDistributor.sendToPlayer(visitor, new OpenDialoguePayload(npc.getId(), view,
                first(required, OpenDialoguePayload.MAX_REQUIRED), first(unready, OpenDialoguePayload.MAX_UNREADY),
                first(notes, OpenDialoguePayload.MAX_NOTES)));
    }

    /** At most the first {@code max} entries of a list. */
    private static <T> List<T> first(List<T> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
    }

    /** Sends the answer the player picked in an NPC's dialogue. */
    public static void requestNpcAction(int entityId, NpcAction action) {
        PacketDistributor.sendToServer(new NpcActionPayload(entityId, action));
    }

    /**
     * Tells an Arrancar which souls its Pesquisa felt, to outline for this many ticks. See {@link PesquisaPayload};
     * past {@link Pesquisa#MAX_MARKS} the list is cut short here, as the codec would refuse it on the way out.
     */
    public static void sendPesquisa(ServerPlayer player, List<Integer> ids, int ticks) {
        PacketDistributor.sendToPlayer(player, new PesquisaPayload(first(ids, Pesquisa.MAX_MARKS), ticks));
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

    /**
     * Tells the server the Devour key went down (with the creature under the crosshair, or -1) or came up, or
     * the Cero key went down or came up, or a gathering Cero should fizzle (the target is then -1).
     */
    public static void requestHollowKey(HollowKey key, int target) {
        PacketDistributor.sendToServer(new HollowKeyPayload(key, target));
    }

    /**
     * Tells the server the Kidō key went down (to cast the chosen spell, or to choose the next one while sneaking) or
     * came up, or that a screen opened while it was held. Only the Kidō page names a spell; the key sends none.
     */
    public static void requestKidoKey(KidoKeyPayload.KidoKey key, Optional<ResourceLocation> spell) {
        PacketDistributor.sendToServer(new KidoKeyPayload(key, spell));
    }

    /** Tells the server the Gate key went down or came up, or that a screen opened while it was held. */
    public static void requestGateKey(GateKeyPayload.GateKey key) {
        PacketDistributor.sendToServer(new GateKeyPayload(key));
    }
}
