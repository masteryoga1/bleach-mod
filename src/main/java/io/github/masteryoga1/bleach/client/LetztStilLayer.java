package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
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
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Quincy: Letzt Stil: lines of burning reishi down a Quincy's body and arms while the power burns
 * out, glowing and slowly pulsing. Only souls who can see spirits see them. Drawn, as Blut Vene's
 * veins are, over the already posed player model from a skin-layout texture that is clear
 * everywhere but the lines.
 */
public final class LetztStilLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/letzt_stil.png");

    private static final RenderType TYPE = RenderType.eyes(TEXTURE);
    /** The pulse swings between these two strengths. */
    private static final float PULSE_BASE = 0.75F;
    private static final float PULSE_SWING = 0.25F;
    private static final float PULSE_SPEED = 0.2F;

    public LetztStilLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || (ClientSouls.appearance(player).quincyLook() & QuincyArtsLogic.LOOK_LETZT) == 0) {
            return;
        }
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || !SoulLogic.seesSpirits(viewer)) {
            return;
        }
        float pulse = Mth.clamp(PULSE_BASE + PULSE_SWING * Mth.sin(ageInTicks * PULSE_SPEED), 0.0F, 1.0F);
        int strength = Math.round(pulse * 255.0F);
        // The eyes type adds its colour to what is behind it and ignores alpha, so the pulse dims the
        // colour itself as well.
        int colour = FastColor.ARGB32.color(strength, strength, strength, strength);
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(TYPE), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, colour);
    }
}
