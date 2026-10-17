package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A Hollow's bone-white mask, drawn over the body model from a texture that is clear everywhere but
 * the face, and swapped for a cracked one after a blow to it. There is no mask while one is still
 * forming over a turned Plus's face, and it is gone a quarter of the way through a purification.
 */
public class HollowMaskLayer<T extends Hollow, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** How far through the white dissolve the mask breaks away. */
    private static final float GONE_AT = 0.25F;

    private final ResourceLocation mask;
    private final ResourceLocation cracked;

    public HollowMaskLayer(RenderLayerParent<T, M> parent, HollowKind kind) {
        super(parent);
        this.mask = kind.maskTexture(false);
        this.cracked = kind.maskTexture(true);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T hollow, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (hollow.isInvisible() || hollow.isMaskForming() || hollow.dissolveProgress(partialTick) > GONE_AT) {
            return;
        }
        renderColoredCutoutModel(getParentModel(), hollow.isMaskCracked() ? cracked : mask, poseStack, buffer,
                packedLight, hollow, -1);
    }
}
