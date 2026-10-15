package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.Minecraft;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SkullBlock;

/**
 * Draws what a player's soul shows on the outside: a Plus's Chain of Fate hanging from the chest,
 * shorter as it wears away, and a Hollow's mask. Placeholder looks built from vanilla models until
 * the mod has its own art.
 */
public final class SoulLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Chain pieces at full length. Each piece is a chain item sprite. */
    private static final int MAX_CHAIN_PIECES = 5;
    private static final float PIECE_SIZE = 0.2F;

    private final ItemStack chain = new ItemStack(Items.CHAIN);
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
        } else if (look.state() == SoulState.HOLLOW) {
            renderMask(poseStack, buffer, packedLight);
        }
    }

    /** A column of chain pieces hanging from the middle of the chest. */
    private void renderChain(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                             AbstractClientPlayer player, float fraction) {
        int pieces = Mth.clamp(Mth.ceil(fraction * MAX_CHAIN_PIECES), 1, MAX_CHAIN_PIECES);
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // Model space is upside down; turn it back so the sprites hang the right way up.
        poseStack.translate(0.0F, 0.3F, -0.17F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        for (int i = 0; i < pieces; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -(i + 0.5F) * PIECE_SIZE, 0.0F);
            poseStack.scale(PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
            Minecraft.getInstance().getItemRenderer().renderStatic(chain, ItemDisplayContext.FIXED, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffer, player.level(), player.getId() + i);
            poseStack.popPose();
        }
        poseStack.popPose();
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
