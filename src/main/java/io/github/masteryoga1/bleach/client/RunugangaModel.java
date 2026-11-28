package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.Runuganga;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Runuganga: a giant of grey sand on a broad base, a cloth at the waist, a hole through the chest, a head like a
 * sandcastle, thin upper arms and far thicker forearms. Drawn at four times a mob model's size (the kind's look), the
 * mask texture his face. A placeholder until the mod has real art.
 */
public final class RunugangaModel extends HierarchicalModel<Runuganga> {
    /** A slow, heavy stride, how far his arms swing with it (radians), and how far his head looks up or down (degrees). */
    private static final float STRIDE = 0.25F, ARM_SWING = 0.2F, HEAD_PITCH_LIMIT = 20.0F;
    /** How far his arms come up over his head for a blow, in radians; and how far he goes into the sand, in pixels. */
    private static final float RAISE = 2.8F, DEPTH = 48.0F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public RunugangaModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    /** The mesh on a 128 by 64 texture, each box's net where {@code tools/art/make_placeholders.py} paints it. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8.0F, 10.0F, -6.0F, 16.0F, 14.0F, 12.0F), PartPose.ZERO);
        root.addOrReplaceChild("waist", CubeListBuilder.create().texOffs(56, 0)
                .addBox(-8.5F, 7.0F, -6.5F, 17.0F, 4.0F, 13.0F), PartPose.ZERO);
        root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 26)
                .addBox(-7.0F, -10.0F, -5.0F, 14.0F, 18.0F, 10.0F), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 26)
                .addBox(-5.0F, -11.0F, -5.0F, 10.0F, 11.0F, 10.0F)
                .texOffs(116, 0)
                .addBox(-5.0F, -14.0F, -5.0F, 3.0F, 3.0F, 3.0F)
                .addBox(2.0F, -14.0F, -5.0F, 3.0F, 3.0F, 3.0F)
                .addBox(-5.0F, -14.0F, 2.0F, 3.0F, 3.0F, 3.0F)
                .addBox(2.0F, -14.0F, 2.0F, 3.0F, 3.0F, 3.0F), PartPose.offset(0.0F, -10.0F, 0.0F));
        arm(root, "right_arm", -8.5F, false);
        arm(root, "left_arm", 8.5F, true);
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** A thin upper arm from the shoulder, and below it a forearm far thicker, banded at the wrist. */
    private static void arm(PartDefinition root, String name, float x, boolean mirror) {
        PartDefinition arm = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(116, 6).mirror(mirror)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F), PartPose.offset(x, -9.0F, 0.0F));
        arm.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(88, 26).mirror(mirror)
                .addBox(-3.5F, 0.0F, -3.5F, 7.0F, 12.0F, 7.0F)
                .texOffs(48, 47).mirror(mirror)
                .addBox(-4.0F, 10.0F, -4.0F, 8.0F, 2.0F, 8.0F), PartPose.offset(0.0F, 9.0F, 0.0F));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** His arms sway as he strides and rise over his head for a blow; all of him sinks as far as he is under. */
    @Override
    public void setupAnim(Runuganga giant, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float stride = Mth.cos(limbSwing * STRIDE) * limbSwingAmount;
        rightArm.xRot = stride * ARM_SWING;
        leftArm.xRot = -stride * ARM_SWING;
        if (giant.armsUp()) {
            rightArm.xRot = -RAISE;
            leftArm.xRot = -RAISE;
        } else if (attackTime > 0.0F) {
            rightArm.xRot = -RAISE * (1.0F - attackTime);
            leftArm.xRot = rightArm.xRot;
        }
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(headPitch, -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT) * Mth.DEG_TO_RAD;
        root.y += giant.sunk(ageInTicks - giant.tickCount) * DEPTH;
    }
}
