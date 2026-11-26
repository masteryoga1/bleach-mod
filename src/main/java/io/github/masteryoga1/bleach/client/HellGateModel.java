package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.HellGate;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Gate of Hell: a deep frame of bone-grey stone, two posts under a lintel, shut by two doors each with a skull on
 * its face; behind them, the dark. The doors swing inward on hinges at the posts. Built at a quarter of its size, so a
 * pixel is a quarter of a block once the renderer scales it up, and its blade is a part of its own, drawn apart from the
 * frame. A placeholder until the mod has real art.
 */
public final class HellGateModel extends HierarchicalModel<HellGate> {
    /** Each pixel of the model, as drawn, is this many of a usual model's. */
    public static final float SIZE = 4.0F;
    /** How far a door swings in when it stands open, in radians: a little short of square, clear of the dark. */
    private static final float DOOR_SWING = 1.4F;
    /** How far below its feet the frame starts before it rises, in its own pixels: all of it. */
    private static final float SUNK = 28.0F;
    /** The least share of its height it keeps as it crumbles. */
    private static final float MIN_HEIGHT = 0.02F;

    private final ModelPart root;
    private final ModelPart gate;
    private final ModelPart leftDoor;
    private final ModelPart rightDoor;
    private final ModelPart blade;

    public HellGateModel(ModelPart root) {
        this.root = root;
        this.gate = root.getChild("gate");
        this.leftDoor = gate.getChild("left_door");
        this.rightDoor = gate.getChild("right_door");
        this.blade = root.getChild("blade");
    }

    /**
     * The mesh on a 128 by 64 texture. The frame stands on its feet at y = 24, 20 pixels across, 28 high and 10 deep,
     * its doorway at -z, the way it faces, and its dark at +z. The blade lies along +z from its hilt end at 0.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition gate = root.addOrReplaceChild("gate", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10.0F, -26.0F, -5.0F, 2.0F, 26.0F, 10.0F)
                .texOffs(0, 0).addBox(8.0F, -26.0F, -5.0F, 2.0F, 26.0F, 10.0F)
                .texOffs(24, 0).addBox(-10.0F, -28.0F, -5.0F, 20.0F, 2.0F, 10.0F)
                .texOffs(42, 12).addBox(-8.0F, -26.0F, 4.0F, 16.0F, 26.0F, 1.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
        gate.addOrReplaceChild("left_door", CubeListBuilder.create()
                .texOffs(24, 12).addBox(0.0F, -26.0F, 0.0F, 8.0F, 26.0F, 1.0F)
                .texOffs(76, 12).addBox(2.5F, -16.0F, -1.0F, 3.0F, 3.0F, 1.0F), PartPose.offset(-8.0F, 0.0F, -5.0F));
        gate.addOrReplaceChild("right_door", CubeListBuilder.create().mirror()
                .texOffs(24, 12).addBox(-8.0F, -26.0F, 0.0F, 8.0F, 26.0F, 1.0F)
                .texOffs(76, 12).addBox(-5.5F, -16.0F, -1.0F, 3.0F, 3.0F, 1.0F), PartPose.offset(8.0F, 0.0F, -5.0F));
        root.addOrReplaceChild("blade", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-8.0F, -1.0F, 0.0F, 16.0F, 2.0F, 16.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** The frame and its doors, without the blade. */
    public ModelPart gate() {
        return gate;
    }

    public ModelPart blade() {
        return blade;
    }

    /** Its pose comes from {@link #pose}, which the renderer calls with the Gate's own steps. */
    @Override
    public void setupAnim(HellGate hellGate, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
    }

    /**
     * Risen this far out of the ground, its doors open this far, and crumbled this far, each from 0 to 1. The left door
     * hangs at -x and the right at +x, so they turn opposite ways to swing in.
     */
    public void pose(float rise, float open, float crumble) {
        root.getAllParts().forEach(ModelPart::resetPose);
        gate.y += (1.0F - rise) * SUNK;
        // Never quite flat: a scale of 0 would leave its faces without a way to light them.
        gate.yScale = Math.max(MIN_HEIGHT, 1.0F - crumble);
        leftDoor.yRot = -open * DOOR_SWING;
        rightDoor.yRot = open * DOOR_SWING;
    }
}
