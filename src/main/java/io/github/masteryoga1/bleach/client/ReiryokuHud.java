package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The reiryoku bar: a thin vertical gauge just right of the hotbar, moved further out when the
 * offhand slot or the hotbar attack indicator is on that side. It gets a white outline while
 * reiatsu is released, and a Plus gets a second gauge beside it for the Chain of Fate, which turns
 * red once Encroachment sets in. Placeholder look until real art exists.
 */
public final class ReiryokuHud {
    public static final ResourceLocation ID = BleachMod.id("reiryoku");

    private static final int WIDTH = 6;
    private static final int HEIGHT = 22;
    private static final int BACKGROUND = 0x90000000;
    private static final int FILL = 0xFF8FD8FF;
    private static final int FILL_LOW = 0xFF4F7FA8;
    private static final int RELEASED_OUTLINE = 0xFFFFFFFF;
    private static final int CHAIN_FILL = 0xFFC8C8D0;
    private static final int CHAIN_FILL_ENCROACHING = 0xFFB03030;

    private ReiryokuHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        if (!data.isInitialized() || !SoulLogic.isAware(data)) {
            return;
        }

        // Vanilla draws the offhand slot on the right for left-handed players, and otherwise puts the
        // hotbar attack indicator (when that option is on) on the right.
        int offset = 6;
        if (player.getMainArm() == HumanoidArm.LEFT) {
            offset += 30;
        } else if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            offset += 20;
        }
        int x = graphics.guiWidth() / 2 + 91 + offset;
        int y = graphics.guiHeight() - HEIGHT;

        float fraction = data.reiryokuFraction();
        drawGauge(graphics, x, y, fraction, fraction < 0.2F ? FILL_LOW : FILL);
        if (data.isReiatsuReleased()) {
            graphics.renderOutline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, RELEASED_OUTLINE);
        }

        if (data.state() == SoulState.PLUS && data.chainMaxTicks() > 0) {
            drawGauge(graphics, x + WIDTH + 2, y, data.chainFraction(),
                    SoulLogic.isEncroaching(data) ? CHAIN_FILL_ENCROACHING : CHAIN_FILL);
        }
    }

    private static void drawGauge(GuiGraphics graphics, int x, int y, float fraction, int color) {
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        int inner = HEIGHT - 2;
        int filled = Mth.ceil(inner * fraction);
        if (filled > 0) {
            int bottom = y + HEIGHT - 1;
            graphics.fill(x + 1, bottom - filled, x + WIDTH - 1, bottom, color);
        }
    }
}
