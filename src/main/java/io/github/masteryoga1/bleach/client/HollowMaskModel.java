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
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * A Hollow player's bone mask: a face plate and a jaw, with tusks, horns or a beak by shape (see
 * {@code HollowPlayerLogic#maskVariant}), and the remnant an Arrancar keeps of it (see
 * {@link #setupFragment}). Built in head space (the head spans -4 to 4 across, -8 to 0 down and -4 to 4
 * deep, its face at -4), so it only has to follow the posed head to be worn. The plate stands a pixel off
 * the face, half a pixel clear of the hat layer in front, and the plate and jaw are grown a hair so that
 * none of their faces shares a plane with the hat or the head, which would flicker. Standing off the face,
 * a remnant hovers just clear of the skin, as an Arrancar's does.
 */
public final class HollowMaskModel extends Model {
    /** Where the jaw hangs, shut, and how far it drops and swings its lower edge forward while devouring. */
    private static final float JAW_SHUT_Y = -1.5F;
    private static final float JAW_OPEN_Y = 0.0F;
    private static final float JAW_OPEN_X_ROT = -0.35F;
    /** How far each horn leans out from the top of the plate. */
    private static final float HORN_LEAN = 0.35F;
    /**
     * How far the plate and jaw grow past their boxes: the plate's sides and top then stand outside the
     * hat layer's and its back inside the head; the jaw grows less, so the plate's front stays ahead of it.
     */
    private static final CubeDeformation PLATE_GROWTH = new CubeDeformation(0.05F);
    private static final CubeDeformation JAW_GROWTH = new CubeDeformation(0.03F);

    private static final int SKULL = 0;
    private static final int FANGED = 1;
    private static final int HORNED = 2;
    private static final int BEAKED = 3;

    private final ModelPart root;
    private final ModelPart plate;
    private final ModelPart jaw;
    private final ModelPart leftTusk;
    private final ModelPart rightTusk;
    private final ModelPart leftHorn;
    private final ModelPart rightHorn;
    private final ModelPart beak;

    public HollowMaskModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.plate = root.getChild("plate");
        this.jaw = root.getChild("jaw");
        this.leftTusk = jaw.getChild("left_tusk");
        this.rightTusk = jaw.getChild("right_tusk");
        this.leftHorn = root.getChild("left_horn");
        this.rightHorn = root.getChild("right_horn");
        this.beak = root.getChild("beak");
    }

    /**
     * The mask's mesh on its 64 by 32 texture. The wearer's left is +x, as in vanilla's models, and a
     * positive z rotation tips a part's top towards +x, so both horns lean outwards. The tusks hang from
     * the jaw and ride on it when it opens; both of a pair share one patch of the texture.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -8.5F, -5.0F, 9.0F, 7.0F, 1.0F, PLATE_GROWTH), PartPose.ZERO);
        PartDefinition jaw = root.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-3.5F, 0.0F, -1.0F, 7.0F, 2.0F, 1.0F, JAW_GROWTH), PartPose.offset(0.0F, JAW_SHUT_Y, -4.0F));
        jaw.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(20, 5)
                .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offset(2.5F, 2.0F, -0.5F));
        jaw.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(20, 5).mirror()
                .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offset(-2.5F, 2.0F, -0.5F));
        root.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(20, 0)
                        .addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(3.5F, -8.0F, -4.5F, 0.0F, 0.0F, HORN_LEAN));
        root.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(20, 0).mirror()
                        .addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(-3.5F, -8.0F, -4.5F, 0.0F, 0.0F, -HORN_LEAN));
        root.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-1.5F, -1.0F, -3.0F, 3.0F, 2.0F, 3.0F), PartPose.offset(0.0F, -3.5F, -5.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /**
     * Shows the whole mask with the parts of this shape (0 the plain skull, 1 fanged, 2 horned, 3 beaked) and
     * hangs the jaw open while the Hollow devours: it drops a pixel and a half and swings its lower edge
     * forward, showing the face beneath. The plate and jaw are shown every time, as one model draws the
     * Hollows' masks and the Arrancar's remnants.
     */
    public void setup(int variant, boolean jawOpen) {
        plate.visible = true;
        jaw.visible = true;
        leftTusk.visible = variant == FANGED;
        rightTusk.visible = variant == FANGED;
        leftHorn.visible = variant == HORNED;
        rightHorn.visible = variant == HORNED;
        beak.visible = variant == BEAKED;
        jaw.y = jawOpen ? JAW_OPEN_Y : JAW_SHUT_Y;
        jaw.xRot = jawOpen ? JAW_OPEN_X_ROT : 0.0F;
    }

    /**
     * Shows only the remnant an Arrancar keeps of a mask of this shape, hovering where it was, with no face
     * plate: a skull mask leaves its jawbone, a fanged one the jaw and its tusks, a horned one a single horn
     * and a beaked one its beak. The jaw is shut, as an Arrancar no longer devours.
     */
    public void setupFragment(int variant) {
        plate.visible = false;
        jaw.visible = variant == SKULL || variant == FANGED;
        leftTusk.visible = variant == FANGED;
        rightTusk.visible = variant == FANGED;
        leftHorn.visible = variant == HORNED;
        rightHorn.visible = false;
        beak.visible = variant == BEAKED;
        jaw.y = JAW_SHUT_Y;
        jaw.xRot = 0.0F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int colour) {
        root.render(poseStack, buffer, packedLight, packedOverlay, colour);
    }
}
