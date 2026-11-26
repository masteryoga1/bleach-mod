package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.ZangetsuSpirit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Zangetsu drawn out, on a player's model with wide arms in the placeholder skin, his long coat drawn on its jacket and
 * trouser layers (see the art script). As the inner Hollow's: a mob's model is never told how its arms are posed, so
 * they are posed here from what he holds. Those blind to spirits never reach this (the client's mixins skip them).
 */
public final class ZangetsuSpiritRenderer extends HumanoidMobRenderer<ZangetsuSpirit, PlayerModel<ZangetsuSpirit>> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/zangetsu_spirit.png");
    private static int drawn;

    public ZangetsuSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(ZangetsuSpirit spirit, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        HumanoidModel.ArmPose main = spirit.getMainHandItem().isEmpty()
                ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        boolean right = spirit.getMainArm() == HumanoidArm.RIGHT;
        getModel().rightArmPose = right ? main : HumanoidModel.ArmPose.EMPTY;
        getModel().leftArmPose = right ? HumanoidModel.ArmPose.EMPTY : main;
        super.render(spirit, yaw, partialTick, poseStack, buffer, packedLight);
        drawn++;
    }

    @Override
    public ResourceLocation getTextureLocation(ZangetsuSpirit spirit) {
        return TEXTURE;
    }

    /** How many times Zangetsu has been drawn since the game started: the smoke test reads it. */
    public static int drawnFrames() {
        return drawn;
    }
}
