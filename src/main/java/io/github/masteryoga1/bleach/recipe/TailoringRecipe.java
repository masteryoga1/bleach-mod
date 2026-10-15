package io.github.masteryoga1.bleach.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A garment sewn at a Tailoring Table from a number of cloth and, for some, a trim. In a data pack:
 * <pre>
 * {"type": "bleach:tailoring", "cloth": {"item": "minecraft:black_wool"}, "cloth_count": 5,
 *  "trim": {"item": "minecraft:white_wool"}, "result": {"id": "bleach:shihakusho_kosode"}}
 * </pre>
 * The trim and both counts are optional; without a trim the trim slot is ignored.
 */
public record TailoringRecipe(Ingredient cloth, int clothCount, Ingredient trim, int trimCount, ItemStack result)
        implements Recipe<TailoringInput> {
    public static final MapCodec<TailoringRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("cloth").forGetter(TailoringRecipe::cloth),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("cloth_count", 1).forGetter(TailoringRecipe::clothCount),
            Ingredient.CODEC.optionalFieldOf("trim", Ingredient.EMPTY).forGetter(TailoringRecipe::trim),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("trim_count", 1).forGetter(TailoringRecipe::trimCount),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(TailoringRecipe::result)
    ).apply(instance, TailoringRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TailoringRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, TailoringRecipe::cloth,
            ByteBufCodecs.VAR_INT, TailoringRecipe::clothCount,
            Ingredient.CONTENTS_STREAM_CODEC, TailoringRecipe::trim,
            ByteBufCodecs.VAR_INT, TailoringRecipe::trimCount,
            ItemStack.STREAM_CODEC, TailoringRecipe::result,
            TailoringRecipe::new);

    /** Whether this recipe needs anything in the trim slot. */
    public boolean needsTrim() {
        return !trim.isEmpty();
    }

    @Override
    public boolean matches(TailoringInput input, Level level) {
        return cloth.test(input.cloth()) && input.cloth().getCount() >= clothCount
                && (!needsTrim() || trim.test(input.trim()) && input.trim().getCount() >= trimCount);
    }

    @Override
    public ItemStack assemble(TailoringInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(cloth);
        if (needsTrim()) {
            ingredients.add(trim);
        }
        return ingredients;
    }

    /** Kept out of the recipe book, which has no tab for tailoring. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TAILORING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.TAILORING.get();
    }

    public static final class Serializer implements RecipeSerializer<TailoringRecipe> {
        @Override
        public MapCodec<TailoringRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TailoringRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
