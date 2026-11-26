package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.ZabimaruSpirit;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Zabimaru drawn out: a great baboon on short legs, its long arms down to the ground, its back bent forward under a
 * shaggy mane, and for a tail a snake that curls up over its back to look ahead, upside down, its jaw below. It reads a
 * 128x64 texture: a leg at 0,0, an arm at 0,17, the body at 20,0, the mane at 20,26, the head at 68,0, the muzzle at
 * 98,0, a tail segment at 76,15, the snake's head at 94,15 and its jaw at 98,26.
 */
public final class ZabimaruSpiritModel extends HierarchicalModel<ZabimaruSpirit> {
    /** How many segments the tail has, each bent up by {@link #CURL} from the one before, to the snake's head. */
    private static final int SEGMENTS = 5;
    private static final float CURL = 0.35F;
    /** The body's lean forward, and the snake head's bend on the last segment, so it ends a half turn over, looking ahead. */
    private static final float LEAN = 0.35F, HEAD_BEND = Mth.PI - SEGMENTS * CURL;
    private static final float STRIDE = 0.6662F, SWING = 1.0F, SWAY = 0.12F, SWAY_SPEED = 0.1F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart[] tail = new ModelPart[SEGMENTS];

    public ZabimaruSpiritModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        ModelPart segment = root;
        for (int i = 0; i < SEGMENTS; i++) {
            segment = segment.getChild("tail" + i);
            tail[i] = segment;
        }
    }

    /** The mesh, with the feet at the box's floor and its front towards negative z, as every model has. */
    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 12.0F, 5.0F);
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-4.0F, 12.0F, 4.0F));
        root.addOrReplaceChild("left_leg", leg, PartPose.offset(4.0F, 12.0F, 4.0F));
        CubeListBuilder arm = CubeListBuilder.create().texOffs(0, 17).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 25.0F, 5.0F);
        root.addOrReplaceChild("right_arm", arm, PartPose.offset(-8.0F, -1.0F, -3.0F));
        root.addOrReplaceChild("left_arm", arm, PartPose.offset(8.0F, -1.0F, -3.0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(20, 0)
                        .addBox(-7.0F, -16.0F, -5.0F, 14.0F, 16.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, 12.0F, 2.0F, LEAN, 0.0F, 0.0F));
        body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(20, 26)
                .addBox(-8.0F, -16.0F, -6.0F, 16.0F, 7.0F, 12.0F), PartPose.ZERO);
        PartDefinition face = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(68, 0)
                .addBox(-4.0F, -8.0F, -6.0F, 8.0F, 8.0F, 7.0F), PartPose.offset(0.0F, -3.0F, -4.0F));
        face.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(98, 0)
                .addBox(-2.5F, -4.0F, -10.0F, 5.0F, 4.0F, 4.0F), PartPose.ZERO);
        PartDefinition segment = root;
        for (int i = 0; i < SEGMENTS; i++) {
            PartPose pose = i == 0 ? PartPose.offsetAndRotation(0.0F, 10.0F, 7.0F, CURL, 0.0F, 0.0F)
                    : PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, CURL, 0.0F, 0.0F);
            segment = segment.addOrReplaceChild("tail" + i, CubeListBuilder.create().texOffs(76, 15)
                    .addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 5.0F), pose);
        }
        PartDefinition snake = segment.addOrReplaceChild("snake_head", CubeListBuilder.create().texOffs(94, 15)
                        .addBox(-2.5F, -2.0F, 0.0F, 5.0F, 4.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, HEAD_BEND, 0.0F, 0.0F));
        // Turned a half over, the head's local top is the world's bottom: the jaw hangs there.
        snake.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(98, 26)
                .addBox(-2.0F, -3.0F, 1.0F, 4.0F, 1.0F, 6.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** Its legs and arms swing in pairs as it walks, its head looks about, and the snake sways from side to side. */
    @Override
    public void setupAnim(ZabimaruSpirit zabimaru, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float swing = Mth.cos(limbSwing * STRIDE) * SWING * limbSwingAmount;
        rightLeg.xRot = swing;
        leftArm.xRot = swing;
        leftLeg.xRot = -swing;
        rightArm.xRot = -swing;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        float sway = Mth.sin(ageInTicks * SWAY_SPEED) * SWAY;
        for (ModelPart segment : tail) {
            segment.yRot = sway;
        }
    }
}
