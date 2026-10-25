package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * How playing as a Hollow looks from inside. The food row is hidden, since soul hunger replaces food (the
 * HUD's hunger gauge is in {@link ReiryokuHud}), and the air bubbles take its place as vanilla stacks
 * them. In first person, the edge of the bone mask frames the view, cracked after a blow and tinged red
 * while the Hollow is ravenous (the client config can turn it off), and the bare arms show the dark
 * forearms and claws of {@link HollowBodyLayer}.
 */
public final class HollowPlayerClient {
    public static final ResourceLocation MASK_EDGE_LAYER_ID = BleachMod.id("hollow_mask_edge");
    public static final ResourceLocation MASK_EDGE = BleachMod.id("textures/misc/hollow_mask_edge.png");
    public static final ResourceLocation MASK_EDGE_CRACKED = BleachMod.id("textures/misc/hollow_mask_edge_cracked.png");

    /** How much of the view behind the mask's edge shows through it. */
    private static final float EDGE_ALPHA = 0.85F;
    /** The green and blue left in the edge while the Hollow is ravenous, which turns it red. */
    private static final float HUNGRY_TINT = 0.55F;

    /** Set while this class draws an arm itself, which posts the arm event again. */
    private static boolean drawingArm;

    private HollowPlayerClient() {
    }

    /** Game-bus listeners: the food row and the first-person claws. */
    static void register(IEventBus gameBus) {
        gameBus.addListener(HollowPlayerClient::onRenderGuiLayer);
        gameBus.addListener(EventPriority.LOW, HollowPlayerClient::onRenderArm);
    }

    /** A Hollow has no food bar to show: soul hunger replaces it. */
    private static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && HollowPlayerLogic.isHollowPlayer(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * A GUI layer above the camera overlays: the inside edge of the mask around a Hollow's view in first
     * person, drawn as vanilla draws the carved pumpkin's blur. Not under a carved pumpkin, which has its
     * own, nor through a spyglass, whose own view fills the screen.
     */
    static void renderMaskEdge(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!BleachClientConfig.maskOverlay() || player == null || minecraft.options.hideGui || player.isSpectator()
                || !minecraft.options.getCameraType().isFirstPerson() || player.isScoping()
                || player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)
                || !HollowPlayerLogic.isHollowPlayer(player)) {
            return;
        }
        float satiety = player.getData(SoulAttachments.SOUL).hollow().satiety();
        float tint = HollowPlayerLogic.band(satiety) != HollowPlayerLogic.Band.FED ? HUNGRY_TINT : 1.0F;
        boolean cracked = (ClientSouls.appearance(player).hollowLook() & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0;
        ResourceLocation texture = cracked ? MASK_EDGE_CRACKED : MASK_EDGE;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, tint, tint, EDGE_ALPHA);
        graphics.blit(texture, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * A Hollow's bare arm in first person, with the dark forearm and claws over it. Vanilla draws the arm
     * and sleeve again from here (the event comes back, and is let through), which leaves them posed, and
     * the claws are drawn on those same parts: so they follow the wide or slim arm, and the sleeve only
     * while the skin shows it.
     */
    private static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        if (drawingArm || player.isInvisible() || !HollowPlayerLogic.isHollowPlayer(player)) {
            return;
        }
        EntityRenderer<? super AbstractClientPlayer> found =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(found instanceof PlayerRenderer renderer)) {
            return;
        }
        event.setCanceled(true);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        drawingArm = true;
        try {
            if (right) {
                renderer.renderRightHand(poseStack, buffer, light, player);
            } else {
                renderer.renderLeftHand(poseStack, buffer, light, player);
            }
        } finally {
            drawingArm = false;
        }
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        VertexConsumer claws = buffer.getBuffer(RenderType.entityCutoutNoCull(HollowBodyLayer.TEXTURE));
        arm.render(poseStack, claws, light, OverlayTexture.NO_OVERLAY);
        sleeve.render(poseStack, claws, light, OverlayTexture.NO_OVERLAY);
    }
}
