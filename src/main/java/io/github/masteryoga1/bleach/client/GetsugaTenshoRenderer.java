package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Getsuga Tenshō: a glowing crescent standing across its path with its middle leading, as wide and
 * tall as the charge made it (the same size the server cuts with) and tinted the blade's colour. It
 * glows as brightly in the dark as at noon, fades in as it leaves the blade and out as its flight
 * ends, and is seen from both sides. The black one, loosed from behind a Visored's mask, is drawn twice:
 * a taller red crescent first, then a black one over it, so a red edge shows around a black core.
 */
public final class GetsugaTenshoRenderer extends EntityRenderer<GetsugaTensho> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/getsuga_tensho.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(TEXTURE);
    /** The black core's own type, the same look but a different type, so asking for it draws the red edge first. */
    private static final RenderType CORE_TYPE = RenderType.entityTranslucentEmissive(TEXTURE, false);
    private static final int RED_EDGE = 0xC8141E;
    private static final int BLACK_CORE = 0x0E0A12;
    /** How much taller the red edge stands than the black core. */
    private static final float EDGE_SCALE = 1.4F;
    /** How far in from the texture's top and bottom rows the red edge samples, so its rim shows past the core's. */
    private static final float EDGE_INSET = 0.15F;
    /** Quads along the arc. */
    private static final int SEGMENTS = 12;
    /** The arc runs this far either side of straight ahead. */
    private static final float ARC_DEGREES = 70.0F;
    /** How far the middle of the arc leads, as a share of the half-width. */
    private static final float BOW = 0.6F;
    /** How thick the tips are, as a share of the middle's thickness. */
    private static final float TIP_THICKNESS = 0.35F;
    private static final float FADE_IN_TICKS = 2.0F;
    private static final float FADE_OUT_TICKS = 5.0F;

    public GetsugaTenshoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GetsugaTensho crescent, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = crescent.age() + partialTick;
        float alpha = Mth.clamp(age / FADE_IN_TICKS, 0.0F, 1.0F)
                * Mth.clamp((crescent.lifeTicks() - age) / FADE_OUT_TICKS, 0.0F, 1.0F);
        if (alpha > 0.0F) {
            float charge = crescent.charge();
            float halfWidth = GetsugaTensho.halfWidthFor(charge);
            float halfHeight = GetsugaTensho.halfHeightFor(charge);
            int alphaBits = Math.round(alpha * 255.0F) << 24;
            poseStack.pushPose();
            // Turned the way it was launched, so local +z points along its flight.
            poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, crescent.yRotO, crescent.getYRot())));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, crescent.xRotO, crescent.getXRot())));
            PoseStack.Pose pose = poseStack.last();
            if (crescent.isBlack()) {
                // The red edge's rows sit further in, where the texture is denser, so its rim outlasts the core's.
                strip(buffer.getBuffer(TYPE), pose, halfWidth, halfHeight * EDGE_SCALE, EDGE_INSET, 1.0F - EDGE_INSET,
                        alphaBits | RED_EDGE);
                strip(buffer.getBuffer(CORE_TYPE), pose, halfWidth, halfHeight, 0.0F, 1.0F, alphaBits | BLACK_CORE);
            } else {
                strip(buffer.getBuffer(TYPE), pose, halfWidth, halfHeight, 0.0F, 1.0F,
                        alphaBits | (crescent.colour() & 0xFFFFFF));
            }
            poseStack.popPose();
        }
        super.render(crescent, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * The crescent's quads along the arc, {@code halfHeight} tall at its middle and thinning to the tips, in one
     * colour, sampling the texture's rows from {@code vTop} at its top edge to {@code vBottom} at its bottom.
     */
    private static void strip(VertexConsumer consumer, PoseStack.Pose pose, float halfWidth, float halfHeight, float vTop,
                              float vBottom, int colour) {
        for (int i = 0; i < SEGMENTS; i++) {
            float u0 = (float) i / SEGMENTS;
            float u1 = (float) (i + 1) / SEGMENTS;
            float a0 = (u0 * 2.0F - 1.0F) * ARC_DEGREES * Mth.DEG_TO_RAD;
            float a1 = (u1 * 2.0F - 1.0F) * ARC_DEGREES * Mth.DEG_TO_RAD;
            float x0 = halfWidth * Mth.sin(a0);
            float z0 = BOW * halfWidth * Mth.cos(a0);
            float t0 = halfHeight * (TIP_THICKNESS + (1.0F - TIP_THICKNESS) * Mth.cos(a0));
            float x1 = halfWidth * Mth.sin(a1);
            float z1 = BOW * halfWidth * Mth.cos(a1);
            float t1 = halfHeight * (TIP_THICKNESS + (1.0F - TIP_THICKNESS) * Mth.cos(a1));
            vertex(consumer, pose, x0, t0, z0, u0, vTop, colour);
            vertex(consumer, pose, x0, -t0, z0, u0, vBottom, colour);
            vertex(consumer, pose, x1, -t1, z1, u1, vBottom, colour);
            vertex(consumer, pose, x1, t1, z1, u1, vTop, colour);
        }
    }

    /**
     * One corner, full-bright. The emissive shader still shades by the normal, so every corner gets
     * one pointing up, where the world's two lights both fall: a crescent flying level is lit fully,
     * whichever way it faces.
     */
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int colour) {
        consumer.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(GetsugaTensho crescent) {
        return TEXTURE;
    }
}
