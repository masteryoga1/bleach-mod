package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.network.GateKeyPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The Gate key (O): held with a zanpakutō in hand, it turns the blade like a key, and when the turn is done a Senkaimon
 * opens; held by a Menos or an Arrancar, it tears at the air until a Garganta opens. This only sends the presses, and a
 * screen opening while the key is held ends the turn; the server decides.
 */
public final class GateKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.bleach.gate", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, BleachClient.KEY_CATEGORY);

    /** Whether the key was down last tick, so holding it sends only once. */
    private static boolean wasDown;
    /** Whether a PRESS was sent and the key is still held. */
    private static boolean held;

    private GateKeys() {
    }

    /** Called every client tick, after KidoKeys. */
    static void tick(Minecraft minecraft) {
        // The press goes by whether the key is down; a click only catches a tap too quick to see held.
        boolean clicked = false;
        while (OPEN.consumeClick()) {
            clicked = true;
        }
        boolean down = OPEN.isDown();
        boolean wasKeyDown = wasDown;
        wasDown = down;
        if (minecraft.player == null || minecraft.getConnection() == null) {
            held = false;
            return;
        }
        boolean inGame = minecraft.screen == null;
        if (inGame && !held && !wasKeyDown && (down || clicked)) {
            BleachNetwork.requestGateKey(GateKeyPayload.GateKey.PRESS);
            held = true;
        }
        // A screen opening releases every key, so it is checked first; a tap is pressed and let go in one tick.
        if (held && (!inGame || !down)) {
            held = false;
            BleachNetwork.requestGateKey(inGame ? GateKeyPayload.GateKey.RELEASE : GateKeyPayload.GateKey.CANCEL);
        }
    }
}
