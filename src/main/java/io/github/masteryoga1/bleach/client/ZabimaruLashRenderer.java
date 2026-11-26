package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Zabimaru lashing out: six blade segments strung along the line from the wielder's sword hand to
 * where the far blow landed (the lash's own position). The line runs out over the first few ticks,
 * holds, and draws back in before the lash ends. Nothing is drawn once the wielder is out of sight.
 */
public final class ZabimaruLashRenderer extends EntityRenderer<ZabimaruLash> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/zabimaru_segment.png");
    private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final int SEGMENTS = 6;
    /** Half a segment's size, in blocks: across, top to bottom, and along the line. */
    private static final float HALF_WIDTH = 0.11F;
    private static final float HALF_HEIGHT = 0.06F;
    private static final float HALF_LENGTH = 0.2F;
    /** Ticks the blade takes to reach out fully, and to draw back. */
    private static final float REACH_TICKS = 3.0F;
    /** Where the sword hand is, seen from outside: this share of the wielder's height up, and this far to the side. */
    private static final float HAND_HEIGHT = 0.6F;
    private static final float HAND_SIDE = 0.35F;

    public ZabimaruLashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ZabimaruLash lash, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Entity owner = lash.owner();
        if (owner == null) {
            return;
        }
        float age = lash.age() + partialTick;
        float out = Mth.clamp(Math.min(age, lash.lifeTicks() - age) / REACH_TICKS, 0.0F, 1.0F);
        // The pose's origin is the lash itself, where the blow landed.
        Vec3 hand = handPosition(owner, partialTick).subtract(lash.getPosition(partialTick));
        Vec3 reach = hand.scale(-out);
        if (out > 0.0F && reach.lengthSqr() > 1.0E-4) {
            float yaw = (float) Mth.atan2(reach.x, reach.z);
            float pitch = (float) -Mth.atan2(reach.y, reach.horizontalDistance());
            VertexConsumer consumer = buffer.getBuffer(TYPE);
            for (int i = 0; i < SEGMENTS; i++) {
                Vec3 at = hand.add(reach.scale((i + 0.5) / SEGMENTS));
                poseStack.pushPose();
                poseStack.translate(at.x, at.y, at.z);
                // Local +z along the line, out from the hand.
                poseStack.mulPose(Axis.YP.rotation(yaw));
                poseStack.mulPose(Axis.XP.rotation(pitch));
                segment(consumer, poseStack.last(), packedLight, HALF_WIDTH, HALF_HEIGHT, HALF_LENGTH);
                poseStack.popPose();
            }
        }
        super.render(lash, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * The wielder's sword hand. Seen from outside, it is at the side of the body at {@link #HAND_HEIGHT};
     * in first person, the line starts where the held item shows, low at the side of the view.
     */
    private Vec3 handPosition(Entity owner, float partialTick) {
        int side = owner instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        if (owner == Minecraft.getInstance().player && entityRenderDispatcher.camera != null
                && entityRenderDispatcher.options.getCameraType().isFirstPerson()) {
            double fovScale = 960.0 / entityRenderDispatcher.options.fov().get();
            Vec3 offset = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525F, -0.1F).scale(fovScale);
            return owner.getEyePosition(partialTick).add(offset);
        }
        float bodyYaw = owner instanceof LivingEntity body
                ? Mth.rotLerp(partialTick, body.yBodyRotO, body.yBodyRot)
                : owner.getViewYRot(partialTick);
        float radians = bodyYaw * Mth.DEG_TO_RAD;
        // Facing along (-sin, cos), the right hand is towards (-cos, -sin).
        double dx = -Mth.cos(radians) * side * HAND_SIDE;
        double dz = -Mth.sin(radians) * side * HAND_SIDE;
        return owner.getPosition(partialTick).add(dx, owner.getBbHeight() * HAND_HEIGHT, dz);
    }

    /**
     * One segment round the pose's origin, a box this wide, high and long by halves, its length along local z, with the
     * whole texture on each face. Grand Fisher's stalk is strung of these too.
     */
    static void segment(VertexConsumer consumer, PoseStack.Pose pose, int light, float halfWidth, float halfHeight,
                        float halfLength) {
        float x0 = -halfWidth;
        float x1 = halfWidth;
        float y0 = -halfHeight;
        float y1 = halfHeight;
        float z0 = -halfLength;
        float z1 = halfLength;
        quad(consumer, pose, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(consumer, pose, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(consumer, pose, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(consumer, pose, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(consumer, pose, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(consumer, pose, light, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, float nx, float ny, float nz,
                             float... corners) {
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2])
                    .setColor(-1)
                    .setUv(i == 0 || i == 1 ? 0.0F : 1.0F, i == 0 || i == 3 ? 0.0F : 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ZabimaruLash lash) {
        return TEXTURE;
    }
}
