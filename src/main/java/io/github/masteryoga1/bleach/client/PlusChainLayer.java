package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * A Plus spirit's Chain of Fate, hanging from its chest in front of its folded arms and shorter as it
 * wears away. The same chain a Plus player wears (see {@link SoulLayer}).
 */
public final class PlusChainLayer extends RenderLayer<PlusSpirit, VillagerModel<PlusSpirit>> {
    /**
     * Where the chain hangs from, from the body's pivot at the neck, in blocks: just above the folded
     * arms, and far enough forward to clear their front edge (about 0.41 blocks out).
     */
    private static final float DOWN = 0.14F;
    private static final float FRONT = -0.42F;

    private final ModelPart body;

    public PlusChainLayer(RenderLayerParent<PlusSpirit, VillagerModel<PlusSpirit>> parent) {
        super(parent);
        this.body = parent.getModel().root().getChild("body");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, PlusSpirit plus,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        float fraction = plus.chainFraction();
        if (plus.isInvisible() || plus.isDeparting() || fraction <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, DOWN, FRONT);
        // Model space is upside down; turn it back so the sprites hang the right way up.
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        ChainPieces.render(poseStack, buffer, packedLight, plus.level(), plus.getId(), fraction);
        poseStack.popPose();
    }
}
