package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * An Arrancar's number, tattooed on the spot its soul's id gives ({@link ArrancarRank#tattooSpot}), drawn digit by
 * digit from a small atlas just outside the skin, so any armour hides it and a released body does not. The atlas
 * glyphs are white and take their ink from the vertex colour: dark, or pale over the released forearm. The number
 * comes from the look the server sends ({@link ArrancarRank#lookNumber}), so every viewer sees it.
 */
public final class TattooLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Ten white digits, 0 to 9, each three pixels by five and four apart, on a 40 by 8 atlas clear elsewhere. */
    public static final ResourceLocation DIGITS = BleachMod.id("textures/entity/arrancar_digits.png");
    /**
     * How far outside the skin the tattoo sits, in model pixels: above the jacket and sleeve layers (0.25), so
     * they never cover it, and under the inner and outer armour (0.5 and 1.0), so clothing always does.
     */
    public static final float LIFT = 0.28F;
    /** The largest one unit of a glyph may grow, in model pixels: a glyph is 3 units by 5, with 1 between. */
    public static final float MAX_UNIT = 0.6F;
    /** Dark ink, rgb(26, 24, 30), for skin. */
    public static final int INK = 0xFF1A181E;
    /** Pale bone ink, rgb(232, 228, 214), for the released body's dark forearm. */
    public static final int PALE_INK = 0xFFE8E4D6;

    /** Where each glyph lies in the atlas, in its pixels. */
    private static final float ATLAS_WIDTH = 40.0F;
    private static final float ATLAS_HEIGHT = 8.0F;
    private static final int GLYPH_STEP = 4;
    private static final int GLYPH_WIDTH = 3;
    private static final int GLYPH_HEIGHT = 5;
    private static final int GLYPH_TOP = 1;

    /** Whether this renderer's arms are the slim ones, whose outer face is a pixel nearer the shoulder's pivot. */
    private final boolean slim;

    public TattooLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        this.slim = slim;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        SoulAppearance look = ClientSouls.appearance(player);
        if (look.state() != SoulState.ARRANCAR) {
            return;
        }
        int number = ArrancarRank.lookNumber(look.hollowLook());
        if (number <= 0) {
            return;
        }
        ArrancarRank.Spot spot = ArrancarRank.tattooSpot(player.getUUID());
        boolean released = (look.hollowLook() & HollowPlayerLogic.LOOK_RESURRECCION) != 0;
        draw(poseStack, buffer, packedLight, LivingEntityRenderer.getOverlayCoords(player, 0.0F), getParentModel(),
                slim, spot, number, ink(spot, released));
    }

    /**
     * The ink for this spot: {@link #PALE_INK} for the back of the left hand on a released body, which paints the
     * forearm (arm y 4 to 10) a black only a shade off the dark ink; {@link #INK} everywhere else.
     */
    public static int ink(ArrancarRank.Spot spot, boolean released) {
        return released && spot == ArrancarRank.Spot.HAND_LEFT ? PALE_INK : INK;
    }

    /**
     * Draws this number on this spot of a humanoid model in this ink (ARGB; 5h's NPCs call it too, with INK).
     * 1 to 99; anything else draws nothing, and so does a spot on a part the renderer has hidden. The digits are
     * as large as the spot's box lets them be, up to {@link #MAX_UNIT}, and centred in it.
     */
    public static void draw(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay,
                            HumanoidModel<?> model, boolean slim, ArrancarRank.Spot spot, int number, int ink) {
        ModelPart part = spot.face == ArrancarRank.Face.LEFT_ARM_OUTER ? model.leftArm : model.body;
        if (!part.visible || number < 1 || number > 99) {
            return;
        }
        String digits = Integer.toString(number);
        // In glyph units: three for each digit and one between each two.
        int units = GLYPH_STEP * digits.length() - 1;
        float width = spot.a1 - spot.a0;
        float height = spot.y1 - spot.y0;
        float unit = Math.min(MAX_UNIT, Math.min(height / GLYPH_HEIGHT, width / units));
        float start = spot.a0 + (width - unit * units) / 2.0F;
        float top = spot.y0 + (height - unit * GLYPH_HEIGHT) / 2.0F;
        float bottom = top + unit * GLYPH_HEIGHT;
        float v0 = GLYPH_TOP / ATLAS_HEIGHT;
        float v1 = (GLYPH_TOP + GLYPH_HEIGHT) / ATLAS_HEIGHT;

        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(DIGITS));
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            float left = start + GLYPH_STEP * unit * i;
            float right = left + GLYPH_WIDTH * unit;
            float u0 = GLYPH_STEP * digit / ATLAS_WIDTH;
            float u1 = (GLYPH_STEP * digit + GLYPH_WIDTH) / ATLAS_WIDTH;
            // Drawn without culling, so the corner order does not matter.
            vertex(out, pose, spot.face, slim, left, top, u0, v0, ink, packedOverlay, packedLight);
            vertex(out, pose, spot.face, slim, left, bottom, u0, v1, ink, packedOverlay, packedLight);
            vertex(out, pose, spot.face, slim, right, bottom, u1, v1, ink, packedOverlay, packedLight);
            vertex(out, pose, spot.face, slim, right, top, u1, v0, ink, packedOverlay, packedLight);
        }
        poseStack.popPose();
    }

    /**
     * One corner of a glyph, at {@code a} along the face's reading direction and {@code y} down it, in model
     * pixels, placed on the face in the part's own space. Model y runs down as the texture's v does, and the
     * renderer turns the model a half-turn about z, so the front reads along +x (the creature's left), the back
     * along -x, and the left arm's outer face from front to back.
     */
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, ArrancarRank.Face face, boolean slim, float a,
                               float y, float u, float v, int ink, int overlay, int light) {
        float x = switch (face) {
            case BODY_FRONT -> a - 4.0F;
            case BODY_BACK -> 4.0F - a;
            // The arm's box runs from x -1 to 2 (slim) or 3 about its pivot at the shoulder.
            case LEFT_ARM_OUTER -> (slim ? 2.0F : 3.0F) + LIFT;
        };
        float z = switch (face) {
            case BODY_FRONT -> -2.0F - LIFT;
            case BODY_BACK -> 2.0F + LIFT;
            case LEFT_ARM_OUTER -> a - 2.0F;
        };
        float nx = face == ArrancarRank.Face.LEFT_ARM_OUTER ? 1.0F : 0.0F;
        float nz = face == ArrancarRank.Face.BODY_FRONT ? -1.0F : face == ArrancarRank.Face.BODY_BACK ? 1.0F : 0.0F;
        out.addVertex(pose, x / 16.0F, y / 16.0F, z / 16.0F)
                .setColor(ink)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, 0.0F, nz);
    }
}
