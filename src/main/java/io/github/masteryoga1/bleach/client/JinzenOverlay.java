package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Settling into Jinzen: while the player sits with their blade (sneaking and holding use with it),
 * the edges of the view darken over the settling time, until the Jinzen screen opens on the inner
 * world. Drawn as nested frames, each fainter than the one outside it.
 */
public final class JinzenOverlay {
    public static final ResourceLocation ID = BleachMod.id("jinzen");

    /** How dark the edges get once settled, from 0 to 1. */
    private static final float MAX_ALPHA = 0.6F;
    private static final int FRAMES = 16;
    /** How far in from the edges the darkness reaches, as a share of the screen's shorter side. */
    private static final float DEPTH = 0.3F;
    private static final int COLOUR = 0x06060C;

    private JinzenOverlay() {
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || minecraft.options.hideGui || !player.isUsingItem()
                || !player.isCrouching() || !(player.getUseItem().getItem() instanceof ZanpakutoItem)) {
            return;
        }
        float settled = Math.min(1.0F, (player.getTicksUsingItem() + deltaTracker.getGameTimeDeltaPartialTick(false))
                / JinzenLogic.SETTLE_TICKS);
        float alpha = settled * MAX_ALPHA;
        if (alpha <= 0.0F) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float depth = Math.min(width, height) * DEPTH;
        for (int frame = 0; frame < FRAMES; frame++) {
            int outer = Math.round(depth * frame / FRAMES);
            int inner = Math.round(depth * (frame + 1) / FRAMES);
            if (inner <= outer) {
                continue;
            }
            float fade = 1.0F - (float) frame / FRAMES;
            int colour = JinzenBackdrops.argb(Math.round(255 * alpha * fade * fade), COLOUR);
            graphics.fill(outer, outer, width - outer, inner, colour);
            graphics.fill(outer, height - inner, width - outer, height - outer, colour);
            graphics.fill(outer, inner, inner, height - inner, colour);
            graphics.fill(width - inner, inner, width - outer, height - inner, colour);
        }
    }
}
