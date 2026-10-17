package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The bloodline question, asked once of a new living Human: does Quincy blood run in them? Three
 * answers, each explained in its tooltip: Human, Quincy, or let fate decide. The server rolls the
 * blood from the answer. The game goes on behind it, and it cannot be put away with Escape, only
 * answered; another screen that takes its place only puts it off, and a player who leaves without
 * answering is asked again next time.
 */
public final class BloodlineScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private static final int PANEL = 0xD0101014;
    private static final int BORDER = 0xFF5A5A66;
    private static final int TITLE = 0xFFD27F;
    private static final int TEXT = 0xE6E6E6;

    private List<FormattedCharSequence> body = List.of();
    private int panelTop;
    private int panelHeight;
    /** Set by the first answer, so a second click in the same moment sends nothing more. */
    private boolean answered;

    public BloodlineScreen() {
        super(Component.translatable("screen.bleach.bloodline"));
    }

    @Override
    protected void init() {
        int textWidth = PANEL_WIDTH - 2 * PADDING;
        body = font.split(Component.translatable("screen.bleach.bloodline.body"), textWidth);
        BloodlineChoice[] choices = BloodlineChoice.values();
        panelHeight = PADDING + LINE + 6 + body.size() * LINE + 8
                + choices.length * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP + PADDING;
        panelTop = Math.max(4, (height - panelHeight) / 2);
        int left = width / 2 - PANEL_WIDTH / 2 + PADDING;
        int y = panelTop + panelHeight - PADDING - choices.length * (BUTTON_HEIGHT + BUTTON_GAP) + BUTTON_GAP;
        for (BloodlineChoice choice : choices) {
            String key = "screen.bleach.bloodline." + choice.name().toLowerCase(Locale.ROOT);
            addRenderableWidget(Button.builder(Component.translatable(key), pressed -> choose(choice))
                    .bounds(left, y, textWidth, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                    .build());
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
    }

    /** Sends the answer and closes the question. Only the first answer counts. */
    public void choose(BloodlineChoice choice) {
        if (answered) {
            return;
        }
        answered = true;
        if (minecraft != null && minecraft.getConnection() != null) {
            BleachNetwork.requestBloodline(choice);
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    /**
     * Put away unanswered by another screen (a death, a container the server opened): the question
     * waits to be asked again once nothing else is showing, while the soul still needs it.
     */
    @Override
    public void removed() {
        super.removed();
        if (!answered && minecraft != null && minecraft.player != null
                && QuincyLogic.needsBloodlineChoice(minecraft.player.getData(SoulAttachments.SOUL))) {
            QuincyClient.bloodlinePending = true;
        }
    }

    /** Settled some other way while it was showing (by command, say): there is nothing left to answer. */
    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null
                && minecraft.player.getData(SoulAttachments.SOUL).quincy().decided()) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - PANEL_WIDTH / 2;
        graphics.fill(left - 1, panelTop - 1, left + PANEL_WIDTH + 1, panelTop + panelHeight + 1, BORDER);
        graphics.fill(left, panelTop, left + PANEL_WIDTH, panelTop + panelHeight, PANEL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - PANEL_WIDTH / 2 + PADDING;
        int y = panelTop + PADDING;
        graphics.drawString(font, title, left, y, TITLE);
        y += LINE + 6;
        for (FormattedCharSequence line : body) {
            graphics.drawString(font, line, left, y, TEXT);
            y += LINE;
        }
    }

    /** The question must be answered: Escape does not put it away. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
