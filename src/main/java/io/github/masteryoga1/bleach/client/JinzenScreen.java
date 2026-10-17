package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStep;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Jinzen: the world inside the blade over the whole screen, what the spirit says, and in a meeting
 * its test. The server builds every page (see {@code JinzenLogic}) and judges every answer; this
 * screen shows the page and sends back which ribbon, answer or strike was chosen, and gets the next
 * page in reply. It does not pause the game, so the world and Jinzen's timers keep running, and it
 * closes itself once the player stops using the blade (hurt, moved, or out of time). Closing it lets
 * go of the blade, which ends Jinzen; leaving early is never a failure.
 */
public final class JinzenScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    private static final int PARAGRAPH_GAP = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int RIBBON_HEIGHT = 64;
    private static final int JAWS_HEIGHT = 56;
    private static final int TIMER_HEIGHT = 18;
    /** How long the inner world takes to fade in from black when the screen first opens, in ticks. */
    private static final float FADE_IN_TICKS = 12.0F;
    /** Ticks without a reply from the server before the choices open again. */
    private static final int ANSWER_TIMEOUT = 40;

    // The fangs, measured in the distance the gap may travel either side of the blade.
    /** How fast the gap slides at first and when the jaws meet, in that distance per tick. */
    private static final float GAP_START_SPEED = 0.02F;
    private static final float GAP_END_SPEED = 0.04F;
    private static final float GAP_HALF_WIDTH = 0.08F;
    private static final float BLADE_HALF_WIDTH = 0.01F;
    /** How much of half the jaws' width the gap travels over. */
    private static final float GAP_TRAVEL = 0.85F;
    private static final int TOOTH_WIDTH = 10;
    private static final int TOOTH_LENGTH = 8;

    private static final int PANEL = 0xC0101014;
    private static final int TITLE = 0xFFD27F;
    private static final int TEXT = 0xE6E6E6;
    private static final int PROMPT = 0xFFFFFF;
    private static final int TIMER = 0xFFD0E8F0;
    private static final int TIMER_LOW = 0xFFE05050;
    private static final int TIMER_BACK = 0x80000000;
    private static final int ROD = 0xFF6A5A48;
    private static final int RIBBON = 0xF0F0F2;
    private static final int RIBBON_SHADE = 0xDCDCE2;
    private static final int RED_RIBBON = 0xC8282C;
    private static final int GUM = 0xFF2A1E1C;
    private static final int TOOTH = 0xFFEDE6D6;
    private static final int BLADE = 0xFFFFFFFF;

    private final int session;
    private JinzenPage page;
    /** Ticks since this page arrived: the client's clock for a timed test. */
    private int pageTicks;
    /** Ticks since the screen opened, for the inner world's drift. */
    private int age;
    /** Whether a choice was sent for this page, and when, so a double click sends only one. */
    private boolean answered;
    private int answeredAt;
    /** The page's time on the last frame drawn, so a strike is judged on what the player saw. */
    private float shownTime;

    private List<List<FormattedCharSequence>> paragraphs = List.of();
    private List<FormattedCharSequence> prompt = List.of();
    private final List<Button> choices = new ArrayList<>();
    @Nullable
    private Button strikeButton;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int textWidth;
    private int textTop;
    private int promptTop;
    private int stepTop;

    public JinzenScreen(int session, JinzenPage page) {
        super(Component.translatable("screen.bleach.jinzen"));
        this.session = session;
        this.page = page;
    }

    /** Shows the next page of the same Jinzen: the test's next step, its outcome, or the spirit's reply. */
    public void update(JinzenPage page) {
        this.page = page;
        pageTicks = 0;
        answered = false;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    public int session() {
        return session;
    }

    public JinzenPage page() {
        return page;
    }

    /**
     * Sends a choice for the current step: a ribbon's index, an answer's position on screen, or 1 for
     * a strike through the gap and 0 for a miss. Does nothing on a finished page, or while an earlier
     * choice is still waiting for the server.
     */
    public void answer(int value) {
        Optional<JinzenStep> step = activeStep();
        if (step.isEmpty() || locked()) {
            return;
        }
        answered = true;
        answeredAt = pageTicks;
        BleachNetwork.answerJinzen(session, step.get().index(), value);
        updateChoices();
    }

    /** The step being tested, or empty on a page without one and on a finished page. */
    private Optional<JinzenStep> activeStep() {
        return page.finished() ? Optional.empty() : page.step();
    }

    private boolean locked() {
        return answered && pageTicks - answeredAt < ANSWER_TIMEOUT;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, width - 16);
        textWidth = panelWidth - 2 * PADDING;
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        int textHeight = 0;
        for (Component line : page.lines()) {
            List<FormattedCharSequence> split = font.split(line, textWidth);
            textHeight += (wrapped.isEmpty() ? 0 : PARAGRAPH_GAP) + split.size() * LINE;
            wrapped.add(split);
        }
        paragraphs = wrapped;
        Optional<JinzenStep> step = activeStep();
        prompt = step.map(s -> font.split(s.prompt(), textWidth)).orElse(List.of());
        int stepHeight = step.map(this::stepHeight).orElse(0);

        panelHeight = PADDING + LINE + 6 + textHeight
                + (step.isPresent() ? 8 + prompt.size() * LINE + 4 + stepHeight : 0)
                + 8 + BUTTON_HEIGHT + PADDING;
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(4, (height - panelHeight) / 2);
        int left = panelLeft + PADDING;
        textTop = panelTop + PADDING + LINE + 6;
        promptTop = textTop + textHeight + 8;
        stepTop = promptTop + prompt.size() * LINE + 4;

        choices.clear();
        strikeButton = null;
        if (step.isPresent()) {
            JinzenStep current = step.get();
            if (current.kind() == JinzenStepKind.QUESTION) {
                int y = stepTop;
                for (int i = 0; i < current.answers().size(); i++) {
                    int position = i;
                    choices.add(addRenderableWidget(Button.builder(current.answers().get(i), pressed -> answer(position))
                            .bounds(left, y, textWidth, BUTTON_HEIGHT).build()));
                    y += BUTTON_HEIGHT + BUTTON_GAP;
                }
            } else if (current.kind() == JinzenStepKind.FANGS) {
                strikeButton = addRenderableWidget(Button.builder(Component.translatable("screen.bleach.jinzen.strike"),
                                pressed -> strike(current))
                        .bounds(left + textWidth / 4, stepTop + JAWS_HEIGHT + TIMER_HEIGHT, textWidth / 2, BUTTON_HEIGHT)
                        .build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.bleach.jinzen.leave"), pressed -> onClose())
                .bounds(left + textWidth / 4, panelTop + panelHeight - PADDING - BUTTON_HEIGHT, textWidth / 2, BUTTON_HEIGHT)
                .build());
        updateChoices();
    }

    /** How tall a step's own part of the panel is, below its prompt. */
    private int stepHeight(JinzenStep step) {
        return switch (step.kind()) {
            case RIBBONS -> RIBBON_HEIGHT + TIMER_HEIGHT;
            case QUESTION -> Math.max(0, step.answers().size() * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP);
            case FANGS -> JAWS_HEIGHT + TIMER_HEIGHT + BUTTON_HEIGHT;
        };
    }

    /** Greys out the choices while one is waiting for the server, and the strike until the jaws may be struck. */
    private void updateChoices() {
        boolean open = !locked();
        for (Button choice : choices) {
            choice.active = open;
        }
        if (strikeButton != null) {
            int ticks = activeStep().map(JinzenStep::ticks).orElse(0);
            strikeButton.active = open && pageTicks >= JinzenLogic.FANGS_MIN_TICKS && pageTicks < ticks;
        }
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        pageTicks++;
        if (minecraft == null || minecraft.player == null || !minecraft.player.isUsingItem()) {
            onClose();
            return;
        }
        Optional<JinzenStep> step = activeStep();
        if (step.isPresent() && step.get().kind() == JinzenStepKind.FANGS && !answered
                && pageTicks >= step.get().ticks()) {
            answer(0); // the jaws met before the strike
        }
        updateChoices();
    }

    // ---- The fangs ----

    /** Strikes at the jaws: through the gap if it is over the blade at this moment, otherwise a miss. */
    private void strike(JinzenStep step) {
        float time = shownTime;
        boolean through = time < step.ticks()
                && Math.abs(gapPosition(step, time)) <= GAP_HALF_WIDTH - BLADE_HALF_WIDTH;
        answer(through ? 1 : 0);
    }

    /**
     * Where the gap in the teeth is at this time, from -1 to 1 either side of the blade. It starts at
     * one end and slides back and forth, faster as the jaws close.
     */
    private static float gapPosition(JinzenStep step, float time) {
        float total = Math.max(1, step.ticks());
        float t = Mth.clamp(time, 0.0F, total);
        float travelled = GAP_START_SPEED * t + (GAP_END_SPEED - GAP_START_SPEED) * t * t / (2.0F * total);
        float lap = travelled % 4.0F; // there and back is four times the distance either side
        return lap < 2.0F ? lap - 1.0F : 3.0F - lap;
    }

    // ---- Input and drawing ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        Optional<JinzenStep> step = activeStep();
        if (button == 0 && step.isPresent() && step.get().kind() == JinzenStepKind.RIBBONS && !locked()) {
            int ribbon = ribbonAt(mouseX, mouseY);
            if (ribbon >= 0) {
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                answer(ribbon);
                return true;
            }
        }
        return false;
    }

    /** Which ribbon hangs under the mouse, or -1 for none. */
    private int ribbonAt(double mouseX, double mouseY) {
        int left = panelLeft + PADDING;
        if (mouseX < left || mouseX >= left + textWidth || mouseY < stepTop || mouseY >= stepTop + RIBBON_HEIGHT) {
            return -1;
        }
        return Mth.clamp((int) ((mouseX - left) * JinzenStep.RIBBONS / textWidth), 0, JinzenStep.RIBBONS - 1);
    }

    /**
     * How far into the current tick this frame is. A screen's own float argument is the time the frame
     * took, not this, so the animations and the strike read it from the game's clock instead.
     */
    private float tickFraction() {
        return minecraft == null ? 0.0F : minecraft.getTimer().getGameTimeDeltaPartialTick(true);
    }

    /** The inner world in place of the usual blurred background, fading in from black, and the panel over it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float time = age + tickFraction();
        JinzenBackdrops.draw(graphics, page.look(), width, height, time);
        float dark = 1.0F - Mth.clamp(time / FADE_IN_TICKS, 0.0F, 1.0F);
        if (dark > 0.0F) {
            graphics.fill(0, 0, width, height, JinzenBackdrops.argb(Math.round(255 * dark), 0));
        }
        int border = JinzenBackdrops.opaque(page.look().tint());
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelTop + panelHeight + 1, border);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float fraction = tickFraction();
        shownTime = pageTicks + fraction;
        if (strikeButton != null) {
            // Ticks run slower than frames: open the strike on the frame the jaws allow it.
            updateChoices();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft + PADDING;
        graphics.drawCenteredString(font, page.title(), width / 2, panelTop + PADDING, TITLE);
        int y = textTop;
        for (List<FormattedCharSequence> paragraph : paragraphs) {
            for (FormattedCharSequence line : paragraph) {
                graphics.drawString(font, line, left, y, TEXT);
                y += LINE;
            }
            y += PARAGRAPH_GAP;
        }
        Optional<JinzenStep> step = activeStep();
        if (step.isEmpty()) {
            return;
        }
        y = promptTop;
        for (FormattedCharSequence line : prompt) {
            graphics.drawString(font, line, left, y, PROMPT);
            y += LINE;
        }
        JinzenStep current = step.get();
        float time = shownTime;
        if (current.kind() == JinzenStepKind.RIBBONS) {
            drawRibbons(graphics, current, left, stepTop, mouseX, mouseY, age + fraction);
            drawTimer(graphics, current, left, stepTop + RIBBON_HEIGHT, time);
        } else if (current.kind() == JinzenStepKind.FANGS) {
            drawJaws(graphics, current, left, stepTop, time);
            drawTimer(graphics, current, left, stepTop + JAWS_HEIGHT, time);
        }
    }

    /** Sixteen ribbons hanging from a rod and stirring, one of them red; the one under the mouse is lit. */
    @SuppressWarnings("deprecation") // drawManaged sends the ribbons' many strips as one batch
    private void drawRibbons(GuiGraphics graphics, JinzenStep step, int left, int top, int mouseX, int mouseY,
                             float time) {
        int hovered = locked() ? -1 : ribbonAt(mouseX, mouseY);
        float spacing = textWidth / (float) JinzenStep.RIBBONS;
        int half = Math.max(2, Math.round(spacing * 0.3F));
        graphics.drawManaged(() -> {
            graphics.fill(left, top, left + textWidth, top + 2, ROD);
            for (int i = 0; i < JinzenStep.RIBBONS; i++) {
                int rgb = i == step.highlight() ? RED_RIBBON : i % 2 == 0 ? RIBBON : RIBBON_SHADE;
                int colour = JinzenBackdrops.opaque(i == hovered ? JinzenBackdrops.shade(rgb, 1.25F) : rgb);
                float middle = left + spacing * (i + 0.5F);
                for (int s = 2; s < RIBBON_HEIGHT; s += 4) {
                    float depth = (float) s / RIBBON_HEIGHT;
                    float sway = Mth.sin(time * 0.09F + i * 0.8F + depth * 2.0F) * 3.0F * depth;
                    int x = Math.round(middle + sway);
                    graphics.fill(x - half, top + s, x + half, top + Math.min(s + 4, RIBBON_HEIGHT), colour);
                }
            }
        });
    }

    /**
     * The jaws: two rows of teeth closing on the blade in the middle over the step's time, with a gap
     * in the teeth sliding back and forth. The blade shines while the gap is over it.
     */
    @SuppressWarnings("deprecation") // drawManaged sends the teeth as one batch
    private void drawJaws(GuiGraphics graphics, JinzenStep step, int left, int top, float time) {
        float closing = Mth.clamp(time / Math.max(1, step.ticks()), 0.0F, 1.0F);
        int middleY = top + JAWS_HEIGHT / 2;
        int open = Math.round((JAWS_HEIGHT / 2.0F - TOOTH_LENGTH) * (1.0F - closing));
        float half = textWidth / 2.0F;
        float middleX = left + half;
        float travel = half * GAP_TRAVEL;
        float gapX = middleX + gapPosition(step, time) * travel;
        float gapHalf = GAP_HALF_WIDTH * travel;
        int bladeHalf = Math.max(1, Math.round(BLADE_HALF_WIDTH * travel));
        boolean through = Math.abs(gapX - middleX) <= gapHalf - BLADE_HALF_WIDTH * travel;
        int gapLeft = Math.round(gapX - gapHalf);
        int gapRight = Math.round(gapX + gapHalf);
        int upperGum = middleY - open - TOOTH_LENGTH;
        int lowerGum = middleY + open + TOOTH_LENGTH;
        int right = left + textWidth;
        graphics.drawManaged(() -> {
            // The gums, cut where the gap is.
            graphics.fill(left, top, Math.max(left, gapLeft), upperGum, GUM);
            graphics.fill(Math.min(right, gapRight), top, right, upperGum, GUM);
            graphics.fill(left, lowerGum, Math.max(left, gapLeft), top + JAWS_HEIGHT, GUM);
            graphics.fill(Math.min(right, gapRight), lowerGum, right, top + JAWS_HEIGHT, GUM);
            for (int x = left; x + TOOTH_WIDTH <= right; x += TOOTH_WIDTH) {
                if (x + TOOTH_WIDTH > gapLeft && x < gapRight) {
                    continue;
                }
                // Each tooth narrows in three steps to its point.
                for (int notch = 0; notch < 3; notch++) {
                    int inset = 1 + notch * 2;
                    int y0 = notch * TOOTH_LENGTH / 3;
                    int y1 = (notch + 1) * TOOTH_LENGTH / 3;
                    graphics.fill(x + inset, upperGum + y0, x + TOOTH_WIDTH - inset, upperGum + y1, TOOTH);
                    graphics.fill(x + inset, lowerGum - y1, x + TOOTH_WIDTH - inset, lowerGum - y0, TOOTH);
                }
            }
            int blade = through ? JinzenBackdrops.opaque(JinzenBackdrops.mix(page.look().tint(), 0xFFFFFF, 0.3F)) : BLADE;
            int x = Math.round(middleX);
            graphics.fill(x - bladeHalf, top, x + bladeHalf, top + JAWS_HEIGHT, blade);
        });
    }

    /** A bar that runs down over a timed step's time, with the seconds left under it. */
    private void drawTimer(GuiGraphics graphics, JinzenStep step, int left, int top, float time) {
        float total = Math.max(1, step.ticks());
        float remaining = Math.max(0.0F, total - time);
        float share = remaining / total;
        graphics.fill(left, top + 3, left + textWidth, top + 6, TIMER_BACK);
        graphics.fill(left, top + 3, left + Math.round(textWidth * share), top + 6, share < 0.25F ? TIMER_LOW : TIMER);
        graphics.drawCenteredString(font, Component.translatable("screen.bleach.jinzen.time", Mth.ceil(remaining / 20.0F)),
                left + textWidth / 2, top + 8, TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Leaving lets go of the blade at once, so the server ends Jinzen before an answer still on its way
     * back could open the screen again.
     */
    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null && minecraft.gameMode != null && minecraft.player.isUsingItem()) {
            minecraft.gameMode.releaseUsingItem(minecraft.player);
        }
        super.onClose();
    }
}
