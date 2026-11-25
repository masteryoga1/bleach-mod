package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Grand Fisher's lure: a small crying girl, a little larger than a baby's proportions would make her, and the stalk
 * that holds her, strung in segments from her body's brow to the top of her head with a little sag. Cut off, she lies
 * limp on her side, her stalk drawing back into the brow, and she whitens as she fades. The stalk is drawn only while
 * her body is known to this client and not hidden from it; she herself is culled for the unaware before this is reached.
 */
public final class GrandFisherLureRenderer extends HumanoidMobRenderer<GrandFisherLure, GrandFisherLureModel> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/grand_fisher_lure.png");
    private static final RenderType STALK =
            RenderType.entityCutoutNoCull(BleachMod.id("textures/entity/grand_fisher_stalk.png"));
    /** How much larger than a baby's model she is drawn. */
    private static final float GIRL_SCALE = 1.2F;
    private static final int SEGMENTS = 12;
    /** Half the stalk's thickness, how far its segments overlap so no gap shows at a bend, and its sag for its length. */
    private static final float HALF_THICKNESS = 0.05F;
    private static final float OVERLAP = 0.02F;
    private static final double SAG = 0.12;

    public GrandFisherLureRenderer(EntityRendererProvider.Context context) {
        super(context, new GrandFisherLureModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.25F);
    }

    @Override
    public void render(GrandFisherLure lure, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        super.render(lure, entityYaw, partialTick, poseStack, buffer, packedLight);
        GrandFisher body = lure.body();
        float withdrawn = lure.withdrawn(partialTick);
        if (body == null || withdrawn >= 1.0F || ClientSouls.isHiddenFromViewer(body)) {
            return;
        }
        // The pose's origin is the lure, where she stands; the cut end draws back along the line to the brow.
        Vec3 origin = lure.getPosition(partialTick);
        Vec3 brow = body.brow(partialTick).subtract(origin);
        Vec3 end = lure.stalkEnd(partialTick).subtract(origin);
        end = end.add(brow.subtract(end).scale(withdrawn));
        double sag = brow.distanceTo(end) * SAG;
        VertexConsumer consumer = buffer.getBuffer(STALK);
        Vec3 from = brow;
        for (int i = 1; i <= SEGMENTS; i++) {
            double along = (double) i / SEGMENTS;
            Vec3 to = brow.lerp(end, along).subtract(0.0, sag * 4.0 * along * (1.0 - along), 0.0);
            Vec3 step = to.subtract(from);
            Vec3 middle = from.add(step.scale(0.5));
            poseStack.pushPose();
            poseStack.translate(middle.x, middle.y, middle.z);
            // Local +z along the step, as the lash's segments lie.
            poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(step.x, step.z)));
            poseStack.mulPose(Axis.XP.rotation((float) -Mth.atan2(step.y, step.horizontalDistance())));
            ZabimaruLashRenderer.segment(consumer, poseStack.last(), packedLight, HALF_THICKNESS, HALF_THICKNESS,
                    (float) step.length() * 0.5F + OVERLAP);
            poseStack.popPose();
            from = to;
        }
    }

    @Override
    protected void scale(GrandFisherLure lure, PoseStack poseStack, float partialTick) {
        poseStack.scale(GIRL_SCALE, GIRL_SCALE, GIRL_SCALE);
    }

    /** Cut off, she lies on her side; slain after, she does not turn over again. */
    @Override
    protected void setupRotations(GrandFisherLure lure, PoseStack poseStack, float bob, float yBodyRot, float partialTick,
                                  float scale) {
        super.setupRotations(lure, poseStack, bob, yBodyRot, partialTick, scale);
        if (lure.isSevered()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(super.getFlipDegrees(lure)));
        }
    }

    @Override
    protected float getFlipDegrees(GrandFisherLure lure) {
        return lure.isSevered() ? 0.0F : super.getFlipDegrees(lure);
    }

    @Override
    protected float getWhiteOverlayProgress(GrandFisherLure lure, float partialTick) {
        return lure.fadeProgress(partialTick);
    }

    @Override
    public ResourceLocation getTextureLocation(GrandFisherLure lure) {
        return TEXTURE;
    }
}
