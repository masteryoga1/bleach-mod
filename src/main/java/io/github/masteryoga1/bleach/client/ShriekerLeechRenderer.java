package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.ShriekerLeech;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A Shrieker's leech, turned the way it flies or, clinging, the way its bearer's body faces. The unaware never see it,
 * as with every spirit (see {@code EntityRenderDispatcherMixin}).
 */
public final class ShriekerLeechRenderer extends EntityRenderer<ShriekerLeech> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/hollow/shrieker_leech.png");

    private final ShriekerLeechModel model;

    public ShriekerLeechRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ShriekerLeechModel(context.bakeLayer(BleachModelLayers.SHRIEKER_LEECH));
    }

    /** As a mob's model is drawn: turned to its yaw, flipped upright, and stood on its feet. */
    @Override
    public void render(ShriekerLeech leech, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, leech.yRotO, leech.getYRot())));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.setupAnim(leech, 0.0F, 0.0F, leech.tickCount + partialTick, 0.0F, 0.0F);
        model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(leech, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ShriekerLeech leech) {
        return TEXTURE;
    }
}
