package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Quincy: Vollständig: two flat wings of light from the shoulder blades, swept back and stirring
 * slowly (spread wider while Sklaverei draws reishi in), and a halo above the head, swapped for a
 * broken one once a blow shatters it. Each part follows the posed player model: the wings ride the
 * body and the halo copies the head.
 */
public final class VollstandigModel extends Model {
    /** How far back each wing is swept, in radians, and how far while Sklaverei is on. */
    private static final float WING_SWEEP = 0.35F;
    private static final float WING_SWEEP_SKLAVEREI = 0.75F;
    private static final float WING_STIR = 0.05F;
    private static final float WING_STIR_SPEED = 0.15F;

    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart halo;
    private final ModelPart brokenHalo;
    /** The body the wings were last set up on; they are drawn in its frame, so they lean as it leans. */
    @Nullable
    private ModelPart body;

    public VollstandigModel(ModelPart root) {
        super(RenderType::entityTranslucentEmissive);
        this.rightWing = root.getChild("right_wing");
        this.leftWing = root.getChild("left_wing");
        this.halo = root.getChild("halo");
        this.brokenHalo = root.getChild("halo_broken");
    }

    /**
     * The mesh on its 64 by 64 texture: each wing a flat panel 16 wide and 24 tall pivoting at the
     * shoulder blade, on the back of the body; the halo a flat ring 12 across, 2.5 pixels above the
     * head, whole or broken.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-16.0F, -4.0F, 0.0F, 16.0F, 24.0F, 0.0F), PartPose.offset(-2.0F, 1.0F, 2.0F));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 0).mirror()
                .addBox(0.0F, -4.0F, 0.0F, 16.0F, 24.0F, 0.0F), PartPose.offset(2.0F, 1.0F, 2.0F));
        root.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-6.0F, -10.5F, -6.0F, 12.0F, 0.0F, 12.0F), PartPose.ZERO);
        root.addOrReplaceChild("halo_broken", CubeListBuilder.create().texOffs(0, 48)
                .addBox(-6.0F, -10.5F, -6.0F, 12.0F, 0.0F, 12.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Poses the wings and halo on the already posed player model, for this moment. */
    public void setup(PlayerModel<?> parent, float ageInTicks, boolean sklaverei, boolean haloBroken) {
        body = parent.body;
        float sweep = (sklaverei ? WING_SWEEP_SKLAVEREI : WING_SWEEP) + WING_STIR * Mth.sin(ageInTicks * WING_STIR_SPEED);
        // Each wing keeps its shoulder-blade pivot inside the body's frame; turning it about y sends
        // its far edge back (+z), so the two turn opposite ways.
        rightWing.resetPose();
        rightWing.yRot = sweep;
        leftWing.resetPose();
        leftWing.yRot = -sweep;
        halo.copyFrom(parent.head);
        brokenHalo.copyFrom(parent.head);
        halo.visible = !haloBroken;
        brokenHalo.visible = haloBroken;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int colour) {
        if (body != null) {
            poseStack.pushPose();
            body.translateAndRotate(poseStack);
            rightWing.render(poseStack, buffer, packedLight, packedOverlay, colour);
            leftWing.render(poseStack, buffer, packedLight, packedOverlay, colour);
            poseStack.popPose();
        }
        halo.render(poseStack, buffer, packedLight, packedOverlay, colour);
        brokenHalo.render(poseStack, buffer, packedLight, packedOverlay, colour);
    }
}
