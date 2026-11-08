package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.entity.Kototsu;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The Kōtotsu: a body of violet light as wide and tall as its lane, drawn 24 blocks back from its front face, which is
 * edged brighter and carries a gold eye. Untextured and added to what lies behind it, as Dankū's pane is. The unaware
 * never see it, as with every spirit (see {@code mixin.client}).
 */
public final class KototsuRenderer extends EntityRenderer<Kototsu> {
    /** The body's colour and alpha, and its front edge's alpha and width. */
    private static final int BODY = 0x8E6CF0;
    private static final float BODY_ALPHA = 0.28F, EDGE_ALPHA = 0.6F, EDGE = 0.06F;
    /** The eye's colour and alpha, and its rectangle on the front face. */
    private static final int EYE = 0xFFC83C;
    private static final float EYE_ALPHA = 0.95F, EYE_X = 1.5F, EYE_LOW = 4.5F, EYE_HIGH = 6.5F;
    /** How far before its front the face and the eye are drawn, so they never fight the rock it waits against. */
    private static final float FACE_Z = 0.01F, EYE_Z = 0.03F;
    /** How far inside the lane's walls, floor and ceiling its long faces are drawn, so they never fight them either. */
    private static final float INSET = 0.02F;

    public KototsuRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Kototsu kototsu, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        float w = (float) Kototsu.HALF_WIDTH;
        float h = (float) Kototsu.HEIGHT;
        float length = (float) Kototsu.DRAWN_LENGTH;
        VertexConsumer out = buffer.getBuffer(RenderType.dragonRays());
        int body = DankuWallRenderer.argb(BODY_ALPHA, BODY);
        int edge = DankuWallRenderer.argb(EDGE_ALPHA, BODY);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, FACE_Z);
        PoseStack.Pose face = poseStack.last();
        DankuWallRenderer.rect(out, face, -w, 0.0F, w, h, body);
        DankuWallRenderer.rect(out, face, -w, 0.0F, -w + EDGE, h, edge);
        DankuWallRenderer.rect(out, face, w - EDGE, 0.0F, w, h, edge);
        DankuWallRenderer.rect(out, face, -w, h - EDGE, w, h, edge);
        DankuWallRenderer.rect(out, face, -w, 0.0F, w, EDGE, edge);
        // The eye twice, for its glow.
        poseStack.translate(0.0F, 0.0F, EYE_Z - FACE_Z);
        int eye = DankuWallRenderer.argb(EYE_ALPHA, EYE);
        for (int glow = 0; glow < 2; glow++) {
            DankuWallRenderer.rect(out, poseStack.last(), -EYE_X, EYE_LOW, EYE_X, EYE_HIGH, eye);
        }
        poseStack.popPose();
        // The sides: a quarter turn about +y sends local +x to world -z, so each runs back from the front.
        for (float x : new float[] {-w + INSET, w - INSET}) {
            poseStack.pushPose();
            poseStack.translate(x, 0.0F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            DankuWallRenderer.rect(out, poseStack.last(), 0.0F, 0.0F, length, h, body);
            poseStack.popPose();
        }
        // The bottom and top: a quarter turn about +x the other way sends local +y to world -z.
        for (float y : new float[] {INSET, h - INSET}) {
            poseStack.pushPose();
            poseStack.translate(0.0F, y, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            DankuWallRenderer.rect(out, poseStack.last(), -w, 0.0F, w, length, body);
            poseStack.popPose();
        }
        super.render(kototsu, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** Never read: the type is untextured. */
    @Override
    public ResourceLocation getTextureLocation(Kototsu kototsu) {
        return CeroBeamRenderer.BEAM_TEXTURE;
    }
}
