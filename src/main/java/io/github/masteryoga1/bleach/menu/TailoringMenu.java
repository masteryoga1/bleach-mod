package io.github.masteryoga1.bleach.menu;

import io.github.masteryoga1.bleach.recipe.TailoringInput;
import io.github.masteryoga1.bleach.recipe.TailoringRecipe;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModMenus;
import io.github.masteryoga1.bleach.registry.ModRecipes;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * The Tailoring Table: cloth and trim go in on the left, a garment is picked from the buttons, and
 * it comes out on the right. Works like the stonecutter, with every tailoring recipe always listed.
 */
public final class TailoringMenu extends AbstractContainerMenu {
    public static final int CLOTH_SLOT = 0;
    public static final int TRIM_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INVENTORY_START = 3;
    private static final int HOTBAR_START = 30;
    private static final int INVENTORY_END = 39;

    private final ContainerLevelAccess access;
    private final Level level;
    /** Every tailoring recipe, ordered by id so the server and client agree on the button numbers. */
    private final List<RecipeHolder<TailoringRecipe>> recipes;
    private final DataSlot selected = DataSlot.standalone();
    private final Container inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();
    private long lastSoundTime;

    public TailoringMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public TailoringMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.TAILORING.get(), containerId);
        this.access = access;
        this.level = inventory.player.level();
        this.recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.TAILORING.get()).stream()
                .sorted(Comparator.comparing((RecipeHolder<TailoringRecipe> holder) -> holder.id()))
                .toList();
        selected.set(-1);

        addSlot(new Slot(inputs, CLOTH_SLOT, 20, 22));
        addSlot(new Slot(inputs, TRIM_SLOT, 20, 44));
        addSlot(new Slot(result, 0, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                stack.onCraftedBy(player.level(), player, stack.getCount());
                takeInputs();
                TailoringMenu.this.access.execute((world, pos) -> {
                    long now = world.getGameTime();
                    if (lastSoundTime != now) {
                        world.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        lastSoundTime = now;
                    }
                });
                super.onTake(player, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlot(selected);
    }

    public List<RecipeHolder<TailoringRecipe>> recipes() {
        return recipes;
    }

    public int selectedIndex() {
        return selected.get();
    }

    /** What lies on the table now. */
    public TailoringInput input() {
        return new TailoringInput(inputs.getItem(CLOTH_SLOT), inputs.getItem(TRIM_SLOT));
    }

    /** Whether the cloth and trim on the table are enough for this recipe. */
    public boolean canMake(int index) {
        return isValidIndex(index) && recipes.get(index).value().matches(input(), level);
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < recipes.size();
    }

    @Nullable
    private TailoringRecipe selectedRecipe() {
        return isValidIndex(selected.get()) ? recipes.get(selected.get()).value() : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (isValidIndex(id)) {
            selected.set(id);
            setupResult();
        }
        return true;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        setupResult();
    }

    private void setupResult() {
        TailoringRecipe recipe = selectedRecipe();
        TailoringInput input = input();
        if (recipe != null && recipe.matches(input, level)) {
            ItemStack made = recipe.assemble(input, level.registryAccess());
            result.setItem(0, made.isItemEnabled(level.enabledFeatures()) ? made : ItemStack.EMPTY);
        } else {
            result.setItem(0, ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    /** Uses up the cloth and trim of the garment just taken. */
    private void takeInputs() {
        TailoringRecipe recipe = selectedRecipe();
        if (recipe == null) {
            return;
        }
        inputs.removeItem(CLOTH_SLOT, recipe.clothCount());
        if (recipe.needsTrim()) {
            inputs.removeItem(TRIM_SLOT, recipe.trimCount());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == RESULT_SLOT) {
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index == CLOTH_SLOT || index == TRIM_SLOT) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, CLOTH_SLOT, RESULT_SLOT, false)) {
            boolean inMain = index < HOTBAR_START;
            if (!moveItemStackTo(stack, inMain ? HOTBAR_START : INVENTORY_START, inMain ? INVENTORY_END : HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        }
        slot.setChanged();
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        broadcastChanges();
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.TAILORING_TABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        result.removeItemNoUpdate(0);
        access.execute((world, pos) -> clearContainer(player, inputs));
    }
}
