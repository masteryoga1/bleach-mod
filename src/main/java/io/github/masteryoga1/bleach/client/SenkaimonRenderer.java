package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A Senkaimon: two shōji of warm paper lattice side by side across the gate's 3 by 4 blocks, with white light behind
 * them that pours out as they fold aside to the frame. Untextured and added to what lies behind it, writing no depth, as
 * Dankū's wall is. It fades in over a quarter-second and its doors part over the next half, and the same runs in reverse
 * over its last 15 ticks. The unaware never see it, as with every spirit (the spirits tag and {@code mixin.client}).
 */
public final class SenkaimonRenderer extends EntityRenderer<Senkaimon> {
    /** Ticks it fades in over, then the ticks its doors take to fold aside; the same again before its end. */
    private static final float FADE = 5.0F;
    private static final float FOLD = 10.0F;
    /** The white light behind the doors: its alpha while they are shut, how much it gains as they part, and its pulse. */
    private static final int LIGHT_RGB = 0xFFFFFF;
    private static final float LIGHT_ALPHA = 0.12F;
    private static final float LIGHT_OPEN_ALPHA = 0.40F;
    private static final float PULSE = 0.03F;
    private static final float PULSE_SPEED = 0.2F;
    /** The doors' warm paper. */
    private static final int PAPER_RGB = 0xFFF2DC;
    private static final float PAPER_ALPHA = 0.30F;
    /** Each door's panes, across and up, and the unlit bars between them, in blocks. */
    private static final int PANE_COLUMNS = 2;
    private static final int PANE_ROWS = 4;
    private static final float BAR = 0.08F;

    public SenkaimonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Senkaimon gate, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        float age = gate.tickCount + partialTick;
        // The long difference first, so a world's great age loses no precision in the float.
        float left = (gate.endsAt() - gate.level().getGameTime()) - partialTick;
        float shown = Math.min(age, left);
        float fade = Mth.clamp(shown / FADE, 0.0F, 1.0F);
        if (fade > 0.0F) {
            float open = Mth.clamp((shown - FADE) / FOLD, 0.0F, 1.0F);
            float w = Senkaimon.HALF_WIDTH;
            float h = Senkaimon.HEIGHT;
            poseStack.pushPose();
            // Drawn across local x, at z = 0: turned for a gate facing east or west, so it runs north to south.
            if (gate.facing().getAxis() == Direction.Axis.X) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer out = buffer.getBuffer(RenderType.dragonRays());
            float light = LIGHT_ALPHA + LIGHT_OPEN_ALPHA * open + PULSE * Mth.sin(age * PULSE_SPEED);
            DankuWallRenderer.rect(out, pose, -w, 0.0F, w, h, DankuWallRenderer.argb(light * fade, LIGHT_RGB));
            int paper = DankuWallRenderer.argb(PAPER_ALPHA * fade, PAPER_RGB);
            door(out, pose, -w, -w * open, h, paper);
            door(out, pose, w * open, w, h, paper);
            poseStack.popPose();
        }
        super.render(gate, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** One door from x0 to x1, its panes of paper with the bars between them left unlit; one too narrow draws nothing. */
    private static void door(VertexConsumer out, PoseStack.Pose pose, float x0, float x1, float h, int argb) {
        float paneWidth = (x1 - x0 - BAR * (PANE_COLUMNS - 1)) / PANE_COLUMNS;
        if (paneWidth <= 0.0F) {
            return;
        }
        float paneHeight = (h - BAR * (PANE_ROWS - 1)) / PANE_ROWS;
        for (int column = 0; column < PANE_COLUMNS; column++) {
            float x = x0 + column * (paneWidth + BAR);
            for (int row = 0; row < PANE_ROWS; row++) {
                float y = row * (paneHeight + BAR);
                DankuWallRenderer.rect(out, pose, x, y, x + paneWidth, y + paneHeight, argb);
            }
        }
    }

    /** Never read: the type is untextured. */
    @Override
    public ResourceLocation getTextureLocation(Senkaimon gate) {
        return CeroBeamRenderer.BEAM_TEXTURE;
    }
}
