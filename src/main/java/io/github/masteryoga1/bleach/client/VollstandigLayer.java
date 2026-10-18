package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
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
 * Quincy: Vollständig's wings and halo (see {@link VollstandigModel}), as bright in the dark as at
 * noon and seen from both sides. Only souls who can see spirits see them, since they are reishi.
 */
public final class VollstandigLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/vollstandig.png");

    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    private final VollstandigModel model;

    public VollstandigLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                            EntityModelSet models) {
        super(parent);
        this.model = new VollstandigModel(models.bakeLayer(BleachModelLayers.VOLLSTANDIG));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int look = ClientSouls.appearance(player).quincyLook();
        if (player.isInvisible() || (look & QuincyArtsLogic.LOOK_VOLL) == 0) {
            return;
        }
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || !SoulLogic.seesSpirits(viewer)) {
            return;
        }
        model.setup(getParentModel(), ageInTicks, (look & QuincyArtsLogic.LOOK_SKLAVEREI) != 0,
                (look & QuincyArtsLogic.LOOK_HALO_BROKEN) != 0);
        model.renderToBuffer(poseStack, buffer.getBuffer(TYPE), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
    }
}
