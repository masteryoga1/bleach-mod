package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DankuWall;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.network.KidoKeyPayload;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.KidoHeal;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;

/**
 * The Kidō page, opened from the soul screen: the spells this soul knows, chosen with a click, and the details of the
 * one under the mouse, else of the chosen one. The server decides the choice
 * ({@link io.github.masteryoga1.bleach.soul.KidoEvents#onKey}); the page only asks.
 */
public final class KidoScreen extends Screen {
    private static final int MAX_PANEL = 400;
    /** The list's width, its scroll bar included, and where the details begin, from the panel's left edge. */
    private static final int LIST_WIDTH = 132, DETAILS_OFFSET = 140;
    private static final int TITLE_Y = 8, STATUS_Y = 20, LIST_TOP = 32;
    /** The pitch of a row, of a line of details and of a line of incantation. */
    private static final int ROW = 11, LINE = 10, CHANT_LINE = 9;
    /** How far above the bottom the Back button's top edge is, as the soul screen's Done. */
    private static final int BACK_TOP = 28;
    private static final int WHITE = 0xFFFFFF, GREY = 0xA0A0A0, WARNING = 0xFFC890;
    private static final int CHOSEN = 0xFF6FBF4F, CHOSEN_FILL = 0x80305A30, HOVER_FILL = 0x40FFFFFF;
    private static final int RULE = 0xFF505050, BAR = 0xFF303030, THUMB = 0xFFA0A0A0;
    /** What ends a line cut to its width. */
    private static final Component ELLIPSIS = Component.literal("…");

    private final Screen parent;
    /** The first row shown, as the wheel has scrolled. */
    private int first;
    @Nullable
    private Button back;
    /** The rows the last frame drew, which clicks and the wheel read; none before the first frame. */
    private Frame frame = Frame.NONE;

    public KidoScreen(Screen parent) {
        super(Component.translatable("screen.bleach.kido"));
        this.parent = parent;
    }

    /** What the page shows this frame, read afresh from the client's copy of the soul. */
    private record View(Player player, SoulData data, List<Holder.Reference<KidoSpell>> listed,
                        Optional<Holder.Reference<KidoSpell>> chosen, Optional<Holder.Reference<KidoSpell>> next) {
    }

    /** The listed spells from {@code first}, {@code rows} of them at most, whether the footer was drawn, and the edges. */
    private record Frame(List<Holder.Reference<KidoSpell>> listed, int first, int rows, boolean footer, int left,
                         int right, int bottom) {
        static final Frame NONE = new Frame(List.of(), 0, 0, false, 0, 0, 0);
    }

    /** This frame's view, or null with no player. */
    @Nullable
    private View view() {
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return null;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        Optional<Registry<KidoSpell>> spells = KidoSpell.registry(player.level());
        return new View(player, data, spells.map(found -> KidoLogic.known(data, found)).orElse(List.of()),
                spells.flatMap(found -> KidoLogic.chosen(data, found)),
                spells.flatMap(found -> KidoLogic.nextToLearn(data, found)));
    }

