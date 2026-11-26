package io.github.masteryoga1.bleach.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * A Bankai's long coat: the wearer's body, arms and legs a little larger than the garb and armour the game draws under
 * it, and its tails, hung from the back of the waist, which swing out behind as the legs stride. It reads a 64x64
 * texture on the armour layout the humanoid mesh has (one net for both arms, one for both legs), clear wherever the
 * coat is not (the head, the open front, the shins), with its tails' net below (see {@link BankaiCoatLayer}). The
 * head is never drawn.
 */
final class BankaiCoatModel<T extends LivingEntity> extends HumanoidModel<T> {
    /**
     * How much larger than the body: over a chestplate's 1.0 and under Blut Vene's shell at 1.25. Kept here, since
     * {@link CubeDeformation} keeps its own to its package.
     */
    private static final float GROWTH = 1.1F;
    private static final String TAILS = "tails";
    /** How far the tails hang out behind standing still, and how much of a leg's stride they swing out more. */
    private static final float TAILS_HANG = 0.08F, TAILS_SWING = 0.6F;

    private final ModelPart tails;

    BankaiCoatModel(ModelPart root) {
        super(root);
        tails = body.getChild(TAILS);
        head.visible = false;
        hat.visible = false;
    }

    /** The mesh: the humanoid's, a little larger, and the tails, 10 wide and 11 long, from the back of the waist. */
    static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(GROWTH), 0.0F);
        PartDefinition body = mesh.getRoot().getChild("body");
        body.addOrReplaceChild(TAILS, CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 11.0F, 1.0F),
                PartPose.offset(0.0F, 11.0F, 2.0F + GROWTH));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Swings the tails out behind by the further-flung leg, once the coat has been posed as the wearer's own model is
     * this frame ({@link HumanoidModel#copyPropertiesTo}).
     */
    void swingTails() {
        tails.xRot = TAILS_HANG + TAILS_SWING * Math.max(Math.abs(rightLeg.xRot), Math.abs(leftLeg.xRot));
    }
}
