package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A Chain of Fate as a column of chain item sprites hanging down from the current pose, shorter as
 * the chain wears away. Shared by Plus players and Plus spirits, so both look the same.
 */
public final class ChainPieces {
    /** Chain pieces at full length. */
    public static final int MAX_PIECES = 5;
    /** The edge of one piece, in blocks. */
    public static final float PIECE_SIZE = 0.2F;

    private static final ItemStack CHAIN = new ItemStack(Items.CHAIN);

    private ChainPieces() {
    }

    /**
     * Draws the chain hanging along -y from the pose's origin. There is always at least one piece left.
     *
     * @param seed     varies the sprites from one soul to the next, usually the entity id
     * @param fraction how much of the chain is left, from 0 to 1
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Level level, int seed,
                              float fraction) {
        int pieces = Mth.clamp(Mth.ceil(fraction * MAX_PIECES), 1, MAX_PIECES);
        for (int i = 0; i < pieces; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -(i + 0.5F) * PIECE_SIZE, 0.0F);
            poseStack.scale(PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
            Minecraft.getInstance().getItemRenderer().renderStatic(CHAIN, ItemDisplayContext.FIXED, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffer, level, seed + i);
            poseStack.popPose();
        }
    }
}
