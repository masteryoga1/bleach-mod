package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
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
 * Hihiō Zabimaru's serpent ({@link ZabimaruLash#isSerpent}) is drawn so too, as great bone segments, one for each
 * {@link #SERPENT_SPACING} blocks of its length, swaying, small at the hand and largest at its head.
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
    /** Hihiō Zabimaru's serpent, in bone segments. */
    private static final ResourceLocation BONE_TEXTURE = BleachMod.id("textures/entity/hihio_zabimaru_segment.png");
    private static final RenderType BONE = RenderType.entityCutoutNoCull(BONE_TEXTURE);
    /** Blocks of the serpent's length to each segment, and the most segments drawn however long it is. */
    private static final double SERPENT_SPACING = 0.8;
    private static final int SERPENT_MOST = 24;
    /** Half a bone segment's size, as {@link #HALF_WIDTH} and the rest are a blade segment's. */
    private static final float BONE_HALF_WIDTH = 0.3F;
    private static final float BONE_HALF_HEIGHT = 0.24F;
    private static final float BONE_HALF_LENGTH = 0.34F;
    /** How much larger the head is than a segment, and the share of a segment's size the one at the hand starts at. */
    private static final float HEAD_GROWTH = 1.5F;
    private static final float ROOT_SHARE = 0.35F;
    /** How far the serpent sways to either side at its middle, in blocks, and how fast, in radians a tick. */
    private static final float SWAY = 0.35F;
    private static final float SWAY_SPEED = 0.7F;
    /** Ticks the serpent takes to reach out fully, and to draw back. */
    private static final float SERPENT_REACH_TICKS = 4.0F;

    /** Serpents drawn since the game started, one for each in each frame, for the smoke test. */
    private static int serpentsDrawn;

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
        float reachTicks = lash.isSerpent() ? SERPENT_REACH_TICKS : REACH_TICKS;
        float out = Mth.clamp(Math.min(age, lash.lifeTicks() - age) / reachTicks, 0.0F, 1.0F);
        // The pose's origin is the lash itself, where the blow landed.
        Vec3 hand = handPosition(owner, partialTick).subtract(lash.getPosition(partialTick));
        Vec3 reach = hand.scale(-out);
        if (out > 0.0F && reach.lengthSqr() > 1.0E-4) {
            float yaw = (float) Mth.atan2(reach.x, reach.z);
            float pitch = (float) -Mth.atan2(reach.y, reach.horizontalDistance());
            if (lash.isSerpent()) {
                serpent(lash, partialTick, poseStack, buffer.getBuffer(BONE), hand, reach, yaw, pitch);
            } else {
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
        }
        super.render(lash, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * Hihiō Zabimaru's serpent along the line from the hand out to {@code reach}, as far as it has come: a segment for
     * each {@link #SERPENT_SPACING} blocks of the whole line (so they crowd as it comes out and draws in), lit where each
     * is, as the line may run from shade into light, and swaying across the line, most at its middle.
     */
    private static void serpent(ZabimaruLash lash, float partialTick, PoseStack poseStack, VertexConsumer consumer,
                                Vec3 hand, Vec3 reach, float yaw, float pitch) {
        serpentsDrawn++;
        Vec3 origin = lash.getPosition(partialTick);
        float age = lash.age() + partialTick;
        int segments = Mth.clamp(Mth.ceil(hand.length() / SERPENT_SPACING), 2, SERPENT_MOST);
        for (int i = 0; i < segments; i++) {
            float along = (i + 0.5F) / segments;
            Vec3 at = hand.add(reach.scale(along));
            float size = i == segments - 1 ? HEAD_GROWTH : Mth.lerp(along, ROOT_SHARE, 1.0F);
            int light = LevelRenderer.getLightColor(lash.level(), BlockPos.containing(origin.add(at)));
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            poseStack.mulPose(Axis.YP.rotation(yaw));
            poseStack.mulPose(Axis.XP.rotation(pitch));
            poseStack.translate(SWAY * Mth.sin(Mth.PI * along) * Mth.sin(age * SWAY_SPEED + i), 0.0F, 0.0F);
            segment(consumer, poseStack.last(), light, BONE_HALF_WIDTH * size, BONE_HALF_HEIGHT * size,
                    BONE_HALF_LENGTH * size);
            poseStack.popPose();
        }
    }

    /**
     * How many serpents have been drawn since the game started, one for each in each frame: the smoke test reads it to
     * see the serpent drawn on the real client.
     */
    public static int serpentFrames() {
        return serpentsDrawn;
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
