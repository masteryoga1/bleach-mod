package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Draws a Plus as the villager it was, in the clothes of its biome and trade, with its Chain of Fate
 * hanging from its chest. A Plus that is passing on fades to a pale ghost of itself as it rises or
 * sinks away.
 */
public final class PlusRenderer extends MobRenderer<PlusSpirit, VillagerModel<PlusSpirit>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    /** Villagers are drawn a little smaller than their model. A baby's half size comes from its scale. */
    private static final float VILLAGER_SCALE = 0.9375F;
    /** How white a departing Plus turns by the time it is gone. */
    private static final float DEPARTING_WHITE = 0.8F;

    public PlusRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        addLayer(new PlusProfessionLayer(this, context.getResourceManager()));
        addLayer(new PlusChainLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(PlusSpirit plus) {
        return TEXTURE;
    }

    @Override
    protected void scale(PlusSpirit plus, PoseStack poseStack, float partialTick) {
        poseStack.scale(VILLAGER_SCALE, VILLAGER_SCALE, VILLAGER_SCALE);
    }

    /** A departing Plus is drawn the way vanilla draws an invisible creature to its team: a faint ghost. */
    @Override
    protected boolean isBodyVisible(PlusSpirit plus) {
        return super.isBodyVisible(plus) && !plus.isDeparting();
    }

    @Override
    protected float getWhiteOverlayProgress(PlusSpirit plus, float partialTick) {
        return plus.isDeparting() ? DEPARTING_WHITE * plus.departProgress(partialTick) : 0.0F;
    }

    /** The villager's biome and trade clothes, left off while the Plus departs so only the ghost shows. */
    static final class PlusProfessionLayer extends VillagerProfessionLayer<PlusSpirit, VillagerModel<PlusSpirit>> {
        PlusProfessionLayer(RenderLayerParent<PlusSpirit, VillagerModel<PlusSpirit>> parent,
                            ResourceManager resourceManager) {
            super(parent, resourceManager, "villager");
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, PlusSpirit plus,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!plus.isDeparting()) {
                super.render(poseStack, buffer, packedLight, plus, limbSwing, limbSwingAmount, partialTick,
                        ageInTicks, netHeadYaw, headPitch);
            }
        }
    }
}
