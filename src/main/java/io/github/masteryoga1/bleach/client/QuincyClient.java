package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.QuincyKey;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * The client half of the Quincy path. The Blut key (N) asks the server for Blut Vene or Arterie, or,
 * pressed while sneaking, lets Blut go; the server decides and says why when it refuses, so this only
 * sends the press and greys the HUD outline for a moment while the answer comes back. The Quincy Form
 * key (H) is sent the same way, as itself or, while sneaking, as its sneaking twin, and the server
 * works out what it means (see {@code QuincyArtsEvents#onFormKey}); only a press that starts Licht
 * Regen is followed here, so the HUD can show the gathering. The bloodline question waits here until
 * nothing else is on screen. While the Quincy Cross is drawn, its model turns into the bow of the
 * Quincy's form (the Heilig Bogen, Letzt Stil's or Ginrei Kojaku) and pulls back like a bow over the
 * form's draw time, the view narrows as a bow's does, and the archer walks at half speed rather than
 * vanilla's fifth. While the power is sealed, the Cross held through the rite shows no bow at all.
 * Heavy Hollow poison pulses violet at the edges of the view.
 */
public final class QuincyClient {
    public static final KeyMapping BLUT_KEY = new KeyMapping("key.bleach.blut", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, BleachClient.KEY_CATEGORY);
    public static final KeyMapping FORM_KEY = new KeyMapping("key.bleach.quincy_form", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, BleachClient.KEY_CATEGORY);

    public static final ResourceLocation POISON_LAYER_ID = BleachMod.id("hollow_poison");
    /** Item model predicates for the Cross, read by {@code models/item/quincy_cross.json}. */
    public static final ResourceLocation PULL = BleachMod.id("pull");
    public static final ResourceLocation PULLING = BleachMod.id("pulling");
    /** The Cross's bow: 0 the Heilig Bogen, 0.5 Letzt Stil's, 1 Ginrei Kojaku. */
    public static final ResourceLocation STYLE = BleachMod.id("style");
    /** Whether a Seele Schneider shows its blade, read by {@code models/item/seele_schneider.json}. */
    public static final ResourceLocation FORMED = BleachMod.id("formed");

    /** Set when the server asks the bloodline question; the screen opens once nothing else is showing. */
    static boolean bloodlinePending;

    private static final long NEVER = Long.MIN_VALUE / 2;
    private static final float STYLE_LETZT = 0.5F;
    private static final float STYLE_GINREI = 1.0F;
    /**
     * How long past its gathering time a Licht Regen is still shown here, for the server's answer to
     * arrive: by then it has fired and stopped the draw, or let it go.
     */
    private static final int LICHT_GATHER_LATE_TICKS = 10;
    /** Vanilla slows a player using an item to a fifth; this brings an archer at the Cross back up to half. */
    private static final float DRAW_MOVEMENT_MULTIPLIER = 2.5F;
    /** How far a full draw narrows the view, as vanilla's bow does. */
    private static final float DRAW_FOV_DROP = 0.15F;

    /** How far in from each edge the poison vignette reaches, in GUI pixels. */
    private static final int VIGNETTE_DEPTH = 12;
    private static final int VIGNETTE_RGB = 0x802090;
    /** The vignette's alpha at the very edge, at the height of its pulse. */
    private static final int VIGNETTE_EDGE_ALPHA = 0x60;
    private static final float PULSE_BASE = 0.6F;
    private static final float PULSE_SWING = 0.4F;
    private static final float PULSE_SPEED = 0.15F;

    /** Whether the Blut key was down last tick, so holding it (and key repeat) sends only once. */
    private static boolean blutWasDown;
    /** Game time of the last Blut press sent from here, for the HUD's settling outline. */
    private static long lastBlutPressAt = NEVER;
    /** Whether the Quincy Form key was down last tick, so holding it (and key repeat) sends only once. */
    private static boolean formWasDown;
    /** Game time of the last Quincy Form press the server will have taken, to skip those it ignores. */
    private static long lastFormPressAt = NEVER;
    /** Game time a Licht Regen began to gather, as far as this client can tell, or {@code NEVER}. */
    private static long lichtGatherStartedAt = NEVER;

    private QuincyClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QuincyClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(QuincyClient::onComputeFov);
        NeoForge.EVENT_BUS.addListener(QuincyClient::onMovementInput);
    }

    /**
     * Gives the Cross the bow's two model predicates, so its model can swap to the bow and follow the
     * pull over the form's draw time, and the style of bow the form gives it; and gives the Seele
     * Schneider its blade while formed. A sealed Quincy's Cross is never drawn as a bow, and the
     * forms' bows and the blade are reishi, so the unaware see the plain Cross and a bare hilt. Run on
     * the main thread, since the property map is not safe to fill from the parallel setup.
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.QUINCY_CROSS.get(), PULLING,
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                            && entity.getUseItem() == stack
                            && (look(entity) & QuincyArtsLogic.LOOK_SEALED) == 0 ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.QUINCY_CROSS.get(), PULL,
                    (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                            : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks())
                                    / (float) drawTicksFor(entity));
            ItemProperties.register(ModItems.QUINCY_CROSS.get(), STYLE,
                    (stack, level, entity, seed) -> style(entity));
            ItemProperties.register(ModItems.SEELE_SCHNEIDER.get(), FORMED,
                    (stack, level, entity, seed) -> SeeleSchneiderItem.isFormed(stack) && viewerSeesSpirits()
                            ? 1.0F : 0.0F);
        });
    }

    /** The Cross's bow for whoever holds it: Letzt Stil's or Ginrei Kojaku's to those who see spirits, else the Heilig Bogen. */
    private static float style(@Nullable LivingEntity entity) {
        if (entity == null || !viewerSeesSpirits()) {
            return 0.0F;
        }
        int look = look(entity);
        if ((look & QuincyArtsLogic.LOOK_LETZT) != 0) {
            return STYLE_LETZT;
        }
        return (look & QuincyArtsLogic.LOOK_GINREI) != 0 ? STYLE_GINREI : 0.0F;
    }

    /** Whether the local player sees spirits; nobody does before there is one. */
    private static boolean viewerSeesSpirits() {
        LocalPlayer viewer = Minecraft.getInstance().player;
        return viewer != null && SoulLogic.seesSpirits(viewer);
    }

    /**
     * The Quincy look bits of a creature: the local player's from its own soul (cheaper than its whole
     * appearance, as the models ask every frame), anyone else's from what the server sent.
     */
    private static int look(LivingEntity entity) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (entity == self) {
            return QuincyArtsLogic.quincyLook(self.getData(SoulAttachments.SOUL));
        }
        return ClientSouls.appearance(entity).quincyLook();
    }

    /** Ticks to a full draw for whoever holds the Cross: the local player's from its own soul, anyone else's from its look. */
    private static int drawTicksFor(LivingEntity entity) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (entity == self) {
            return QuincyArtsLogic.drawTicks(self.getData(SoulAttachments.SOUL));
        }
        return QuincyArtsLogic.drawTicksForLook(ClientSouls.appearance(entity).quincyLook());
    }

    /**
     * Called every client tick: sends Blut and Quincy Form presses, follows a Licht Regen gathering,
     * and opens the bloodline question when it can.
     */
    static void tick(Minecraft minecraft) {
        boolean pressed = false;
        while (BLUT_KEY.consumeClick()) {
            pressed = true;
        }
        boolean down = BLUT_KEY.isDown();
        boolean wasDown = blutWasDown;
        blutWasDown = down;
        boolean formPressed = false;
        while (FORM_KEY.consumeClick()) {
            formPressed = true;
        }
        boolean formDown = FORM_KEY.isDown();
        boolean formHeld = formWasDown;
        formWasDown = formDown;

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            // Out of the world: a question or a press from the last one means nothing in the next.
            bloodlinePending = false;
            lastBlutPressAt = NEVER;
            lastFormPressAt = NEVER;
            lichtGatherStartedAt = NEVER;
            return;
        }
        long now = player.level().getGameTime();
        if (pressed && !wasDown && minecraft.screen == null && minecraft.getConnection() != null) {
            BleachNetwork.requestQuincyKey(player.isShiftKeyDown() ? QuincyKey.BLUT_OFF : QuincyKey.BLUT);
            lastBlutPressAt = now;
        }
        if (formPressed && !formHeld && minecraft.screen == null && minecraft.getConnection() != null) {
            boolean sneak = player.isShiftKeyDown();
            BleachNetwork.requestQuincyKey(sneak ? QuincyKey.FORM_SNEAK : QuincyKey.FORM);
            // The server ignores a press too soon after the last one it took, and only counts those.
            if (now - lastFormPressAt >= QuincyArtsLogic.FORM_DEBOUNCE_TICKS) {
                lastFormPressAt = now;
                if (!sneak && lichtRegenStarts(player, now)) {
                    lichtGatherStartedAt = now;
                }
            }
        }
        // The gathering ends with the draw: fired (the server stops the draw), let go, or refused.
        if (!drawing(player) || now - lichtGatherStartedAt > QuincyArtsLogic.LICHT_GATHER_TICKS + LICHT_GATHER_LATE_TICKS) {
            lichtGatherStartedAt = NEVER;
        }

        // While the soul the server last sent has no question to answer, it waits: either it was settled
        // some other way since (by command, say) or the soul is no longer a living Human, and the server
        // would ignore an answer, or the soul that unsettles it (after a Quincy reset) is still on its way.
        if (bloodlinePending && minecraft.screen == null
                && QuincyLogic.needsBloodlineChoice(player.getData(SoulAttachments.SOUL))) {
            bloodlinePending = false;
            minecraft.setScreen(new BloodlineScreen());
        }
    }

    /** Whether a Blut press from here is still waiting on the server: within the switch time of the last one. */
    public static boolean blutSettling(long gameTime) {
        return gameTime >= lastBlutPressAt && gameTime - lastBlutPressAt < QuincyLogic.BLUT_SWITCH_TICKS;
    }

    /**
     * Whether a Quincy Form press the server takes now starts Licht Regen there: not while already
     * gathering, and with a restored Cross drawn, the volley ready again and the reishi to pay for it.
     * The server checks it all again; this only keeps the HUD from showing a gathering that was
     * refused.
     */
    private static boolean lichtRegenStarts(LocalPlayer player, long now) {
        if (lichtGatherStartedAt != NEVER || !drawing(player)) {
            return false;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        return QuincyArtsLogic.isRestored(data) && QuincyLogic.canUsePowers(data)
                && now >= QuincyArtsLogic.arts(data).forms().lichtRegenReadyAt()
                && QuincyLogic.reishi(data) >= BleachConfig.get(BleachConfig.LICHT_REGEN_COST);
    }

    /**
     * How far the local player has drawn the Cross, from 0 to 1, as a bow's pull over the draw time of
     * the Quincy's form; 0 when not drawing.
     */
    public static float drawPower(LocalPlayer player) {
        return drawing(player)
                ? QuincyArtsLogic.drawPower(player.getTicksUsingItem(), drawTicksFor(player)) : 0.0F;
    }

    /** How many ticks the local player has held the Cross drawn, or 0 when not drawing. */
    public static int useTicks(LocalPlayer player) {
        return drawing(player) ? player.getTicksUsingItem() : 0;
    }

    /** Whether a Licht Regen is gathering in the local player's drawn Cross, as far as this client can tell. */
    static boolean lichtGathering(LocalPlayer player) {
        return lichtGatherStartedAt != NEVER && drawing(player);
    }

    /** How far a Licht Regen has gathered, from 0 to 1, or 0 when none is gathering. */
    public static float lichtGatherProgress(LocalPlayer player) {
        if (!lichtGathering(player)) {
            return 0.0F;
        }
        long gathered = player.level().getGameTime() - lichtGatherStartedAt;
        return Mth.clamp(gathered / (float) QuincyArtsLogic.LICHT_GATHER_TICKS, 0.0F, 1.0F);
    }

    /**
     * Whether letting go now looses a Letzt Pfeil: in Letzt Stil, held well past the full draw, with
     * the reishi to pay for it. Otherwise a release is an ordinary full draw.
     */
    public static boolean letztChargeReady(LocalPlayer player) {
        if (!drawing(player)) {
            return false;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        return QuincyArtsLogic.inLetzt(data)
                && player.getTicksUsingItem() >= QuincyArtsLogic.drawTicks(data) + QuincyArtsLogic.LETZT_CHARGE_EXTRA_TICKS
                && QuincyLogic.reishi(data) >= BleachConfig.get(BleachConfig.LETZT_PFEIL_COST);
    }

    /** Whether this player is drawing the Cross right now. */
    private static boolean drawing(Player player) {
        return player.isUsingItem() && QuincyCrossItem.is(player.getUseItem());
    }

    /**
     * Narrows the view while the Cross is drawn, by vanilla's bow curve over the form's draw time: up
     * to 15% at a full draw. Not while the power is sealed, when the Cross is held through the rite
     * and no bow is drawn. Worked from the unblended modifier and blended again with the player's FOV
     * effects setting, as vanilla blends its own.
     */
    private static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (!drawing(player) || (look(player) & QuincyArtsLogic.LOOK_SEALED) != 0) {
            return;
        }
        float drawn = player.getTicksUsingItem() / (float) drawTicksFor(player);
        float zoom = Math.min(1.0F, drawn * drawn);
        float modifier = event.getFovModifier() * (1.0F - DRAW_FOV_DROP * zoom);
        float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        event.setNewFovModifier(Mth.lerp(scale, 1.0F, modifier));
    }

    /**
     * Lets an archer at the Cross walk at half speed. The event comes just before vanilla cuts the
     * input to a fifth for any item in use; it does not cut it for a rider, so neither is a rider
     * sped up here.
     */
    private static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player.isPassenger() || !drawing(player)) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse *= DRAW_MOVEMENT_MULTIPLIER;
        input.forwardImpulse *= DRAW_MOVEMENT_MULTIPLIER;
    }

    /**
     * Heavy Hollow poison: violet bands along the four edges of the view, darkest at the edge and
     * fading inwards, pulsing slowly while the poison is past the point where it brings pangs.
     */
    static void renderPoison(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        if (!data.isInitialized() || data.quincy().poison() < QuincyLogic.POISON_PANG) {
            return;
        }
        float time = player.level().getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        float pulse = PULSE_BASE + PULSE_SWING * Mth.sin(time * PULSE_SPEED);
        int alpha = Mth.clamp(Math.round(VIGNETTE_EDGE_ALPHA * pulse), 0, 255);
        if (alpha <= 0) {
            return;
        }
        int edge = alpha << 24 | VIGNETTE_RGB;
        int inner = VIGNETTE_RGB;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // fillGradient fades from top to bottom, so the side bands are drawn turned on their sides.
        graphics.fillGradient(0, 0, width, VIGNETTE_DEPTH, edge, inner);
        graphics.fillGradient(0, height - VIGNETTE_DEPTH, width, height, inner, edge);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // Turned a quarter left about the bottom-left corner: the band's top edge lies along the screen's left.
        pose.translate(0.0F, height, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        graphics.fillGradient(0, 0, height, VIGNETTE_DEPTH, edge, inner);
        pose.popPose();
        pose.pushPose();
        // Turned a quarter right about the top-right corner: the band's top edge lies along the screen's right.
        pose.translate(width, 0.0F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        graphics.fillGradient(0, 0, height, VIGNETTE_DEPTH, edge, inner);
        pose.popPose();
    }
}
