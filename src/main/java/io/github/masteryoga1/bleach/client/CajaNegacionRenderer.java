package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Caja Negación's cage: a cube whose edges are Negación's pale gold light, the vanilla beacon beam tinted as
 * Negación's pillar is (see {@link NegacionLightRenderer}), glowing as brightly in the dark as at noon. As it closes,
 * its twelve edges draw in on the creature from well outside it, beams rebound inside from face to face, and its
 * faces darken until the creature can hardly be seen. Once it holds, those outside see it shrink away to nothing, the
 * creature gone with it (see {@link ClientSouls#isHiddenFromViewer}); a spectator still sees the creature, with the
 * cage's edges faintly round it; and the creature itself, looking out, sits in the dark, among glowing edges, with a
 * few beams drifting slowly across. When it lets go, its edges burst outward and fade. Every beam's path comes from
 * the cage's id, so every viewer sees the same. The entity gives the box it is culled by, which holds the cage at its
 * largest, and the unaware never see it, as with every spirit (see {@code mixin.client}). A cage with no one in it
 * yet draws nothing, and none casts a shadow.
 */
public final class CajaNegacionRenderer extends EntityRenderer<CajaNegacion> {
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(CeroBeamRenderer.BEAM_TEXTURE);
    /**
     * The faces seen from inside a cage that holds, dark for the whole hold. Unlike the light, these write depth, so
     * what the world draws after the cage (particles, weather, water, clouds, creatures that come into sight later)
     * stays behind them instead of showing through at full strength.
     */
    private static final RenderType INSIDE_FACE_TYPE = RenderType.entityTranslucent(CeroBeamRenderer.BEAM_TEXTURE);
    private static final float EDGE_HALF_WIDTH = 0.035F;
    private static final float BEAM_HALF_WIDTH = 0.02F;
    private static final float EDGE_ALPHA = 0.9F;
    private static final float SPECTATOR_EDGE_ALPHA = 0.6F;
    private static final float BEAM_ALPHA = 0.8F;
    private static final int FACE_RGB = 0x0B0B14;
    private static final float FACE_ALPHA = 0.85F;
    private static final float INSIDE_FACE_ALPHA = 0.92F;
    /** The floor face sits this far above the ground, so it never flickers against the block below. */
    private static final float FLOOR_LIFT = 0.01F;
    /** Beams rebounding inside as it closes, each striking the next face on its path every so many ticks. */
    private static final int BEAMS = 6;
    private static final int BOUNCE_TICKS = 3;
    /** Beams drifting across the dark seen from inside, far more slowly. */
    private static final int DRIFT_BEAMS = 4;
    private static final int DRIFT_TICKS = 12;
    /** The cage closes in from this many times its size, and bursts out to this many as it ends. */
    private static final float START_SCALE = 1.6F;
    private static final float BURST_SCALE = 1.5F;
    /** How fast the texture runs along the light, in texture lengths a tick. */
    private static final float SCROLL_PER_TICK = 0.05F;
    /** How far from a face's middle a beam may strike it, either way, as a share of the edge. */
    private static final float FACE_SPREAD = 0.45F;
    /** Spreads neighbouring seeds apart: the game's random starts out almost alike for seeds next to one another. */
    private static final long SEED_SPREAD = 0x9E3779B97F4A7C15L;
    /** Shorter than this, a stretch of light has nothing to draw. */
    private static final float MIN_LENGTH = 1.0E-3F;
    /** The twelve edges, each a pair of corners numbered by bits: 1 for the +x side, 2 for the top, 4 for +z. */
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    /** Who is looking, which decides what they see: the one held, a spectator, or anyone else. */
    private enum View { INSIDE, SPECTATOR, OUTSIDE }

    /**
     * What is drawn of a cage this frame: its size as a share of its own, how strongly its faces and edges show,
     * whether the faces write depth, and which beams run (from {@code firstBeam}, so the drifting ones never follow
     * the rebounding ones' paths), each striking a face every {@code hopTicks}.
     */
    private record Look(float scale, float faceAlpha, boolean facesWriteDepth, float edgeAlpha, int firstBeam,
                        int beams, int hopTicks) {
        static Look edgesOnly(float scale, float edgeAlpha) {
            return new Look(scale, 0.0F, false, edgeAlpha, 0, 0, 1);
        }
    }

    public CajaNegacionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CajaNegacion caja, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float t = Math.max(0, caja.phaseTicks()) + partialTick;
        Look look = look(caja, view(caja), t);
        if (look != null && caja.size() * look.scale() > MIN_LENGTH) {
            draw(caja, look, t, caja.tickCount + partialTick, poseStack, buffer);
        }
        super.render(caja, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** Whether the camera is the one held in this cage, a spectator's, or anyone else's. */
    private static View view(CajaNegacion caja) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera != null && camera.getVehicle() == caja) {
            return View.INSIDE;
        }
        LocalPlayer player = minecraft.player;
        return player != null && player.isSpectator() ? View.SPECTATOR : View.OUTSIDE;
    }

    /**
     * What this viewer sees of the cage {@code t} ticks into its phase, or null for nothing. Closing, it draws in
     * eased from {@link #START_SCALE} with its beams rebounding and its faces darkening as it goes. Holding, it is a dark
     * room with drifting beams to the one inside, bare faint edges round the creature to a spectator, and to anyone
     * else it shrinks and fades away over {@link CajaNegacion#SHRINK_TICKS}. Ending, its edges burst outward and fade.
     */
    @Nullable
    private static Look look(CajaNegacion caja, View view, float t) {
        return switch (caja.phase()) {
            case CLOSING -> {
                // A cage summoned empty waits unseen: it is closed round no one yet.
                if (caja.held() == null) {
                    yield null;
                }
                float k = Math.min(1.0F, t / CajaNegacion.CLOSE_TICKS);
                float eased = 1.0F - (1.0F - k) * (1.0F - k);
                float faces = (view == View.INSIDE ? INSIDE_FACE_ALPHA : FACE_ALPHA) * k;
                yield new Look(Mth.lerp(eased, START_SCALE, 1.0F), faces, false, EDGE_ALPHA, 0, BEAMS, BOUNCE_TICKS);
            }
            case HOLDING -> switch (view) {
                case INSIDE -> new Look(1.0F, INSIDE_FACE_ALPHA, true, EDGE_ALPHA, BEAMS, DRIFT_BEAMS, DRIFT_TICKS);
                case SPECTATOR -> Look.edgesOnly(1.0F, SPECTATOR_EDGE_ALPHA);
                case OUTSIDE -> {
                    float left = 1.0F - t / CajaNegacion.SHRINK_TICKS;
                    yield left > 0.0F ? new Look(left, FACE_ALPHA * left, false, EDGE_ALPHA * left, 0, 0, 1) : null;
                }
            };
            case ENDING -> {
                float k = Math.min(1.0F, t / CajaNegacion.FADE_TICKS);
                yield k < 1.0F ? Look.edgesOnly(Mth.lerp(k, 1.0F, BURST_SCALE), 1.0F - k) : null;
            }
        };
    }

    /**
     * The cage as this look has it: centred across on the entity and scaled about its middle, so at its own size it
     * stands where it was closed. Faces that write depth go first, so the light inside is drawn over them; the rest
     * share one buffer, sorted by distance, so the near faces darken the beams and far edges behind them.
     */
    private static void draw(CajaNegacion caja, Look look, float t, float time, PoseStack poseStack,
                             MultiBufferSource buffer) {
        float size = caja.size();
        float edge = size * look.scale();
        float half = edge / 2.0F;
        float centreY = size / 2.0F;
        float scroll = time * SCROLL_PER_TICK;
        // Lit as if facing the sky whichever way each stretch runs, so the normals stay in this unturned pose.
        PoseStack.Pose upright = poseStack.last();
        if (look.facesWriteDepth() && look.faceAlpha() > 0.0F) {
            faces(buffer.getBuffer(INSIDE_FACE_TYPE), upright, centreY, half, scroll, look.faceAlpha());
        }
        VertexConsumer consumer = buffer.getBuffer(TYPE);
        if (!look.facesWriteDepth() && look.faceAlpha() > 0.0F) {
            faces(consumer, upright, centreY, half, scroll, look.faceAlpha());
        }
        if (look.edgeAlpha() > 0.0F) {
            // Edges thin as the cage shrinks away, but keep their width as it bursts.
            float halfWidth = EDGE_HALF_WIDTH * Math.min(1.0F, look.scale());
            for (int[] pair : EDGES) {
                Vector3f from = corner(pair[0], centreY, half);
                Vector3f to = corner(pair[1], centreY, half);
                // Each runs on past its corners by its half-width, so the corners meet closed.
                Vector3f past = new Vector3f(to).sub(from).normalize(halfWidth);
                stretch(consumer, poseStack, upright, from.sub(past), to.add(past), halfWidth, scroll, look.edgeAlpha());
            }
        }
        if (look.beams() > 0) {
            float hops = t / look.hopTicks();
            int hop = Mth.floor(hops);
            float along = hops - hop;
            for (int beam = look.firstBeam(); beam < look.firstBeam() + look.beams(); beam++) {
                Vector3f before = facePoint(caja, beam, hop - 1, centreY, edge);
                Vector3f struck = facePoint(caja, beam, hop, centreY, edge);
                Vector3f next = facePoint(caja, beam, hop + 1, centreY, edge);
                // One stretch long, it runs on along its path, bending where it strikes a face.
                stretch(consumer, poseStack, upright, before.lerp(struck, along), struck, BEAM_HALF_WIDTH, scroll,
                        BEAM_ALPHA);
                stretch(consumer, poseStack, upright, struck, new Vector3f(struck).lerp(next, along), BEAM_HALF_WIDTH,
                        scroll, BEAM_ALPHA);
            }
        }
    }

    /** One of the cube's corners, numbered as in {@link #EDGES}. */
    private static Vector3f corner(int corner, float centreY, float half) {
        return new Vector3f((corner & 1) != 0 ? half : -half, centreY + ((corner & 2) != 0 ? half : -half),
                (corner & 4) != 0 ? half : -half);
    }

    /**
     * Where a beam strikes the cage on this hop of its path: a face, then a point on it within {@link #FACE_SPREAD} of
     * its middle, both from the cage's id, the beam and the hop. The hops alternate between the faces on the cube's
     * west, bottom and north sides and those on its east, top and south, so a beam always goes on to another face.
     */
    private static Vector3f facePoint(CajaNegacion caja, int beam, int hop, float centreY, float edge) {
        RandomSource random = RandomSource.create((caja.getId() * 31L + beam * 7919L + hop) * SEED_SPREAD);
        int axis = random.nextInt(3);
        float side = (hop & 1) == 0 ? -0.5F : 0.5F;
        float a = (random.nextFloat() * 2.0F - 1.0F) * FACE_SPREAD;
        float b = (random.nextFloat() * 2.0F - 1.0F) * FACE_SPREAD;
        float x = axis == 0 ? side : a;
        float y = axis == 1 ? side : axis == 0 ? a : b;
        float z = axis == 2 ? side : b;
        return new Vector3f(x * edge, centreY + y * edge, z * edge);
    }

    /** A tube of light from {@code from} to {@code to}: the Cero's tube, turned so its +y runs along the stretch. */
    private static void stretch(VertexConsumer consumer, PoseStack poseStack, PoseStack.Pose upright, Vector3f from,
                                Vector3f to, float halfWidth, float scroll, float alpha) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < MIN_LENGTH || halfWidth <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dx, dy, dz));
        CeroBeamRenderer.tube(consumer, poseStack.last(), upright, length, halfWidth, scroll, NegacionLight.PALE_GOLD,
                alpha);
        poseStack.popPose();
    }

    /**
     * The cube's six faces, dark and seen from either side, about the entity at {@code centreY} with this half-size;
     * the texture runs slowly across them. Each corner gets a normal pointing straight up, as the light's do.
     */
    private static void faces(VertexConsumer consumer, PoseStack.Pose pose, float centreY, float half, float scroll,
                              float alpha) {
        float x0 = -half;
        float x1 = half;
        float y0 = centreY - half;
        float y1 = centreY + half;
        float z0 = -half;
        float z1 = half;
        // The floor stands a hair above the ground the rider stands on, so the two never fight for the same pixels.
        float floor = y0 + FLOOR_LIFT;
        // Down, up, north, south, west and east, each corner listed round the face.
        float[][] faces = {
                {x0, floor, z0, x1, floor, z0, x1, floor, z1, x0, floor, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        float[] us = {0.0F, 0.0F, 1.0F, 1.0F};
        float[] vs = {scroll, scroll + 1.0F, scroll + 1.0F, scroll};
        float r = (FACE_RGB >> 16 & 0xFF) / 255.0F;
        float g = (FACE_RGB >> 8 & 0xFF) / 255.0F;
        float b = (FACE_RGB & 0xFF) / 255.0F;
        for (float[] face : faces) {
            for (int corner = 0; corner < 4; corner++) {
                consumer.addVertex(pose, face[corner * 3], face[corner * 3 + 1], face[corner * 3 + 2])
                        .setColor(r, g, b, alpha)
                        .setUv(us[corner], vs[corner])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CajaNegacion caja) {
        return CeroBeamRenderer.BEAM_TEXTURE;
    }
}
