package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.Adjuchas;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * An Adjuchas: a long, low beast on four legs with a beast's skull for a mask and a hole through its
 * flank. Each has one of three forms, horns, a row of spikes down its back or a long tail, chosen by
 * its UUID (see {@link Adjuchas#variant()}), so every client draws the same one. It trots with its
 * tail swaying, snaps its head down to bite and lifts its face as a Cero gathers. A placeholder until
 * the mod has real art.
 */
public final class AdjuchasModel extends HierarchicalModel<Adjuchas> {
    /** The wolf's stride. */
    private static final float STRIDE = 0.6662F;
    private static final float LEG_SWING = 1.4F;
    /** How far the head snaps down at the height of a bite, in radians. */
    private static final float BITE = 0.4F;
    /** How far the face tips up while a Cero gathers, in radians. */
    private static final float CERO_TILT = 0.25F;
    /** How far the tail sways from side to side, in radians, and how fast. */
    private static final float TAIL_SWAY = 0.15F;
    private static final float TAIL_SWAY_SPEED = 0.15F;

    private final ModelPart root;
    private final ModelPart spikes;
    private final ModelPart head;
    private final ModelPart horns;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart tail;
    private final ModelPart tailTip;

    public AdjuchasModel(ModelPart root) {
        this.root = root;
        this.spikes = root.getChild("body").getChild("spikes");
        this.head = root.getChild("head");
        this.horns = head.getChild("horns");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftHindLeg = root.getChild("left_hind_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
        this.tail = root.getChild("tail");
        this.tailTip = tail.getChild("tail_tip");
    }

    /**
     * The mesh on a 128 by 64 texture, standing on the ground at y = 24 like every mob model. Each box's
     * net is laid out where {@code tools/art/make_placeholders.py} paints it, and the mask texture covers
     * the front of the head, the horns and the spikes on the same layout. The tail starts raised behind.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5.0F, -4.0F, -9.0F, 10.0F, 9.0F, 18.0F), PartPose.offset(0.0F, 10.0F, 0.0F));
        body.addOrReplaceChild("spikes", CubeListBuilder.create().texOffs(86, 0)
                .addBox(-0.5F, -8.0F, -6.0F, 1.0F, 4.0F, 1.0F)
                .addBox(-0.5F, -8.0F, -2.0F, 1.0F, 4.0F, 1.0F)
                .addBox(-0.5F, -8.0F, 2.0F, 1.0F, 4.0F, 1.0F)
                .addBox(-0.5F, -8.0F, 6.0F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(56, 0)
                .addBox(-3.5F, -3.5F, -6.0F, 7.0F, 7.0F, 6.0F), PartPose.offset(0.0F, 8.0F, -9.0F));
        head.addOrReplaceChild("horns", CubeListBuilder.create().texOffs(82, 0)
                .addBox(-3.0F, -6.5F, -3.0F, 1.0F, 4.0F, 1.0F)
                .addBox(2.0F, -6.5F, -3.0F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(0, 27)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F), PartPose.offset(-3.5F, 14.0F, 6.5F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(0, 27).mirror()
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F), PartPose.offset(3.5F, 14.0F, 6.5F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(0, 27)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F), PartPose.offset(-3.5F, 14.0F, -6.5F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(0, 27).mirror()
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F), PartPose.offset(3.5F, 14.0F, -6.5F));
        // A positive x rotation lifts a part that runs back towards +z, so the tail rises behind.
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(12, 27)
                .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 7.0F, 9.0F, 0.6F, 0.0F, 0.0F));
        tail.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(40, 27)
                .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 12.0F, 0.3F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Adjuchas adjuchas, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        // Diagonal pairs swing together, as a trotting beast's legs do.
        float trot = limbSwing * STRIDE;
        float swing = Mth.cos(trot) * LEG_SWING * limbSwingAmount;
        float opposite = Mth.cos(trot + Mth.PI) * LEG_SWING * limbSwingAmount;
        rightHindLeg.xRot = swing;
        leftFrontLeg.xRot = swing;
        leftHindLeg.xRot = opposite;
        rightFrontLeg.xRot = opposite;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD + Mth.sin(attackTime * Mth.PI) * BITE
                - (adjuchas.isChargingCero() ? CERO_TILT : 0.0F);
        tail.yRot = Mth.cos(ageInTicks * TAIL_SWAY_SPEED) * TAIL_SWAY;
        // Resetting the pose leaves visibility alone, so the form is set every frame. The mask layer
        // draws this same posed model, so what is hidden here is hidden under the mask too.
        int variant = adjuchas.variant();
        horns.visible = variant == Adjuchas.VARIANT_HORNS;
        spikes.visible = variant == Adjuchas.VARIANT_SPIKES;
        tailTip.visible = variant == Adjuchas.VARIANT_TAIL;
    }
}
