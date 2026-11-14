package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.Collections;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
                    List.of(ModItems.ROOF_TILES, ModItems.ROOF_TILES, ModItems.ROOF_TILES), 6, 2),
            "shoji", new Kin(Set.of("mineable/axe"), 30, 60, "decorations", 3, List.of(Items.STICK, Items.PAPER,
                    Items.STICK, Items.STICK, Items.PAPER, Items.STICK, Items.STICK, Items.PAPER, Items.STICK), 6, 0),
            "shoji_door", new Kin(Set.of("mineable/axe", "wooden_doors", "doors", "mob_interactable_doors"), 30, 60,
                    "redstone", 2, Collections.nCopies(6, ModItems.SHOJI), 3, 0),
            // A wool carpet of any colour edges a bale of straw, through vanilla's tag.
            "tatami", new Kin(Set.of("mineable/hoe"), 60, 20, "building_blocks", 2,
                    List.of(Items.HAY_BLOCK, Items.RED_CARPET), 2, 0));

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
        // The door: its upper half drops nothing; it is every mob's wooden door, of the mod's own registered set, with
        // bamboo's sounds; a piston breaks it; and its item burns in a furnace as a wooden door's does.
        DoorBlock shojiDoor = ModBlocks.SHOJI_DOOR.get();
        BlockState door = shojiDoor.defaultBlockState();
        helper.assertTrue(Block.getDrops(door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), level, at, null).isEmpty()
                        && DoorBlock.isWoodenDoor(door) && shojiDoor.type() == ModBlocks.SHOJI_SET
                        && BlockSetType.values().anyMatch(type -> type == ModBlocks.SHOJI_SET)
                        && door.getSoundType(level, at, null) == SoundType.BAMBOO_WOOD
                        && door.getPistonPushReaction() == PushReaction.DESTROY && shojiDoor.defaultDestroyTime() == 2.0F,
                "the shōji door is not a wooden door of its own set, or not as light as paper and thin wood");
        ItemStack doorItem = ModItems.SHOJI_DOOR.toStack();
        helper.assertTrue(doorItem.is(ItemTags.WOODEN_DOORS) && doorItem.getBurnTime(RecipeType.SMELTING) == 200,
                "the shōji door does not burn in a furnace as a wooden door does");
        // Shōji is a pane that hides no face behind it; tatami a block that faces a way.
        Block shoji = ModBlocks.SHOJI.get();
        Block tatami = ModBlocks.TATAMI.get();
        helper.assertTrue(shoji instanceof IronBarsBlock && !shoji.defaultBlockState().canOcclude()
                        && shoji.defaultDestroyTime() == 1.0F
                        && tatami instanceof GlazedTerracottaBlock && tatami.defaultDestroyTime() == 0.5F,
                "shōji is not a pane, or tatami a block that faces a way");
        helper.succeed();
    }

    @GameTest(template = "lane")
    public static void seireiteiBlocksPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        // Every spot the test sets, set back to air however it ends: the door's lower half first, which takes its
        // upper half with it, and that half drops nothing.
        List<BlockPos> laid = List.of(new BlockPos(1, 2, 1), new BlockPos(1, 3, 1), new BlockPos(4, 2, 1),
                new BlockPos(5, 2, 1), new BlockPos(6, 2, 1), new BlockPos(5, 2, 2), new BlockPos(8, 2, 1),
                new BlockPos(10, 2, 0), new BlockPos(10, 2, 2));
        try {
            // The door, from one real placing looking south at the top of a floor block: two halves, facing south, hinged
            // on the left, and shut, to a walking mob too.
            placeOn(helper, player, ModItems.SHOJI_DOOR.toStack(), new BlockPos(1, 1, 1), Direction.UP);
            BlockPos lower = helper.absolutePos(new BlockPos(1, 2, 1));
            BlockState bottom = level.getBlockState(lower);
            BlockState top = level.getBlockState(lower.above());
            helper.assertTrue(bottom.is(ModBlocks.SHOJI_DOOR.get()) && top.is(ModBlocks.SHOJI_DOOR.get())
                            && bottom.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                            && top.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                            && bottom.getValue(DoorBlock.FACING) == Direction.SOUTH
                            && bottom.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
                            && !bottom.getValue(DoorBlock.OPEN) && !bottom.isPathfindable(PathComputationType.LAND),
                    "the door was set down as " + bottom + " under " + top);
            // A hand opens it, both halves, and a walking mob may then pass.
            BlockHitResult knock = new BlockHitResult(Vec3.atCenterOf(lower), Direction.NORTH, lower, false);
            helper.assertTrue(bottom.useWithoutItem(level, player, knock).consumesAction(), "a hand did not open the door");
            bottom = level.getBlockState(lower);
            top = level.getBlockState(lower.above());
            helper.assertTrue(bottom.getValue(DoorBlock.OPEN) && top.getValue(DoorBlock.OPEN)
                    && bottom.isPathfindable(PathComputationType.LAND), "the opened door is " + bottom + " under " + top);
            // Shōji set against the stone behind it, between a cobblestone wall and a glass pane: it joins all three, the
            // pane joins it back, and nothing joins it in front.
            helper.setBlock(new BlockPos(4, 2, 1), Blocks.COBBLESTONE_WALL);
            helper.setBlock(new BlockPos(6, 2, 1), Blocks.GLASS_PANE);
            helper.setBlock(new BlockPos(5, 2, 2), Blocks.STONE);
            placeOn(helper, player, ModItems.SHOJI.toStack(), new BlockPos(5, 2, 2), Direction.NORTH);
            BlockState shoji = helper.getBlockState(new BlockPos(5, 2, 1));
            BlockState pane = helper.getBlockState(new BlockPos(6, 2, 1));
            helper.assertTrue(shoji.is(ModBlocks.SHOJI.get()) && shoji.getValue(CrossCollisionBlock.WEST)
                            && shoji.getValue(CrossCollisionBlock.EAST) && shoji.getValue(CrossCollisionBlock.SOUTH)
                            && !shoji.getValue(CrossCollisionBlock.NORTH) && !shoji.getValue(CrossCollisionBlock.WATERLOGGED)
                            && pane.getValue(CrossCollisionBlock.WEST),
                    "shōji between a wall and a pane is " + shoji + ", the pane " + pane);
            // Set in water, it holds the water, and fire cannot take it.
            BlockPos pool = new BlockPos(8, 2, 1);
            helper.setBlock(pool, Blocks.WATER);
            placeOn(helper, player, ModItems.SHOJI.toStack(), new BlockPos(8, 1, 1), Direction.UP);
            BlockState wet = helper.getBlockState(pool);
            helper.assertTrue(wet.is(ModBlocks.SHOJI.get()) && wet.getValue(CrossCollisionBlock.WATERLOGGED)
                            && wet.getFlammability(level, helper.absolutePos(pool), Direction.UP) == 0,
                    "shōji set in water is " + wet);
            // Tatami faces whoever lays it: laid looking south, it faces north; looking west, east.
            placeOn(helper, player, ModItems.TATAMI.toStack(), new BlockPos(10, 1, 0), Direction.UP);
            player.setYRot(90.0F);
            placeOn(helper, player, ModItems.TATAMI.toStack(), new BlockPos(10, 1, 2), Direction.UP);
            Direction first = helper.getBlockState(new BlockPos(10, 2, 0)).getValue(HorizontalDirectionalBlock.FACING);
            Direction second = helper.getBlockState(new BlockPos(10, 2, 2)).getValue(HorizontalDirectionalBlock.FACING);
            helper.assertTrue(first == Direction.NORTH && second == Direction.EAST,
                    "tatami was laid facing " + first + " and " + second);
        } finally {
            laid.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
        }
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

    /** Sets this block item down against this face of the block at this relative spot, as this player would. */
    private static void placeOn(GameTestHelper helper, Player player, ItemStack stack, BlockPos against, Direction face) {
        BlockPos at = helper.absolutePos(against);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
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
