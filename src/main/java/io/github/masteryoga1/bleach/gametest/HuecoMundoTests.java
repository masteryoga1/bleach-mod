package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hueco Mundo's blocks: each with its item, drop, tool and tags, white sand's fall, and what is crafted and smelted. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HuecoMundoTests {
    private HuecoMundoTests() {
    }

    @GameTest(template = "empty")
    public static void huecoMundoBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A spot in the test's own box, for the calls that ask where the block would be; none of them reads it.
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        List<String> names = List.of("white_sand", "white_sandstone", "smooth_white_sandstone", "hueco_quartz",
                "hueco_quartz_bricks");
        for (String name : names) {
            ResourceLocation id = BleachMod.id(name);
            Block block = BuiltInRegistries.BLOCK.get(id);
            BlockState state = block.defaultBlockState();
            boolean shovel = name.equals("white_sand");
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id) && block.asItem() != Items.AIR
                    && BuiltInRegistries.ITEM.getKey(block.asItem()).equals(id), id + " is not a block with its item");
            // Its loot table gives one of itself, as its kin's does; without the right tool a player gets none of the
            // four stone ones, which is the tool check below, not the table's.
            List<ItemStack> drops = Block.getDrops(state, level, at, null);
            helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0), new ItemStack(block)),
                    id + " drops " + drops);
            helper.assertTrue(state.getLightEmission(level, at) == 0, id + " glows");
            // White sand needs no tool, as sand does; the stone ones need a pickaxe, and the least of them will do.
            ItemStack tool = new ItemStack(shovel ? Items.WOODEN_SHOVEL : Items.WOODEN_PICKAXE);
            helper.assertTrue(state.is(shovel ? BlockTags.MINEABLE_WITH_SHOVEL : BlockTags.MINEABLE_WITH_PICKAXE)
                            && state.requiresCorrectToolForDrops() != shovel && tool.isCorrectToolForDrops(state),
                    id + " is not mined as its kin is");
        }
        BlockState sand = ModBlocks.WHITE_SAND.get().defaultBlockState();
        helper.assertTrue(BuiltInRegistries.BLOCK.get(BleachMod.id("white_sand")) instanceof ColoredFallingBlock falling
                        && falling.getDustColor(sand, level, at) == ModBlocks.WHITE_SAND_DUST
                        && sand.getSoundType(level, at, null) == SoundType.SAND,
                "white sand is not a falling block with white dust and sand's sound");
        // A camel treads it and an Enderman carries it as sand; nothing that grows on sand takes root in it.
        helper.assertTrue(sand.is(BlockTags.CAMEL_SAND_STEP_SOUND_BLOCKS) && sand.is(BlockTags.ENDERMAN_HOLDABLE)
                && !sand.is(BlockTags.SAND), "white sand is not trodden or carried as sand, or is planted as sand");
        BlockState quartz = ModBlocks.HUECO_QUARTZ.get().defaultBlockState();
        helper.assertTrue(quartz.getBlock() instanceof RotatedPillarBlock
                && quartz.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y, "Hueco quartz is not an upright pillar");
        helper.assertTrue(ModBlocks.SMOOTH_WHITE_SANDSTONE.get().defaultDestroyTime() == 2.0F
                        && ModBlocks.SMOOTH_WHITE_SANDSTONE.get().getExplosionResistance() == 6.0F
                        && ModBlocks.WHITE_SANDSTONE.get().defaultDestroyTime() == 0.8F,
                "white sandstone is not as hard as sandstone, or smooth white sandstone as smooth sandstone");
        // Four white sand make a white sandstone, which smelts smooth; four Hueco quartz make four bricks; white sand
        // smelts to glass through vanilla's own recipe, as sand does.
        ItemStack grain = new ItemStack(ModItems.WHITE_SAND.get()), trunk = new ItemStack(ModItems.HUECO_QUARTZ.get());
        assertCrafts(helper, List.of(grain, grain, grain, grain), new ItemStack(ModItems.WHITE_SANDSTONE.get()));
        assertCrafts(helper, List.of(trunk, trunk, trunk, trunk), new ItemStack(ModItems.HUECO_QUARTZ_BRICKS.get(), 4));
        assertSmelts(helper, new ItemStack(ModItems.WHITE_SANDSTONE.get()), ModItems.SMOOTH_WHITE_SANDSTONE.get());
        assertSmelts(helper, grain, Items.GLASS);
        // The recipe book offers each of the mod's three once its ingredient is first held, as vanilla's are offered.
        for (String recipe : List.of("white_sandstone", "smooth_white_sandstone", "hueco_quartz_bricks")) {
            ResourceLocation unlock = BleachMod.id("recipes/building_blocks/" + recipe);
            helper.assertTrue(level.getServer().getAdvancements().get(unlock) != null, recipe + " has no recipe unlock");
        }
        helper.succeed();
    }

    /**
     * White sand over a block of air falls onto the white sandstone below it, and white sand laid on white sandstone stays.
     * Both columns start in the template's lowest layer, relative y 1, since y 0 is the layer of its structure block; the
     * sandstone there stands over air, as sandstone does not fall.
     */
    @GameTest(template = "empty")
    public static void whiteSandFalls(GameTestHelper helper) {
        Block sand = ModBlocks.WHITE_SAND.get(), sandstone = ModBlocks.WHITE_SANDSTONE.get();
        // Every block the test sets or the fall leaves, each column from the top down, so clearing it leaves no white
        // sand over air to fall again.
        List<BlockPos> laid = List.of(new BlockPos(1, 3, 1), new BlockPos(1, 2, 1), new BlockPos(1, 1, 1),
                new BlockPos(0, 2, 0), new BlockPos(0, 1, 0));
        Runnable clear = () -> {
            QuincyArtsTests.discardAll(helper.getEntities(EntityType.FALLING_BLOCK));
            laid.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
        };
        // Its tick comes 2 after it is set, and the one-block fall lands 7 ticks after that. The check ends the test
        // either way, long before its timeout, and clears up first.
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertBlockPresent(sand, 1, 2, 1);
                helper.assertBlockNotPresent(sand, 1, 3, 1);
                helper.assertBlockPresent(sand, 0, 2, 0);
                helper.assertEntityNotPresent(EntityType.FALLING_BLOCK);
            } finally {
                clear.run();
            }
            helper.succeed();
        });
        HollowBaitTests.orClear(() -> {
            helper.setBlock(1, 1, 1, sandstone);
            helper.setBlock(1, 3, 1, sand);
            helper.setBlock(0, 1, 0, sandstone);
            helper.setBlock(0, 2, 0, sand);
        }, clear).run();
    }

    /** Asserts that these four stacks, two by two in a crafting grid, make exactly this. */
    private static void assertCrafts(GameTestHelper helper, List<ItemStack> grid, ItemStack expected) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 2, grid);
        ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(ItemStack.matches(made, expected), grid + " crafts " + made + ", not " + expected);
    }

    /** Asserts that this stack smelts in a furnace to one of this item. */
    private static void assertSmelts(GameTestHelper helper, ItemStack stack, Item expected) {
        ServerLevel level = helper.getLevel();
        SingleRecipeInput input = new SingleRecipeInput(stack);
        ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(made.is(expected) && made.getCount() == 1, stack + " smelts to " + made + ", not " + expected);
    }
}
