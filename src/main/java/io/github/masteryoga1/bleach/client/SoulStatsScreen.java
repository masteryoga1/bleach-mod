package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The soul screen: state, reiryoku, stats with training progress, awakened powers and what comes next. */
public final class SoulStatsScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int LINE = 12;
    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int GOLD = 0xFFD27F;
    private static final int REIRYOKU = 0x8FD8FF;
    private static final int ENCROACHING = 0xFF5555;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final int XP_BACKGROUND = 0xFF303030;
    private static final int XP_FILL = 0xFF6FBF4F;

    public SoulStatsScreen() {
        super(Component.translatable("screen.bleach.soul"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        int left = width / 2 - PANEL_WIDTH / 2;
        int y = 16;

        graphics.drawCenteredString(font, title, width / 2, y, WHITE);
        y += LINE + 6;
        String stateKey = ShinigamiLogic.isSubstitute(data) ? "screen.bleach.soul.state.substitute"
                : ShinigamiLogic.isStudent(data) ? "screen.bleach.soul.state.student" : "screen.bleach.soul.state";
        graphics.drawString(font, Component.translatable(stateKey, data.state().displayName()), left, y, WHITE);
        y += LINE;
        graphics.drawString(font, Component.translatable("screen.bleach.soul.reiryoku",
                (int) Math.floor(data.reiryoku()), (int) Math.floor(data.maxReiryoku())), left, y, REIRYOKU);
        y += LINE;
        if (data.state() == SoulState.PLUS && data.chainMaxTicks() > 0) {
            boolean encroaching = SoulLogic.isEncroaching(data);
            graphics.drawString(font, Component.translatable(encroaching
                            ? "screen.bleach.soul.chain.encroaching" : "screen.bleach.soul.chain",
                    StringUtil.formatTickDuration(data.chainTicks(), TICKS_PER_SECOND)),
                    left, y, encroaching ? ENCROACHING : WHITE);
            y += LINE;
        }
        if (SoulLogic.isAware(data)) {
            Component release = data.isReiatsuReleased()
                    ? Component.translatable("screen.bleach.soul.reiatsu.released",
                            BleachClient.REIATSU_RELEASE_KEY.getTranslatedKeyMessage())
                    : Component.translatable("screen.bleach.soul.reiatsu.sealed",
                            BleachClient.REIATSU_RELEASE_KEY.getTranslatedKeyMessage());
            graphics.drawString(font, release, left, y, GREY);
            y += LINE;
        }
        y += 6;

        graphics.drawString(font, Component.translatable("screen.bleach.soul.stats"), left, y, GOLD);
        y += LINE;
        int maxLevel = SoulLogic.maxStatLevel();
        for (Stat stat : Stat.values()) {
            int level = data.level(stat);
            graphics.drawString(font, stat.displayName(), left + 6, y, WHITE);
            graphics.drawString(font, Component.translatable("screen.bleach.soul.level", level), left + 120, y, GREY);
            float progress = level >= maxLevel ? 1.0F : (float) data.xp(stat) / SoulData.xpToNextLevel(level);
            int barLeft = left + 165;
            int barWidth = PANEL_WIDTH - 165;
            graphics.fill(barLeft, y + 2, barLeft + barWidth, y + 6, XP_BACKGROUND);
            graphics.fill(barLeft, y + 2, barLeft + Math.round(barWidth * progress), y + 6, XP_FILL);
            y += LINE;
        }
        y += 6;

        graphics.drawString(font, Component.translatable("screen.bleach.soul.powers"), left, y, GOLD);
        y += LINE;
        boolean anyPower = false;
        for (PowerPath path : PowerPath.values()) {
            int tier = data.tier(path);
            if (tier > 0) {
                anyPower = true;
                graphics.drawString(font, Component.translatable("screen.bleach.soul.tier", path.displayName(), tier),
                        left + 6, y, WHITE);
                y += LINE;
            }
        }
        if (!anyPower) {
            graphics.drawString(font, Component.translatable("screen.bleach.soul.no_powers"), left + 6, y, GREY);
            y += LINE;
        }
        y += 6;

        graphics.drawWordWrap(font, nextStep(player, data), left, y, PANEL_WIDTH, GREY);
    }

    /**
     * What this soul can do next. A Plus in the Overworld who was told where the Academy is gets its
     * direction too.
     */
    private static Component nextStep(Player player, SoulData data) {
        String key = ShinigamiLogic.nextStepKey(data, player.level().getGameTime());
        if (key.equals("plus") && data.shinigami().academyPos().isPresent()
                && player.level().dimension() == Level.OVERWORLD) {
            BlockPos academy = data.shinigami().academyPos().get();
            double dx = academy.getX() + 0.5 - player.getX();
            double dz = academy.getZ() + 0.5 - player.getZ();
            return Component.translatable("screen.bleach.soul.next.plus.academy",
                    Component.translatable(ShinigamiLogic.compassKey(dx, dz)),
                    ShinigamiLogic.roundedDistance(Math.sqrt(dx * dx + dz * dz)));
        }
        return Component.translatable("screen.bleach.soul.next." + key);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
