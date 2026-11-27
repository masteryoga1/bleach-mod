package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.entity.TrialBlade;
import io.github.masteryoga1.bleach.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * A trial's blade: an Asauchi in its spirit's Shikai look, as an item stood point down; nothing while it is held. The
 * unaware never see it, as with every spirit (see {@code EntityRenderDispatcherMixin}).
 */
public final class TrialBladeRenderer extends EntityRenderer<TrialBlade> {
    /** How high its middle stands above the ground, and how large it is drawn, in blocks. */
    private static final float MIDDLE = 0.55F, SIZE = 1.1F;
    /** A sword's picture runs from its hilt at the bottom left to its point at the top right: this turns its point down. */
    private static final float POINT_DOWN = 135.0F;
    private static int drawn;

    /** The look last drawn, as every blade of a bout has the same, and its stack. */
    private int model = -1;
    private ItemStack stack = ItemStack.EMPTY;

    public TrialBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(TrialBlade blade, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        if (blade.held()) {
            return;
        }
        drawn++;
        poseStack.pushPose();
        poseStack.translate(0.0F, MIDDLE, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-blade.getYRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(POINT_DOWN));
        poseStack.scale(SIZE, SIZE, SIZE);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack(blade.model()), ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, blade.level(), blade.getId());
        poseStack.popPose();
        super.render(blade, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** An Asauchi in this look, as the spirit's own blade is ({@code ZangetsuSpirit.held}). */
    private ItemStack stack(int look) {
        if (look != model) {
            model = look;
            stack = new ItemStack(ModItems.ASAUCHI.get());
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(look));
        }
        return stack;
    }

    /** The blocks atlas, which the item renderer binds itself. */
    @Override
    public ResourceLocation getTextureLocation(TrialBlade blade) {
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }

    /** How many times a blade has been drawn since the game started: the smoke test reads it. */
    public static int drawnFrames() {
        return drawn;
    }
}
