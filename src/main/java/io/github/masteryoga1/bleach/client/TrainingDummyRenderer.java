package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.TrainingDummy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a training dummy as a straw figure on the vanilla humanoid body (the zombie's layer), arms held out, in its
 * placeholder texture. Its name shows only as a mob's does, when it has one and is looked at.
 */
public class TrainingDummyRenderer extends LivingEntityRenderer<TrainingDummy, HumanoidModel<TrainingDummy>> {
    private static final ResourceLocation TEXTURE = BleachMod.id("textures/entity/training_dummy.png");

    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<TrainingDummy>(context.bakeLayer(ModelLayers.ZOMBIE)) {
            @Override
            public void setupAnim(TrainingDummy dummy, float swing, float swingAmount, float age, float headYaw,
                                  float headPitch) {
                super.setupAnim(dummy, swing, swingAmount, age, headYaw, headPitch);
                rightArm.zRot = (float) Math.toRadians(80.0);
                leftArm.zRot = (float) Math.toRadians(-80.0);
            }
        }, 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingDummy dummy) {
        return TEXTURE;
    }

    /** LivingEntityRenderer would name every one aloud; this keeps MobRenderer's rule. */
    @Override
    protected boolean shouldShowName(TrainingDummy dummy) {
        return super.shouldShowName(dummy) && (dummy.shouldShowName()
                || dummy.hasCustomName() && dummy == entityRenderDispatcher.crosshairPickEntity);
    }
}
