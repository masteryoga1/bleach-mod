package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Draws what a player's soul shows on the outside: a Plus's Chain of Fate hanging from the chest,
 * shorter as it wears away, and a Hollow's bone mask in one of four shapes, its jaw hanging open while
 * it devours, cracked after a blow, and with red eyes while it is ravenous. The chain is built from
 * vanilla models; the mask is placeholder art drawn by the art script.
 */
public final class SoulLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation MASK = BleachMod.id("textures/entity/hollow_player_mask.png");
    public static final ResourceLocation MASK_CRACKED = BleachMod.id("textures/entity/hollow_player_mask_cracked.png");
    public static final ResourceLocation MASK_EYES = BleachMod.id("textures/entity/hollow_player_mask_eyes.png");
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
        if (look.state() == SoulState.PLUS && look.chainFraction() > 0.0F) {
            renderChain(poseStack, buffer, packedLight, player, look.chainFraction());
        } else if (look.state() == SoulState.HOLLOW && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            // Anything worn on the head (a helmet in creative, a skull, a carved pumpkin) sits where the mask is.
            renderMask(poseStack, buffer, packedLight, player, look);
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
}
