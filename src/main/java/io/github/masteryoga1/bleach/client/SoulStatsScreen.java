package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyProgress;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The soul screen: state, reiryoku, the zanpakutō (or until it has a name, the Asauchi bond), Hollows
 * purified and slain and Pluses sent on, Quincy blood (its reishi and Blut, the Hollow poison in it,
 * souls destroyed and how rich the reishi is here), stats with training progress, awakened powers
 * and what comes next.
 */
public final class SoulStatsScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int LINE = 12;
    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int GOLD = 0xFFD27F;
    private static final int REIRYOKU = 0x8FD8FF;
    private static final int ENCROACHING = 0xFF5555;
    private static final int REISHI = 0xBFE6FF;
    private static final int POISONED = 0xC080E0;
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
                : data.state() == SoulState.HUMAN && QuincyLogic.isAwakened(data) ? "screen.bleach.soul.state.quincy"
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
        Component zanpakuto = zanpakutoLine(player, data);
        if (zanpakuto != null) {
            graphics.drawString(font, zanpakuto, left, y, GREY);
            y += LINE;
        }
        HuntRecord hunt = data.hunt();
        if (hunt.purified() + hunt.slain() + hunt.sentOn() > 0) {
            graphics.drawString(font, Component.translatable("screen.bleach.soul.hunt",
                    hunt.purified(), hunt.slain(), hunt.sentOn()), left, y, GREY);
            y += LINE;
        }
        y = renderQuincy(graphics, player, data, left, y);
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
     * Up to two lines on Quincy blood, when the soul has it. Awakened: the blood, reishi held and the
     * most it can hold, and the Blut it is calling on; then the Hollow poison in it, souls its arrows
     * have destroyed, and how rich the reishi is where it stands (leaving out what other souls'
     * reiatsu adds, which only the server weighs). Not yet awakened: just the blood.
     *
     * @return the line below the last one drawn
     */
    private int renderQuincy(GuiGraphics graphics, Player player, SoulData data, int left, int y) {
        Bloodline bloodline = QuincyLogic.bloodline(data);
        if (bloodline == Bloodline.NONE) {
            return y;
        }
        if (!QuincyLogic.isAwakened(data)) {
            graphics.drawString(font, Component.translatable("screen.bleach.soul.quincy.latent",
                    bloodline.displayName()), left, y, GREY);
            return y + LINE;
        }
        QuincyProgress quincy = data.quincy();
        graphics.drawString(font, Component.translatable("screen.bleach.soul.quincy", bloodline.displayName(),
                Math.round(QuincyLogic.reishi(data)), Math.round(QuincyLogic.maxReishi(data)),
                QuincyLogic.activeBlut(data).displayName()), left, y, REISHI);
        y += LINE;
        Component density = Component.translatable(
                QuincyLogic.densityKey(QuincyLogic.placeDensity(player.level(), player.position())));
        graphics.drawString(font, Component.translatable("screen.bleach.soul.quincy.poison",
                Math.round(quincy.poison()), quincy.destroyed(), density),
                left, y, quincy.poison() >= QuincyLogic.POISON_BLOCK ? POISONED : GREY);
        return y + LINE;
    }

    /**
     * The zanpakutō's name and whether it is sealed, in Shikai or always released; before the name,
     * how far the Asauchi bond has grown, once it has begun or while a nameless Asauchi is carried.
     * Null when there is nothing to say. Whether it is in Shikai is read from the player's own blade.
     */
    @Nullable
    private static Component zanpakutoLine(Player player, SoulData data) {
        ZanpakutoBond bond = data.zanpakuto();
        if (bond.named()) {
            String state;
            if (bond.constant()) {
                state = bond.firstReleaseDone() ? "constant" : "constant_waiting";
            } else {
                state = ZanpakutoItem.blade(ZanpakutoLogic.findOwnBlade(player)).released() ? "released" : "sealed";
            }
            Component name = bond.name().getString().isEmpty()
                    ? Component.translatable("screen.bleach.jinzen.unknown_name") : bond.name();
            return Component.translatable("screen.bleach.soul.zanpakuto", name,
                    Component.translatable("screen.bleach.soul.zanpakuto." + state));
        }
        if (bond.bond() > 0 || ZanpakutoLogic.carriesUnboundAsauchi(player)) {
            return Component.translatable("screen.bleach.soul.bond", bond.bond() / 10);
        }
        return null;
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
