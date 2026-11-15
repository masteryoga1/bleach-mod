package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GateKeeper;
import io.github.masteryoga1.bleach.world.GateState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a gatekeeper as a player's figure scaled to its 8 blocks, in its own placeholder skin by its gate, holding its
 * weapons; both arms go up for half a minute after it yields, and it kneels as a sneaking player stoops.
 */
public class GateKeeperRenderer extends HumanoidMobRenderer<GateKeeper, PlayerModel<GateKeeper>> {
    /** How much larger than a player's figure, 1.8 blocks tall, a keeper is drawn. */
    private static final float SCALE = GateKeeper.HEIGHT / 1.8F;
    private static final ResourceLocation[] TEXTURES = GateState.NAMES.stream()
            .map(name -> BleachMod.id("textures/entity/gate_keeper/" + name + ".png")).toArray(ResourceLocation[]::new);

    public GateKeeperRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F * SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(GateKeeper keeper) {
        return TEXTURES[keeper.gate()];
    }

    @Override
    protected void scale(GateKeeper keeper, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    /** A mob's model is told neither how its arms are posed nor when to crouch, so both are set here from synced data. */
    @Override
    public void render(GateKeeper keeper, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        PlayerModel<GateKeeper> model = getModel();
        model.rightArmPose = pose(keeper, !keeper.getMainHandItem().isEmpty());
        model.leftArmPose = pose(keeper, !keeper.getOffhandItem().isEmpty());
        model.crouching = keeper.isCrouching();
        super.render(keeper, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** An arm raised high, as a trident is before it is thrown, or holding what it holds out, or empty. */
    private static HumanoidModel.ArmPose pose(GateKeeper keeper, boolean holding) {
        return keeper.armsUp() ? HumanoidModel.ArmPose.THROW_SPEAR
                : holding ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
    }

    /** Kneeling, it is drawn lower by as much as a sneaking player is, at its size, so its feet stay on the ground. */
    @Override
    public Vec3 getRenderOffset(GateKeeper keeper, float partialTick) {
        return keeper.isCrouching() ? new Vec3(0.0, SCALE * -2.0F / 16.0, 0.0) : super.getRenderOffset(keeper, partialTick);
    }
}
