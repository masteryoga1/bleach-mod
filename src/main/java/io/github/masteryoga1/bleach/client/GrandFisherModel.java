package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.GrandFisher;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Grand Fisher: a hunched, fur-covered body on short, thick legs, long arms that hang nearly to the ground, a broad head
 * set low on its shoulders behind its mask, with round ears and the stalk's root on its brow, and a long bare tail. Its
 * hands and feet end in red, bird-like claws. It never pitches its head, so the mask is drawn where a blow finds it. Drawn
 * at twice its size, so each pixel here is an eighth of a block. A placeholder until the mod has real art.
 */
public final class GrandFisherModel extends HierarchicalModel<GrandFisher> {
    private static final float STRIDE = 0.6662F;
    private static final float LEG_SWING = 0.9F;
    private static final float ARM_SWING = 0.5F;
    /** How far its tail droops behind it, and how far either way and how fast it sways, in radians and per tick. */
    private static final float TAIL_DROOP = 0.6F;
    private static final float TAIL_SWAY = 0.2F;
    private static final float TAIL_RATE = 0.08F;
    /** How far its arms come up and forward as it strikes. */
    private static final float SWIPE = 1.6F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart tail;

    public GrandFisherModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.tail = body.getChild("tail");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /**
     * The mesh on a 128 by 64 texture, standing on the ground at y = 24 like every mob model; 30 pixels to the crown, 3.75
     * blocks at twice its size, and the ears and the stalk's root 3 pixels over that.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7.0F, -15.0F, -5.0F, 14.0F, 15.0F, 11.0F), PartPose.offset(0.0F, 16.0F, 1.0F));
        // The head's front, (61, 9) to (73, 19) on the texture, is where the mask is drawn.
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(52, 0)
                .addBox(-6.0F, -9.0F, -9.0F, 12.0F, 10.0F, 9.0F), PartPose.offset(0.0F, -13.0F, -4.0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-1.5F, -3.0F, -0.5F, 3.0F, 3.0F, 1.0F), PartPose.offset(-4.5F, -9.0F, -3.0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(96, 0).mirror()
                .addBox(-1.5F, -3.0F, -0.5F, 3.0F, 3.0F, 1.0F), PartPose.offset(4.5F, -9.0F, -3.0F));
        // The stalk's root, at the front of the crown over the mask (GrandFisher.BROW_UP and BROW_FORWARD).
        head.addOrReplaceChild("stalk_root", CubeListBuilder.create().texOffs(96, 6)
                .addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, -9.0F, -7.0F));
        // Each arm ends in a hand of red claws reaching forward; one net serves both, the left one mirrored.
        body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(0, 28).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 15.0F, 5.0F)
                .texOffs(22, 28).addBox(-2.5F, 14.0F, -4.5F, 5.0F, 4.0F, 2.0F), PartPose.offset(-9.5F, -13.0F, -1.0F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                .texOffs(0, 28).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 15.0F, 5.0F)
                .texOffs(22, 28).addBox(-2.5F, 14.0F, -4.5F, 5.0F, 4.0F, 2.0F), PartPose.offset(9.5F, -13.0F, -1.0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(66, 28)
                .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 16.0F), PartPose.offset(0.0F, -3.0F, 6.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(40, 28).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 8.0F, 6.0F)
                .texOffs(22, 36).addBox(-3.0F, 6.0F, -5.0F, 6.0F, 2.0F, 2.0F), PartPose.offset(-4.5F, 16.0F, 2.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
                .texOffs(40, 28).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 8.0F, 6.0F)
                .texOffs(22, 36).addBox(-3.0F, 6.0F, -5.0F, 6.0F, 2.0F, 2.0F), PartPose.offset(4.5F, 16.0F, 2.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(GrandFisher fisher, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        float stride = Mth.cos(limbSwing * STRIDE) * limbSwingAmount;
        float blow = Mth.sin(attackTime * Mth.PI) * SWIPE;
        rightLeg.xRot = stride * LEG_SWING;
        leftLeg.xRot = -stride * LEG_SWING;
        // The arms swing against the legs, and come up together, claws first, as it strikes.
        rightArm.xRot = -stride * ARM_SWING - blow;
        leftArm.xRot = stride * ARM_SWING - blow;
        // A positive turn about x lifts the tail's tip (+z); a negative one lets it droop towards the ground.
        tail.xRot = -TAIL_DROOP;
        tail.yRot = Mth.sin(ageInTicks * TAIL_RATE) * TAIL_SWAY;
    }
}
