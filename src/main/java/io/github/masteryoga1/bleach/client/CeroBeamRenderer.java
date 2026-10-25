package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A Cero. While it gathers, a white-hot orb grows at the caster's mouth inside a pulsing halo of the
 * beam's colour, and motes of light stream in to it from all round, whitening as they come; they are
 * drawn here rather than spawned as particles, because the game's dust cannot be made to fly inwards.
 * Once it fires, a tube of the beam's colour runs from the mouth to where it stopped, around a white-hot
 * core, with its texture streaming outwards and a flare at either end, and it narrows and fades over its
 * last ticks. It is the vanilla beacon beam tinted, glows as brightly in the dark as at noon, and is seen
 * from inside as well as out. The entity gives the box it is culled by, which holds the whole beam, or
 * the orb with its motes, and is drawn from up to 128 blocks away; the unaware never see it, as with
 * every spirit (see {@code mixin.client}).
 */
public final class CeroBeamRenderer extends EntityRenderer<CeroBeam> {
    public static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(BEAM_TEXTURE);

    /** Motes streaming in to a gathering orb, one along each of {@link #MOTE_DIRECTIONS}. */
    private static final int MOTES = 12;
    /** Ticks a mote takes to come in; they are spread evenly through it. */
    private static final float MOTE_PERIOD = 10.0F;
    /** How far out a mote starts, in blocks: this, and this many beam half-widths more. */
    private static final float MOTE_START = 1.2F;
    private static final float MOTE_START_PER_RADIUS = 2.5F;
    /** A mote's half-size, in blocks: this, and this share of the beam's half-width more. */
    private static final float MOTE_SIZE = 0.08F;
    private static final float MOTE_SIZE_PER_RADIUS = 0.06F;
    /** A mote's alpha as it starts out; it is fully bright when it arrives. */
    private static final float MOTE_ALPHA = 0.3F;
    /** The orb's white core at the full charge, as a share of the beam's half-width. */
    private static final float ORB_CORE = 0.6F;
    /** How much bigger than the core its halo is. */
    private static final float ORB_HALO = 1.7F;
    /** The share of its full size the orb starts at; it grows the rest as the charge gathers. */
    private static final float ORB_START = 0.2F;
    private static final float ORB_CORE_ALPHA = 0.9F;
    /** The halo's alpha, and how much of it, and how fast, it pulses. */
    private static final float HALO_ALPHA = 0.45F;
    private static final float HALO_PULSE = 0.15F;
    private static final float HALO_PULSE_SPEED = 0.8F;
    /** How fast the orb's texture churns, in texture lengths a tick. */
    private static final float ORB_CHURN = 0.05F;
    /** The fired beam's white core, as a share of its half-width. */
    private static final float CORE_SHARE = 0.35F;
    /** The share of its half-width the outer tube loses as it fades. */
    private static final float BEAM_NARROWING = 0.4F;
    private static final float BEAM_ALPHA = 0.55F;
    private static final float CORE_ALPHA = 0.9F;
    /** How fast the texture streams out along the beam, in texture lengths a tick. */
    private static final float SCROLL_PER_TICK = 0.5F;
    /** The flares' half-sizes, in beam half-widths: a wide one where it strikes, a smaller one at the mouth. */
    private static final float END_FLARE = 2.5F;
    private static final float MUZZLE_FLARE = 1.8F;
    private static final float FLARE_ALPHA = 0.6F;
    /** Shorter than this, a fired beam has no tube to draw, only its flares. */
    private static final double MIN_LENGTH = 1.0E-3;
    /** Twelve fixed directions spread evenly over a sphere on a golden spiral, for the motes. */
    private static final Vec3[] MOTE_DIRECTIONS = spiral(MOTES);

