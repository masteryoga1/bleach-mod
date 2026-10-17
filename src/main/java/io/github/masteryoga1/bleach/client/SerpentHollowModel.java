package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.SerpentHollow;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Serpent Hollow: two coils on the ground and a neck of four segments rearing up to a hooded head.
 * The neck sways while it waits and draws back as it bites. A placeholder until the mod has real art.
 */
public final class SerpentHollowModel extends HierarchicalModel<SerpentHollow> {
    private static final int NECK_SEGMENTS = 4;
    private static final float SEGMENT_LENGTH = 6.0F;
    /** How far each segment nods and sways, in radians, and how fast. */
    private static final float NOD = 0.08F;
    private static final float NOD_SPEED = 0.1F;
    private static final float SWAY = 0.12F;
    private static final float SWAY_SPEED = 0.07F;
    private static final float SWAY_PHASE = 0.8F;
    /** How far each segment draws back at the height of a bite; the four together come to half a radian. */
    private static final float STRIKE = 0.125F;
    /** How fast the lower coil turns as the serpent slides along. */
    private static final float COIL_TURN = 0.3F;

    private final ModelPart root;
    private final ModelPart coil;
    private final ModelPart[] neck = new ModelPart[NECK_SEGMENTS];
    private final ModelPart head;

    public SerpentHollowModel(ModelPart root) {
        this.root = root;
        this.coil = root.getChild("coil0");
        ModelPart part = root.getChild("neck0");
        neck[0] = part;
        for (int i = 1; i < NECK_SEGMENTS; i++) {
            part = part.getChild("neck" + i);
            neck[i] = part;
        }
        this.head = part.getChild("head");
    }

    /** The mesh on a 64 by 64 texture, standing on the ground at y = 24 like every mob model. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("coil0", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7.0F, -3.0F, -7.0F, 14.0F, 3.0F, 14.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("coil1", CubeListBuilder.create().texOffs(0, 17)
                .addBox(-5.0F, -3.0F, -5.0F, 10.0F, 3.0F, 10.0F), PartPose.offset(0.0F, 21.0F, 0.0F));
        // Each neck segment hangs from the top of the one below it.
        PartDefinition neck = root.addOrReplaceChild("neck0", CubeListBuilder.create().texOffs(40, 17)
                .addBox(-2.5F, -SEGMENT_LENGTH, -2.5F, 5.0F, SEGMENT_LENGTH, 5.0F), PartPose.offset(0.0F, 18.0F, 1.0F));
        for (int i = 1; i < NECK_SEGMENTS; i++) {
            neck = neck.addOrReplaceChild("neck" + i, CubeListBuilder.create().texOffs(40, 28)
                    .addBox(-2.0F, -SEGMENT_LENGTH, -2.0F, 4.0F, SEGMENT_LENGTH, 4.0F),
                    PartPose.offset(0.0F, -SEGMENT_LENGTH, 0.0F));
        }
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-3.5F, -5.0F, -7.0F, 7.0F, 5.0F, 8.0F), PartPose.offset(0.0F, -SEGMENT_LENGTH, 0.0F));
        head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(30, 40)
                .addBox(-6.0F, -7.0F, 0.0F, 12.0F, 9.0F, 1.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(SerpentHollow serpent, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        for (int i = 0; i < NECK_SEGMENTS; i++) {
            neck[i].xRot = NOD * Mth.sin(ageInTicks * NOD_SPEED + i) - STRIKE * attackTime;
            neck[i].zRot = SWAY * Mth.sin(ageInTicks * SWAY_SPEED + SWAY_PHASE * i);
        }
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        coil.yRot = limbSwing * COIL_TURN;
    }
}
