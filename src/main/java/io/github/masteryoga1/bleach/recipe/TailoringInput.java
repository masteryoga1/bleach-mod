package io.github.masteryoga1.bleach.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** What lies on a Tailoring Table: the cloth, and the trim sewn onto it. */
public record TailoringInput(ItemStack cloth, ItemStack trim) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> cloth;
            case 1 -> trim;
            default -> throw new IllegalArgumentException("A Tailoring Table has no slot " + index);
        };
    }

    @Override
    public int size() {
        return 2;
    }
}
