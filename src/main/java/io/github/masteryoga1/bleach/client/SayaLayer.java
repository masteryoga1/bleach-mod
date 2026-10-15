package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.function.Predicate;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * A zanpakutō sheathed at the hip opposite the sword arm, tucked through the obi with the hilt
 * forward and the scabbard sloping down behind. Drawn as plain coloured boxes until the mod has
 * its own art.
 */
public final class SayaLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final RenderType TYPE =
            RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/misc/white.png"));
    /** Where the scabbard's mouth sits, from the body's pivot, in blocks: out at the side, at the hip, a little forward. */
    private static final float SIDE = 0.29F;
    private static final float HIP = 0.62F;
    private static final float FORWARD = -0.12F;
    private static final float SLOPE_DEGREES = -16.0F;
    private static final float SPLAY_DEGREES = 8.0F;

    private static final int[] SAYA = {22, 22, 26};
    private static final int[] FITTING = {196, 156, 58};
    private static final int[] TSUKA = {40, 36, 62};

    private final Predicate<T> wearsBlade;

    /** @param wearsBlade whether a creature has a blade at its hip right now */
    public SayaLayer(RenderLayerParent<T, M> parent, Predicate<T> wearsBlade) {
        super(parent);
        this.wearsBlade = wearsBlade;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !wearsBlade.test(entity)) {
            return;
        }
        // Model +x is the creature's left, so a right-handed swordsman wears the blade on +x.
        float side = entity.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(side * SIDE, HIP, FORWARD);
        // Model space has y pointing down and the front at -z: tilt the back end down and out.
        poseStack.mulPose(Axis.YP.rotationDegrees(side * SPLAY_DEGREES));
        poseStack.mulPose(Axis.XP.rotationDegrees(SLOPE_DEGREES));
        VertexConsumer consumer = buffer.getBuffer(TYPE);
        PoseStack.Pose pose = poseStack.last();
        box(consumer, pose, packedLight, -0.035F, -0.045F, 0.0F, 0.035F, 0.045F, 0.70F, SAYA);
        box(consumer, pose, packedLight, -0.04F, -0.05F, 0.70F, 0.04F, 0.05F, 0.73F, FITTING);
        box(consumer, pose, packedLight, -0.08F, -0.08F, -0.025F, 0.08F, 0.08F, 0.0F, FITTING);
        box(consumer, pose, packedLight, -0.03F, -0.04F, -0.27F, 0.03F, 0.04F, -0.025F, TSUKA);
        box(consumer, pose, packedLight, -0.035F, -0.045F, -0.29F, 0.035F, 0.045F, -0.27F, FITTING);
        poseStack.popPose();
    }

    /** One solid-coloured box, all six faces. Drawn without culling, so the corner order does not matter. */
    private static void box(VertexConsumer consumer, PoseStack.Pose pose, int light, float x0, float y0, float z0,
                            float x1, float y1, float z1, int[] rgb) {
        quad(consumer, pose, light, rgb, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(consumer, pose, light, rgb, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(consumer, pose, light, rgb, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(consumer, pose, light, rgb, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(consumer, pose, light, rgb, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(consumer, pose, light, rgb, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, int[] rgb, float nx, float ny,
                             float nz, float... corners) {
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2])
                    .setColor(rgb[0], rgb[1], rgb[2], 255)
                    .setUv(i == 0 || i == 1 ? 0.0F : 1.0F, i == 0 || i == 3 ? 0.0F : 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
