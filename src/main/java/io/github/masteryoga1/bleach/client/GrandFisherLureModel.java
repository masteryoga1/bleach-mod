package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Grand Fisher's lure: a slim player's body at a child's proportions, as the renderer draws a baby. On her stalk she
 * stands crying, her head bowed and her hands to her face, her shoulders shaking with her sobs; cut off, she lies limp,
 * her arms fallen loose at her sides.
 */
public final class GrandFisherLureModel extends PlayerModel<GrandFisherLure> {
    /** Crying: how far the head bows, how far the arms rise and turn in to bring the hands to the face. */
    private static final float BOW = 0.35F;
    private static final float HANDS_UP = -2.1F;
    private static final float HANDS_IN = 0.45F;
    /** How far and how fast the shoulders shake with the sobs. */
    private static final float SOB_SHAKE = 0.06F;
    private static final float SOB_RATE = 0.9F;
    /** How far the loose arms fall from the sides of one lying limp. */
    private static final float LIMP_SPREAD = 0.25F;

    public GrandFisherLureModel(ModelPart root) {
        super(root, true);
    }

    @Override
    public void setupAnim(GrandFisherLure lure, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        super.setupAnim(lure, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (lure.isSevered()) {
            head.xRot = 0.0F;
            head.yRot = 0.0F;
            rightArm.xRot = 0.0F;
            leftArm.xRot = 0.0F;
            rightArm.zRot = LIMP_SPREAD;
            leftArm.zRot = -LIMP_SPREAD;
            rightLeg.xRot = 0.0F;
            leftLeg.xRot = 0.0F;
        } else {
            float sob = Mth.sin(ageInTicks * SOB_RATE) * SOB_SHAKE;
            head.xRot = BOW;
            rightArm.xRot = HANDS_UP + sob;
            leftArm.xRot = HANDS_UP + sob;
            rightArm.yRot = -HANDS_IN;
            leftArm.yRot = HANDS_IN;
        }
        // The player model copies its outer layer from the limbs before this, so it is copied again after.
        hat.copyFrom(head);
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg);
        leftPants.copyFrom(leftLeg);
    }
}
