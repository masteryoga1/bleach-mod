package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.Shrieker;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Shrieker: a hunched, ape-like body on short legs with long arms, a tall narrow head behind its mask, and two broad
 * bat's wings from its shoulders, a bone spar each with dark membrane behind it, that beat in time with the wingbeats the
 * aware hear. It never pitches its head, so the mask is drawn where a blow finds it. In a swoop the wings sweep back and
 * the arms reach ahead, its jaw open; on the ground the wings hang folded at its sides as it strides. Its tongue is in the
 * mesh, hidden until it has a use. A placeholder until the mod has real art.
 */
public final class ShriekerModel extends HierarchicalModel<Shrieker> {
    private static final float STRIDE = 0.6662F;
    private static final float LEG_SWING = 1.0F;
    private static final float ARM_SWING = 0.6F;
    /** How high the wings ride above level as they beat, and how far either way they beat, in radians. */
    private static final float WING_LIFT = 0.15F;
    private static final float WING_BEAT = 0.7F;
    /** In a swoop: how far back the wings sweep, how much they still beat, and how far forward the arms reach. */
    private static final float SWEEP = 0.9F;
    private static final float SWOOP_BEAT = 0.15F;
    private static final float REACH = -1.4F;
    /** How far forward the arms hang in the air. */
    private static final float HANG = -0.2F;
    /** How far the folded wings hang down at its sides on the ground. */
    private static final float FOLDED = -1.3F;
    /** How far its legs trail behind it in the air. */
    private static final float TRAIL = 0.35F;
    private static final float JAW_OPEN = 0.5F;
    private static final float JAW_REST = 0.08F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ShriekerModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightWing = body.getChild("right_wing");
        this.leftWing = body.getChild("left_wing");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        jaw.getChild("tongue").visible = false;
    }

    /** The mesh on a 128 by 64 texture, standing on the ground at y = 24 like every mob model; 2.2 blocks to the crown. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7.0F, -16.0F, -5.0F, 14.0F, 16.0F, 10.0F), PartPose.offset(0.0F, 15.0F, 0.0F));
        // The head's front, (55, 7) to (63, 17) on the texture, is where the mask is drawn.
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-4.0F, -10.0F, -7.0F, 8.0F, 10.0F, 7.0F), PartPose.offset(0.0F, -16.0F, -3.0F));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(78, 0)
                .addBox(-3.0F, 0.0F, -5.0F, 6.0F, 2.0F, 5.0F), PartPose.offset(0.0F, 0.0F, -2.0F));
        jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(100, 0)
                .addBox(-1.0F, 0.0F, -8.0F, 2.0F, 1.0F, 8.0F), PartPose.offset(0.0F, 0.5F, -4.0F));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 26)
                .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 18.0F, 4.0F), PartPose.offset(-9.0F, -14.0F, -1.0F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 26).mirror()
                .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 18.0F, 4.0F), PartPose.offset(9.0F, -14.0F, -1.0F));
        // Each wing: a bone spar along its leading edge, and a flat membrane a block deep behind it.
        body.addOrReplaceChild("right_wing", CubeListBuilder.create()
                .texOffs(60, 26).addBox(-22.0F, -1.0F, -1.0F, 22.0F, 2.0F, 2.0F)
                .texOffs(0, 48).addBox(-22.0F, 0.0F, 0.0F, 22.0F, 0.0F, 16.0F), PartPose.offset(-4.0F, -13.0F, 5.0F));
        body.addOrReplaceChild("left_wing", CubeListBuilder.create().mirror()
                .texOffs(60, 26).addBox(0.0F, -1.0F, -1.0F, 22.0F, 2.0F, 2.0F)
                .texOffs(0, 48).addBox(0.0F, 0.0F, 0.0F, 22.0F, 0.0F, 16.0F), PartPose.offset(4.0F, -13.0F, 5.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(16, 26)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offset(-4.0F, 15.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 26).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offset(4.0F, 15.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Shrieker shrieker, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        boolean swooping = shrieker.isSwooping();
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        float blow = Mth.sin(attackTime * Mth.PI);
        jaw.xRot = swooping || attackTime > 0.0F ? JAW_OPEN : JAW_REST;
        if (shrieker.onGround() && !swooping) {
            // On the ground: the wings folded, and a stride with the arms swinging against the legs.
            float stride = Mth.cos(limbSwing * STRIDE) * limbSwingAmount;
            rightLeg.xRot = stride * LEG_SWING;
            leftLeg.xRot = -stride * LEG_SWING;
            rightArm.xRot = -stride * ARM_SWING - blow;
            leftArm.xRot = stride * ARM_SWING - blow;
            rightWing.zRot = FOLDED;
            leftWing.zRot = -FOLDED;
            return;
        }
        float lift = WING_LIFT + Mth.cos(wingPhase(shrieker, ageInTicks)) * (swooping ? SWOOP_BEAT : WING_BEAT);
        // The right wing reaches to -x, so a positive turn about z raises its tip; the left wing's is the other way.
        rightWing.zRot = lift;
        leftWing.zRot = -lift;
        if (swooping) {
            // Turned about y, back towards the tail (+z), as a diving bat holds them.
            rightWing.yRot = SWEEP;
            leftWing.yRot = -SWEEP;
        }
        rightLeg.xRot = TRAIL;
        leftLeg.xRot = TRAIL;
        float arms = swooping ? REACH : HANG;
        rightArm.xRot = arms - blow;
        leftArm.xRot = arms - blow;
    }

    /**
     * Where its wings are in their beat, in radians, from the world's time as the server reads it for the sound of the
     * beat (Shrieker#tick), so the two keep time, the wings at the top of the stroke as the beat sounds; two side by
     * side, by their ids, do not beat together.
     */
    private static float wingPhase(Shrieker shrieker, float ageInTicks) {
        long beat = Math.floorMod(shrieker.level().getGameTime() + shrieker.getId() * 3L, (long) Shrieker.FLAP_TICKS);
        float partial = ageInTicks - shrieker.tickCount;
        return (beat + partial) / Shrieker.FLAP_TICKS * Mth.TWO_PI;
    }
}
