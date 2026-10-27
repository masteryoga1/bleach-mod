package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowKey;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.MenosBody;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.entity.PartEntity;
import org.lwjgl.glfw.GLFW;

/**
 * The Hollow keys. Devour (J): a Hollow holds it on a weakened soul to seize and devour it, and a Plus
 * holds it to gnaw at its own Chain of Fate; pressed while sneaking, it turns a Hollow's sight on or off
 * instead. The press goes to the server with the creature under the crosshair, and letting go (or a
 * screen opening, or dying) goes after it, so a hold lasts as long as the key does. Cero (Y): a Hollow
 * or an Arrancar holds it to gather a Cero at its mouth and lets go to fire it, and an Arrancar that sneaks as
 * it presses the key gathers a Gran Rey Cero instead; a screen opening, dying or no longer being a Hollow or an
 * Arrancar while it is held lets the beam fizzle instead. The server decides everything and says why when it
 * refuses (a plain Hollow is told only a Menos can gather one); this only sends the presses, and keeps a guess
 * at the hold and the gathering Cero for the HUD. Nothing is sent for the Devour key but a Hollow's, a Plus's
 * or an Arrancar's (an Arrancar no longer devours: held still, its Devour key sends out Pesquisa), and nothing
 * for the Cero key but a Hollow's or an Arrancar's.
 *
 * <p>It also keeps the souls an Arrancar's last Pesquisa felt, which this client outlines through walls for a
 * few seconds (see {@link #marked}), and tells {@link MenosBody} how tall the giants this client sees stand,
 * every second.
 */
public final class HollowKeys {
    public static final KeyMapping DEVOUR = new KeyMapping("key.bleach.devour", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, BleachClient.KEY_CATEGORY);
    public static final KeyMapping CERO = new KeyMapping("key.bleach.cero", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, BleachClient.KEY_CATEGORY);

    /**
     * How long the HUD trusts a hold the server has not yet shown it: past this, a hold whose jaw never
     * opened (refused, on cooldown, or broken) is not shown. Room for the press to reach the server and
     * the look to come back on a slow connection.
     */
    private static final int CONFIRM_TICKS = 10;
    /** Every so many ticks this client tells {@link MenosBody} about the giants it sees, as the server does. */
    private static final int GIANT_REFRESH_TICKS = 20;

    /** Client ticks counted here: the clock the devour bar and the Pesquisa outlines run on. */
    private static int ticks;
    /** Whether the key was down last tick, so holding it (and key repeat) sends only once. */
    private static boolean wasDown;
    /** Whether a press was sent and its release not yet. */
    private static boolean holding;
    private static int holdStartedAt;
    /** The creature under the crosshair when the press was sent, or -1 for none (the server looks nearby). */
    private static int holdTargetId = -1;
    /** Whether the Cero key was down last tick, so holding it (and key repeat) sends only once. */
    private static boolean ceroWasDown;
    /** Whether a Cero press was sent and neither its release nor a cancel yet. */
    private static boolean ceroHolding;
    /** This player's own gathering Cero, once the server's beam has come; looked for each tick until then. */
    @Nullable
    private static CeroBeam ownCero;
    /** Ids the last Pesquisa marked, each to the client tick its outline fades. Cleared with no world or on a new one. */
    private static final Int2IntOpenHashMap MARKS = new Int2IntOpenHashMap();
    /** The world the marks' ids belong to. */
    @Nullable
    private static ClientLevel markLevel;

    private HollowKeys() {
    }

