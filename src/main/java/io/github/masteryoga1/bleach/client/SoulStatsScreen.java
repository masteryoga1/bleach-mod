package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.BaitRecord;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.Hierro;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.MenosEvolution;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyArts;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyForms;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyProgress;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import io.github.masteryoga1.bleach.soul.QuincyTeaching;
import io.github.masteryoga1.bleach.soul.SanreiProgress;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SlowRoad;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The soul screen: state, reiryoku, a Plus's Chain of Fate or a Hollow's soul hunger, the zanpakutō (or
 * until it has a name, the Asauchi bond; for an Arrancar, its own blade and whether it is released), a
 * Visored's Hollow mask and how near it is to the masked Cero, whether a Hollow rooted in the soul has its
 * body or may seize it (and, when a server lets lost fights make a Hollow, how many it has won), Hollows
 * purified and slain and Pluses sent on, Hollow Bait crushed and contests with the Quincy Hunter, the souls
 * a Hollow has devoured and what they were, Quincy blood (its reishi and Blut, the Hollow poison in it,
 * souls destroyed and how rich the reishi is here, how far its arts have come, and the Quincy Elder's
 * lessons), stats with training progress, awakened powers (an Arrancar's with the form it rose from and its
 * rank) and what comes next, and a button to the Kidō page for a soul that may use Kidō.
 */
