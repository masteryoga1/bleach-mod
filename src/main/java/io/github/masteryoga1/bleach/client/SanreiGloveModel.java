package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * The Sanrei Glove on a player's arm: one box over the lower right forearm and hand, a little
 * larger than the arm and its sleeve so it sits on top of both. Built in arm space, so it only has
 * to copy the posed arm to follow it.
 */
public final class SanreiGloveModel extends Model {
    /** How far the glove stands off the arm: past the sleeve layer's quarter pixel. */
    private static final float LOOSENESS = 0.35F;

    private final ModelPart glove;

    public SanreiGloveModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.glove = root.getChild("glove");
    }

    /** The glove's mesh on its 32 by 16 texture: four wide, six long and four deep, from the elbow's far side down. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("glove", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, 4.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(LOOSENESS)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    /** The glove itself, to be posed like the right arm before it is drawn. */
    public ModelPart glove() {
        return glove;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int colour) {
        glove.render(poseStack, buffer, packedLight, packedOverlay, colour);
    }
}
