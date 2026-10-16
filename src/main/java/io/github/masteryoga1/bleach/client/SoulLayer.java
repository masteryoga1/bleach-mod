package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;

/**
 * Draws what a player's soul shows on the outside: a Plus's Chain of Fate hanging from the chest,
 * shorter as it wears away, and a Hollow's mask. Placeholder looks built from vanilla models until
 * the mod has its own art.
 */
public final class SoulLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final SkullModelBase mask;
    private final RenderType maskType = SkullBlockRenderer.getRenderType(SkullBlock.Types.SKELETON, null);

    public SoulLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                     EntityModelSet models) {
        super(parent);
        this.mask = new SkullModel(models.bakeLayer(ModelLayers.SKELETON_SKULL));
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
        } else if (look.state() == SoulState.HOLLOW && !wearsSkull(player)) {
            renderMask(poseStack, buffer, packedLight);
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

    /** A worn mob or player head would sit exactly where the mask goes. */
    private static boolean wearsSkull(AbstractClientPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BlockItem item
                && item.getBlock() instanceof AbstractSkullBlock;
    }

    /** A skull over the face, placed the way vanilla places a worn skull. */
    private void renderMask(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        poseStack.scale(1.1875F, -1.1875F, -1.1875F);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        SkullBlockRenderer.renderSkull(null, 180.0F, 0.0F, poseStack, buffer, packedLight, mask, maskType);
        poseStack.popPose();
    }
}
