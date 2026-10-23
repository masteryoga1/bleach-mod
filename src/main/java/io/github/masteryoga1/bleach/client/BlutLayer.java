package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Blut Vene: glowing blue veins over a Quincy's arms and chest while reishi runs in them, flaring
 * white for a moment each time a blow lands. Only souls who can see spirits see them. Drawn over the
 * already posed player model, or over the armour they wear (see {@link BlutVeins}), from a
 * skin-layout texture that is clear everywhere but the veins. Blut Arterie shows nothing on the body yet.
 */
public final class BlutLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/blut_vene.png");

    private final BlutVeins<AbstractClientPlayer> veins;

    public BlutLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                     EntityModelSet models) {
        super(parent);
        veins = new BlutVeins<>(models);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || ClientSouls.appearance(player).blut() != BlutMode.VENE) {
            return;
        }
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || !SoulLogic.seesSpirits(viewer)) {
            return;
        }
        veins.render(getParentModel(), poseStack, buffer, player);
    }
}
