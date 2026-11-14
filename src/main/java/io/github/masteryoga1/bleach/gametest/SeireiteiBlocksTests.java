package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Seireitei's building blocks: each registered with its item, its drop, its tool, its vanilla tags and how fire takes
 * it, and made by its recipe.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SeireiteiBlocksTests {
    /** Each block of the palette by name, one row a block, which tests 1 and 3 run through. */
    private static final Map<String, Kin> PALETTE = Map.of(
            "white_plaster", new Kin(Set.of("mineable/pickaxe"), 0, 0, "building_blocks", 3, List.of(Items.BONE_MEAL,
                    Items.SAND, Items.SAND, Items.SAND, Items.SAND, Items.CLAY_BALL, Items.CLAY_BALL, Items.CLAY_BALL,
                    Items.CLAY_BALL), 8, 0),
            "black_plaster", new Kin(Set.of("mineable/pickaxe"), 0, 0, "building_blocks", 3,
                    ring(ModItems.WHITE_PLASTER, Items.COAL), 8, 0),
            // Charcoal does for coal, through vanilla's tag.
            "roof_tiles", new Kin(Set.of("mineable/pickaxe"), 0, 0, "building_blocks", 3,
                    ring(Items.TERRACOTTA, Items.CHARCOAL), 8, 0),
            "roof_tile_stairs", new Kin(Set.of("mineable/pickaxe", "stairs"), 0, 0, "building_blocks", 3,
                    List.of(ModItems.ROOF_TILES, Items.AIR, Items.AIR, ModItems.ROOF_TILES, ModItems.ROOF_TILES, Items.AIR,
                            ModItems.ROOF_TILES, ModItems.ROOF_TILES, ModItems.ROOF_TILES), 4, 1),
            "roof_tile_slab", new Kin(Set.of("mineable/pickaxe", "slabs"), 0, 0, "building_blocks", 3,
                    List.of(ModItems.ROOF_TILES, ModItems.ROOF_TILES, ModItems.ROOF_TILES), 6, 2));

    private SeireiteiBlocksTests() {
    }

    @GameTest(template = "empty")
    public static void seireiteiBlocksAreRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A spot in the test's own box, for the calls that ask where the block would be; none of them reads it.
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        Player bare = helper.makeMockPlayer(GameType.SURVIVAL);
        Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        PALETTE.forEach((name, kin) -> {
            ResourceLocation id = BleachMod.id(name);
            Block block = BuiltInRegistries.BLOCK.get(id);
            BlockState state = block.defaultBlockState();
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id)
                    && BuiltInRegistries.ITEM.getKey(block.asItem()).equals(id), id + " is not a block with its item");
            // One of itself to the miner's pick, as a player's break asks it; stone-like blocks only to a pickaxe, the
            // least of them, and the rest to a bare hand too.
            List<ItemStack> drops = Block.getDrops(state, level, at, null, miner, miner.getMainHandItem());
            helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0), new ItemStack(block)),
                    id + " drops " + drops);
            boolean stone = kin.tags().contains("mineable/pickaxe");
            helper.assertTrue(state.requiresCorrectToolForDrops() == stone
                    && bare.hasCorrectToolForDrops(state, level, at) != stone
                    && miner.hasCorrectToolForDrops(state, level, at), id + " is not mined as its kin is");
            // In exactly these of vanilla's tags, and in none of the mod's.
            Set<String> tags = state.getTags().map(TagKey::location)
                    .filter(tag -> tag.getNamespace().equals("minecraft") || tag.getNamespace().equals(BleachMod.MODID))
                    .map(tag -> tag.getNamespace().equals("minecraft") ? tag.getPath() : tag.toString())
                    .collect(Collectors.toSet());
            helper.assertTrue(tags.equals(kin.tags()), id + " is in the tags " + tags + ", not " + kin.tags());
            // Fire takes it with vanilla's odds for its kin, and lava lights only what fire takes.
            int spread = state.getFireSpreadSpeed(level, at, Direction.UP);
            int burn = state.getFlammability(level, at, Direction.UP);
            boolean lava = state.ignitedByLava(level, at, Direction.UP);
            helper.assertTrue(spread == kin.spread() && burn == kin.burn() && lava == (burn > 0),
                    id + " catches at " + spread + " and burns at " + burn + ", and lava lights it: " + lava);
        });
        BlockState slab = ModBlocks.ROOF_TILE_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        helper.assertTrue(Block.getDrops(slab, level, at, null).get(0).getCount() == 2,
                "a double roof tile slab does not drop two");
        // Plaster is as hard as concrete and tile as bricks; on a map plaster is white or dark grey, and tile blue-grey.
        helper.assertTrue(ModBlocks.WHITE_PLASTER.get().defaultDestroyTime() == 1.8F
                        && ModBlocks.ROOF_TILES.get().defaultDestroyTime() == 2.0F
                        && ModBlocks.ROOF_TILES.get().getExplosionResistance() == 6.0F
                        && ModBlocks.WHITE_PLASTER.get().defaultMapColor() == MapColor.SNOW
                        && ModBlocks.BLACK_PLASTER.get().defaultMapColor() == MapColor.COLOR_GRAY
                        && ModBlocks.ROOF_TILES.get().defaultMapColor() == MapColor.TERRACOTTA_CYAN,
                "plaster or tile is not as hard, or not the colour on a map, that the spec gives");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void seireiteiRecipesResolve(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // What the stonecutter cuts from one roof tile block, by recipe.
        SingleRecipeInput tile = new SingleRecipeInput(ModItems.ROOF_TILES.toStack());
        Map<ResourceLocation, ItemStack> cuts = level.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, tile, level)
                .stream().collect(Collectors.toMap(RecipeHolder::id,
                        each -> each.value().assemble(tile, level.registryAccess())));
        PALETTE.forEach((name, kin) -> {
            Item made = BuiltInRegistries.ITEM.get(BleachMod.id(name));
            // The row's grid crafts so many of it, by the mod's recipe of its name.
            List<ItemStack> grid = kin.grid().stream().map(ItemStack::new).toList();
            CraftingInput input = CraftingInput.of(kin.width(), grid.size() / kin.width(), grid);
            Optional<RecipeHolder<CraftingRecipe>> found =
                    level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            ItemStack crafted = found.map(holder -> holder.value().assemble(input, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            helper.assertTrue(found.isPresent() && found.get().id().equals(BleachMod.id(name))
                            && ItemStack.matches(crafted, new ItemStack(made, kin.count())),
                    grid + " crafts " + crafted + " by " + found.map(RecipeHolder::id).orElse(null) + ", not " + name);
            // The stonecutter cuts so many of it from roof tiles, as it cuts deepslate tiles, where the row says.
            String cut = name + "_from_roof_tiles_stonecutting";
            helper.assertTrue(kin.cut() == 0 || ItemStack.matches(cuts.getOrDefault(BleachMod.id(cut), ItemStack.EMPTY),
                    new ItemStack(made, kin.cut())), "roof tiles cut to " + cuts);
            // Each recipe is unlocked, for the recipe book or the stonecutter, once what makes it is first held.
            for (String recipe : kin.cut() == 0 ? List.of(name) : List.of(name, cut)) {
                ResourceLocation unlock = BleachMod.id("recipes/" + kin.unlock() + "/" + recipe);
                helper.assertTrue(level.getServer().getAdvancements().get(unlock) != null, recipe + " has no unlock");
            }
        });
        helper.assertTrue(cuts.size() == 2, "roof tiles cut to " + cuts);
        helper.succeed();
    }

    /** Eight of one item round one of another, a 3 by 3 grid. */
    private static List<ItemLike> ring(ItemLike outer, ItemLike middle) {
        return List.of(outer, outer, outer, outer, middle, outer, outer, outer, outer);
    }

    /**
     * A block of the palette: the vanilla tags it is in; fire's odds of catching it and of burning it away; the folder
     * of its recipe's unlock; the grid that crafts it, so many wide, and how many it makes; and how many of it the
     * stonecutter cuts from one roof tile block.
     */
    private record Kin(Set<String> tags, int spread, int burn, String unlock, int width, List<ItemLike> grid, int count,
                       int cut) {
    }
}
