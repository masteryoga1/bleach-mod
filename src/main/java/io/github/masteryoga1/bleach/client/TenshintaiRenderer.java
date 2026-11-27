package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.Tenshintai;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * A Tenshintai: a life-sized doll, faintly human, in the humanoid shape the training dummy also takes, its arms held a
 * little off its sides and never moving, turned and stood as a living body is drawn (mc LivingEntityRenderer). It is
 * no spirit, so everyone sees it.
 */
public final class TenshintaiRenderer extends EntityRenderer<Tenshintai> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/tenshintai.png");
    /** How far its arms are held off its sides, in radians. */
    private static final float ARMS_OUT = 0.12F;
    private static int drawn;

    private final HumanoidModel<LivingEntity> model;

    public TenshintaiRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE));
        // A model is young until a living body's renderer says otherwise, and a young one is drawn as a child.
        model.young = false;
        model.rightArm.zRot = ARMS_OUT;
        model.leftArm.zRot = -ARMS_OUT;
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(Tenshintai doll, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        drawn++;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - doll.getYRot()));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(doll, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Tenshintai doll) {
        return TEXTURE;
    }

    /** How many times a doll has been drawn since the game started: the smoke test reads it. */
    public static int drawnFrames() {
        return drawn;
    }
}
