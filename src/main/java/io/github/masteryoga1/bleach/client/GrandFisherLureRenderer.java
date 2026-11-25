package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws Grand Fisher's lure: a small girl, a slim player's body at a child's proportions, as a baby is drawn, and a little
 * larger than those would make her, standing. The stalk that holds her is not drawn; she herself is culled for the
 * unaware before this is reached.
 */
public final class GrandFisherLureRenderer extends HumanoidMobRenderer<GrandFisherLure, PlayerModel<GrandFisherLure>> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/grand_fisher_lure.png");
    /** How much larger than a baby's model she is drawn. */
    private static final float GIRL_SCALE = 1.2F;

    public GrandFisherLureRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.25F);
    }

    @Override
    protected void scale(GrandFisherLure lure, PoseStack poseStack, float partialTick) {
        poseStack.scale(GIRL_SCALE, GIRL_SCALE, GIRL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(GrandFisherLure lure) {
        return TEXTURE;
    }
}
