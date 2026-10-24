package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowKey;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The Devour key (J). A Hollow holds it on a weakened soul to seize and devour it, and a Plus holds it to
 * gnaw at its own Chain of Fate; pressed while sneaking, it turns a Hollow's sight on or off instead. The
 * press goes to the server with the creature under the crosshair, and letting go (or a screen opening,
 * or dying) goes after it, so a hold lasts as long as the key does. The server decides everything and
 * says why when it refuses; this only sends the presses, and keeps a guess at the hold for the HUD.
 * Nothing is sent for a soul that is neither a Hollow nor a Plus.
 */
public final class HollowKeys {
    public static final KeyMapping DEVOUR = new KeyMapping("key.bleach.devour", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, BleachClient.KEY_CATEGORY);

    /**
     * How long the HUD trusts a hold the server has not yet shown it: past this, a hold whose jaw never
     * opened (refused, on cooldown, or broken) is not shown. Room for the press to reach the server and
     * the look to come back on a slow connection.
     */
    private static final int CONFIRM_TICKS = 10;

    /** Client ticks counted here: the clock the devour bar runs on. */
    private static int ticks;
    /** Whether the key was down last tick, so holding it (and key repeat) sends only once. */
    private static boolean wasDown;
    /** Whether a press was sent and its release not yet. */
    private static boolean holding;
    private static int holdStartedAt;
    /** The creature under the crosshair when the press was sent, or -1 for none (the server looks nearby). */
    private static int holdTargetId = -1;

    private HollowKeys() {
    }

    /** Called every client tick. */
    static void tick(Minecraft minecraft) {
        ticks++;
        // The hold goes by whether the key is down; a click only catches a tap too quick to see held.
        boolean clicked = false;
        while (DEVOUR.consumeClick()) {
            clicked = true;
        }
        boolean down = DEVOUR.isDown();
        boolean rising = (down || clicked) && !wasDown;
        wasDown = down;

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            holding = false;
            return;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        boolean eligible = data.isInitialized() && (data.state() == SoulState.HOLLOW || data.state() == SoulState.PLUS)
                && !player.isSpectator();
        // A screen opening releases every key, so it lets go as the key coming up does.
        if (holding && (!down || minecraft.screen != null || !player.isAlive() || !eligible)) {
            BleachNetwork.requestHollowKey(HollowKey.RELEASE, -1);
            holding = false;
        }
        if (rising && minecraft.screen == null && eligible && player.isAlive()) {
            if (player.isShiftKeyDown() && data.state() == SoulState.HOLLOW) {
                BleachNetwork.requestHollowKey(HollowKey.SIGHT, -1);
            } else {
                // Only a living creature can be prey: anything else is no aim, and the server looks nearby.
                Entity target = minecraft.crosshairPickEntity;
                int targetId = target instanceof LivingEntity ? target.getId() : -1;
                BleachNetwork.requestHollowKey(HollowKey.PRESS, targetId);
                holding = true;
                holdStartedAt = ticks;
                holdTargetId = targetId;
            }
        }
    }

    /**
     * How far the devour hold has come, from 0 to 1, as this client guesses it from the time the key has
     * been held over the hold the prey takes (a Plus is quicker); 0 when there is nothing to show. Only
     * while a Hollow holds the key, the creature it aimed at is still alive, and, once the server has had
     * time to answer, its jaw hangs open (the server's word that the hold is on). A guide only: the server
     * times the real hold. Once the jaw is open the server's look also says which hold it is, which
     * matters when nothing was aimed at and the server picked the prey itself; before that the bar
     * guesses from the creature aimed at, or assumes the longer hold.
     */
    public static float holdProgress(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!holding || player == null || minecraft.level == null
                || player.getData(SoulAttachments.SOUL).state() != SoulState.HOLLOW) {
            return 0.0F;
        }
        LivingEntity prey = null;
        if (holdTargetId >= 0) {
            if (!(minecraft.level.getEntity(holdTargetId) instanceof LivingEntity living) || !living.isAlive()) {
                return 0.0F;
            }
            prey = living;
        }
        int held = ticks - holdStartedAt;
        int look = ClientSouls.appearance(player).hollowLook();
        boolean confirmed = (look & HollowPlayerLogic.LOOK_DEVOURING) != 0;
        if (held > CONFIRM_TICKS && !confirmed) {
            return 0.0F;
        }
        int hold;
        if (confirmed) {
            hold = (look & HollowPlayerLogic.LOOK_DEVOURING_QUICK) != 0
                    ? BleachConfig.get(BleachConfig.DEVOUR_PLUS_HOLD_TICKS)
                    : BleachConfig.get(BleachConfig.DEVOUR_HOLD_TICKS);
        } else {
            hold = prey != null ? DevourLogic.holdTicks(prey) : BleachConfig.get(BleachConfig.DEVOUR_HOLD_TICKS);
        }
        hold = Math.max(1, hold);
        return Math.min(1.0F, (held + partialTick) / hold);
    }

    /** Whether this client's Plus is holding the key to gnaw at its chain, where the server would let it. */
    public static boolean gnawing() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!holding || player == null) {
            return false;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        return data.state() == SoulState.PLUS && DevourLogic.gnawRefusal(player, data) == DevourLogic.Gnaw.GNAWING;
    }
}
