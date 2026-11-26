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
 * Hihiō Zabimaru's baboon pelt: a shaggy mane about the neck, a mantle over the shoulders and chest, the hide hung down
 * the back, and a pad of fur on each shoulder, a little larger than the garb and armour the game draws under them, posed
 * as the wearer is (see {@link BankaiCoatLayer}). It reads a 64x32 texture: the mane's net at 0,0, the mantle's at 0,9,
 * the hide's at 24,9 and the shoulders' at 44,0. Head and legs carry nothing.
 */
final class BankaiPeltModel<T extends LivingEntity> extends HumanoidModel<T> {
    /** How much larger than the body, over a chestplate's 1.0, so the fur stands out of the armour under it. */
    private static final float GROWTH = 1.3F;

    BankaiPeltModel(ModelPart root) {
        super(root);
    }

    /** The mesh: the seven parts a humanoid model needs, with the pelt's boxes on the body and the arms alone. */
    static LayerDefinition createLayer() {
        CubeDeformation fur = new CubeDeformation(GROWTH);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (String bare : new String[] {"head", "hat", "right_leg", "left_leg"}) {
            root.addOrReplaceChild(bare, CubeListBuilder.create(), PartPose.ZERO);
        }
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0F, -1.0F, -3.0F, 10.0F, 3.0F, 6.0F, fur)
                .texOffs(0, 9).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F, fur)
                .texOffs(24, 9).addBox(-4.0F, 1.0F, 2.0F, 8.0F, 10.0F, 1.0F, fur), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(44, 0)
                .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, fur), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(44, 0).mirror()
                .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, fur), PartPose.offset(5.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
