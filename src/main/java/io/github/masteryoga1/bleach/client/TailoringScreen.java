package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.menu.TailoringMenu;
import io.github.masteryoga1.bleach.menu.TailoringPages;
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
 * The Tailoring Table's window: cloth and trim slots on the left, a page of garment buttons in the
 * middle, the garment on the right. With more garments than fit on a page, arrows and the mouse wheel
 * turn the pages, and "n/m" between the arrows says which one shows. The page is this screen's alone:
 * each button still sends the garment's place in the whole list, so the menu never knows of pages.
 * Drawn in flat colours until the mod has its own art.
 */
public final class TailoringScreen extends AbstractContainerScreen<TailoringMenu> {
    private static final int BUTTONS_X = 52;
    private static final int BUTTONS_Y = 18;
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 20;
    private static final int COLUMNS = TailoringPages.COLUMNS;
    private static final int MAX_BUTTONS = TailoringPages.PER_PAGE;
    /** The page arrows sit under the buttons, clear of the inventory's label. */
    private static final int PREVIOUS_X = 52;
    private static final int NEXT_X = 118;
    private static final int ARROWS_Y = 59;
    private static final int ARROW_WIDTH = 12;
    private static final int ARROW_HEIGHT = 9;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SELECTED = 0xFF6FBF4F;
    private static final int HOVERED = 0xFFA8D8FF;
    private static final int DIMMED = 0x90303030;
    /** The colour vanilla writes a container's labels in. */
    private static final int LABEL = 0xFF404040;

    private int page;

    public TailoringScreen(TailoringMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * Opens on the page of the garment already chosen. This runs again when the window is resized, and
     * then keeps the page that was turned to, unless a garment has been chosen.
     */
    @Override
    protected void init() {
        super.init();
        int selected = menu.selectedIndex();
        page = selected >= 0 ? TailoringPages.pageOf(selected) : page;
        page = TailoringPages.clampPage(page, recipeCount());
    }

    /** The page showing, counted from 0. */
    public int page() {
        return page;
    }

    public void nextPage() {
        page = TailoringPages.clampPage(page + 1, recipeCount());
    }

    public void previousPage() {
        page = TailoringPages.clampPage(page - 1, recipeCount());
    }

    private int recipeCount() {
        return menu.recipes().size();
    }

    private int pageCount() {
        return TailoringPages.pageCount(recipeCount());
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

        for (int slot = 0; slot < MAX_BUTTONS; slot++) {
            int index = TailoringPages.indexAt(page, slot, recipeCount());
            if (index < 0) {
                break;
            }
            int x = buttonX(slot);
            int y = buttonY(slot);
            boolean hovered = mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
            int frame = index == menu.selectedIndex() ? SELECTED : hovered ? HOVERED : SLOT_SHADOW;
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, frame);
            graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, SLOT);
            graphics.renderItem(result(index), x + 1, y + 1);
            if (!menu.canMake(index)) {
                graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, DIMMED);
            }
        }

        int pages = pageCount();
        if (pages > 1) {
            drawArrow(graphics, left + PREVIOUS_X, "<", page > 0, mouseX, mouseY);
            drawArrow(graphics, left + NEXT_X, ">", page < pages - 1, mouseX, mouseY);
            String label = (page + 1) + "/" + pages;
            int centre = left + (PREVIOUS_X + ARROW_WIDTH + NEXT_X) / 2;
            graphics.drawString(font, label, centre - font.width(label) / 2, top + ARROWS_Y + 1, LABEL, false);
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

    /** A page arrow, framed like the buttons, and dimmed where there is no page to turn to. */
    private void drawArrow(GuiGraphics graphics, int x, String glyph, boolean enabled, int mouseX, int mouseY) {
        int y = topPos + ARROWS_Y;
        boolean hovered = enabled && overArrow(mouseX, mouseY, x);
        graphics.fill(x, y, x + ARROW_WIDTH, y + ARROW_HEIGHT, hovered ? HOVERED : SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + ARROW_WIDTH - 1, y + ARROW_HEIGHT - 1, SLOT);
        graphics.drawString(font, glyph, x + (ARROW_WIDTH - font.width(glyph) + 1) / 2, y + 1, LABEL, false);
        if (!enabled) {
            graphics.fill(x + 1, y + 1, x + ARROW_WIDTH - 1, y + ARROW_HEIGHT - 1, DIMMED);
        }
    }

    private boolean overArrow(double mouseX, double mouseY, int x) {
        int y = topPos + ARROWS_Y;
        return mouseX >= x && mouseX < x + ARROW_WIDTH && mouseY >= y && mouseY < y + ARROW_HEIGHT;
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
        if (pageCount() > 1) {
            // A click on an arrow is the arrow's, even where there is no page to turn to.
            if (overArrow(mouseX, mouseY, leftPos + PREVIOUS_X)) {
                turnTo(page - 1);
                return true;
            }
            if (overArrow(mouseX, mouseY, leftPos + NEXT_X)) {
                turnTo(page + 1);
                return true;
            }
        }
        int index = buttonAt(mouseX, mouseY);
        if (index >= 0 && minecraft != null && minecraft.player != null && minecraft.gameMode != null
                && menu.clickMenuButton(minecraft.player, index)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0F));
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The wheel turns the pages, up for the one before and down for the one after. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pageCount() > 1 && scrollY != 0.0) {
            turnTo(scrollY > 0.0 ? page - 1 : page + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Turns to a page, if there is one, with the sound of a page turning. */
    private void turnTo(int target) {
        int turned = TailoringPages.clampPage(target, recipeCount());
        if (turned != page) {
            page = turned;
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
        }
    }

    private ItemStack result(int index) {
        return minecraft == null || minecraft.level == null ? ItemStack.EMPTY
                : menu.recipes().get(index).value().getResultItem(minecraft.level.registryAccess());
    }

    private int buttonX(int slot) {
        return leftPos + BUTTONS_X + slot % COLUMNS * BUTTON_GAP;
    }

    private int buttonY(int slot) {
        return topPos + BUTTONS_Y + slot / COLUMNS * BUTTON_GAP;
    }

    /** The garment button under the mouse, as its place in the whole list, or -1. */
    private int buttonAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < MAX_BUTTONS; slot++) {
            int index = TailoringPages.indexAt(page, slot, recipeCount());
            if (index < 0) {
                break;
            }
            int x = buttonX(slot);
            int y = buttonY(slot);
            if (mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE) {
                return index;
            }
        }
        return -1;
    }
}
