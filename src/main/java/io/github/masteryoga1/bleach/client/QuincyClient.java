package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.QuincyKey;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
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
 * sends the press and greys the HUD outline for a moment while the answer comes back. The bloodline
 * question waits here until nothing else is on screen. While the Quincy Cross is drawn, its model
 * turns into the Heilig Bogen and pulls back like a bow, the view narrows as a bow's does, and the
 * archer walks at half speed rather than vanilla's fifth. Heavy Hollow poison pulses violet at the
 * edges of the view.
 */
public final class QuincyClient {
    public static final KeyMapping BLUT_KEY = new KeyMapping("key.bleach.blut", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, BleachClient.KEY_CATEGORY);

    public static final ResourceLocation POISON_LAYER_ID = BleachMod.id("hollow_poison");
    /** Item model predicates for the Cross, read by {@code models/item/quincy_cross.json}. */
    public static final ResourceLocation PULL = BleachMod.id("pull");
    public static final ResourceLocation PULLING = BleachMod.id("pulling");

    /** Set when the server asks the bloodline question; the screen opens once nothing else is showing. */
    static boolean bloodlinePending;

    private static final long NEVER = Long.MIN_VALUE / 2;
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

    private QuincyClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QuincyClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(QuincyClient::onComputeFov);
        NeoForge.EVENT_BUS.addListener(QuincyClient::onMovementInput);
    }

    /**
     * Gives the Cross the bow's two model predicates, so its model can swap to the Heilig Bogen and
     * follow the pull. Run on the main thread, since the property map is not safe to fill from the
     * parallel setup.
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.QUINCY_CROSS.get(), PULLING,
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                            && entity.getUseItem() == stack ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.QUINCY_CROSS.get(), PULL,
                    (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                            : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
        });
    }

    /** Called every client tick: sends Blut presses and opens the bloodline question when it can. */
    static void tick(Minecraft minecraft) {
        boolean pressed = false;
        while (BLUT_KEY.consumeClick()) {
            pressed = true;
        }
        boolean down = BLUT_KEY.isDown();
        boolean wasDown = blutWasDown;
        blutWasDown = down;

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            // Out of the world: a question or a press from the last one means nothing in the next.
            bloodlinePending = false;
            lastBlutPressAt = NEVER;
            return;
        }
        if (pressed && !wasDown && minecraft.screen == null) {
            BleachNetwork.requestQuincyKey(player.isShiftKeyDown() ? QuincyKey.BLUT_OFF : QuincyKey.BLUT);
            lastBlutPressAt = player.level().getGameTime();
        }

        // While the soul the server last sent is settled, the question waits: either it was settled some
        // other way since (by command, say) and the server would ignore an answer, or the soul that
        // unsettles it (after a Quincy reset) is still on its way.
        if (bloodlinePending && minecraft.screen == null && !player.getData(SoulAttachments.SOUL).quincy().decided()) {
            bloodlinePending = false;
            minecraft.setScreen(new BloodlineScreen());
        }
    }

    /** Whether a Blut press from here is still waiting on the server: within the switch time of the last one. */
    public static boolean blutSettling(long gameTime) {
        return gameTime >= lastBlutPressAt && gameTime - lastBlutPressAt < QuincyLogic.BLUT_SWITCH_TICKS;
    }

    /** How far the local player has drawn the Heilig Bogen, from 0 to 1, as a bow's pull; 0 when not drawing. */
    public static float drawPower(LocalPlayer player) {
        return drawing(player) ? BowItem.getPowerForTime(player.getTicksUsingItem()) : 0.0F;
    }

    /** Whether this player is drawing the Cross right now. */
    private static boolean drawing(Player player) {
        return player.isUsingItem() && QuincyCrossItem.is(player.getUseItem());
    }

    /**
     * Narrows the view while the Cross is drawn, by vanilla's bow curve: up to 15% at a full draw.
     * Worked from the unblended modifier and blended again with the player's FOV effects setting, as
     * vanilla blends its own.
     */
    private static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (!drawing(player)) {
            return;
        }
        float drawn = player.getTicksUsingItem() / (float) QuincyLogic.FULL_DRAW_TICKS;
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
