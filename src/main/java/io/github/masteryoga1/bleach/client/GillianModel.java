package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.Gillian;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A Gillian: a towering figure in a black robe with long pale hands, a white mask over the whole head
 * with a long pointed nose, and a collar of bone spikes about the neck. It lumbers along with its
 * feet hidden under the hem, swats with its right arm and tips its face up as a Cero gathers at the
 * mouth. It is drawn at six times a mob model's size (the kind's look), so the top of its head is 15
 * blocks up. A placeholder until the mod has real art.
 */
public final class GillianModel extends HierarchicalModel<Gillian> {
    /** A slow, heavy stride: under half a wolf's pace for each block walked. */
    private static final float STRIDE = 0.3F;
    /** How far the arms swing as it walks, in radians at a full stride. */
    private static final float ARM_SWING = 0.25F;
    /** How far the feet slide under the hem, in model pixels at a full stride. */
    private static final float FOOT_SLIDE = 2.0F;
    /** How far the right arm comes up at the height of a swat, in radians. */
    private static final float SWAT = 2.2F;
    /** The head looks no further up or down than this, in degrees, or it would sink into the robe. */
    private static final float HEAD_PITCH_LIMIT = 25.0F;
    /** How far the face tips up while a Cero gathers, in radians. */
    private static final float CERO_TILT = 0.15F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightFoot;
    private final ModelPart leftFoot;

    public GillianModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightFoot = root.getChild("right_foot");
        this.leftFoot = root.getChild("left_foot");
    }

    /**
     * The mesh on a 128 by 64 texture, standing on the ground at y = 24 like every mob model. Each box's
     * net is laid out where {@code tools/art/make_placeholders.py} paints it, and the mask texture covers
     * the head, the nose and the collar on the same layout.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("robe_lower", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5.0F, 4.0F, -4.0F, 10.0F, 18.0F, 8.0F), PartPose.ZERO);
        root.addOrReplaceChild("robe_upper", CubeListBuilder.create().texOffs(36, 0)
                .addBox(-4.5F, -8.0F, -3.5F, 9.0F, 12.0F, 7.0F), PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(68, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, -8.0F, 0.0F));
        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(100, 0)
                .addBox(-1.0F, -1.0F, -7.0F, 2.0F, 2.0F, 7.0F), PartPose.offset(0.0F, -4.0F, -4.0F));
        // Three spikes to the front and three to the back of the neck, and one to each side. The collar
        // stays on the shoulders as the head turns.
        root.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(100, 10)
                .addBox(-3.5F, 0.0F, -7.0F, 1.0F, 1.0F, 3.0F)
                .addBox(-0.5F, 0.0F, -7.0F, 1.0F, 1.0F, 3.0F)
                .addBox(2.5F, 0.0F, -7.0F, 1.0F, 1.0F, 3.0F)
                .addBox(-3.5F, 0.0F, 4.0F, 1.0F, 1.0F, 3.0F)
                .addBox(-0.5F, 0.0F, 4.0F, 1.0F, 1.0F, 3.0F)
                .addBox(2.5F, 0.0F, 4.0F, 1.0F, 1.0F, 3.0F)
                .texOffs(108, 10)
                .addBox(-8.0F, 0.0F, -0.5F, 3.0F, 1.0F, 1.0F)
                .addBox(5.0F, 0.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offset(0.0F, -8.0F, 0.0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 26)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 20.0F, 3.0F), PartPose.offset(-5.5F, -7.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 26).mirror()
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 20.0F, 3.0F), PartPose.offset(5.5F, -7.0F, 0.0F));
        root.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(12, 26)
                .addBox(-1.5F, 0.0F, -3.0F, 3.0F, 2.0F, 5.0F), PartPose.offset(-2.5F, 22.0F, 0.0F));
        root.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(12, 26).mirror()
                .addBox(-1.5F, 0.0F, -3.0F, 3.0F, 2.0F, 5.0F), PartPose.offset(2.5F, 22.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Gillian gillian, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float stride = Mth.cos(limbSwing * STRIDE) * limbSwingAmount;
        rightArm.xRot = stride * ARM_SWING;
        leftArm.xRot = -stride * ARM_SWING;
        // There are no legs to swing: the feet slide back and forth under the hem.
        rightFoot.z = stride * FOOT_SLIDE;
        leftFoot.z = -stride * FOOT_SLIDE;
        // The swat: the right arm comes up in front and down again on the prey.
        rightArm.xRot -= Mth.sin(attackTime * Mth.PI) * SWAT;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(headPitch, -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT) * Mth.DEG_TO_RAD
                - (gillian.isChargingCero() ? CERO_TILT : 0.0F);
    }
}
