package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import java.util.UUID;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * Draws an Arrancar NPC as a player with wide arms, in the mod's own pale, black-haired placeholder skin, wearing
 * the white uniform as armour (as a player wears it) and holding its sealed blade in the main hand. Its {@link Layer}
 * adds what the creature's soul shows: the hole, the mask remnant and the number tattoo, drawn by the helpers a
 * player's are ({@link SoulLayer}, {@link TattooLayer}). Each comes from the creature's UUID or its synced number,
 * so every viewer sees the same. A jacket hides the hole and a tattoo under it by depth, as on a player.
 */
public class ArrancarNpcRenderer extends HumanoidMobRenderer<ArrancarNpc, PlayerModel<ArrancarNpc>> {
    /** The one skin every Arrancar NPC wears, drawn by the art script: bare skin, black hair and dark eyes. */
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/arrancar.png");

    public ArrancarNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new Layer(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(ArrancarNpc npc) {
        return TEXTURE;
    }

    /**
     * Unlike a player's, a mob's model is never told how its arms are posed, so they are set here: the blade held
     * out in the main hand, the other arm empty. The poses follow the main arm, as the held item and the swing do,
     * since a few NPCs are left-handed. Nor is it told when to crouch: a Número kneeling to an Espada stoops as a
     * sneaking player does, from the pose the server syncs (see {@link #getRenderOffset}).
     */
    @Override
    public void render(ArrancarNpc npc, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        PlayerModel<ArrancarNpc> model = getModel();
        HumanoidModel.ArmPose main = npc.getMainHandItem().isEmpty()
                ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (npc.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = main;
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        } else {
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            model.leftArmPose = main;
        }
        model.crouching = npc.isCrouching();
        super.render(npc, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** A kneeling Número is drawn lower by as much as a sneaking player is, so its feet stay on the ground as it stoops. */
    @Override
    public Vec3 getRenderOffset(ArrancarNpc npc, float partialTick) {
        return npc.isCrouching()
                ? new Vec3(0.0, npc.getScale() * -2.0F / 16.0, 0.0)
                : super.getRenderOffset(npc, partialTick);
    }

    /**
     * What an Arrancar NPC's soul shows on the outside, in this order: its hole at the place its UUID gives, the
     * remnant of its mask in the shape its UUID gives (never cracked, as nothing breaks an NPC's), and the number it
     * is tattooed with, if any, on the spot its UUID gives. Clothing covers the hole and the tattoo by depth.
     */
    static final class Layer extends RenderLayer<ArrancarNpc, PlayerModel<ArrancarNpc>> {
        private final HollowMaskModel mask;

        Layer(RenderLayerParent<ArrancarNpc, PlayerModel<ArrancarNpc>> parent, EntityModelSet models) {
            super(parent);
            this.mask = new HollowMaskModel(models.bakeLayer(BleachModelLayers.HOLLOW_PLAYER_MASK));
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ArrancarNpc npc, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (npc.isInvisible()) {
                return;
            }
            PlayerModel<ArrancarNpc> model = getParentModel();
            UUID id = npc.getUUID();
            int overlay = LivingEntityRenderer.getOverlayCoords(npc, 0.0F);
            SoulLayer.renderHole(model, poseStack, buffer, packedLight, overlay, ArrancarLogic.holePlace(id));
            // Anything worn on the head (a helmet or a skull given by command) sits where the remnant is, as on a player.
            if (npc.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                SoulLayer.renderFragment(mask, model.getHead(), poseStack, buffer, packedLight, overlay,
                        HollowPlayerLogic.maskVariant(id), false);
            }
            int number = npc.tattooNumber();
            if (number > 0) {
                TattooLayer.draw(poseStack, buffer, packedLight, overlay, model, false, ArrancarRank.tattooSpot(id), number,
                        TattooLayer.INK);
            }
        }
    }
}
