package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Blut Vene on a living Quincy: the same glowing blue veins a player's {@link BlutLayer} draws, from
 * the same skin-layout texture, flaring white for a moment each time a blow lands. Shown while the
 * Quincy's Vene flag is set, and only to souls who can see spirits, as on a player.
 */
public final class QuincyNpcVeneLayer<T extends QuincyNpc> extends RenderLayer<T, PlayerModel<T>> {
    private static final RenderType TYPE = RenderType.eyes(BlutLayer.TEXTURE);
    private static final int VEINS = 0xFF7FA0D0;
    private static final int VEINS_STRUCK = 0xFFFFFFFF;

    public QuincyNpcVeneLayer(RenderLayerParent<T, PlayerModel<T>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (npc.isInvisible() || !npc.veneShown()) {
            return;
        }
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || !SoulLogic.seesSpirits(viewer)) {
            return;
        }
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(TYPE), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, npc.hurtTime > 0 ? VEINS_STRUCK : VEINS);
    }
}
