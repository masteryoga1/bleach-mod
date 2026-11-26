package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.zanpakuto.BankaiLook;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The coat a soul wears in its Bankai, over its garb and any armour: Tensa Zangetsu's long black coat while its owner
 * holds it. It is drawn by what the player's own client and every other one already know, the blade in its main hand
 * ({@link BankaiLook#worn}), so no payload carries it, nobody else holding the blade wears it, and it comes off with
 * the frame the blade leaves the hand. Only souls who can see spirits see it, and, as with every layer, never the
 * wearer itself in first person.
 */
public final class BankaiCoatLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TENSA_ZANGETSU_COAT = BleachMod.id("textures/entity/tensa_zangetsu_coat.png");

    /** Coats drawn since the game started, one for each wearer in each frame, for the smoke test. */
    private static int drawn;

    private final BankaiCoatModel<AbstractClientPlayer> coat;

    public BankaiCoatLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                           EntityModelSet models) {
        super(parent);
        coat = new BankaiCoatModel<>(models.bakeLayer(BleachModelLayers.BANKAI_COAT));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ResourceLocation texture = texture(BankaiLook.worn(player));
        if (texture == null || player.isInvisible()) {
            return;
        }
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || !SoulLogic.seesSpirits(viewer)) {
            return;
        }
        drawn++;
        getParentModel().copyPropertiesTo(coat);
        coat.swingTails();
        coat.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F));
    }

    /** The coat's texture for this look, or null for a look that wears none. */
    @Nullable
    static ResourceLocation texture(BankaiLook look) {
        return switch (look) {
            case TENSA_ZANGETSU -> TENSA_ZANGETSU_COAT;
            case NONE -> null;
        };
    }

    /**
     * How many coats have been drawn since the game started, one for each wearer in each frame: the smoke test reads it
     * to see the coat drawn, and then no longer drawn.
     */
    public static int drawnFrames() {
        return drawn;
    }
}
