package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Negación's pillar: a column of pale gold light about a white core, from the ground the light fell on up to the
 * tear its comrade rises into, with its texture running downwards so it reads as light falling from the gate. It
 * is the vanilla beacon beam tinted, as a Cero is (see {@link CeroBeamRenderer}), glows as brightly in the dark as
 * at noon and is seen from inside as well as out. It fades in as it lands, pulses gently while it waits for a
 * player to step in, and fades out once it has ended. A comrade looking out from inside it through its own eyes
 * sees only the outer light, faintly, so the column does not blind it. The entity gives the box it is culled by,
 * which holds the whole column, and the unaware never see it, as with every spirit (see {@code mixin.client}).
 * Its motes are the server's particles; nothing is spawned here, and it casts no shadow.
 */
public final class NegacionLightRenderer extends EntityRenderer<NegacionLight> {
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(CeroBeamRenderer.BEAM_TEXTURE);
    /** A column without a measured top reaches this far above where it stands, in blocks. */
    private static final float DEFAULT_HEIGHT = 12.0F;
    private static final float OUTER_ALPHA = 0.40F;
    /** The white core, as a share of the column's half-width. */
    private static final float CORE_SHARE = 0.55F;
    private static final float CORE_ALPHA = 0.55F;
    private static final int CORE_COLOUR = 0xFFFFFF;
    /** How fast the texture runs down the column, in texture lengths a tick. */
    private static final float SCROLL_PER_TICK = 0.03F;
    /** An offer pulses about this share of its alpha, by this much either way, and this fast. */
    private static final float OFFER_PULSE_MIDDLE = 0.75F;
    private static final float OFFER_PULSE_DEPTH = 0.25F;
    private static final float OFFER_PULSE_SPEED = 0.3F;
    /** The share of the outer light a comrade sees from inside, through its own eyes. */
    private static final float FROM_INSIDE = 0.5F;
    /** Shorter than this, a column has nothing to draw. */
    private static final float MIN_LENGTH = 1.0E-3F;

    public NegacionLightRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(NegacionLight light, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float alpha = alpha(light, partialTick);
        float y = (float) Mth.lerp(partialTick, light.yOld, light.getY());
        float base = Float.isNaN(light.columnBase()) ? y : light.columnBase();
        float top = Float.isNaN(light.columnTop()) ? base + DEFAULT_HEIGHT : light.columnTop();
        float length = top - base;
        if (alpha > 0.0F && length > MIN_LENGTH) {
            float time = light.tickCount + partialTick;
            float scroll = -time * SCROLL_PER_TICK;
            float halfWidth = light.columnHalfWidth();
            boolean fromInside = seenFromInside(light);
            poseStack.pushPose();
            // The entity rises with its comrade, but the column stays standing on the ground it fell on.
            poseStack.translate(0.0F, base - y, 0.0F);
            // Upright, so the column's own pose serves for its normals too.
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer consumer = buffer.getBuffer(TYPE);
            CeroBeamRenderer.tube(consumer, pose, pose, length, halfWidth, scroll, NegacionLight.PALE_GOLD,
                    OUTER_ALPHA * alpha * (fromInside ? FROM_INSIDE : 1.0F));
            if (!fromInside) {
                CeroBeamRenderer.tube(consumer, pose, pose, length, halfWidth * CORE_SHARE, scroll, CORE_COLOUR,
                        CORE_ALPHA * alpha);
            }
            poseStack.popPose();
        }
        super.render(light, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * How strongly the column is drawn: growing over its landing, pulsing while it is offered, and down to
     * nothing over its fade once it has ended.
     */
    private static float alpha(NegacionLight light, float partialTick) {
        float alpha = Mth.clamp((light.tickCount + partialTick) / NegacionLight.LAND_TICKS, 0.0F, 1.0F);
        switch (light.phase()) {
            case OFFERED -> alpha *= OFFER_PULSE_MIDDLE
                    + OFFER_PULSE_DEPTH * Mth.sin((light.tickCount + partialTick) * OFFER_PULSE_SPEED);
            case ENDING -> alpha *= 1.0F - Mth.clamp((light.phaseTicks() + partialTick) / NegacionLight.FADE_TICKS,
                    0.0F, 1.0F);
            default -> {
            }
        }
        return alpha;
    }

    /** Whether the camera looks out through the eyes of the comrade this light holds, in first person. */
    private static boolean seenFromInside(NegacionLight light) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        return camera != null && camera.getVehicle() == light && minecraft.options.getCameraType().isFirstPerson();
    }

    @Override
    public ResourceLocation getTextureLocation(NegacionLight light) {
        return CeroBeamRenderer.BEAM_TEXTURE;
    }
}
