package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.BaitRecord;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.Hierro;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.HuntRecord;
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
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
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
 * The soul screen: state, reiryoku, a Plus's Chain of Fate or a Hollow's soul hunger, the zanpakutō
 * (or until it has a name, the Asauchi bond; for an Arrancar, its own blade and whether it is released),
 * Hollows purified and slain and Pluses sent on, Hollow Bait crushed and contests with the Quincy Hunter,
 * the souls a Hollow has devoured and what they were, Quincy blood (its reishi and Blut, the Hollow
 * poison in it, souls destroyed and how rich the reishi is here, how far its arts have come, and the
 * Quincy Elder's lessons), stats with training progress, awakened powers (an Arrancar's with the form it
 * rose from) and what comes next.
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

    public SoulStatsScreen() {
        super(Component.translatable("screen.bleach.soul"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - DONE_TOP, 200, 20)
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
        List<FormattedCharSequence> next = font.split(nextStep(player, data), PANEL_WIDTH);
        // Everything stays above the Done button: when the next step would reach it, the rows close up;
        // when even the closed-up rows would, they close up to a line of text each and start higher; when
        // those still would (the most a soul shows, such as a Hollow that was a Quincy), the two section
        // headings go; and what still does not fit of the next step is left out.
        int bottom = height - DONE_TOP - 4;
        int y;
        if (drawRows(null, player, data, TOP, LINE, GAP, true) + next.size() * font.lineHeight <= bottom) {
            y = drawRows(graphics, player, data, TOP, LINE, GAP, true);
        } else if (drawRows(null, player, data, TOP, COMPACT_LINE, 0, true) <= bottom) {
            y = drawRows(graphics, player, data, TOP, COMPACT_LINE, 0, true);
        } else if (drawRows(null, player, data, TIGHT_TOP, TIGHT_LINE, 0, true) <= bottom) {
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
     * {@code graphics}, only measures them.
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
        String stateKey = ShinigamiLogic.isSubstitute(data) ? "screen.bleach.soul.state.substitute"
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
                // rose from.
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
            }
        }
        if (!anyPower) {
            text(graphics, Component.translatable("screen.bleach.soul.no_powers"), left + 6, y, GREY);
            y += line;
        }
        return y + gap;
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
     * The zanpakutō's name and whether it is sealed, in Shikai or always released; before the name,
     * how far the Asauchi bond has grown, once it has begun or while a nameless Asauchi is carried.
     * Null when there is nothing to say. Whether it is in Shikai is read from the player's own blade.
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
     * direction too, and a Plus who could gnaw its own chain is told it can, with the Devour key. A
     * Hollow is told how to feed with that key and, once it has, how many souls are within it. A Menos
     * is told its form, the Cero key, and how far it has come towards the next form; an Adjuchas also
     * how long it may go without a Hollow before it sinks back, wherever the server lets it. An Adjuchas
     * or Vasto Lorde is told it can tear off its mask, where the server lets it; an Arrancar, the name of
     * its blade and the keys that release and seal it, the Cero key, the keys for Sonído, Bala and Pesquisa,
     * and how much of a blow from a soul as strong as it is its Hierro turns aside, released or not as it is now.
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
                            Math.round(Hierro.share(data, released) * 100.0F)));
        }
        if (!key.equals("plus")) {
            return Component.translatable("screen.bleach.soul.next." + key);
        }
        Component plus;
        if (data.shinigami().academyPos().isPresent() && player.level().dimension() == Level.OVERWORLD) {
            BlockPos academy = data.shinigami().academyPos().get();
            double dx = academy.getX() + 0.5 - player.getX();
            double dz = academy.getZ() + 0.5 - player.getZ();
            plus = Component.translatable("screen.bleach.soul.next.plus.academy",
                    Component.translatable(ShinigamiLogic.compassKey(dx, dz)),
                    ShinigamiLogic.roundedDistance(Math.sqrt(dx * dx + dz * dz)));
        } else {
            plus = Component.translatable("screen.bleach.soul.next.plus");
        }
        // Only where the server would let it gnaw: not for a Quincy, nor with Hollowfication or gnawing off.
        if (DevourLogic.gnawRefusal(player, data) == DevourLogic.Gnaw.GNAWING) {
            return plus.copy().append(" ").append(Component.translatable("screen.bleach.soul.next.plus.gnaw", devour));
        }
        return plus;
    }

    /**
     * The next step of a Menos, by its step key, or null for any other key. The counts are those the server
     * last sent, read against the server's own settings, as the HUD reads them. Each Adjuchas step ends with
     * the minutes left before it sinks back, unless the server has regression off.
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
        if (step == null || !key.startsWith("hollow.adjuchas") || regression == HollowTier.Regression.OFF) {
            return step;
        }
        return step.copy().append(" ").append(Component.translatable("screen.bleach.soul.next.hollow.adjuchas.clock",
                MenosEvolution.minutesLeft(data)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
