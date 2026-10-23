package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;

/**
 * Draws Blut Vene's glowing veins over a posed humanoid, flaring white for a moment each time a blow
 * lands. On a bare body they lie on the skin. Armour on the chest or legs would hide them, garb
 * included, so then they go on a thin shell just outside it ({@link BleachModelLayers#VEIN_SHELL}),
 * which reads the same skin-layout texture: the left arm shows the right arm's veins, mirrored, as
 * armour does.
 */
final class BlutVeins<T extends LivingEntity> {
    private static final RenderType TYPE = RenderType.eyes(BlutLayer.TEXTURE);
    private static final int VEINS = 0xFF7FA0D0;
    private static final int VEINS_STRUCK = 0xFFFFFFFF;

    private final HumanoidModel<T> shell;

    BlutVeins(EntityModelSet models) {
        shell = new HumanoidModel<>(models.bakeLayer(BleachModelLayers.VEIN_SHELL));
        // The texture has veins only on the arms and the chest.
        shell.setAllVisible(false);
        shell.body.visible = true;
        shell.rightArm.visible = true;
        shell.leftArm.visible = true;
    }

    /** Draws the veins over {@code body}, the entity's own model, already posed for this frame. */
    void render(HumanoidModel<T> body, PoseStack poseStack, MultiBufferSource buffer, T entity) {
        HumanoidModel<T> model = body;
        if (covered(entity)) {
            body.copyPropertiesTo(shell);
            model = shell;
        }
        model.renderToBuffer(poseStack, buffer.getBuffer(TYPE), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                entity.hurtTime > 0 ? VEINS_STRUCK : VEINS);
    }

    /** Whether armour that the game draws over the body, on the chest or the legs, would hide veins on the skin. */
    private static boolean covered(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem
                || entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof ArmorItem;
    }
}