    /** Called every client tick. */
    static void tick(Minecraft minecraft) {
        ticks++;
        if (ticks % GIANT_REFRESH_TICKS == 0) {
            // With no world, none: so a giant seen in a world left behind stops counting.
            MenosBody.noteGiants(minecraft.level != null ? minecraft.level.players() : List.of(), true);
        }
        // The hold goes by whether the key is down; a click only catches a tap too quick to see held.
        boolean clicked = false;
        while (DEVOUR.consumeClick()) {
            clicked = true;
        }
        boolean down = DEVOUR.isDown();
        boolean rising = (down || clicked) && !wasDown;
        wasDown = down;
        boolean ceroClicked = false;
        while (CERO.consumeClick()) {
            ceroClicked = true;
        }
        boolean ceroDown = CERO.isDown();
        boolean ceroRising = (ceroDown || ceroClicked) && !ceroWasDown;
        ceroWasDown = ceroDown;

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            holding = false;
            ceroHolding = false;
            ownCero = null;
            MARKS.clear();
            markLevel = null;
            return;
        }
        tickMarks(minecraft);
        SoulData data = player.getData(SoulAttachments.SOUL);
        // The outlines are the Arrancar's art: they go at death (a same-world respawn keeps the world) or with the state.
        if (!player.isAlive() || player.isSpectator() || !data.isInitialized() || data.state() != SoulState.ARRANCAR) {
            MARKS.clear();
            markLevel = null;
        }
        boolean eligible = data.isInitialized() && (data.state() == SoulState.HOLLOW || data.state() == SoulState.PLUS
                || data.state() == SoulState.ARRANCAR) && !player.isSpectator();
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
                // A giant is aimed at by any of its parts, which stand for the whole body.
                Entity target = minecraft.crosshairPickEntity;
                if (target instanceof PartEntity<?> part) {
                    target = part.getParent();
                }
                int targetId = target instanceof LivingEntity ? target.getId() : -1;
                BleachNetwork.requestHollowKey(HollowKey.PRESS, targetId);
                holding = true;
                holdStartedAt = ticks;
                holdTargetId = targetId;
            }
        }
        tickCero(minecraft, player, data, ceroDown, ceroRising);
    }

    /**
     * The Cero key: down sends the start, and up the release, which fires it; a screen opening, dying or no
     * longer being a Hollow or an Arrancar cancels it. A tap is let go before anything has gathered, so it
     * fizzles. An Arrancar sneaking as the key goes down starts a Gran Rey Cero instead, which is let go and
     * cancelled the same way. While the key is held, this looks for the player's own gathering beam, a Cero
     * or a Gran Rey Cero, so the HUD and the model can show it.
     */
    private static void tickCero(Minecraft minecraft, LocalPlayer player, SoulData data, boolean down, boolean rising) {
        boolean hollow = data.isInitialized() && (data.state() == SoulState.HOLLOW || data.state() == SoulState.ARRANCAR)
                && !player.isSpectator();
        // A screen opening releases every key, so check for one before taking the key's release as a shot.
        if (ceroHolding && (minecraft.screen != null || !player.isAlive() || !hollow)) {
            BleachNetwork.requestHollowKey(HollowKey.CERO_CANCEL, -1);
            ceroHolding = false;
        } else if (ceroHolding && !down) {
            BleachNetwork.requestHollowKey(HollowKey.CERO_RELEASE, -1);
            ceroHolding = false;
        }
        if (rising && minecraft.screen == null && hollow && player.isAlive()) {
            // Only the press decides which: sneaking or not after it, the release and cancel go to whatever gathers.
            boolean grand = player.isShiftKeyDown() && data.state() == SoulState.ARRANCAR;
            BleachNetwork.requestHollowKey(grand ? HollowKey.GRAN_REY_CERO : HollowKey.CERO, -1);
            ceroHolding = true;
            ownCero = null;
        }
        if (!ceroHolding) {
            ownCero = null;
        } else if (!gathering(ownCero, player)) {
            ownCero = findOwnCero(player);
        }
    }

    /**
     * The Pesquisa outlines: all of them go when the world changes (the ids were another world's), and each
     * when its time is up.
     */
    private static void tickMarks(Minecraft minecraft) {
        if (MARKS.isEmpty()) {
            // Nothing left to outline, so let the world go: holding it would keep it in memory past a dimension change.
            markLevel = null;
            return;
        }
        if (minecraft.level != markLevel) {
            MARKS.clear();
            markLevel = null;
            return;
        }
        for (ObjectIterator<Int2IntMap.Entry> entries = MARKS.int2IntEntrySet().fastIterator(); entries.hasNext(); ) {
            if (entries.next().getIntValue() <= ticks) {
                entries.remove();
            }
        }
    }

    /**
     * Outlines these souls, felt by this player's Pesquisa, for this many ticks. A pulse's marks replace the
     * last one's, and belong to the world this client is in now.
     */
    static void mark(List<Integer> ids, int ticks) {
        MARKS.clear();
        markLevel = Minecraft.getInstance().level;
        if (markLevel == null) {
            return;
        }
        int fades = HollowKeys.ticks + Math.max(0, ticks);
        for (int id : ids) {
            MARKS.put(id, fades);
        }
    }

    /**
     * Whether this entity is outlined through walls for this player, as one its last Pesquisa felt and whose
     * outline has not yet faded (see {@code MinecraftMixin}).
     */
    public static boolean marked(Entity entity) {
        if (MARKS.isEmpty() || entity.level() != markLevel) {
            return false;
        }
        int id = entity.getId();
        return MARKS.containsKey(id) && MARKS.get(id) > ticks;
    }

    /** The player's own Cero that is gathering about its mouth, if the server has sent one. */
    @Nullable
    private static CeroBeam findOwnCero(LocalPlayer player) {
        // The orb sits ahead of the eyes by a share of the player's size, so a box that grows with it holds it.
        double reach = CeroBeam.LOOK_MUZZLE_AHEAD * player.getScale() + 1.0;
        List<CeroBeam> found = player.level().getEntitiesOfClass(CeroBeam.class, player.getBoundingBox().inflate(reach),
                beam -> gathering(beam, player));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Whether this Cero is the player's own, in its world, and still gathering. */
    private static boolean gathering(@Nullable CeroBeam beam, LocalPlayer player) {
        return beam != null && beam.isCharging() && beam.level() == player.level() && beam.owner() == player;
    }

    /**
     * How far this client's own Cero has gathered, from 0 to 1, while the Cero key is held: the charging
     * beam's own progress, which the server set going. 0 while nothing is gathering, so a refused press,
     * a Cero that has fired or one that fizzled shows nothing.
     */
    public static float ceroProgress(float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!ceroHolding || player == null || !gathering(ownCero, player)) {
            return 0.0F;
        }
        return ownCero.chargeProgress(partialTick);
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
            hold = (look & HollowPlayerLogic.LOOK_DEVOURING_PLUS) != 0
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
