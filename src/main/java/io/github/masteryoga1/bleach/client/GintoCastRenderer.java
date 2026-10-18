package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * What a Gintō spell leaves standing, drawn from flat pictures on one texture, glowing as brightly
 * in the dark as at noon and seen from both sides: Heizen's pillar of light (two crossed panels the
 * size of the space it strikes, over a cross on the ground) brightening until it strikes, then
 * fading; Wolke's cloud, a disc as wide as it reaches, bobbing a little; Sprenger's ring on the
 * ground among the blades, then a column as it goes off; and Gritz's small spinning cross. The
 * unaware never see any of it, as with every spirit (see {@code EntityRenderDispatcherMixin}).
 */
public final class GintoCastRenderer extends EntityRenderer<GintoCast> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/ginto_cast.png");

    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    // Where each picture lies on the texture: the disc top left, the column strip in the third
    // quarter across (bright at the bottom), and the cross in the top right corner.
    private static final float DISC_U0 = 0.0F;
    private static final float DISC_U1 = 0.5F;
    private static final float DISC_V0 = 0.0F;
    private static final float DISC_V1 = 0.5F;
    private static final float COLUMN_U0 = 0.5F;
    private static final float COLUMN_U1 = 0.75F;
    private static final float CROSS_U0 = 0.75F;
    private static final float CROSS_U1 = 1.0F;
    private static final float CROSS_V0 = 0.0F;
    private static final float CROSS_V1 = 0.25F;

    /** Ground pictures sit this far up, clear of the block below. */
    private static final float GROUND_LIFT = 0.02F;
    /** How faint a pillar or ring is when it first rises; it brightens to full at the fuse. */
    private static final float RISE_FLOOR = 0.2F;
    /** How long a struck Heizen or Sprenger takes to fade, the rest of its life. */
    private static final float STRUCK_FADE_TICKS = 10.0F;
    private static final float WOLKE_LIFT = 0.1F;
    private static final float WOLKE_BOB = 0.05F;
    private static final float WOLKE_BOB_SPEED = 0.1F;
    private static final float WOLKE_FADE_IN_TICKS = 5.0F;
    private static final float WOLKE_FADE_OUT_TICKS = 20.0F;
    /**
     * Gritz's cross: a block across, spinning this many degrees a tick. It needs no lift: the cast
     * stands at the middle of the creature's body (see {@code GintoSpells}).
     */
    private static final float GRITZ_HALF_SIZE = 0.5F;
    private static final float GRITZ_SPIN_DEGREES = 12.0F;
    /** How long a Gritz mark lasts (the cast's own life, which only the server knows), and its fade. */
    private static final float GRITZ_LIFE_TICKS = 20.0F;
    private static final float GRITZ_FADE_TICKS = 10.0F;

    public GintoCastRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GintoCast cast, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = cast.tickCount + partialTick;
        VertexConsumer consumer = buffer.getBuffer(TYPE);
        poseStack.pushPose();
        switch (cast.spell()) {
            case HEIZEN -> renderHeizen(cast, age, poseStack, consumer);
            case WOLKE -> renderWolke(cast, age, poseStack, consumer);
            case SPRENGER -> renderSprenger(cast, age, poseStack, consumer);
            case GRITZ -> renderGritz(age, poseStack, consumer);
        }
        poseStack.popPose();
        super.render(cast, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** A pillar the size of the space Heizen strikes, over a cross of the same width on the ground. */
    private static void renderHeizen(GintoCast cast, float age, PoseStack poseStack, VertexConsumer consumer) {
        int colour = white(fuseAlpha(cast, age));
        if (colour == 0) {
            return;
        }
        float halfWidth = (float) QuincyArtsLogic.HEIZEN_HALF_WIDTH;
        flat(consumer, poseStack.last(), halfWidth, GROUND_LIFT, CROSS_U0, CROSS_V0, CROSS_U1, CROSS_V1, colour);
        column(poseStack, consumer, halfWidth, (float) QuincyArtsLogic.HEIZEN_HEIGHT, colour);
    }

    /** The cloud: a disc as wide as it reaches, just above where it was cast, coming and going softly. */
    private static void renderWolke(GintoCast cast, float age, PoseStack poseStack, VertexConsumer consumer) {
        // Wolke's life is server config, synced to clients, so its end can be seen coming.
        float life = BleachConfig.get(BleachConfig.WOLKE_TICKS);
        float alpha = Mth.clamp(age / WOLKE_FADE_IN_TICKS, 0.0F, 1.0F)
                * Mth.clamp((life - age) / WOLKE_FADE_OUT_TICKS, 0.0F, 1.0F);
        int colour = white(alpha);
        if (colour == 0) {
            return;
        }
        float y = WOLKE_LIFT + WOLKE_BOB * Mth.sin(age * WOLKE_BOB_SPEED);
        flat(consumer, poseStack.last(), cast.radius(), y, DISC_U0, DISC_V0, DISC_U1, DISC_V1, colour);
    }

    /** A ring on the ground through the blades while the fuse burns, then a column over all of it as it goes off. */
    private static void renderSprenger(GintoCast cast, float age, PoseStack poseStack, VertexConsumer consumer) {
        int colour = white(fuseAlpha(cast, age));
        if (colour == 0) {
            return;
        }
        float radius = cast.radius();
        flat(consumer, poseStack.last(), radius, GROUND_LIFT, DISC_U0, DISC_V0, DISC_U1, DISC_V1, colour);
        if (age >= cast.fuse()) {
            column(poseStack, consumer, radius, (float) QuincyArtsLogic.HEIZEN_HEIGHT, colour);
        }
    }

    /** A small cross turning about the bound creature, fading as the mark's short life ends. */
    private static void renderGritz(float age, PoseStack poseStack, VertexConsumer consumer) {
        int colour = white(Mth.clamp((GRITZ_LIFE_TICKS - age) / GRITZ_FADE_TICKS, 0.0F, 1.0F));
        if (colour == 0) {
            return;
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(age * GRITZ_SPIN_DEGREES));
        for (int i = 0; i < 2; i++) {
            upright(consumer, poseStack.last(), GRITZ_HALF_SIZE, -GRITZ_HALF_SIZE, GRITZ_HALF_SIZE,
                    CROSS_U0, CROSS_V0, CROSS_U1, CROSS_V1, colour);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        }
    }

    /**
     * How bright a spell with a fuse is: rising from faint to full as the fuse burns, then fading
     * away once it has struck.
     */
    private static float fuseAlpha(GintoCast cast, float age) {
        float fuse = cast.fuse();
        if (age < fuse) {
            return RISE_FLOOR + (1.0F - RISE_FLOOR) * Mth.clamp(age / fuse, 0.0F, 1.0F);
        }
        return Mth.clamp(1.0F - (age - fuse) / STRUCK_FADE_TICKS, 0.0F, 1.0F);
    }

    /** White at this alpha, or 0 when there is nothing left to see. */
    private static int white(float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return a <= 0 ? 0 : a << 24 | 0xFFFFFF;
    }

    /** Two upright panels crossed at the middle, standing on the ground, in the column strip. */
    private static void column(PoseStack poseStack, VertexConsumer consumer, float halfWidth, float height, int colour) {
        poseStack.pushPose();
        for (int i = 0; i < 2; i++) {
            upright(consumer, poseStack.last(), halfWidth, 0.0F, height, COLUMN_U0, 0.0F, COLUMN_U1, 1.0F, colour);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        }
        poseStack.popPose();
    }

    /** A level square of this half size at height {@code y}. */
    private static void flat(VertexConsumer consumer, PoseStack.Pose pose, float half, float y,
                             float u0, float v0, float u1, float v1, int colour) {
        vertex(consumer, pose, -half, y, -half, u0, v0, colour);
        vertex(consumer, pose, -half, y, half, u0, v1, colour);
        vertex(consumer, pose, half, y, half, u1, v1, colour);
        vertex(consumer, pose, half, y, -half, u1, v0, colour);
    }

    /** An upright panel across x, from {@code bottom} to {@code top}, with the picture's top edge at the top. */
    private static void upright(VertexConsumer consumer, PoseStack.Pose pose, float halfWidth, float bottom, float top,
                                float u0, float v0, float u1, float v1, int colour) {
        vertex(consumer, pose, -halfWidth, bottom, 0.0F, u0, v1, colour);
        vertex(consumer, pose, halfWidth, bottom, 0.0F, u1, v1, colour);
        vertex(consumer, pose, halfWidth, top, 0.0F, u1, v0, colour);
        vertex(consumer, pose, -halfWidth, top, 0.0F, u0, v0, colour);
    }

    /**
     * One corner, full-bright. The emissive shader still shades by the normal, so every corner gets
     * one pointing up, where the world's two lights both fall, as the Getsuga Tenshō's do.
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
    public ResourceLocation getTextureLocation(GintoCast cast) {
        return TEXTURE;
    }
}