    public CeroBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CeroBeam beam, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (beam.isFired()) {
            renderBeam(beam, partialTick, poseStack, buffer);
        } else {
            renderCharge(beam, partialTick, poseStack, buffer);
        }
        super.render(beam, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** The gathering orb and the motes coming in to it. */
    private void renderCharge(CeroBeam beam, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        float progress = beam.chargeProgress(partialTick);
        float time = beam.tickCount + partialTick;
        float radius = beam.radius();
        int colour = beam.colour();
        // Drawn at the mouth as it is this frame: the orb only catches up with a turning head on its own tick.
        Vec3 at = Vec3.ZERO;
        LivingEntity owner = beam.owner();
        if (owner != null) {
            at = CeroBeam.muzzleOf(owner, partialTick).subtract(Mth.lerp(partialTick, beam.xOld, beam.getX()),
                    Mth.lerp(partialTick, beam.yOld, beam.getY()), Mth.lerp(partialTick, beam.zOld, beam.getZ()));
        }
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffer.getBuffer(TYPE);
        float core = radius * (ORB_START + (1.0F - ORB_START) * progress) * ORB_CORE;
        float churn = -time * ORB_CHURN;
        cube(consumer, pose, at, core, churn, CeroBeam.HOT_CORE, ORB_CORE_ALPHA);
        float pulse = 1.0F - HALO_PULSE + HALO_PULSE * Mth.sin(time * HALO_PULSE_SPEED);
        cube(consumer, pose, at, core * ORB_HALO, churn, colour, HALO_ALPHA * pulse);

        Quaternionf camera = entityRenderDispatcher.cameraOrientation();
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera);
        float start = MOTE_START + MOTE_START_PER_RADIUS * radius;
        float size = MOTE_SIZE + MOTE_SIZE_PER_RADIUS * radius;
        for (int i = 0; i < MOTES; i++) {
            float come = Mth.frac(time / MOTE_PERIOD + (float) i / MOTES);
            Vec3 mote = at.add(MOTE_DIRECTIONS[i].scale(start * (1.0F - come)));
            flare(consumer, pose, mote, size, right, up, mix(colour, CeroBeam.HOT_CORE, come),
                    MOTE_ALPHA + (1.0F - MOTE_ALPHA) * come);
        }
    }

