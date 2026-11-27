package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.VastoLorde;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A Vasto Lorde: the humanoid mesh on a 64 by 64 texture, with the bone of each of its three looks
 * ({@link VastoLorde#look}) shown only in that look: plates over the shoulders and the top of the chest, two horns from
 * the brow, or a crest from brow to nape. Their nets lie in the texture's lower half, which the mesh leaves free: the
 * plates are painted in the body's texture, the horns and the crest in the mask's, so they go with the mask, whole or
 * cracked, and break away with it as it is purified. A placeholder until the mod has real art.
 */
public final class VastoLordeModel extends HumanoidModel<VastoLorde> {
    private final ModelPart horns;
    private final ModelPart crest;
    private final ModelPart plate;
    private final ModelPart rightPauldron;
    private final ModelPart leftPauldron;

    public VastoLordeModel(ModelPart root) {
        super(root);
        this.horns = head.getChild("horns");
        this.crest = head.getChild("crest");
        this.plate = body.getChild("plate");
        this.rightPauldron = rightArm.getChild("pauldron");
        this.leftPauldron = leftArm.getChild("pauldron");
    }

    /** The humanoid mesh, standing on the ground at y = 24, with each look's bone added to its parts. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("horns", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-4.0F, -13.0F, -4.0F, 2.0F, 5.0F, 2.0F)
                .texOffs(8, 32).addBox(2.0F, -13.0F, -4.0F, 2.0F, 5.0F, 2.0F), PartPose.ZERO);
        head.addOrReplaceChild("crest", CubeListBuilder.create()
                .texOffs(16, 32).addBox(-0.5F, -11.0F, -4.0F, 1.0F, 3.0F, 8.0F), PartPose.ZERO);
        root.getChild("body").addOrReplaceChild("plate", CubeListBuilder.create()
                .texOffs(36, 32).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 3.0F, 1.0F), PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("pauldron", CubeListBuilder.create()
                .texOffs(0, 48).addBox(-3.5F, -2.5F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("pauldron", CubeListBuilder.create()
                .texOffs(0, 48).mirror().addBox(-1.5F, -2.5F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Before each frame, for the body and the mask's layer alike, which draws this same model: its own look's bone. */
    @Override
    public void prepareMobModel(VastoLorde vasto, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(vasto, limbSwing, limbSwingAmount, partialTick);
        int look = vasto.look();
        boolean plated = look == VastoLorde.PLATED;
        plate.visible = plated;
        rightPauldron.visible = plated;
        leftPauldron.visible = plated;
        horns.visible = look == VastoLorde.HORNED;
        crest.visible = look == VastoLorde.CRESTED;
    }
}
