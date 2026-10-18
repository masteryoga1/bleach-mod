package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Heilig Pfeil: drawn as a vanilla arrow, in pale reishi and as bright in the dark as at noon, a
 * little bigger the harder it was drawn. The unaware never see it, as with every spirit (see
 * {@code EntityRenderDispatcherMixin}). It leaves no particles of its own: the arrow is common to
 * every client, and those who cannot see spirits would see them.
 */
public final class HeiligPfeilRenderer extends ArrowRenderer<HeiligPfeil> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/heilig_pfeil.png");

    /** A tap is drawn at this size, and a full draw this much bigger again. */
    private static final float TAP_SCALE = 0.8F;
    private static final float FULL_DRAW_EXTRA_SCALE = 0.4F;
    private static final int FULL_BRIGHT_BLOCK_LIGHT = 15;

    public HeiligPfeilRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(HeiligPfeil arrow, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float scale = TAP_SCALE + FULL_DRAW_EXTRA_SCALE * arrow.charge();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        super.render(arrow, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    /** Reishi gives its own light. */
    @Override
    protected int getBlockLightLevel(HeiligPfeil arrow, BlockPos pos) {
        return FULL_BRIGHT_BLOCK_LIGHT;
    }

    @Override
    public ResourceLocation getTextureLocation(HeiligPfeil arrow) {
        return TEXTURE;
    }
}
