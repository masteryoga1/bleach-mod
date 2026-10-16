package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a Hollow of one kind: its model at the kind's scale with the mask as a layer of its own. A
 * purified Hollow turns white, shrinks and rises as it dissolves into light, and never falls over.
 * Hidden spirits are culled before this is reached (see {@code mixin.client}).
 */
public class HollowRenderer<T extends Hollow, M extends EntityModel<T>> extends MobRenderer<T, M> {
    /** The share of its size a dissolving Hollow has lost by the end. */
    private static final float DISSOLVE_SHRINK = 0.6F;
    /** How far a dissolving Hollow has risen by the end, in blocks. */
    private static final float DISSOLVE_RISE = 0.5F;

    private final HollowKind kind;
    private final ResourceLocation texture;
    private final float flipDegrees;

    /**
     * @param flipDegrees how far the body rolls over as a slain Hollow dies: spiders end on their backs,
     *                    the rest on their sides
     */
    public HollowRenderer(EntityRendererProvider.Context context, M model, HollowKind kind, float flipDegrees) {
        super(context, model, kind.look.shadowRadius());
        this.kind = kind;
        this.texture = kind.texture();
        this.flipDegrees = flipDegrees;
        addLayer(new HollowMaskLayer<>(this, kind));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    /**
     * The kind's model scale, shifted back so the hitbox covers the body. The Hollow's own size has
     * already been applied. Model space is upside down here, so rising is towards -y, and +z is
     * towards the back.
     */
    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        float dissolve = entity.dissolveProgress(partialTick);
        float scale = kind.look.modelScale() * (1.0F - DISSOLVE_SHRINK * dissolve);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0F, -DISSOLVE_RISE * dissolve / scale, kind.look.backShift() / scale);
    }

    @Override
    protected float getWhiteOverlayProgress(T entity, float partialTick) {
        return entity.dissolveProgress(partialTick);
    }

    /** Only a Hollow that is slain falls over: a purified one dissolves standing. */
    @Override
    protected float getFlipDegrees(T entity) {
        return flipDegrees;
    }
}