    /** The fired beam, from the entity (where the mouth was) along its reach, with a flare at either end. */
    private void renderBeam(CeroBeam beam, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        float fade = beam.fade(partialTick);
        if (fade <= 0.0F) {
            return;
        }
        Vec3 reach = beam.reach();
        double length = reach.length();
        float radius = beam.radius();
        int colour = beam.colour();
        VertexConsumer consumer = buffer.getBuffer(TYPE);
        // Lit as if facing the sky whichever way the beam runs, so the normals stay in the unturned pose.
        PoseStack.Pose upright = poseStack.last();
        if (length > MIN_LENGTH) {
            float scroll = -beam.firedAge(partialTick) * SCROLL_PER_TICK;
            poseStack.pushPose();
            // Turned so +y runs along the beam: first about y towards it, then down from straight up.
            poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(reach.x, reach.z)));
            poseStack.mulPose(Axis.XP.rotation((float) Math.acos(Mth.clamp(reach.y / length, -1.0, 1.0))));
            PoseStack.Pose along = poseStack.last();
            tube(consumer, along, upright, (float) length, radius * (1.0F - BEAM_NARROWING + BEAM_NARROWING * fade),
                    scroll, colour, BEAM_ALPHA * fade);
            tube(consumer, along, upright, (float) length, radius * CORE_SHARE, scroll, CeroBeam.HOT_CORE,
                    CORE_ALPHA * fade);
            poseStack.popPose();
        }
        Quaternionf camera = entityRenderDispatcher.cameraOrientation();
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera);
        flare(consumer, upright, Vec3.ZERO, MUZZLE_FLARE * radius * fade, right, up, colour, FLARE_ALPHA * fade);
        flare(consumer, upright, reach, END_FLARE * radius * fade, right, up, colour, FLARE_ALPHA * fade);
    }

    /**
     * A square tube of this half-width from 0 to {@code length} along the pose's +y, its texture wrapped
     * once round each side and repeating along it once every width, starting at {@code scroll}. Its ends
     * are open: the flares cover them.
     */
    private static void tube(VertexConsumer consumer, PoseStack.Pose pose, PoseStack.Pose normal, float length,
                             float halfWidth, float scroll, int rgb, float alpha) {
        float v1 = scroll + length / (2.0F * halfWidth);
        // The corners in turn round the beam; each side runs from one to the next.
        float[] xs = {-halfWidth, halfWidth, halfWidth, -halfWidth};
        float[] zs = {-halfWidth, -halfWidth, halfWidth, halfWidth};
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) % 4;
            vertex(consumer, pose, normal, xs[side], 0.0F, zs[side], 0.0F, scroll, rgb, alpha);
            vertex(consumer, pose, normal, xs[side], length, zs[side], 0.0F, v1, rgb, alpha);
            vertex(consumer, pose, normal, xs[next], length, zs[next], 1.0F, v1, rgb, alpha);
            vertex(consumer, pose, normal, xs[next], 0.0F, zs[next], 1.0F, scroll, rgb, alpha);
        }
    }

    /** A cube of this half-size about {@code centre}, lined up with the world, its texture starting at {@code v0}. */
    private static void cube(VertexConsumer consumer, PoseStack.Pose pose, Vec3 centre, float half, float v0, int rgb,
                             float alpha) {
        float x0 = (float) centre.x - half;
        float x1 = (float) centre.x + half;
        float y0 = (float) centre.y - half;
        float y1 = (float) centre.y + half;
        float z0 = (float) centre.z - half;
        float z1 = (float) centre.z + half;
        float v1 = v0 + 1.0F;
        // Down, up, north, south, west and east, each corner listed round the face.
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        float[] us = {0.0F, 0.0F, 1.0F, 1.0F};
        float[] vs = {v0, v1, v1, v0};
        for (float[] face : faces) {
            for (int corner = 0; corner < 4; corner++) {
                vertex(consumer, pose, pose, face[corner * 3], face[corner * 3 + 1], face[corner * 3 + 2],
                        us[corner], vs[corner], rgb, alpha);
            }
        }
    }

    /** A square of this half-size about {@code centre} that faces the camera, spanned by its right and up. */
    private static void flare(VertexConsumer consumer, PoseStack.Pose pose, Vec3 centre, float half, Vector3f right,
                              Vector3f up, int rgb, float alpha) {
        float rx = right.x() * half;
        float ry = right.y() * half;
        float rz = right.z() * half;
        float ux = up.x() * half;
        float uy = up.y() * half;
        float uz = up.z() * half;
        float x = (float) centre.x;
        float y = (float) centre.y;
        float z = (float) centre.z;
        vertex(consumer, pose, pose, x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, rgb, alpha);
        vertex(consumer, pose, pose, x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, rgb, alpha);
        vertex(consumer, pose, pose, x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, rgb, alpha);
        vertex(consumer, pose, pose, x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, rgb, alpha);
    }

    /**
     * One corner, full-bright, placed by {@code pose}. The emissive shader still shades by the normal, so
     * every corner gets one pointing straight up in {@code normal}, where the world's two lights both fall.
     */
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, PoseStack.Pose normal, float x, float y,
                               float z, float u, float v, int rgb, float alpha) {
        consumer.addVertex(pose, x, y, z)
                .setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal, 0.0F, 1.0F, 0.0F);
    }

    /** The colour {@code share} of the way from {@code from} to {@code to}, each as {@code 0xRRGGBB}. */
    private static int mix(int from, int to, float share) {
        int r = Math.round(Mth.lerp(share, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(share, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(share, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    /** {@code count} unit vectors spread evenly over a sphere, turning by the golden angle from pole to pole. */
    private static Vec3[] spiral(int count) {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        Vec3[] directions = new Vec3[count];
        for (int i = 0; i < count; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / count;
            double ring = Math.sqrt(1.0 - y * y);
            double angle = goldenAngle * i;
            directions[i] = new Vec3(Math.cos(angle) * ring, y, Math.sin(angle) * ring);
        }
        return directions;
    }

    @Override
    public ResourceLocation getTextureLocation(CeroBeam beam) {
        return BEAM_TEXTURE;
    }
}
