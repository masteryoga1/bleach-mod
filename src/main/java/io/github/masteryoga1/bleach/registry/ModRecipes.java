package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.recipe.TailoringRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, BleachMod.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BleachMod.MODID);

    /** Garments sewn at a Tailoring Table. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<TailoringRecipe>> TAILORING =
            RECIPE_TYPES.register("tailoring", () -> RecipeType.<TailoringRecipe>simple(BleachMod.id("tailoring")));
    public static final DeferredHolder<RecipeSerializer<?>, TailoringRecipe.Serializer> TAILORING_SERIALIZER =
            RECIPE_SERIALIZERS.register("tailoring", TailoringRecipe.Serializer::new);

    private ModRecipes() {
    }
}
