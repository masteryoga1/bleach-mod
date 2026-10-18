package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The Sanrei Glove on a Quincy's right hand while it is worn, tinted gold once it is mastered.
 * Everyone sees it, aware or not: it is cloth, not reishi. A slim arm wears the same glove as a wide
 * one, so it sits a little loose on the outside of the wrist.
 */
public final class SanreiGloveLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/sanrei_glove.png");

    private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final int PLAIN = 0xFFFFFFFF;
    private static final int MASTERED = 0xFFF0D080;

    private final SanreiGloveModel model;

    public SanreiGloveLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                            EntityModelSet models) {
        super(parent);
        this.model = new SanreiGloveModel(models.bakeLayer(BleachModelLayers.SANREI_GLOVE));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        int look = ClientSouls.appearance(player).quincyLook();
        if ((look & QuincyArtsLogic.LOOK_GLOVE) == 0) {
            return;
        }
        // Layers run after the body is posed, so the arm already swings, aims the bow or crouches.
        ModelPart glove = model.glove();
        glove.copyFrom(getParentModel().rightArm);
        glove.render(poseStack, buffer.getBuffer(TYPE), packedLight, OverlayTexture.NO_OVERLAY,
                (look & QuincyArtsLogic.LOOK_GLOVE_MASTERED) != 0 ? MASTERED : PLAIN);
    }
}
