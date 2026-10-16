package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.FishboneD;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Fishbone D: a hunched giant with a fish-skull head, a fin down its back and long arms that it
 * brings down on its prey with both hands, jaws open. A placeholder until the mod has real art.
 */
public final class FishboneDModel extends HierarchicalModel<FishboneD> {
    private static final float STRIDE = 0.6662F;
    private static final float LEG_SWING = 1.2F;
    /** The arms swing less than the legs. */
    private static final float ARM_SWING = 0.8F;
    /** How far forward both arms come at the height of a blow, in radians. */
    private static final float SMASH = 1.6F;
    private static final float JAW_OPEN = 0.4F;
    private static final float JAW_REST = 0.05F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public FishboneDModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /** The mesh on a 128 by 64 texture, standing on the ground at y = 24 like every mob model. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8.0F, -16.0F, -5.0F, 16.0F, 16.0F, 10.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(52, 0)
                .addBox(-5.0F, -8.0F, -10.0F, 10.0F, 8.0F, 10.0F), PartPose.offset(0.0F, -14.0F, -4.0F));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(92, 0)
                .addBox(-4.0F, 0.0F, -9.0F, 8.0F, 3.0F, 9.0F), PartPose.ZERO);
        body.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(52, 18)
                .addBox(-1.0F, -10.0F, 0.0F, 2.0F, 10.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, -0.35F, 0.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 26)
                .addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F), PartPose.offset(-4.5F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 26).mirror()
                .addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F), PartPose.offset(4.5F, 12.0F, 0.0F));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(24, 26)
                .addBox(-2.5F, -2.0F, -3.0F, 5.0F, 22.0F, 6.0F), PartPose.offset(-10.5F, -14.0F, 0.0F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(24, 26).mirror()
                .addBox(-2.5F, -2.0F, -3.0F, 5.0F, 22.0F, 6.0F), PartPose.offset(10.5F, -14.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(FishboneD fishbone, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float stride = Mth.cos(limbSwing * STRIDE) * limbSwingAmount;
        rightLeg.xRot = stride * LEG_SWING;
        leftLeg.xRot = -stride * LEG_SWING;
        // The arms swing against the legs, and both come down together in a blow.
        float smash = Mth.sin(attackTime * Mth.PI) * SMASH;
        rightArm.xRot = -stride * ARM_SWING - smash;
        leftArm.xRot = stride * ARM_SWING - smash;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        jaw.xRot = attackTime > 0.0F ? JAW_OPEN : JAW_REST;
    }
}
