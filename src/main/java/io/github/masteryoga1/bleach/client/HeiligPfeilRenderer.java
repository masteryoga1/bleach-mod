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
 * Heilig Pfeil and the arrows of the later arts: drawn as a vanilla arrow, in reishi and as bright
 * in the dark as at noon. A plain Heilig Pfeil is pale and a little bigger the harder it was drawn
 * (a Licht Regen or stream arrow counts as half drawn); a Seele Schneider is a blade of the same
 * size in flight or stuck in the ground; a Letzt Pfeil is two and a half times the size, burning
 * pale gold. The unaware never see any of them, as with every spirit (see
 * {@code EntityRenderDispatcherMixin}). They leave no particles of their own: the arrow is common
 * to every client, and those who cannot see spirits would see them.
 */
public final class HeiligPfeilRenderer<T extends HeiligPfeil> extends ArrowRenderer<T> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/heilig_pfeil.png");
    public static final ResourceLocation SEELE_TEXTURE = BleachMod.id("textures/entity/seele_schneider.png");
    public static final ResourceLocation LETZT_TEXTURE = BleachMod.id("textures/entity/letzt_pfeil.png");

    /** A tap is drawn at this size, and a full draw this much bigger again. */
    private static final float TAP_SCALE = 0.8F;
    private static final float FULL_DRAW_EXTRA_SCALE = 0.4F;
    private static final int FULL_BRIGHT_BLOCK_LIGHT = 15;

    private final ResourceLocation texture;
    private final float baseScale;
    private final float chargeScale;

    /** The plain Heilig Pfeil. */
    public HeiligPfeilRenderer(EntityRendererProvider.Context context) {
        this(context, TEXTURE, TAP_SCALE, FULL_DRAW_EXTRA_SCALE);
    }

    /**
     * An arrow in this texture, drawn {@code baseScale} times a vanilla arrow's size, and
     * {@code chargeScale} times bigger again for a full draw.
     */
    public HeiligPfeilRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float baseScale,
                               float chargeScale) {
        super(context);
        this.texture = texture;
        this.baseScale = baseScale;
        this.chargeScale = chargeScale;
    }

    @Override
    public void render(T arrow, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float scale = baseScale + chargeScale * arrow.charge();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        super.render(arrow, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    /** Reishi gives its own light. */
    @Override
    protected int getBlockLightLevel(T arrow, BlockPos pos) {
        return FULL_BRIGHT_BLOCK_LIGHT;
    }

    @Override
    public ResourceLocation getTextureLocation(T arrow) {
        return texture;
    }
}
