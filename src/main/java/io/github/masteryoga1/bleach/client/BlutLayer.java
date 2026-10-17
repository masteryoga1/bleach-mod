package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Blut Vene: glowing blue veins over a Quincy's arms and chest while reishi runs in them, flaring
 * white for a moment each time a blow lands. Only souls who can see spirits see them. Drawn over the
 * already posed player model from a skin-layout texture that is clear everywhere but the veins.
 * Blut Arterie shows nothing on the body yet.
 */
public final class BlutLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/blut_vene.png");

    private static final RenderType TYPE = RenderType.eyes(TEXTURE);
    private static final int VEINS = 0xFF7FA0D0;
    private static final int VEINS_STRUCK = 0xFFFFFFFF;

    public BlutLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
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
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(TYPE), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, player.hurtTime > 0 ? VEINS_STRUCK : VEINS);
    }
}