    private int panelWidth() {
        return Math.min(width - 16, MAX_PANEL);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    /** The lowest y the list and the details reach: above the Back button, and above the next spell's line. */
    private int bottom(boolean footer) {
        return height - BACK_TOP - 4 - (footer ? ROW : 0);
    }

    @Override
    protected void init() {
        back = addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 100, height - BACK_TOP, 200, 20).build());
    }

    /** The Back button, or null before the page is shown (the smoke test presses it). */
    @Nullable
    public Button backButton() {
        return back;
    }

    /** Back to the soul screen, by the button or Escape. */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** Closes the page once the soul may no longer use Kidō. */
    @Override
    public void tick() {
        if (minecraft != null && (minecraft.player == null
                || !KidoLogic.trained(minecraft.player.getData(SoulAttachments.SOUL)))) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Draws the page, keeping the rows as drawn for this frame's hover and the next click. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        View view = view();
        if (view == null) {
            // Nothing drawn, so nothing to click.
            frame = Frame.NONE;
            return;
        }
        int left = left(), right = left + panelWidth();
        boolean footer = view.next().isPresent();
        int bottom = bottom(footer), rows = Math.max(1, (bottom - LIST_TOP) / ROW);
        List<Holder.Reference<KidoSpell>> listed = view.listed();
        first = Mth.clamp(first, 0, Math.max(0, listed.size() - rows));
        // The rows as drawn, which this frame's hover and the next click read.
        frame = new Frame(listed, first, rows, footer, left, right, bottom);
        graphics.drawCenteredString(font, title, width / 2, TITLE_Y, WHITE);
        Component level = Component.translatable("screen.bleach.soul.level", view.data().level(Stat.KIDO));
        graphics.drawString(font, level, right - font.width(level), TITLE_Y, GREY);
        Component status = status(view);
        if (status != null) {
            graphics.drawCenteredString(font, cut(status, width - 8), width / 2, STATUS_Y, WARNING);
        }
        int hoveredRow = rowAt(mouseX, mouseY);
        Holder.Reference<KidoSpell> shown = hoveredRow < 0 ? null : listed.get(hoveredRow);
        if (listed.isEmpty()) {
            graphics.drawString(font, Component.translatable("message.bleach.kido.none_known"), left + 7, LIST_TOP + 1,
                    GREY);
        }
        for (int index = first; index < Math.min(listed.size(), first + rows); index++) {
            KidoSpell spell = listed.get(index).value();
            int y = LIST_TOP + (index - first) * ROW;
            // A rule between the kinds, in the gap above the row.
            if (index > first && spell.kind() != listed.get(index - 1).value().kind()) {
                graphics.hLine(left, left + LIST_WIDTH - 5, y - 1, RULE);
            }
            if (view.chosen().orElse(null) == listed.get(index)) {
                graphics.fill(left, y, left + LIST_WIDTH - 4, y + ROW - 1, CHOSEN_FILL);
                graphics.renderOutline(left, y, LIST_WIDTH - 4, ROW - 1, CHOSEN);
            } else if (index == hoveredRow) {
                graphics.fill(left, y, left + LIST_WIDTH - 4, y + ROW - 1, HOVER_FILL);
            }
            graphics.fill(left + 2, y + 1, left + 4, y + 8, 0xFF000000 | spell.look().colour());
            graphics.drawString(font, cut(spell.title(), LIST_WIDTH - 11), left + 7, y + 1, WHITE);
        }
        if (listed.size() > rows) {
            int track = rows * ROW - 1, thumb = Math.max(8, track * rows / listed.size());
            int top = LIST_TOP + (track - thumb) * first / (listed.size() - rows);
            graphics.fill(left + LIST_WIDTH - 2, LIST_TOP, left + LIST_WIDTH, LIST_TOP + track, BAR);
            graphics.fill(left + LIST_WIDTH - 2, top, left + LIST_WIDTH, top + thumb, THUMB);
        }
        if (footer) {
            KidoSpell next = view.next().get().value();
            graphics.drawString(font, cut(Component.translatable("screen.bleach.kido.next", next.title(), next.level()),
                    right - left), left, bottom + 2, GREY);
            if (shown == null && footerAt(mouseX, mouseY)) {
                shown = view.next().get();
            }
        }
        if (shown == null) {
            shown = view.chosen().orElse(null);
        }
        if (shown != null) {
            details(graphics, view, shown.value(), left + DETAILS_OFFSET, right - left - DETAILS_OFFSET, bottom);
        }
    }

    /** The spell's details down the right: title, kind, levels, what it costs and does, and its incantation. */
    private void details(GuiGraphics graphics, View view, KidoSpell spell, int x, int width, int bottom) {
        int kido = view.data().level(Stat.KIDO);
        boolean chat = BleachConfig.get(BleachConfig.KIDO_CHAT_CASTING);
        int y = draw(graphics, spell.title(), x, LIST_TOP, width, bottom, LINE, WHITE);
        Component kind = Component.translatable(spell.kind().translationKey());
        Component gloss = gloss(spell);
        y = draw(graphics, gloss == null ? kind : Component.translatable("screen.bleach.kido.kind", kind, gloss), x, y,
                width, bottom, LINE, GREY);
        // A chant is shown only where the server would hear one (6b's rule).
        Component levels = !spell.chantable() ? Component.translatable("screen.bleach.kido.levels.cast", spell.level())
                : chat ? Component.translatable("screen.bleach.kido.levels", spell.level(), spell.byNameLevel())
                : Component.translatable("screen.bleach.kido.levels.by_name", spell.byNameLevel());
        y = draw(graphics, levels, x, y, width, bottom, LINE, GREY);
        List<Component> lines = new ArrayList<>();
        if (spell.shape() == KidoSpell.Shape.HEAL) {
            healLines(lines, view, spell, kido);
        } else {
            lines.add(Component.translatable("screen.bleach.kido.cost", Math.round(KidoLogic.cost(spell, view.data())),
                    number(spell.cooldownTicks() / 20.0)));
            Component does = does(spell, view);
            if (does != null) {
                lines.add(does);
            }
        }
        for (Component line : lines) {
            y = draw(graphics, line, x, y, width, bottom, LINE, GREY);
        }
        if (spell.chantable() && chat) {
            y = draw(graphics, Component.translatable("screen.bleach.kido.incantation"), x, y + 4, width, bottom, LINE,
                    WHITE);
            for (String incantation : spell.incantations()) {
                y = draw(graphics, Component.literal(incantation).withStyle(ChatFormatting.ITALIC), x, y, width, bottom,
                        CHANT_LINE, GREY);
            }
        }
    }

    /** Draws the text wrapped to the width, each line that fits above the bottom; returns the y below it. */
    private int draw(GuiGraphics graphics, Component text, int x, int y, int width, int bottom, int pitch, int colour) {
        // Empty text splits to no line at all, so it draws nothing and moves nothing down.
        for (FormattedCharSequence line : font.split(text, width)) {
            if (y + font.lineHeight <= bottom) {
                graphics.drawString(font, line, x, y, colour);
            }
            y += pitch;
        }
        return y;
    }

    /** The text on one line within this width, ended with "…" when cut; empty text gives an empty line. */
    private FormattedCharSequence cut(Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        FormattedText kept = font.substrByWidth(text, Math.max(0, width - font.width(ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(kept, ELLIPSIS));
    }

    /** The name's gloss in italics, from its own key and ".gloss", or null when the language has none. */
    @Nullable
    private static Component gloss(KidoSpell spell) {
        return spell.name().getContents() instanceof TranslatableContents name && I18n.exists(name.getKey() + ".gloss")
                ? Component.translatable(name.getKey() + ".gloss").withStyle(ChatFormatting.ITALIC) : null;
    }

    /** Kaidō's lines: its cost and reach, what this soul's tier heals and lifts, the next tier, and the self-heal. */
    private static void healLines(List<Component> lines, View view, KidoSpell spell, int kido) {
        KidoHeal.Tier tier = KidoHeal.tier(kido);
        lines.add(Component.translatable("screen.bleach.kido.heal.cost", Math.round(KidoLogic.cost(spell, view.data())),
                number(spell.effect().range())));
        lines.add(Component.translatable("screen.bleach.kido.heal.rate", hearts(tier.perSecond()),
                Math.round(tier.upTo() * 100)));
        if (!tier.clears().isEmpty()) {
            lines.add(Component.translatable("screen.bleach.kido.heal.clears", effects(tier.clears())));
        }
        for (KidoHeal.Tier later : KidoHeal.Tier.values()) {
            if (later.compareTo(tier) > 0) {
                lines.add(Component.translatable("screen.bleach.kido.heal.next", later.from(), hearts(later.perSecond()),
                        Math.round(later.upTo() * 100)));
                break;
            }
        }
        lines.add(kido >= KidoHeal.SELF_LEVEL ? Component.translatable("screen.bleach.kido.heal.self")
                : Component.translatable("screen.bleach.kido.heal.self.later", KidoHeal.SELF_LEVEL));
        lines.add(Component.translatable("screen.bleach.kido.heal.reiryoku"));
    }

    /** What the spell does, by its shape, at this soul's full-share strength; null for a heal or no shape. */
    @Nullable
    private static Component does(KidoSpell spell, View view) {
        KidoSpell.Effect effect = spell.effect();
        KidoSpell.Ward ward = effect.ward();
        Component hearts = hearts(KidoLogic.power(spell, view.data(), 1.0F) * KidoLogic.handShare(spell, view.player()));
        return switch (spell.shape()) {
            case BEAM -> Component.translatable("screen.bleach.kido.does.beam", hearts, number(effect.range()));
            case GUST -> Component.translatable("screen.bleach.kido.does.gust", number(effect.angle()),
                    number(Math.min(effect.range(), KidoSpell.GUST_MAX_RANGE)), hearts);
            case BIND -> Component.translatable(effect.boundSeconds() > 0 && effect.heldSeconds() > 0
                            ? "screen.bleach.kido.does.bind" : effect.boundSeconds() > 0
                            ? "screen.bleach.kido.does.bind.arms" : "screen.bleach.kido.does.bind.body",
                    number(Math.max(effect.boundSeconds(), effect.heldSeconds())), number(effect.range()));
            case SMOKE -> Component.translatable("screen.bleach.kido.does.smoke", number(ward.seconds()),
                    number(ward.speedSeconds()));
            case SHIELD -> Component.translatable("screen.bleach.kido.does.shield", hearts, number(ward.seconds()));
            // As the wall is raised (DankuWall.raise), so a datapack's oversized wall reads as the one it gets.
            case WALL -> Component.translatable("screen.bleach.kido.does.wall",
                    number(2.0 * Mth.clamp(effect.radius(), DankuWall.MIN_SIZE, DankuWall.MAX_HALF_WIDTH)),
                    number(Mth.clamp(effect.range(), 1.0, DankuWall.MAX_DISTANCE)), number(ward.seconds()));
            case NONE, HEAL -> null;
        };
    }

    /** Health as "half a heart", "a heart" or so many hearts, to the half. */
    private static Component hearts(float health) {
        int halves = Math.round(health);
        if (halves == 1) {
            return Component.translatable("screen.bleach.kido.half_heart");
        }
        return halves == 2 ? Component.translatable("screen.bleach.kido.heart")
                : Component.translatable("screen.bleach.kido.hearts", number(halves / 2.0));
    }

    /** The number to a tenth, without a ".0". */
    private static String number(double value) {
        double tenths = Math.round(value * 10.0) / 10.0;
        return tenths == Math.rint(tenths) ? Long.toString((long) tenths) : String.format(Locale.ROOT, "%.1f", tenths);
    }

    /** The effects' names joined with "and"; never called with none. */
    private static Component effects(List<Holder<MobEffect>> effects) {
        Component joined = effects.get(0).value().getDisplayName();
        for (int index = 1; index < effects.size(); index++) {
            joined = Component.translatable("screen.bleach.kido.and", joined, effects.get(index).value().getDisplayName());
        }
        return joined;
    }

    /** Why the Kidō key would refuse now, as far as the client can know it (no cooldown or chant), or null. */
    @Nullable
    private static Component status(View view) {
        Player player = view.player();
        SoulData data = view.data();
        if ((ClientSouls.appearance(player).hollowLook() & HollowPlayerLogic.LOOK_TAKEN_OVER) != 0) {
            return refusal(KidoLogic.Refusal.TAKEN);
        }
        if (player.hasEffect(ModEffects.BAKUDO_BOUND)) {
            return refusal(KidoLogic.Refusal.BOUND);
        }
        if (ShinigamiLogic.isPowerLent(data, player.level().getGameTime())) {
            return refusal(KidoLogic.Refusal.LENT);
        }
        KidoSpell spell = view.chosen().map(Holder::value).orElse(null);
        if (spell == null) {
            return null;
        }
        if (KidoLogic.gate(spell, KidoLogic.Form.NAME, data.level(Stat.KIDO)) == KidoLogic.Refusal.NEEDS_NAME_LEVEL) {
            return refusal(KidoLogic.Refusal.NEEDS_NAME_LEVEL, spell.title(), spell.byNameLevel());
        }
        if (!player.isCreative() && data.reiryoku() - KidoLogic.cost(spell, data) < KidoLogic.MIN_LEFT) {
            return refusal(KidoLogic.Refusal.TOO_WEAK, spell.title());
        }
        return null;
    }

    /** The refusal's own action-bar line. */
    private static Component refusal(KidoLogic.Refusal refusal, Object... args) {
        return Component.translatable(refusal.messageKey(), args);
    }

    /** The index of the listed spell the last frame drew at this point, or -1. */
    private int rowAt(double x, double y) {
        if (x < frame.left() || x >= frame.left() + LIST_WIDTH - 4 || y < LIST_TOP) {
            return -1;
        }
        int index = frame.first() + (int) ((y - LIST_TOP) / ROW);
        return index < Math.min(frame.listed().size(), frame.first() + frame.rows()) ? index : -1;
    }

    /** The index of the spell of this id among the rows the last frame drew, or -1. */
    private int rowOf(ResourceLocation id) {
        for (int index = frame.first(); index < Math.min(frame.listed().size(), frame.first() + frame.rows()); index++) {
            if (frame.listed().get(index).key().location().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    /** Whether this point is on the next spell's line as the last frame drew it. */
    private boolean footerAt(double x, double y) {
        return frame.footer() && x >= frame.left() && x < frame.right() && y > frame.bottom()
                && y <= frame.bottom() + ROW;
    }

    /** A left click on a row the last frame drew asks for its spell; any other click goes to the Back button. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (index < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        choose(frame.listed().get(index).key().location());
        return true;
    }

    /** Asks the server to choose this spell, with a button's click; the mark moves when the soul syncs back. */
    private void choose(ResourceLocation id) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        BleachNetwork.requestKidoKey(KidoKeyPayload.KidoKey.SELECT, Optional.of(id));
    }

    /** The wheel moves the list a row a notch, within the rows the last frame listed. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        // The next frame clamps it again, to the list it then draws.
        first = Mth.clamp(first - (int) Math.signum(scrollY), 0, Math.max(0, frame.listed().size() - frame.rows()));
        return true;
    }

    /** The spells the last frame listed, in their order, or none before the first frame (the smoke test reads it). */
    public List<Holder.Reference<KidoSpell>> listed() {
        return frame.listed();
    }

    /** Clicks this spell's row where the mouse would, if the last frame drew it; false otherwise. For the smoke test. */
    public boolean clickRow(ResourceLocation id) {
        int index = rowOf(id);
        return index >= 0 && mouseClicked(frame.left() + 66, LIST_TOP + (index - frame.first()) * ROW + 5.5, 0);
    }
}
