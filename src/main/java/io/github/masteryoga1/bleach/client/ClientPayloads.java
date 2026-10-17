package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.github.masteryoga1.bleach.network.OpenJinzenPayload;
import net.minecraft.client.Minecraft;

/**
 * Client halves of payload handlers that need client-only classes. Common code may call these, but
 * only from handlers that run on the client; the signatures use common types only.
 */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    public static void openDialogue(OpenDialoguePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(payload.entityId()) instanceof ShinigamiNpc npc) {
            minecraft.setScreen(new ShinigamiDialogueScreen(npc, payload));
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
}
