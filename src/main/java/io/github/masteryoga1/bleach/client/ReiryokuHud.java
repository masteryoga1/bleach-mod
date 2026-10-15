package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The reiryoku bar: a thin vertical gauge beside the hotbar, on the side away from the offhand
 * slot. Placeholder look until real art exists.
 */
public final class ReiryokuHud {
    public static final ResourceLocation ID = BleachMod.id("reiryoku");

    private static final int WIDTH = 6;
    private static final int HEIGHT = 22;
    private static final int BACKGROUND = 0x90000000;
    private static final int FILL = 0xFF8FD8FF;
    private static final int FILL_LOW = 0xFF4F7FA8;

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

        // Vanilla puts the offhand slot on the right of the hotbar for left-handed players.
        int offset = player.getMainArm() == HumanoidArm.LEFT ? 36 : 6;
        int x = graphics.guiWidth() / 2 + 91 + offset;
        int y = graphics.guiHeight() - HEIGHT;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);

        float fraction = data.reiryokuFraction();
        int inner = HEIGHT - 2;
        int filled = Mth.ceil(inner * fraction);
        if (filled > 0) {
            int bottom = y + HEIGHT - 1;
            graphics.fill(x + 1, bottom - filled, x + WIDTH - 1, bottom, fraction < 0.2F ? FILL_LOW : FILL);
        }
    }
}
