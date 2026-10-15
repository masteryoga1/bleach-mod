package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
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
}
