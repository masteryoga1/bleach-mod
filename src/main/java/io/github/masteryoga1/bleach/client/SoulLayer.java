package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws what a player's soul shows on the outside: a Plus's Chain of Fate hanging from the chest,
 * shorter as it wears away; a Hollow's bone mask in one of four shapes, its jaw hanging open while it
 * devours, cracked after a blow, and with red eyes while it is ravenous; an Arrancar's hole and the
 * remnant of its mask, and, released, its Hollow body and mask; and a Visored's Hollow mask while it is on,
 * a white skull with red stripes over the left eye, which only those who see spirits see. The chain is built
 * from vanilla models; the masks, the hole and the Hollow body are placeholder art drawn by the art script.
 * The hole and the remnant are drawn by two helpers that an Arrancar NPC's renderer borrows too
 * ({@link ArrancarNpcRenderer}).
 */
public final class SoulLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation MASK = BleachMod.id("textures/entity/hollow_player_mask.png");
    public static final ResourceLocation MASK_CRACKED = BleachMod.id("textures/entity/hollow_player_mask_cracked.png");
    public static final ResourceLocation MASK_EYES = BleachMod.id("textures/entity/hollow_player_mask_eyes.png");
    /** A Visored's mask, on the skull's plate and jaw: whiter than a Hollow's, red-striped, with no cracked look. */
    public static final ResourceLocation VISORED_MASK = BleachMod.id("textures/entity/visored_mask.png");
    /** An Arrancar's hole, on the body and jacket in the skin's layout, clear everywhere else. */
    public static final ResourceLocation HOLE = BleachMod.id("textures/entity/arrancar_hole.png");
    /**
     * How many pixels down the body the hole is drawn from where the texture has it, by the player's hole
     * place (see {@link ArrancarLogic#holePlace}): up at the throat, at the sternum, or down at the belly.
     * Each keeps the hole and its rim inside the body's twelve pixels.
     */
    private static final int[] HOLE_SHIFT_PIXELS = {-2, 0, 3};
    /**
     * How much deeper the hole's body and jacket are drawn than the player's own, so its faces stand a hair
     * in front of the skin's instead of in the same plane, where the two would flicker through each other.
     */
    private static final float HOLE_DEPTH_GROWTH = 1.03F;
    /** Full brightness for the glowing eyes, as vanilla's EyesLayer passes it. */
    private static final int EYES_LIGHT = 15728640;

    private final HollowMaskModel mask;

    public SoulLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                     EntityModelSet models) {
        super(parent);
        this.mask = new HollowMaskModel(models.bakeLayer(BleachModelLayers.HOLLOW_PLAYER_MASK));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        SoulAppearance look = ClientSouls.appearance(player);
        // Anything worn on the head (a helmet in creative, a skull, a carved pumpkin) sits where the mask is.
        boolean bareHead = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        if (look.state() == SoulState.PLUS && look.chainFraction() > 0.0F) {
            renderChain(poseStack, buffer, packedLight, player, look.chainFraction());
        } else if (look.state() == SoulState.HOLLOW && bareHead) {
            renderMask(poseStack, buffer, packedLight, player, look);
        } else if (look.state() == SoulState.ARRANCAR) {
            boolean released = (look.hollowLook() & HollowPlayerLogic.LOOK_RESURRECCION) != 0;
            if (released) {
                renderReleasedBody(poseStack, buffer, packedLight, player, look);
            } else {
                renderHole(poseStack, buffer, packedLight, player);
            }
            if (bareHead && released) {
                renderMask(poseStack, buffer, packedLight, player, look);
            } else if (bareHead) {
                renderFragment(poseStack, buffer, packedLight, player, look);
            }
        } else if ((look.hollowLook() & HollowPlayerLogic.LOOK_MASK) != 0 && bareHead && viewerSeesSpirits()) {
            // Only those who see spirits see it: a Substitute's living body is seen by all, but not its mask.
            renderVisoredMask(poseStack, buffer, packedLight, player);
        }
    }

    /** A column of chain pieces hanging from the middle of the chest. */
    private void renderChain(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                             AbstractClientPlayer player, float fraction) {
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // In front of the chest, or of the chestplate when one is worn.
        float front = player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? -0.17F : -0.21F;
        poseStack.translate(0.0F, 0.3F, front);
        // Model space is upside down; turn it back so the sprites hang the right way up.
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        ChainPieces.render(poseStack, buffer, packedLight, player.level(), player.getId(), fraction);
        poseStack.popPose();
    }

    /**
     * The mask on the posed head, in the shape the player's id gives, and the eyes over it, glowing at
     * full brightness, while the Hollow is ravenous. The mask is built in head space, so the head's own
     * pose is all it needs.
     */
    private void renderMask(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                            AbstractClientPlayer player, SoulAppearance look) {
        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        int bits = look.hollowLook();
        mask.setup(HollowPlayerLogic.maskVariant(player.getUUID()), (bits & HollowPlayerLogic.LOOK_DEVOURING) != 0);
        ResourceLocation texture = (bits & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0 ? MASK_CRACKED : MASK;
        mask.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F), -1);
        if ((bits & HollowPlayerLogic.LOOK_RAVENOUS) != 0) {
            mask.renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(MASK_EYES)), EYES_LIGHT,
                    OverlayTexture.NO_OVERLAY, -1);
        }
        poseStack.popPose();
    }

    /**
     * A Visored's Hollow mask on the posed head: the plain skull's plate and its shut jaw, in the Visored's own
     * texture. It takes the scene's light, with no eyes that glow, and has no cracked look of its own: a crack
     * shows only from inside, in the wearer's first-person edge.
     */
    private void renderVisoredMask(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                   AbstractClientPlayer player) {
        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        // Shape 0 is the plain skull: the plate and the jaw, with no tusks, horns or beak.
        mask.setup(0, false);
        mask.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(VISORED_MASK)), packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F), -1);
        poseStack.popPose();
    }

    /**
     * Whether the local player sees spirits, by the rules {@link ClientSouls#isHiddenFromViewer} keeps: one in
     * creative or spectator mode does, as one aware of spirits does, and every soul is seen before the server has
     * sent the viewer's own.
     */
    private static boolean viewerSeesSpirits() {
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || viewer.isCreative() || viewer.isSpectator()) {
            return true;
        }
        SoulData own = viewer.getData(SoulAttachments.SOUL);
        return !own.isInitialized() || SoulLogic.isAware(own);
    }

    /**
     * What a sealed Arrancar keeps of its mask: the part of the mask of its own shape that the mask model
     * leaves for a remnant, on the posed head as the whole mask is worn, and cracked like it. No eyes burn
     * in a remnant.
     */
    private void renderFragment(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                AbstractClientPlayer player, SoulAppearance look) {
        renderFragment(mask, getParentModel().getHead(), poseStack, buffer, packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F), HollowPlayerLogic.maskVariant(player.getUUID()),
                (look.hollowLook() & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0);
    }

    /**
     * A mask remnant of this variant on the posed head, cracked or not: the part of the mask of that shape
     * the mask model leaves for a remnant ({@link HollowMaskModel#setupFragment}), built in head space, so
     * the head's own pose is all it needs. A player's and an Arrancar NPC's are drawn alike.
     */
    static void renderFragment(HollowMaskModel mask, ModelPart head, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int overlay, int variant, boolean cracked) {
        poseStack.pushPose();
        head.translateAndRotate(poseStack);
        mask.setupFragment(variant);
        ResourceLocation texture = cracked ? MASK_CRACKED : MASK;
        mask.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), packedLight, overlay, -1);
        poseStack.popPose();
    }

    /** A sealed Arrancar player's hole, at its throat, sternum or belly by player. */
    private void renderHole(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player) {
        renderHole(getParentModel(), poseStack, buffer, packedLight, LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                ArrancarLogic.holePlace(player.getUUID()));
    }

    /**
     * A sealed Arrancar's hole at this place (0 throat, 1 sternum, 2 belly), on the body's and the jacket layer's
     * faces. The texture is clear but for the hole, front and back, so only the hole is drawn; it is moved along
     * the body in the body's own frame, so it stays on the chest as the body leans. A chestplate hides it by depth,
     * as it should. A player's and an Arrancar NPC's are drawn alike.
     */
    static void renderHole(PlayerModel<?> model, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           int overlay, int place) {
        float shift = HOLE_SHIFT_PIXELS[place] / 16.0F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(HOLE));
        renderShifted(model.body, poseStack, consumer, packedLight, overlay, shift);
        // Hidden while the skin's jacket layer is turned off, as the jacket itself is.
        renderShifted(model.jacket, poseStack, consumer, packedLight, overlay, shift);
    }

    /**
     * Draws a posed part moved {@code shift} blocks down its own length, and grown a hair in depth. The part
     * turns about its pivot, so the move is turned with it and made before the part's own pose.
     */
    private static void renderShifted(ModelPart part, PoseStack poseStack, VertexConsumer consumer, int packedLight,
                                      int overlay, float shift) {
        if (!part.visible) {
            return;
        }
        // Turned as ModelPart.translateAndRotate turns the part.
        Vector3f along = new Vector3f(0.0F, shift, 0.0F)
                .rotate(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        float zScale = part.zScale;
        poseStack.pushPose();
        poseStack.translate(along.x(), along.y(), along.z());
        part.zScale = zScale * HOLE_DEPTH_GROWTH;
        part.render(poseStack, consumer, packedLight, overlay);
        part.zScale = zScale;
        poseStack.popPose();
    }

    /**
     * A released Arrancar's Hollow body over its skin, as a Hollow's is drawn (see {@link HollowBodyLayer}),
     * with a Vasto Lorde's bone over that for one that rose from a Vasto Lorde. The skin's own hole through
     * the chest stands for the Arrancar's while it is released.
     */
    private void renderReleasedBody(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                    AbstractClientPlayer player, SoulAppearance look) {
        renderColoredCutoutModel(getParentModel(), HollowBodyLayer.TEXTURE, poseStack, buffer, packedLight, player, -1);
        if ((look.hollowLook() & HollowPlayerLogic.LOOK_VASTO_LORDE) != 0) {
            renderColoredCutoutModel(getParentModel(), HollowBodyLayer.VASTO_LORDE_TEXTURE, poseStack, buffer,
                    packedLight, player, -1);
        }
    }
}
