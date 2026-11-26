package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.ZabimaruSpirit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Zabimaru drawn out, the baboon and its snake in one model, in the placeholder's red fur and the segment's bone and
 * scales (see the art script). The snake's hit box is not drawn: parts have no renderer. Those blind to spirits never
 * reach this.
 */
public final class ZabimaruSpiritRenderer extends MobRenderer<ZabimaruSpirit, ZabimaruSpiritModel> {
    public static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/zabimaru_spirit.png");
    private static final float SHADOW = 0.9F;
    private static int drawn;

    public ZabimaruSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new ZabimaruSpiritModel(context.bakeLayer(BleachModelLayers.ZABIMARU_SPIRIT)), SHADOW);
    }

    @Override
    public void render(ZabimaruSpirit zabimaru, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        super.render(zabimaru, yaw, partialTick, poseStack, buffer, packedLight);
        drawn++;
    }

    @Override
    public ResourceLocation getTextureLocation(ZabimaruSpirit zabimaru) {
        return TEXTURE;
    }

    /** How many times Zabimaru has been drawn since the game started: the smoke test reads it. */
    public static int drawnFrames() {
        return drawn;
    }
}
