package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a Shinigami NPC as a player in the Shihakushō with a blade at the hip, crouched when the NPC
 * is (the wounded Shinigami). Uses one of the game's default player skins for now.
 */
public final class ShinigamiNpcRenderer<T extends ShinigamiNpc> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private static final Vec3 CROUCH_OFFSET = new Vec3(0.0, -0.125, 0.0);

    private final ResourceLocation texture;

    public ShinigamiNpcRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.texture = texture;
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new SayaLayer<>(this, npc -> true));
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        getModel().crouching = entity.isCrouching();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public Vec3 getRenderOffset(T entity, float partialTick) {
        return entity.isCrouching() ? CROUCH_OFFSET : super.getRenderOffset(entity, partialTick);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
