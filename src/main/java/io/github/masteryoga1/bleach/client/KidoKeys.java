package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.network.KidoKeyPayload;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The Kidō key (U): pressed, it casts the chosen spell by its number and name alone (Eishōhaki); pressed while
 * sneaking, it chooses the next spell the soul's Kidō level allows instead, and the action bar names it. Sneaking is
 * read here, on the client, as the Gran Rey Cero's is. Letting go of a cast's press goes to the server after it, and a
 * screen opening while the key is held lets it go without casting, so spells that gather or channel while the key is
 * held need no new message. The server decides everything and says why when it refuses (a soul that cannot use Kidō is
 * told so); this only sends the presses.
 */
public final class KidoKeys {
    public static final KeyMapping CAST = new KeyMapping("key.bleach.kido", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, BleachClient.KEY_CATEGORY);

    /** Whether the key was down last tick, so holding it (and key repeat) sends only once. */
    private static boolean wasDown;
    /** Whether a PRESS was sent and the key is still held. */
    private static boolean held;

    private KidoKeys() {
    }

    /** Called every client tick, after HollowKeys. */
    static void tick(Minecraft minecraft) {
        // The press goes by whether the key is down; a click only catches a tap too quick to see held.
        boolean clicked = false;
        while (CAST.consumeClick()) {
            clicked = true;
        }
        boolean down = CAST.isDown();
        boolean wasKeyDown = wasDown;
        wasDown = down;

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            held = false;
            return;
        }
        boolean inGame = minecraft.screen == null;
        if (inGame && !held && !wasKeyDown && (down || clicked)) {
            if (player.isShiftKeyDown()) {
                // Only choosing: nothing is held, so letting go sends nothing.
                BleachNetwork.requestKidoKey(KidoKeyPayload.KidoKey.NEXT, Optional.empty());
            } else {
                BleachNetwork.requestKidoKey(KidoKeyPayload.KidoKey.PRESS, Optional.empty());
                held = true;
            }
        }
        if (held) {
            // A screen opening releases every key, so check for one before taking the key's release as a let-go.
            // A tap is pressed and let go in the same tick.
            if (!inGame) {
                held = false;
                BleachNetwork.requestKidoKey(KidoKeyPayload.KidoKey.CANCEL, Optional.empty());
            } else if (!down) {
                held = false;
                BleachNetwork.requestKidoKey(KidoKeyPayload.KidoKey.RELEASE, Optional.empty());
            }
        }
    }
}
