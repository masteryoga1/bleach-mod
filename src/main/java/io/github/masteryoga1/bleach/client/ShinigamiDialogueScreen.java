package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Talking to a Shinigami NPC: what they say to a soul like yours, the exams for a student, and the
 * answers you can give. It does not pause the game, and closes if the NPC leaves or you walk off.
 */
public final class ShinigamiDialogueScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    /** Walking further away than this, squared, ends the conversation. */
    private static final double TALK_RANGE_SQR = 64.0;

    private static final int PANEL = 0xD0101014;
    private static final int BORDER = 0xFF5A5A66;
    private static final int TITLE = 0xFFD27F;
    private static final int TEXT = 0xE6E6E6;
    private static final int PASSED = 0x7FD860;
    private static final int NOT_YET = 0xB0B0B0;

    private final ShinigamiNpc npc;
    private final DialogueView view;
    private final List<Integer> required;
    private final boolean asauchiReady;
    private List<FormattedCharSequence> speech = List.of();
    private final List<Component> examLines = new ArrayList<>();
    private final List<Integer> examColours = new ArrayList<>();
    private int panelTop;
    private int panelHeight;

    public ShinigamiDialogueScreen(ShinigamiNpc npc, OpenDialoguePayload payload) {
        super(npc.getDisplayName());
        this.npc = npc;
        this.view = payload.view();
        this.required = payload.required();
        this.asauchiReady = payload.asauchiReady();
    }

    @Override
    protected void init() {
        int textWidth = PANEL_WIDTH - 2 * PADDING;
        speech = font.split(Component.translatable("screen.bleach.dialogue." + npc.dialogueKey() + "." + view.key()), textWidth);
        buildExamLines();
        List<NpcAction> actions = NpcAction.offeredIn(view);

        panelHeight = PADDING + LINE + 6 + speech.size() * LINE
                + (examLines.isEmpty() ? 0 : 6 + examLines.size() * LINE)
                + 8 + (actions.size() + 1) * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP + PADDING;
        panelTop = Math.max(4, (height - panelHeight) / 2);
        int left = width / 2 - PANEL_WIDTH / 2 + PADDING;
        int y = panelTop + panelHeight - PADDING - (actions.size() + 1) * (BUTTON_HEIGHT + BUTTON_GAP) + BUTTON_GAP;
        for (NpcAction action : actions) {
            Button button = Button.builder(Component.translatable("screen.bleach.dialogue.action." + action.key()),
                    pressed -> choose(action)).bounds(left, y, textWidth, BUTTON_HEIGHT).build();
            if (action == NpcAction.ASAUCHI && !asauchiReady) {
                button.active = false;
                button.setTooltip(Tooltip.create(Component.translatable("screen.bleach.dialogue.asauchi_not_ready")));
            }
            addRenderableWidget(button);
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.bleach.dialogue.leave"), pressed -> onClose())
                .bounds(left, y, textWidth, BUTTON_HEIGHT).build());
    }

    /** For a student, how each art stands against the exams, from the levels the client knows. */
    private void buildExamLines() {
        examLines.clear();
        examColours.clear();
        if (view != DialogueView.STUDENT || minecraft == null || minecraft.player == null) {
            return;
        }
        SoulData data = minecraft.player.getData(SoulAttachments.SOUL);
        for (int i = 0; i < ShinigamiNpc.EXAMINED_ARTS.size() && i < required.size(); i++) {
            Stat art = ShinigamiNpc.EXAMINED_ARTS.get(i);
            Stat examined = ShinigamiLogic.examinedStat(art);
            Component name = examined == art ? art.displayName()
                    : Component.translatable("screen.bleach.dialogue.exam.via", art.displayName(), examined.displayName());
            int have = data.level(examined);
            int need = required.get(i);
            boolean passed = have >= need;
            examLines.add(passed ? Component.translatable("screen.bleach.dialogue.exam.passed", name)
                    : Component.translatable("screen.bleach.dialogue.exam", name, have, need));
            examColours.add(passed ? PASSED : NOT_YET);
        }
    }

    private void choose(NpcAction action) {
        BleachNetwork.requestNpcAction(npc.getId(), action);
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (npc.isRemoved() || !npc.isAlive() || minecraft == null || minecraft.player == null
                || minecraft.player.distanceToSqr(npc) > TALK_RANGE_SQR) {
            onClose();
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
        for (FormattedCharSequence line : speech) {
            graphics.drawString(font, line, left, y, TEXT);
            y += LINE;
        }
        if (!examLines.isEmpty()) {
            y += 6;
            for (int i = 0; i < examLines.size(); i++) {
                graphics.drawString(font, examLines.get(i), left, y, examColours.get(i));
                y += LINE;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
