package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Hollow's body over the player's skin: a hole through the chest, front and back, dark forearms with
 * bone plates, and bone claws. Drawn, as Blut Vene's veins are, over the already posed player model from
 * a skin-layout texture that is clear everywhere else, so the skin shows through; but lit as the body is,
 * since it is the body. The model's parts follow the skin's customisation, so the jacket and sleeves are
 * only covered while they show. The first-person arm takes the same texture (see {@link HollowPlayerClient}).
 * A Vasto Lorde, human-sized again, gets bone over that: ribs about the hole, caps on the shoulders and
 * plates down the shins. A Gillian or Adjuchas is not drawn on the player model at all (see
 * {@link MenosPlayerRenderer}).
 */
public final class HollowBodyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/hollow_player.png");
    public static final ResourceLocation VASTO_LORDE_TEXTURE = BleachMod.id("textures/entity/hollow_vasto_lorde.png");

    public HollowBodyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        SoulAppearance appearance = ClientSouls.appearance(player);
        if (player.isInvisible() || appearance.state() != SoulState.HOLLOW) {
            return;
        }
        renderColoredCutoutModel(getParentModel(), TEXTURE, poseStack, buffer, packedLight, player, -1);
        if ((appearance.hollowLook() & HollowPlayerLogic.LOOK_VASTO_LORDE) != 0) {
            renderColoredCutoutModel(getParentModel(), VASTO_LORDE_TEXTURE, poseStack, buffer, packedLight, player, -1);
        }
    }
}