public final class SoulStatsScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    /** Where the title sits in the full and compact layouts, and in the tight one. */
    private static final int TOP = 16, TIGHT_TOP = 6;
    private static final int LINE = 12;
    /** The rows' spacing on a screen too short for the full layout, which also drops the gaps between sections. */
    private static final int COMPACT_LINE = 10;
    /** The rows' spacing, a line of text each, when even the compact rows would reach the Done button. */
    private static final int TIGHT_LINE = 9;
    /** The space between sections in the full layout. */
    private static final int GAP = 6;
    /** How far above the bottom the Done button's top edge is. */
    private static final int DONE_TOP = 28;
    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int GOLD = 0xFFD27F;
    private static final int REIRYOKU = 0x8FD8FF;
    private static final int ENCROACHING = 0xFF5555;
    private static final int REISHI = 0xBFE6FF;
    private static final int POISONED = 0xC080E0;
    private static final int LETZT = 0xFFC890;
    private static final int VOLLSTANDIG = 0xFFD86A;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final int XP_BACKGROUND = 0xFF303030;
    private static final int XP_FILL = 0xFF6FBF4F;
    /** The Kidō page's button, or null for a soul that may not use Kidō. */
    @Nullable
    private Button kido;

    public SoulStatsScreen() {
        super(Component.translatable("screen.bleach.soul"));
    }

    @Override
    protected void init() {
        // A soul that may use Kidō has its page beside Done.
        boolean offered = minecraft != null && minecraft.player != null
                && KidoLogic.trained(minecraft.player.getData(SoulAttachments.SOUL));
        kido = offered ? addRenderableWidget(Button.builder(Component.translatable("screen.bleach.kido"),
                        button -> minecraft.setScreen(new KidoScreen(this)))
                .bounds(width / 2 - 100, height - DONE_TOP, 98, 20).build()) : null;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(offered ? width / 2 + 2 : width / 2 - 100, height - DONE_TOP, offered ? 98 : 200, 20)
                .build());
    }

    /** The Kidō page's button, or null when this soul may not use Kidō (the smoke test presses it). */
    @Nullable
    public Button kidoButton() {
        return kido;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        List<FormattedCharSequence> next = font.split(nextStep(player, data), PANEL_WIDTH);
        // Everything stays above the Done button: when the rows and the next step would reach it, the rows
        // close up; when they still would, the rows close up to a line of text each and start higher; when
        // they still would (the most a soul shows, such as a Hollow that was a Quincy), the two section
        // headings go; and what still does not fit of the next step is left out.
        int bottom = height - DONE_TOP - 4;
        int below = next.size() * font.lineHeight;
        int y;
        if (drawRows(null, player, data, TOP, LINE, GAP, true) + below <= bottom) {
            y = drawRows(graphics, player, data, TOP, LINE, GAP, true);
        } else if (drawRows(null, player, data, TOP, COMPACT_LINE, 0, true) + below <= bottom) {
            y = drawRows(graphics, player, data, TOP, COMPACT_LINE, 0, true);
        } else if (drawRows(null, player, data, TIGHT_TOP, TIGHT_LINE, 0, true) + below <= bottom) {
            y = drawRows(graphics, player, data, TIGHT_TOP, TIGHT_LINE, 0, true);
        } else {
            y = drawRows(graphics, player, data, TIGHT_TOP, TIGHT_LINE, 0, false);
        }
        int left = width / 2 - PANEL_WIDTH / 2;
        for (FormattedCharSequence text : next) {
            if (y + font.lineHeight > bottom) {
                break;
            }
            graphics.drawString(font, text, left, y, GREY, false);
            y += font.lineHeight;
        }
    }

    /**
     * Every row above the next step, from the title at {@code top}, {@code line} apart with {@code gap}
     * more between sections, and the stats and powers headings only with {@code headers}; with no
     * {@code graphics}, only measures them. A reached Bankai is named on the row under the zanpakutō's (see
     * {@link BankaiLogic#screenLine}), or before then the time its Shikai has been held released, towards the slow
     * road ({@link SlowRoad#screenLine}); a Tenshintai set down has its rows there ({@link BankaiLogic#dollLines},
     * by the client's game time, the clock its window runs on). A Visored, while the server has the path on, says so
     * on its state row and gets two rows of its own after those (see {@link #visoredRows}), and a soul with a Hollow
     * rooted in it gets the rows of {@link #takeoverRows}.
     *
     * @return where the next step starts
     */
    private int drawRows(@Nullable GuiGraphics graphics, Player player, SoulData data, int top, int line, int gap,
                         boolean headers) {
        int left = width / 2 - PANEL_WIDTH / 2;
        int y = top;

        if (graphics != null) {
            graphics.drawCenteredString(font, title, width / 2, y, WHITE);
        }
        y += line + gap;
        boolean visored = VisoredLogic.enabled() && VisoredLogic.isVisored(data);
        boolean substitute = ShinigamiLogic.isSubstitute(data);
        // A Visored is named first, so a Substitute who has beaten its Hollow is both on one row.
        String stateKey = visored && substitute ? "screen.bleach.soul.state.visored_substitute"
                : visored ? "screen.bleach.soul.state.visored"
                : substitute ? "screen.bleach.soul.state.substitute"
                : data.state() == SoulState.HUMAN && QuincyLogic.isAwakened(data) ? "screen.bleach.soul.state.quincy"
                : ShinigamiLogic.isStudent(data) ? "screen.bleach.soul.state.student" : "screen.bleach.soul.state";
        text(graphics, Component.translatable(stateKey, data.state().displayName()), left, y, WHITE);
        y += line;
        text(graphics, Component.translatable("screen.bleach.soul.reiryoku",
                (int) Math.floor(data.reiryoku()), (int) Math.floor(data.maxReiryoku())), left, y, REIRYOKU);
        y += line;
        if (data.state() == SoulState.PLUS && data.chainMaxTicks() > 0) {
            boolean encroaching = SoulLogic.isEncroaching(data);
            text(graphics, Component.translatable(encroaching
                            ? "screen.bleach.soul.chain.encroaching" : "screen.bleach.soul.chain",
                    StringUtil.formatTickDuration(data.chainTicks(), TICKS_PER_SECOND)),
                    left, y, encroaching ? ENCROACHING : WHITE);
            y += line;
        } else if (data.state() == SoulState.HOLLOW && data.hollow().hungerStarted()) {
            // In the chain's place, as a Hollow has no chain: red while the hunger is sharp.
            float satiety = data.hollow().satiety();
            boolean hungry = HollowPlayerLogic.band(satiety) != HollowPlayerLogic.Band.FED;
            text(graphics, Component.translatable("screen.bleach.soul.hollow", (int) Math.floor(satiety)),
                    left, y, hungry ? ENCROACHING : WHITE);
            y += line;
        }
        if (SoulLogic.isAware(data)) {
            Component release = data.isReiatsuReleased()
                    ? Component.translatable("screen.bleach.soul.reiatsu.released",
                            BleachClient.REIATSU_RELEASE_KEY.getTranslatedKeyMessage())
                    : Component.translatable("screen.bleach.soul.reiatsu.sealed",
                            BleachClient.REIATSU_RELEASE_KEY.getTranslatedKeyMessage());
            text(graphics, release, left, y, GREY);
            y += line;
        }
        Component zanpakuto = zanpakutoLine(player, data);
        if (zanpakuto != null) {
            text(graphics, zanpakuto, left, y, GREY);
            y += line;
        }
        Component bankai = BankaiLogic.screenLine(data);
        if (bankai == null) {
            bankai = SlowRoad.screenLine(data);
        }
        if (bankai != null) {
            text(graphics, bankai, left, y, GREY);
            y += line;
        }
        for (Component doll : BankaiLogic.dollLines(data, player.level().getGameTime(), player.level().dimension())) {
            text(graphics, doll, left, y, GREY);
            y += line;
        }
        if (visored) {
            y = visoredRows(graphics, player, data, left, y, line);
        }
        y = takeoverRows(graphics, player, data, left, y, line);
        HuntRecord hunt = data.hunt();
        if (hunt.purified() + hunt.slain() + hunt.sentOn() > 0) {
            text(graphics, Component.translatable("screen.bleach.soul.hunt",
                    hunt.purified(), hunt.slain(), hunt.sentOn()), left, y, GREY);
            y += line;
        }
        BaitRecord bait = hunt.bait();
        if (bait.crushed() + bait.won() + bait.lost() > 0) {
            text(graphics, Component.translatable("screen.bleach.soul.bait",
                    bait.crushed(), bait.finales(), bait.won(), bait.lost()), left, y, GREY);
            y += line;
        }
        HollowProgress hollow = data.hollow();
        if (data.state() == SoulState.HOLLOW && hollow.meals() > 0) {
            text(graphics, Component.translatable("screen.bleach.soul.hollow.souls",
                    hollow.souls(), hollow.pluses(), hollow.totalHollows(), hollow.players()), left, y, GREY);
            y += line;
        }
        y = renderQuincy(graphics, player, data, left, y, line);
        y += gap;

        if (headers) {
            text(graphics, Component.translatable("screen.bleach.soul.stats"), left, y, GOLD);
            y += line;
        }
        int maxLevel = SoulLogic.maxStatLevel();
        for (Stat stat : Stat.values()) {
            if (graphics != null) {
                int level = data.level(stat);
                graphics.drawString(font, stat.displayName(), left + 6, y, WHITE);
                graphics.drawString(font, Component.translatable("screen.bleach.soul.level", level), left + 120, y, GREY);
                float progress = level >= maxLevel ? 1.0F : (float) data.xp(stat) / SoulData.xpToNextLevel(level);
                int barLeft = left + 165;
                int barWidth = PANEL_WIDTH - 165;
                graphics.fill(barLeft, y + 2, barLeft + barWidth, y + 6, XP_BACKGROUND);
                graphics.fill(barLeft, y + 2, barLeft + Math.round(barWidth * progress), y + 6, XP_FILL);
            }
            y += line;
        }
        y += gap;

        if (headers) {
            text(graphics, Component.translatable("screen.bleach.soul.powers"), left, y, GOLD);
            y += line;
        }
        boolean anyPower = false;
        for (PowerPath path : PowerPath.values()) {
            int tier = data.tier(path);
            // An Arrancar always rose from some form, even one an old command left at tier 0: it reads as a Gillian.
            boolean arrancar = path == PowerPath.HOLLOW && ArrancarLogic.isArrancar(data);
            if (tier > 0 || arrancar) {
                anyPower = true;
                // A Hollow's tier is its form: Hollow, Gillian, Adjuchas or Vasto Lorde; an Arrancar's, the form it
                // rose from, with its rank as the server's roster last said on a row of its own, so neither runs wide.
                Component row;
                if (path == PowerPath.HOLLOW && HollowPlayerLogic.isHollow(data)) {
                    row = Component.translatable("screen.bleach.soul.tier.hollow", path.displayName(),
                            HollowTier.of(data).displayName());
                } else if (arrancar) {
                    row = Component.translatable("screen.bleach.soul.tier.arrancar", path.displayName(),
                            ArrancarLogic.origin(data).displayName());
                } else {
                    row = Component.translatable("screen.bleach.soul.tier", path.displayName(), tier);
                }
                text(graphics, row, left + 6, y, WHITE);
                y += line;
                if (arrancar) {
                    text(graphics, Component.translatable("screen.bleach.soul.rank", ArrancarRank.of(data).label()),
                            left + 12, y, WHITE);
                    y += line;
                }
            }
        }
        if (!anyPower) {
            text(graphics, Component.translatable("screen.bleach.soul.no_powers"), left + 6, y, GREY);
            y += line;
        }
        return y + gap;
    }

    /**
     * A Visored's two rows: its mask (on, with the time left; forming again, with the wait; or ready) and the masked
     * Cero (the time its masks have been worn of the time it needs, or the key, once it has it).
     *
     * @return where the next row starts
     */
    private int visoredRows(@Nullable GuiGraphics graphics, Player player, SoulData data, int left, int y, int line) {
        long now = player.level().getGameTime();
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        Component mask;
        int colour = GREY;
        if (record.masked()) {
            mask = Component.translatable("screen.bleach.soul.visored.masked", duration(record.maskEndsAt() - now));
            colour = WHITE;
        } else if (now < record.maskReadyAt()) {
            mask = Component.translatable("screen.bleach.soul.visored.waiting", duration(record.maskReadyAt() - now));
        } else {
            mask = Component.translatable("screen.bleach.soul.visored.ready");
        }
        text(graphics, mask, left, y, colour);
        y += line;
        Component cero = VisoredLogic.ceroUnlocked(data)
                ? Component.translatable("screen.bleach.soul.visored.cero", HollowKeys.CERO.getTranslatedKeyMessage())
                : Component.translatable("screen.bleach.soul.visored.cero_training",
                        duration(record.maskTrainedTicks()), duration(VisoredLogic.ceroUnlockTicks()));
        text(graphics, cero, left, y, GREY);
        return y + line;
    }

    /**
     * The rows of a soul with a Hollow rooted in it, while the server has the path on: that the Hollow has its body now
     * (the look the server sends), or, before the win, that it may seize it near death; and, before the win with the fall
     * turned on, how many fights the Hollow has won, red when one more makes a Hollow.
     *
     * @return where the next row starts
     */
    private int takeoverRows(@Nullable GuiGraphics graphics, Player player, SoulData data, int left, int y, int line) {
        if (!VisoredLogic.enabled() || !VisoredLogic.isRooted(data) || !ShinigamiLogic.hasShinigamiPower(data)) {
            return y;
        }
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        if ((ClientSouls.appearance(player).hollowLook() & HollowPlayerLogic.LOOK_TAKEN_OVER) != 0) {
            text(graphics, Component.translatable("screen.bleach.soul.takeover.on"), left, y, WHITE);
            y += line;
        } else if (!record.subdued() && (BleachConfig.get(BleachConfig.VISORED_TAKEOVER_CHANCE) > 0.0
                || BleachConfig.get(BleachConfig.VISORED_TAKEOVER_CHANCE_AFTER_GETSUGA) > 0.0)) {
            text(graphics, Component.translatable("screen.bleach.soul.takeover.near_death"), left, y, GREY);
            y += line;
        }
        if (!record.subdued() && BleachConfig.get(BleachConfig.VISORED_FALL_INTO_HOLLOW)) {
            int needed = BleachConfig.get(BleachConfig.VISORED_LOSSES_TO_FALL);
            boolean last = record.losses() + 1 >= needed;
            text(graphics, last ? Component.translatable("screen.bleach.soul.takeover.last")
                    : Component.translatable("screen.bleach.soul.takeover.losses", record.losses(), needed),
                    left, y, last ? ENCROACHING : GREY);
            y += line;
        }
        return y;
    }

    /** Ticks as {@code mm:ss}, or {@code hh:mm:ss} from an hour; never below 0. */
    private static String duration(long ticks) {
        return StringUtil.formatTickDuration((int) Math.max(0L, Math.min(Integer.MAX_VALUE, ticks)), TICKS_PER_SECOND);
    }

    /** Draws a row, unless only measuring. */
    private void text(@Nullable GuiGraphics graphics, Component text, int x, int y, int color) {
        if (graphics != null) {
            graphics.drawString(font, text, x, y, color);
        }
    }

    /**
     * Up to six lines on Quincy blood, when the soul has it. Awakened: the blood, reishi held and the
     * most it can hold, and the Blut it is calling on; then the Hollow poison in it, souls its arrows
     * have destroyed, and how rich the reishi is where it stands (leaving out what other souls'
     * reiatsu adds, which only the server weighs); then up to three on its arts (see
     * {@link #artsLines}). Not yet awakened: just the blood. Either way, once a Quincy Elder has been
     * met, the lessons he has given last. With no {@code graphics}, only counts them.
     *
     * @return the line below the last one drawn
     */
    private int renderQuincy(@Nullable GuiGraphics graphics, Player player, SoulData data, int left, int y, int line) {
        Bloodline bloodline = QuincyLogic.bloodline(data);
        if (bloodline == Bloodline.NONE) {
            return y;
        }
        if (!QuincyLogic.isAwakened(data)) {
            text(graphics, Component.translatable("screen.bleach.soul.quincy.latent", bloodline.displayName()),
                    left, y, GREY);
            return lessonsLine(graphics, data, left, y + line, line);
        }
        List<Component> arts = artsLines(data, player, player.level().getGameTime());
        if (graphics == null) {
            return lessonsLine(null, data, left, y + (2 + arts.size()) * line, line);
        }
        QuincyProgress quincy = data.quincy();
        graphics.drawString(font, Component.translatable("screen.bleach.soul.quincy", bloodline.displayName(),
                Math.round(QuincyLogic.reishi(data)), Math.round(QuincyLogic.maxReishi(data)),
                QuincyLogic.activeBlut(data).displayName()), left, y, REISHI);
        y += line;
        Component density = Component.translatable(
                QuincyLogic.densityKey(QuincyLogic.placeDensity(player.level(), player.position())));
        graphics.drawString(font, Component.translatable("screen.bleach.soul.quincy.poison",
                Math.round(quincy.poison()), quincy.destroyed(), density),
                left, y, quincy.poison() >= QuincyLogic.POISON_BLOCK ? POISONED : GREY);
        y += line;
        for (Component art : arts) {
            graphics.drawString(font, art, left, y, GREY);
            y += line;
        }
        return lessonsLine(graphics, data, left, y, line);
    }

    /**
     * The Quincy Elder's lessons, given out of the most he gives, once the soul has met him. With no
     * {@code graphics}, only counts the line.
     *
     * @return the line below it, or {@code y} when there is none
     */
    private int lessonsLine(@Nullable GuiGraphics graphics, SoulData data, int left, int y, int line) {
        QuincyTeaching teaching = data.quincy().teaching();
        if (!teaching.elderMet()) {
            return y;
        }
        text(graphics, Component.translatable("screen.bleach.soul.quincy.lessons", teaching.lessons(),
                BleachConfig.get(BleachConfig.ELDER_MAX_LESSONS)), left, y, GREY);
        return y + line;
    }

    /**
     * How far an awakened Quincy's arts have come, in up to three lines. First where the power stands:
     * the Sanrei Glove's training or its mastery, the time Letzt Stil has left, the wait while sealed,
     * or Ginrei Kojaku. Then, while a Gintō is in hand, the spell it casts; otherwise, once the power
     * is restored, the way to Vollständig, or that it is ready (with its key) or taken. Last,
     * Ransōtengai: woven now, or once learned, how long until the threads hold again after a
     * collapse, or simply that it is known.
     */
    private static List<Component> artsLines(SoulData data, Player player, long now) {
        List<Component> lines = new ArrayList<>(3);
        QuincyArts arts = QuincyArtsLogic.arts(data);
        SanreiProgress glove = arts.glove();
        switch (arts.stage()) {
            case UNTOUCHED -> {
                if (glove.worn() && glove.mastered()) {
                    lines.add(Component.translatable("screen.bleach.soul.quincy.glove_mastered"));
                } else if (glove.worn()) {
                    lines.add(Component.translatable("screen.bleach.soul.quincy.glove", glove.days(),
                            BleachConfig.get(BleachConfig.SANREI_DAYS_TO_MASTER),
                            QuincyArtsLogic.gloveToday(glove, now, player.level().getDayTime()),
                            BleachConfig.get(BleachConfig.SANREI_POINTS_PER_DAY)));
                }
            }
            case LETZT_STIL -> lines.add(Component.translatable("screen.bleach.soul.quincy.letzt",
                    QuincyArtsLogic.formatTicks(arts.letztEndsAt() - now)).withColor(LETZT));
            case SEALED -> lines.add(QuincyArtsLogic.sealedWaitOver(data, now)
                    ? Component.translatable("screen.bleach.soul.quincy.sealed_ready")
                    : Component.translatable("screen.bleach.soul.quincy.sealed", QuincyArtsLogic.formatTicks(
                            arts.stageSince() + QuincyArtsLogic.sealedWaitTicks() - now)));
            case RESTORED -> lines.add(Component.translatable("screen.bleach.soul.quincy.ginrei").withColor(REISHI));
        }

        GintoSpell spell = QuincyArtsLogic.selectedSpell(data);
        if (holdsGinto(player) && QuincyArtsLogic.knowsSpell(data, spell)) {
            lines.add(Component.translatable("screen.bleach.soul.quincy.ginto", spell.displayName()));
        } else if (QuincyArtsLogic.inVollstandig(data)) {
            lines.add(Component.translatable("screen.bleach.soul.quincy.vollstandig_active").withColor(VOLLSTANDIG));
        } else if (QuincyArtsLogic.vollUnlocked(data)) {
            lines.add(Component.translatable("screen.bleach.soul.quincy.vollstandig_ready",
                    QuincyClient.FORM_KEY.getTranslatedKeyMessage()));
        } else if (QuincyArtsLogic.isRestored(data)) {
            int needed = Math.max(0, BleachConfig.get(BleachConfig.VOLL_TRAINING_AFTER_RESTORE));
            lines.add(Component.translatable("screen.bleach.soul.quincy.vollstandig_progress",
                    Math.max(0, needed - QuincyArtsLogic.vollTrainingLeft(data)), needed));
        }

        QuincyForms forms = arts.forms();
        if (QuincyRansotengai.isWoven(data)) {
            lines.add(Component.translatable("screen.bleach.soul.quincy.ransotengai_woven").withColor(REISHI));
        } else if (QuincyRansotengai.isKnown(data) && now < forms.ransotengaiReadyAt()) {
            lines.add(Component.translatable("screen.bleach.soul.quincy.ransotengai_cooldown",
                    QuincyArtsLogic.formatTicks(forms.ransotengaiReadyAt() - now)));
        } else if (QuincyRansotengai.isKnown(data)) {
            lines.add(Component.translatable("screen.bleach.soul.quincy.ransotengai_known"));
        }
        return lines;
    }

    /** Whether a Gintō, filled or empty, is in either hand. */
    private static boolean holdsGinto(Player player) {
        return isGinto(player.getMainHandItem()) || isGinto(player.getOffhandItem());
    }

    private static boolean isGinto(ItemStack stack) {
        return stack.is(ModItems.GINTO.get()) || stack.is(ModItems.EMPTY_GINTO.get());
    }

    /**
     * The zanpakutō's name and whether it is sealed, in Shikai, in Bankai or always released; before the
     * name, how far the Asauchi bond has grown, once it has begun or while a nameless Asauchi is carried.
     * Null when there is nothing to say. Whether it is in Shikai or Bankai is read from the player's own blade.
     * An Arrancar's line is its own blade's instead: in Resurrección (as the server last showed it),
     * sealed and settling with the time left before it can be released again, or sealed.
     */
    @Nullable
    private static Component zanpakutoLine(Player player, SoulData data) {
        if (ArrancarLogic.isArrancar(data)) {
            long now = player.level().getGameTime();
            long ready = data.hollow().arrancar().releaseReadyAt();
            Component state;
            if ((ClientSouls.appearance(player).hollowLook() & HollowPlayerLogic.LOOK_RESURRECCION) != 0) {
                state = Component.translatable("screen.bleach.soul.zanpakuto.resurreccion");
            } else if (now < ready) {
                state = Component.translatable("screen.bleach.soul.zanpakuto.settling",
                        StringUtil.formatTickDuration((int) Math.min(Integer.MAX_VALUE, ready - now), TICKS_PER_SECOND));
            } else {
                state = Component.translatable("screen.bleach.soul.zanpakuto.sealed");
            }
            Component name = ArrancarLogic.blade(player.getUUID()).displayName();
            return Component.translatable("screen.bleach.soul.zanpakuto", name, state);
        }
        ZanpakutoBond bond = data.zanpakuto();
        if (bond.named()) {
            ZanpakutoStage stage = ZanpakutoItem.blade(ZanpakutoLogic.findOwnBlade(player)).stage();
            String state;
            if (stage == ZanpakutoStage.BANKAI) {
                state = "bankai";
            } else if (bond.constant()) {
                state = bond.firstReleaseDone() ? "constant" : "constant_waiting";
            } else {
                state = stage.isReleased() ? "released" : "sealed";
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
     * What this soul can do next. In Soul Society a Plus, a soul that may enrol and a student are shown the
     * way to the Academy, unless they are already there; a Plus in another world but the Overworld is told
     * that its chain holds there, or in the Dangai that no Shinigami on duty comes there, and a Plus who could
     * gnaw its own chain is told it can, with the Devour key. A Hollow is told how to feed with
     * that key and, once it has, how many souls are within it. A Menos
     * is told its form, the Cero key, and how far it has come towards the next form; an Adjuchas also
     * how long it may go without a Hollow before it sinks back, wherever the server lets it, and a Gillian how
     * to call down Negación, where the server lets it. An Adjuchas or Vasto Lorde is told it can tear off its
     * mask, where the server lets it; an Arrancar, the name of its blade and the keys that release and seal it,
     * the Cero key, the keys for Sonído, Bala and Pesquisa, how much of a blow from a soul as strong as it is its
     * Hierro turns aside, released or not as it is now, and what a Gran Rey Cero costs or how much maximum
     * reiryoku it still needs.
     */
    private static Component nextStep(Player player, SoulData data) {
        String key = ShinigamiLogic.nextStepKey(data, player.level().getGameTime());
        Component devour = HollowKeys.DEVOUR.getTranslatedKeyMessage();
        if (key.equals("hollow")) {
            return Component.translatable("screen.bleach.soul.next.hollow", devour);
        }
        if (key.equals("hollow.fed")) {
            return Component.translatable("screen.bleach.soul.next.hollow.fed", devour, data.hollow().souls());
        }
        Component menos = menosStep(key, data, devour);
        if (menos != null) {
            if (!ArrancarLogic.canTear(HollowTier.of(data)) || !BleachConfig.get(BleachConfig.NATURAL_ARRANCAR)) {
                return menos;
            }
            return menos.copy().append(" ").append(Component.translatable("screen.bleach.soul.next.hollow.tear",
                    ZanpakutoKeys.RELEASE.getTranslatedKeyMessage()));
        }
        if (key.equals("arrancar")) {
            // The release is the server's to know: this client sees it only in the look the server sends.
            boolean released = (ClientSouls.appearance(player).hollowLook() & HollowPlayerLogic.LOOK_RESURRECCION) != 0;
            return Component.translatable("screen.bleach.soul.next.arrancar",
                    ArrancarLogic.blade(player.getUUID()).displayName(), ZanpakutoKeys.RELEASE.getTranslatedKeyMessage(),
                    ZanpakutoKeys.SEAL.getTranslatedKeyMessage(), HollowKeys.CERO.getTranslatedKeyMessage())
                    .append(" ").append(Component.translatable("screen.bleach.soul.next.arrancar.arts",
                            BleachClient.FLASH_STEP_KEY.getTranslatedKeyMessage(),
                            ZanpakutoKeys.TECHNIQUE.getTranslatedKeyMessage(), devour,
                            Math.round(Hierro.share(data, released) * 100.0F)))
                    .append(" ").append(granReyCero(data));
        }
        if (!key.equals("plus")) {
            // A soul that may enrol, or is training, is shown the way to the Academy too (see withWay).
            Component step = Component.translatable("screen.bleach.soul.next." + key);
            return key.equals("rukongai") || key.startsWith("student") ? withWay(step, player) : step;
        }
        // The Shinigami on duty is promised only where the server sends one: in the Overworld, not in creative or spectator.
        boolean duty = BleachConfig.get(BleachConfig.KONSO_DUTY_SHINIGAMI) && !player.isCreative() && !player.isSpectator();
        boolean overworld = player.level().dimension() == Level.OVERWORLD;
        Component plus;
        // No Shinigami on duty comes to Soul Society, where the chain holds: there the Academy itself is the way.
        if (player.level().dimension() == BleachWorlds.SOUL_SOCIETY) {
            plus = withWay(Component.translatable("screen.bleach.soul.next.plus.academy"), player);
        } else if (!SoulLogic.chainWearsIn(player.level().dimension())) {
            plus = Component.translatable(
                    duty ? "screen.bleach.soul.next.plus.holds.duty" : "screen.bleach.soul.next.plus.holds");
        } else if (!overworld && duty) {
            plus = Component.translatable("screen.bleach.soul.next.plus.elsewhere");
        } else {
            plus = Component.translatable("screen.bleach.soul.next.plus");
        }
        if (overworld && duty) {
            plus = plus.copy().append(" ").append(Component.translatable("screen.bleach.soul.next.plus.duty"));
        }
        // Only where the server would let it gnaw: not for a Quincy, nor with Hollowfication or gnawing off.
        if (DevourLogic.gnawRefusal(player, data) == DevourLogic.Gnaw.GNAWING) {
            return plus.copy().append(" ").append(Component.translatable("screen.bleach.soul.next.plus.gnaw", devour));
        }
        return plus;
    }

    /**
     * This step, and after it the way to the Academy from the soul's block, in Soul Society only and not at the Academy
     * itself, where "about 50 blocks away" would mislead.
     */
    private static Component withWay(Component step, Player player) {
        BlockPos at = player.blockPosition();
        if (player.level().dimension() != BleachWorlds.SOUL_SOCIETY || AcademyLocator.atAcademy(at)) {
            return step;
        }
        return step.copy().append(" ").append(AcademyLocator.wayLine(at));
    }

    /**
     * The next step of a Menos, by its step key, or null for any other key. The counts are those the server
     * last sent, read against the server's own settings, as the HUD reads them. Each Gillian step ends with
     * how to call down Negación, unless the server has it off for players, and each Adjuchas step with the
     * minutes left before it sinks back, unless the server has regression off.
     */
    @Nullable
    private static Component menosStep(String key, SoulData data, Component devour) {
        Component cero = HollowKeys.CERO.getTranslatedKeyMessage();
        HollowProgress.Evolution evolution = data.hollow().evolution();
        HollowTier.Regression regression = BleachConfig.get(BleachConfig.ADJUCHAS_REGRESSION);
        Component step = switch (key) {
            // In soft mode each fall back into a Gillian asks for as many Hollows again.
            case "hollow.gillian" -> Component.translatable("screen.bleach.soul.next.hollow.gillian", devour, cero,
                    evolution.tierHollows(), BleachConfig.get(BleachConfig.ADJUCHAS_HOLLOWS)
                            * (regression == HollowTier.Regression.SOFT ? 1L + evolution.regressions() : 1L),
                    evolution.tierMenos(), BleachConfig.get(BleachConfig.ADJUCHAS_MENOS));
            case "hollow.adjuchas" -> Component.translatable("screen.bleach.soul.next.hollow.adjuchas", devour, cero,
                    evolution.tierHollows(), BleachConfig.get(BleachConfig.VASTO_HOLLOWS), evolution.tierMenos(),
                    BleachConfig.get(BleachConfig.VASTO_ADJUCHAS));
            case "hollow.gillian.lost", "hollow.adjuchas.stalled", "hollow.adjuchas.full" ->
                    Component.translatable("screen.bleach.soul.next." + key, devour, cero);
            case "hollow.vasto_lorde" -> Component.translatable("screen.bleach.soul.next.hollow.vasto_lorde", cero);
            default -> null;
        };
        if (step != null && key.startsWith("hollow.gillian") && BleachConfig.get(BleachConfig.NEGACION_PLAYER_CAST)) {
            step = step.copy().append(" ").append(Component.translatable("screen.bleach.soul.next.hollow.gillian.negacion",
                    ZanpakutoKeys.TECHNIQUE.getTranslatedKeyMessage()));
        }
        if (step == null || !key.startsWith("hollow.adjuchas") || regression == HollowTier.Regression.OFF) {
            return step;
        }
        return step.copy().append(" ").append(Component.translatable("screen.bleach.soul.next.hollow.adjuchas.clock",
                MenosEvolution.minutesLeft(data)));
    }

    /**
     * An Arrancar's Gran Rey Cero: how to fire it and what it costs (its blood in hearts, its reiryoku and the
     * wait after it), or, until it holds a seat among the Espada, what a seat needs and the maximum reiryoku it
     * has. Both read only the synced soul and the server's settings, which this client has.
     */
    private static Component granReyCero(SoulData data) {
        if (GranReyCero.eligible(data)) {
            return Component.translatable("screen.bleach.soul.next.arrancar.gran_rey_cero",
                    HollowKeys.CERO.getTranslatedKeyMessage(), GranReyCero.bloodHearts(), Math.round(GranReyCero.cost(data)),
                    BleachConfig.get(BleachConfig.GRAN_REY_CERO_COOLDOWN_SECONDS));
        }
        return Component.translatable("screen.bleach.soul.next.arrancar.gran_rey_cero.locked",
                Math.round(ArrancarRank.espadaMinMaxReiryoku()), Math.round(data.maxReiryoku()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
