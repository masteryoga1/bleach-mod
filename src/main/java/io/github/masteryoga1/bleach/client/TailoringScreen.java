package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.menu.TailoringMenu;
import io.github.masteryoga1.bleach.recipe.TailoringRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The Tailoring Table's window: cloth and trim slots on the left, a button per garment in the
 * middle, the garment on the right. Drawn in flat colours until the mod has its own art.
 */
public final class TailoringScreen extends AbstractContainerScreen<TailoringMenu> {
    private static final int BUTTONS_X = 52;
    private static final int BUTTONS_Y = 18;
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 20;
    private static final int COLUMNS = 3;
    private static final int MAX_BUTTONS = 6;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SELECTED = 0xFF6FBF4F;
    private static final int HOVERED = 0xFFA8D8FF;
    private static final int DIMMED = 0x90303030;

    public TailoringScreen(TailoringMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, PANEL);
        graphics.fill(left, top, left + imageWidth - 1, top + 1, LIGHT);
        graphics.fill(left, top, left + 1, top + imageHeight - 1, LIGHT);
        graphics.fill(left + 1, top + imageHeight - 1, left + imageWidth, top + imageHeight, SHADOW);
        graphics.fill(left + imageWidth - 1, top + 1, left + imageWidth, top + imageHeight, SHADOW);
        for (Slot slot : menu.slots) {
            drawSlot(graphics, left + slot.x, top + slot.y);
        }

        int count = Math.min(MAX_BUTTONS, menu.recipes().size());
        for (int i = 0; i < count; i++) {
            int x = buttonX(i);
            int y = buttonY(i);
            boolean hovered = mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
            int frame = i == menu.selectedIndex() ? SELECTED : hovered ? HOVERED : SLOT_SHADOW;
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, frame);
            graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, SLOT);
            graphics.renderItem(result(i), x + 1, y + 1);
            if (!menu.canMake(i)) {
                graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, DIMMED);
            }
        }
    }

    /** A sunken square behind a slot, the way vanilla draws them. */
    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT);
        graphics.fill(x - 1, y - 1, x + 17, y, SLOT_SHADOW);
        graphics.fill(x - 1, y - 1, x, y + 17, SLOT_SHADOW);
        graphics.fill(x, y + 16, x + 17, y + 17, LIGHT);
        graphics.fill(x + 16, y, x + 17, y + 17, LIGHT);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        int index = buttonAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        TailoringRecipe recipe = menu.recipes().get(index).value();
        List<Component> lines = new ArrayList<>();
        lines.add(result(index).getHoverName());
        lines.add(needs(recipe.cloth(), recipe.clothCount()));
        if (recipe.needsTrim()) {
            lines.add(needs(recipe.trim(), recipe.trimCount()));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private static Component needs(Ingredient ingredient, int count) {
        ItemStack[] options = ingredient.getItems();
        Component name = options.length == 0 ? Component.literal("?") : options[0].getHoverName();
        return Component.translatable("tooltip.bleach.tailoring.needs", count, name);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = buttonAt(mouseX, mouseY);
        if (index >= 0 && minecraft != null && minecraft.player != null && minecraft.gameMode != null
                && menu.clickMenuButton(minecraft.player, index)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0F));
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ItemStack result(int index) {
        return minecraft == null || minecraft.level == null ? ItemStack.EMPTY
                : menu.recipes().get(index).value().getResultItem(minecraft.level.registryAccess());
    }

    private int buttonX(int index) {
        return leftPos + BUTTONS_X + index % COLUMNS * BUTTON_GAP;
    }

    private int buttonY(int index) {
        return topPos + BUTTONS_Y + index / COLUMNS * BUTTON_GAP;
    }

    /** The garment button under the mouse, or -1. */
    private int buttonAt(double mouseX, double mouseY) {
        int count = Math.min(MAX_BUTTONS, menu.recipes().size());
        for (int i = 0; i < count; i++) {
            int x = buttonX(i);
            int y = buttonY(i);
            if (mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
