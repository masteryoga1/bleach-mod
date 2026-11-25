package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.ShriekerLeech;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A Shrieker's leech: a swollen, dark body with a round sucker at its front and two small wings on top that whir in
 * flight and lie folded while it clings, the sucker pressed to the back it holds. A placeholder until the mod has real
 * art.
 */
public final class ShriekerLeechModel extends HierarchicalModel<ShriekerLeech> {
    /** How fast its wings whir, and how far either way, in radians; folded, they lie a little down. */
    private static final float WHIR_RATE = 1.3F;
    private static final float WHIR = 0.6F;
    private static final float FOLDED = 0.2F;

    private final ModelPart root;
    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public ShriekerLeechModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.rightWing = body.getChild("right_wing");
        this.leftWing = body.getChild("left_wing");
    }

    /** The mesh on a 32 by 16 texture, its body's middle a quarter of a block over its feet at y = 24. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.5F, -2.0F, -3.5F, 5.0F, 4.0F, 7.0F)
                .texOffs(24, 0).addBox(-1.5F, -1.5F, -4.5F, 3.0F, 3.0F, 1.0F), PartPose.offset(0.0F, 20.0F, 0.0F));
        body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 11)
                .addBox(-5.0F, 0.0F, -2.0F, 5.0F, 0.0F, 4.0F), PartPose.offset(-1.5F, -2.0F, 0.5F));
        body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 11).mirror()
                .addBox(0.0F, 0.0F, -2.0F, 5.0F, 0.0F, 4.0F), PartPose.offset(1.5F, -2.0F, 0.5F));
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(ShriekerLeech leech, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        // The right wing reaches to -x, so a positive turn about z raises its tip; the left wing's is the other way.
        float lift = leech.isClinging() ? -FOLDED : Mth.cos(ageInTicks * WHIR_RATE) * WHIR;
        rightWing.zRot = lift;
        leftWing.zRot = -lift;
    }
}
