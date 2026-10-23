package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * Draws a living Quincy as a player in the white garb they wear, with the Quincy Cross in hand, which
 * draws as the Heilig Bogen while a shot is drawn. Uses one of the game's default player skins for now.
 * The veins of Blut Vene go over the body for those who can see spirits ({@link QuincyNpcVeneLayer}).
 */
public final class QuincyNpcRenderer<T extends QuincyNpc> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private final ResourceLocation texture;

    public QuincyNpcRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.texture = texture;
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new QuincyNpcVeneLayer<>(this, context.getModelSet()));
    }

    /**
     * Unlike a player's, a mob's model is never told how its arms are posed, so they are set here the
     * way the game sets a player's: both arms raised to the bow while a shot is drawn, otherwise the
     * Cross held out in the main hand.
     */
    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        PlayerModel<T> model = getModel();
        HumanoidModel.ArmPose main = armPose(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose off = main.isTwoHanded()
                ? entity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM
                : armPose(entity, InteractionHand.OFF_HAND);
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = main;
            model.leftArmPose = off;
        } else {
            model.rightArmPose = off;
            model.leftArmPose = main;
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** How the arm holding this hand's item is posed: drawing the bow, holding something, or empty. */
    private static HumanoidModel.ArmPose armPose(QuincyNpc npc, InteractionHand hand) {
        ItemStack stack = npc.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (npc.isUsingItem() && npc.getUsedItemHand() == hand && stack.getUseAnimation() == UseAnim.BOW) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
