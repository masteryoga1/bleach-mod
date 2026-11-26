package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.HellGate;
import io.github.masteryoga1.bleach.entity.Hollow;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Gate of Hell, turned the way it faces and grown to its scale, rising, opening, shutting and crumbling by its own
 * clock ({@link HellGate#rise}, {@link HellGate#openness}, {@link HellGate#crumble}); and, while it comes, the blade from
 * the back of its dark through the middle of its body, which the body is drawn in along. It casts no shadow. The unaware
 * never see it, as with every spirit (see {@code EntityRenderDispatcherMixin}).
 */
public final class HellGateRenderer extends EntityRenderer<HellGate> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/hell_gate.png");
    /** How far past the body's middle the blade's point runs, in blocks at scale 1. */
    private static final double PAST = 1.0;

    private final HellGateModel model;

    public HellGateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HellGateModel(context.bakeLayer(BleachModelLayers.HELL_GATE));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(HellGate gate, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(model.renderType(TEXTURE));
        float scale = gate.scale();
        float size = HellGateModel.SIZE * scale;
        model.pose(gate.rise(partialTick), gate.openness(partialTick), gate.crumble(partialTick));
        // As a mob's model is drawn: turned to its yaw, flipped upright and stood on its feet, at its size.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - gate.getYRot()));
        poseStack.scale(size, size, size);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.gate().render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        float reach = gate.bladeReach(partialTick);
        Hollow body = gate.body();
        if (reach > 0.0F && body != null) {
            Vec3 origin = gate.getPosition(partialTick);
            Vec3 from = gate.bladeFrom(body).subtract(origin);
            Vec3 to = body.getPosition(partialTick).add(0.0, body.getBbHeight() * 0.5, 0.0).subtract(origin);
            Vec3 along = to.subtract(from);
            double length = along.length();
            if (length > 1.0E-3) {
                // The blade's box lies along +z, a block long: turned onto the line, then stretched to it and past.
                poseStack.pushPose();
                poseStack.translate(from.x, from.y, from.z);
                poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(along.x, along.z)));
                poseStack.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(along.y / length, -1.0, 1.0))));
                poseStack.scale(scale, scale, (float) ((length + PAST * scale) * reach));
                model.blade().render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
        super.render(gate, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HellGate gate) {
        return TEXTURE;
    }
}
