package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.DialogueNpc;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.github.masteryoga1.bleach.network.OpenJinzenPayload;
import io.github.masteryoga1.bleach.network.PesquisaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client halves of payload handlers that need client-only classes. Common code may call these, but
 * only from handlers that run on the client; the signatures use common types only.
 */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    /** Opens the dialogue with the NPC the payload names, if this client still knows it as one that talks. */
    public static void openDialogue(OpenDialoguePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(payload.entityId());
        if (entity instanceof LivingEntity living && entity instanceof DialogueNpc) {
            minecraft.setScreen(new NpcDialogueScreen(living, payload));
        }
    }

    /**
     * Opens Jinzen on the inner world, or turns the open Jinzen screen to the next page of the same
     * session. A page for a player who has already let go of the blade is a late reply to a Jinzen that
     * has ended, and opens nothing.
     */
    public static void openJinzen(OpenJinzenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isUsingItem()) {
            return;
        }
        if (minecraft.screen instanceof JinzenScreen screen && screen.session() == payload.session()) {
            screen.update(payload.page());
        } else {
            minecraft.setScreen(new JinzenScreen(payload.session(), payload.page()));
        }
    }

    /**
     * Asks the bloodline question. It waits for the world to finish loading and for any other screen
     * to close (see {@link QuincyClient}); asked again while it is already showing, it is not queued twice.
     */
    public static void openBloodline() {
        if (!(Minecraft.getInstance().screen instanceof BloodlineScreen)) {
            QuincyClient.bloodlinePending = true;
        }
    }

    /** Outlines the souls this player's Pesquisa felt, in place of the last pulse's (see {@link HollowKeys#marked}). */
    public static void pesquisa(PesquisaPayload payload) {
        HollowKeys.mark(payload.ids(), payload.ticks());
    }
}
