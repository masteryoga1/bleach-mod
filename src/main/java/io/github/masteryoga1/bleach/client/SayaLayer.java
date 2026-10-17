package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.function.Function;
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
 * A sheathed zanpakutō, in the look of the blade it is (see {@link SheathLook}). Most hang at the hip
 * opposite the sword arm, tucked through the obi with the hilt forward and the scabbard sloping down
 * behind. Zangetsu, which has no saya, is slung across the back wrapped in cloth, its hilt over the
 * sword arm's shoulder. Drawn as plain coloured boxes until the mod has its own art.
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
    /** A saya is a little narrower across than it is deep. */
    private static final float SAYA_WIDTH_SHARE = 7.0F / 9.0F;
    /** How far the end caps stand proud of the scabbard. */
    private static final float CAP_PROUD = 0.005F;

    /** Where a blade on the back sits, from the body's pivot: a third of the way down and just behind the back. */
    private static final float BACK_DOWN = 0.25F;
    private static final float BACK_BEHIND = 0.18F;
    private static final float BACK_TILT_DEGREES = 40.0F;
    /** How flat the wrapped blade is, front to back. */
    private static final float BACK_DEPTH = 0.06F;
    private static final float BACK_HILT_LENGTH = 0.30F;
    private static final float BAND_HALF_WIDTH = 0.02F;

    private final Function<T, SheathLook> look;

    /** @param look the blade a creature wears sheathed right now, or null for none */
    public SayaLayer(RenderLayerParent<T, M> parent, Function<T, SheathLook> look) {
        super(parent);
        this.look = look;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        SheathLook sheath = look.apply(entity);
        if (sheath == null) {
            return;
        }
        // Model +x is the creature's left, so a right-handed swordsman wears the blade on +x.
        float side = entity.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        VertexConsumer consumer = buffer.getBuffer(TYPE);
        if (sheath.onBack()) {
            renderOnBack(poseStack, consumer, packedLight, sheath, side);
        } else {
            renderAtHip(poseStack, consumer, packedLight, sheath, side);
        }
        poseStack.popPose();
    }

    /** The saya through the obi: its mouth at the hip with the guard and hilt in front of it. */
    private static void renderAtHip(PoseStack poseStack, VertexConsumer consumer, int light, SheathLook sheath,
                                    float side) {
        poseStack.translate(side * SIDE, HIP, FORWARD);
        // Model space has y pointing down and the front at -z: tilt the back end down and out.
        poseStack.mulPose(Axis.YP.rotationDegrees(side * SPLAY_DEGREES));
        poseStack.mulPose(Axis.XP.rotationDegrees(SLOPE_DEGREES));
        PoseStack.Pose pose = poseStack.last();
        float deep = sheath.thickness() / 2.0F;
        float wide = deep * SAYA_WIDTH_SHARE;
        float length = sheath.length();
        box(consumer, pose, light, -wide, -deep, 0.0F, wide, deep, length, sheath.saya());
        box(consumer, pose, light, -wide - CAP_PROUD, -deep - CAP_PROUD, length,
                wide + CAP_PROUD, deep + CAP_PROUD, length + 0.03F, sheath.fitting());
        box(consumer, pose, light, -0.08F, -0.08F, -0.025F, 0.08F, 0.08F, 0.0F, sheath.fitting());
        box(consumer, pose, light, -0.03F, -0.04F, -0.27F, 0.03F, 0.04F, -0.025F, sheath.tsuka());
        box(consumer, pose, light, -0.035F, -0.045F, -0.29F, 0.035F, 0.045F, -0.27F, sheath.fitting());
    }

    /**
     * A broad blade wrapped in cloth, slung diagonally behind the back from the far hip up to the
     * sword arm's shoulder, with three bands round the wrapping and the hilt sticking out at the top.
     * It has no guard.
     */
    private static void renderOnBack(PoseStack poseStack, VertexConsumer consumer, int light, SheathLook sheath,
                                     float side) {
        poseStack.translate(0.0F, BACK_DOWN, BACK_BEHIND);
        // The top end (model -y) leans towards the sword arm, which is on -x for a right-handed wielder.
        poseStack.mulPose(Axis.ZP.rotationDegrees(-side * BACK_TILT_DEGREES));
        PoseStack.Pose pose = poseStack.last();
        float half = sheath.length() / 2.0F;
        float broad = sheath.thickness() / 2.0F;
        float flat = BACK_DEPTH / 2.0F;
        box(consumer, pose, light, -broad, -half, -flat, broad, half, flat, sheath.saya());
        for (int band = 1; band <= 3; band++) {
            float y = -half + sheath.length() * band / 4.0F;
            box(consumer, pose, light, -broad - CAP_PROUD, y - BAND_HALF_WIDTH, -flat - CAP_PROUD,
                    broad + CAP_PROUD, y + BAND_HALF_WIDTH, flat + CAP_PROUD, sheath.fitting());
        }
        box(consumer, pose, light, -0.035F, -half - BACK_HILT_LENGTH, -0.035F, 0.035F, -half, 0.035F, sheath.tsuka());
    }

    /** One solid-coloured box, all six faces. Drawn without culling, so the corner order does not matter. */
    private static void box(VertexConsumer consumer, PoseStack.Pose pose, int light, float x0, float y0, float z0,
                            float x1, float y1, float z1, int rgb) {
        int colour = 0xFF000000 | rgb;
        quad(consumer, pose, light, colour, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(consumer, pose, light, colour, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(consumer, pose, light, colour, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(consumer, pose, light, colour, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(consumer, pose, light, colour, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(consumer, pose, light, colour, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, float nx, float ny,
                             float nz, float... corners) {
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2])
                    .setColor(colour)
                    .setUv(i == 0 || i == 1 ? 0.0F : 1.0F, i == 0 || i == 3 ? 0.0F : 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
