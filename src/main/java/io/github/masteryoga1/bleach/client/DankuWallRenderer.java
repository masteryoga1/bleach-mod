package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.entity.DankuWall;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Bakudō #81 Dankū: a plain pane of light in the spell's colour, as wide and tall as the wall's box, gently pulsing,
 * with a brighter border at its edge. Untextured and added to what lies behind it, so it brightens the scene and never
 * hides it, and it writes no depth. It fades in as it rises and out over its last half-second; one that shatters or
 * loses its caster is simply gone. The unaware never see it, as with every spirit (see {@code mixin.client}).
 */
public final class DankuWallRenderer extends EntityRenderer<DankuWall> {
    /** The pane's alpha, and how much it pulses either way, how fast. */
    private static final float SHEET_ALPHA = 0.22F;
    private static final float PULSE = 0.04F;
    private static final float PULSE_SPEED = 0.15F;
    /** The border's alpha and width, in blocks. */
    private static final float EDGE_ALPHA = 0.55F;
    private static final float EDGE = 0.06F;
    /** Ticks it fades in over as it rises, and out over before its end. */
    private static final float FADE_IN = 4.0F;
    private static final float FADE_OUT = 10.0F;
    /** A rectangle's corners, bottom left round to top left, as two triangles each wound both ways (the type culls). */
    private static final int[] CORNERS = {0, 1, 2, 0, 2, 3, 0, 2, 1, 0, 3, 2};

    public DankuWallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DankuWall wall, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        float age = wall.tickCount + partialTick;
        // The long difference first, so a world's great age loses no precision in the float.
        float left = (wall.endsAt() - wall.level().getGameTime()) - partialTick;
        float fade = Mth.clamp(Math.min(age / FADE_IN, left / FADE_OUT), 0.0F, 1.0F);
        if (fade > 0.0F) {
            float w = wall.halfWidth();
            float h = wall.height();
            int rgb = wall.colour() & 0xFFFFFF;
            poseStack.pushPose();
            // Drawn across local x, at z = 0: turned for a wall facing east or west, so it runs north to south.
            if (wall.facing().getAxis() == Direction.Axis.X) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer out = buffer.getBuffer(RenderType.dragonRays());
            rect(out, pose, -w, 0.0F, w, h, argb((SHEET_ALPHA + PULSE * Mth.sin(age * PULSE_SPEED)) * fade, rgb));
            int edge = argb(EDGE_ALPHA * fade, rgb);
            rect(out, pose, -w, 0.0F, -w + EDGE, h, edge);
            rect(out, pose, w - EDGE, 0.0F, w, h, edge);
            rect(out, pose, -w, h - EDGE, w, h, edge);
            rect(out, pose, -w, 0.0F, w, EDGE, edge);
            poseStack.popPose();
        }
        super.render(wall, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** One flat rectangle in the plane z = 0, seen from both sides. */
    static void rect(VertexConsumer out, PoseStack.Pose pose, float x0, float y0, float x1, float y1, int argb) {
        float[] xs = {x0, x1, x1, x0};
        float[] ys = {y0, y0, y1, y1};
        for (int corner : CORNERS) {
            out.addVertex(pose, xs[corner], ys[corner], 0.0F).setColor(argb);
        }
    }

    /** A colour with this alpha, from 0 to 1. */
    static int argb(float alpha, int rgb) {
        return FastColor.ARGB32.color(Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F), rgb);
    }

    /** Never read: the type is untextured. */
    @Override
    public ResourceLocation getTextureLocation(DankuWall wall) {
        return CeroBeamRenderer.BEAM_TEXTURE;
    }
}
