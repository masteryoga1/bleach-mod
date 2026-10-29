package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The zanpakutō keys: Release (Z) calls the blade's name, Seal (B) seals it, and Technique (G) is
 * held to gather a technique and let go to loose it. Hollow Mask (M) is no zanpakutō key, but goes
 * with them: a Visored presses it to call its Hollow mask and again to take it off, with or without
 * a blade in hand. The server decides everything and says why when it refuses; this only sends the
 * presses. Changing hotbar slot or opening a screen while the Technique key is held lets the
 * gathered reiatsu go without loosing it.
 */
public final class ZanpakutoKeys {
    public static final KeyMapping RELEASE = new KeyMapping("key.bleach.zanpakuto_release",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, BleachClient.KEY_CATEGORY);
    public static final KeyMapping SEAL = new KeyMapping("key.bleach.zanpakuto_seal",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, BleachClient.KEY_CATEGORY);
    public static final KeyMapping TECHNIQUE = new KeyMapping("key.bleach.zanpakuto_technique",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, BleachClient.KEY_CATEGORY);
    public static final KeyMapping MASK = new KeyMapping("key.bleach.visored_mask",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, BleachClient.KEY_CATEGORY);

    /**
     * Ticks a gather takes to fill, and how long it lasts before it fades, as the server's defaults.
     * The client does not know the server's settings, so the bar is only a guide; the server times
     * the real charge.
     */
    private static final float FULL_CHARGE_TICKS = 30.0F;
    private static final long FADE_TICKS = 200;

    /** Client ticks counted here: the clock the gather bar runs on. */
    private static long ticks;
    /** Whether each key was down last tick, so holding one (and key repeat) sends only once. */
    private static boolean releaseWasDown;
    private static boolean sealWasDown;
    private static boolean techniqueWasDown;
    private static boolean maskWasDown;
    /** Whether a gather was asked for and is still held. */
    private static boolean gathering;
    private static long gatherStartedAt;
    /** The hotbar slot the gather began in; changing it lets the gather go. */
    private static int gatherSlot;

    private ZanpakutoKeys() {
    }

    /** Called every client tick. */
    static void tick(Minecraft minecraft) {
        ticks++;
        boolean release = false;
        while (RELEASE.consumeClick()) {
            release = true;
        }
        boolean seal = false;
        while (SEAL.consumeClick()) {
            seal = true;
        }
        // The technique goes by how long the key is held; a click only catches a tap too quick to see held.
        boolean technique = false;
        while (TECHNIQUE.consumeClick()) {
            technique = true;
        }
        boolean mask = false;
        while (MASK.consumeClick()) {
            mask = true;
        }
        boolean releaseDown = RELEASE.isDown();
        boolean sealDown = SEAL.isDown();
        boolean techniqueDown = TECHNIQUE.isDown();
        boolean maskDown = MASK.isDown();
        boolean wasReleaseDown = releaseWasDown;
        boolean wasSealDown = sealWasDown;
        boolean wasTechniqueDown = techniqueWasDown;
        boolean wasMaskDown = maskWasDown;
        releaseWasDown = releaseDown;
        sealWasDown = sealDown;
        techniqueWasDown = techniqueDown;
        maskWasDown = maskDown;

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            gathering = false;
            return;
        }
        boolean inGame = minecraft.screen == null;
        if (inGame && release && !wasReleaseDown) {
            BleachNetwork.requestZanpakutoKey(ZanpakutoKey.RELEASE);
        }
        if (inGame && seal && !wasSealDown) {
            BleachNetwork.requestZanpakutoKey(ZanpakutoKey.SEAL);
        }
        // Once per press: a click, or the key found down after it was up, as with the technique.
        if (inGame && (mask || maskDown) && !wasMaskDown) {
            BleachNetwork.requestZanpakutoKey(ZanpakutoKey.MASK);
        }

        if (!gathering && inGame && !wasTechniqueDown && (techniqueDown || technique)) {
            gathering = true;
            gatherStartedAt = ticks;
            gatherSlot = player.getInventory().selected;
            BleachNetwork.requestZanpakutoKey(ZanpakutoKey.TECHNIQUE_PRESS);
        }
        if (gathering) {
            // A screen opening releases every key, so check for one before taking the key's release as a swing.
            if (!inGame || player.getInventory().selected != gatherSlot) {
                gathering = false;
                BleachNetwork.requestZanpakutoKey(ZanpakutoKey.TECHNIQUE_CANCEL);
            } else if (!techniqueDown) {
                // Let go: loose it. A tap begins and ends in the same tick, as the weakest swing.
                gathering = false;
                BleachNetwork.requestZanpakutoKey(ZanpakutoKey.TECHNIQUE_RELEASE);
            }
        }
    }

    /**
     * How full the gather is, from 0 to 1, while the Technique key is held; 0 when nothing is
     * gathering, or once a gather held this long would have faded.
     */
    static float gatherProgress(float partialTick) {
        long held = ticks - gatherStartedAt;
        if (!gathering || held >= FADE_TICKS) {
            return 0.0F;
        }
        return Math.min(1.0F, (held + partialTick) / FULL_CHARGE_TICKS);
    }
}
