package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A Garganta: a black tear in the air, one flat picture that always turns to face the viewer. It
 * widens from a slit as it opens and narrows again as it closes. Unlit, so it is as dark at noon as at
 * midnight, and it casts no shadow.
 */
public final class GargantaRenderer extends EntityRenderer<GargantaRift> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/garganta.png");
    private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    /** The size of a fully open tear of size 1, in blocks. */
    private static final float WIDTH = 2.0F;
    private static final float HEIGHT = 4.0F;
    /** How tall a tear that has only just opened is, as a share of its full height. */
    private static final float SLIT_HEIGHT = 0.3F;

    public GargantaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GargantaRift rift, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float open = rift.openness(partialTick);
        Camera camera = entityRenderDispatcher.camera;
        if (open > 0.0F && camera != null) {
            Vec3 eye = camera.getPosition();
            float yaw = (float) Mth.atan2(eye.x - rift.getX(), eye.z - rift.getZ());
            float halfWidth = WIDTH * rift.size() * open / 2.0F;
            float height = HEIGHT * rift.size() * (SLIT_HEIGHT + (1.0F - SLIT_HEIGHT) * open);
            poseStack.pushPose();
            // Turn the picture's face (+z) towards the camera; its bottom edge stays on the rift's position.
            poseStack.mulPose(Axis.YP.rotation(yaw));
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer consumer = buffer.getBuffer(TYPE);
            vertex(consumer, pose, -halfWidth, 0.0F, 0.0F, 1.0F);
            vertex(consumer, pose, halfWidth, 0.0F, 1.0F, 1.0F);
            vertex(consumer, pose, halfWidth, height, 1.0F, 0.0F);
            vertex(consumer, pose, -halfWidth, height, 0.0F, 0.0F);
            poseStack.popPose();
        }
        super.render(rift, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(GargantaRift rift) {
        return TEXTURE;
    }
}
