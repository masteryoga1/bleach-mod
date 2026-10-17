package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.HoundHollow;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * The Hound Hollow: the vanilla wolf's mesh, always on the prowl with its tail raised. It needs a
 * model class of its own because the vanilla one only animates wolves.
 */
public final class HoundHollowModel extends HierarchicalModel<HoundHollow> {
    /** How far the tail is lifted, in radians from hanging straight down; the wolf rests at a fifth of pi. */
    private static final float TAIL_RAISED = 1.1F;
    /** The wolf's stride. */
    private static final float STRIDE = 0.6662F;
    private static final float LEG_SWING = 1.4F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart tail;

    public HoundHollowModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftHindLeg = root.getChild("left_hind_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
        this.tail = root.getChild("tail");
    }

    /** The wolf's mesh on its 64 by 32 texture. */
    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(WolfModel.createMeshDefinition(CubeDeformation.NONE), 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(HoundHollow hound, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        // Diagonal pairs swing together, as a trotting dog's legs do.
        float swing = Mth.cos(limbSwing * STRIDE) * LEG_SWING * limbSwingAmount;
        float opposite = Mth.cos(limbSwing * STRIDE + Mth.PI) * LEG_SWING * limbSwingAmount;
        rightHindLeg.xRot = swing;
        leftFrontLeg.xRot = swing;
        leftHindLeg.xRot = opposite;
        rightFrontLeg.xRot = opposite;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        tail.xRot = TAIL_RAISED;
    }
}
