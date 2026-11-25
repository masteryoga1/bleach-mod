package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Las Noches' halls: their table and what it cuts from the shell, and the sky block that lines them. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class LasNochesHallsTests {
    private LasNochesHallsTests() {
    }

    // ---- H1. The table ----

    /** Whether this cell over the floor is air once the shell, then the halls, are laid over the level ground. */
    private static boolean open(int x, int y, int z) {
        HuecoMundoPlan.Hall hall = HuecoMundoPlan.hallsAt(x, y, z);
        return y > HuecoMundoPlan.FLOOR_Y
                && (hall == HuecoMundoPlan.Hall.CUT || hall == null && HuecoMundoPlan.shellAt(x, y, z) == null);
    }

    /** A cell worked by hand, and what the halls lay there; null for what stands there. */
    private record Cell(int x, int y, int z, @Nullable HuecoMundoPlan.Hall kind) {
    }

    /**
     * Test H1. The halls follow their table: hand-worked cells of each part, the count of each kind, the shell's cells
     * they change (the four doors and the stair's way through the crown, nothing else), a stair of 48 steps with
     * headroom from the floor to the throne room, and no air left shut in once both stand.
     */
    @GameTest(template = "empty")
    public static void hallsFollowTheirTable(GameTestHelper helper) {
        HuecoMundoPlan.Hall cut = HuecoMundoPlan.Hall.CUT, stone = HuecoMundoPlan.Hall.STONE,
                tiles = HuecoMundoPlan.Hall.TILES, trim = HuecoMundoPlan.Hall.TRIM, sky = HuecoMundoPlan.Hall.SKY,
                north = HuecoMundoPlan.Hall.STAIR_NORTH, east = HuecoMundoPlan.Hall.STAIR_EAST,
                south = HuecoMundoPlan.Hall.STAIR_SOUTH, west = HuecoMundoPlan.Hall.STAIR_WEST;
        for (Cell cell : List.of(
                // The floor: tiles, with rings of sky at r 10 to 50; beyond the dome's foot, the sand as it lies.
                new Cell(0, 95, 25, tiles), new Cell(10, 95, 0, sky), new Cell(-62, 95, 0, tiles), new Cell(-63, 95, 0, null),
                // The south door through the dome's tiles, and the corridor: roofed, piers every 6 under a dark band.
                new Cell(0, 96, 60, cut), new Cell(-4, 107, 60, null), new Cell(0, 108, 40, tiles),
                new Cell(0, 100, 40, null),
                new Cell(-5, 100, 52, stone), new Cell(-5, 100, 54, null), new Cell(-5, 105, 54, trim),
                // The meeting hall: its wall, band and roof; the table, the head's seat and a seat on each side.
                new Cell(-9, 100, -28, stone), new Cell(-9, 109, -28, trim), new Cell(0, 112, -28, tiles),
                new Cell(0, 111, -28, null), new Cell(0, 100, -16, null), new Cell(0, 96, -28, trim),
                new Cell(-1, 96, -23, south), new Cell(-2, 96, -24, null), new Cell(-2, 96, -25, west),
                new Cell(1, 96, -25, east),
                // The fifth tower's foot: its stone, the lit core, the door, the stair's first and last steps, a landing.
                new Cell(-8, 100, 0, stone), new Cell(-3, 100, -3, sky), new Cell(-2, 100, -2, stone),
                new Cell(-1, 96, -9, null), new Cell(3, 96, -3, south), new Cell(3, 101, 3, tiles),
                new Cell(2, 102, 3, west), new Cell(-4, 108, 2, north), new Cell(-3, 114, -5, east),
                new Cell(2, 143, -5, east), new Cell(0, 143, -5, cut),
                // The throne room: lined with sky, the dais's steps, the throne's seat, an arm and its banded back.
                new Cell(7, 150, 0, sky), new Cell(0, 205, 0, sky), new Cell(0, 144, 0, null), new Cell(0, 144, 2, south),
                new Cell(0, 146, 5, tiles), new Cell(0, 147, 5, south), new Cell(-2, 147, 5, HuecoMundoPlan.Hall.SLAB),
                new Cell(0, 150, 6, trim), new Cell(0, 153, 6, tiles), new Cell(0, 154, 6, null),
                // The lining inside the tiles, the open air under it, and the shell's own tiles left as laid.
                new Cell(41, 125, 0, sky), new Cell(40, 125, 0, null), new Cell(0, 141, 12, null))) {
            HuecoMundoPlan.Hall kind = HuecoMundoPlan.hallsAt(cell.x(), cell.y(), cell.z());
            helper.assertTrue(kind == cell.kind(), cell + " holds " + kind);
        }

        BoundingBox box = HuecoMundoPlan.HALLS_BOX;
        Map<HuecoMundoPlan.Hall, Integer> kinds = new EnumMap<>(HuecoMundoPlan.Hall.class);
        Map<String, Integer> changed = new HashMap<>();
        int[] faces = new int[6];
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    HuecoMundoPlan.Hall kind = HuecoMundoPlan.hallsAt(x, y, z);
                    if (kind == null) {
                        continue;
                    }
                    kinds.merge(kind, 1, Integer::sum);
                    HuecoMundoPlan.Shell shell = HuecoMundoPlan.shellAt(x, y, z);
                    if (shell != null || kind == cut) {
                        changed.merge(kind + " over " + shell, 1, Integer::sum);
                    }
                    faces[0] += x == box.minX() ? 1 : 0;
                    faces[1] += x == box.maxX() ? 1 : 0;
                    faces[2] += y == box.minY() ? 1 : 0;
                    faces[3] += y == box.maxY() ? 1 : 0;
                    faces[4] += z == box.minZ() ? 1 : 0;
                    faces[5] += z == box.maxZ() ? 1 : 0;
                }
            }
        }
        helper.assertTrue(kinds.equals(Map.of(cut, 876, stone, 13_266, tiles, 14_610, trim, 730, sky, 21_308, north, 24,
                east, 29, south, 46, west, 29, HuecoMundoPlan.Hall.SLAB, 2)), "the halls lay " + kinds);
        helper.assertTrue(IntStream.of(faces).allMatch(face -> face > 0),
                "the halls' box is wider than they are: no cell on a face of " + box);
        // The one change to the shell: its tiles cut for four doors of 8 by 12, and its crown for the stair's top flight.
        helper.assertTrue(changed.equals(Map.of("CUT over TILES", 864, "CUT over STONE", 12, "STAIR_EAST over STONE", 4)),
                "the halls change the shell's cells " + changed);

        // The stair: two steps side by side at each height from the floor to the crown, each height's beside a step or
        // a landing one lower, with three cells of air over every step; the last opens onto the throne room.
        List<BlockPos> lower = List.of();
        for (int y = HuecoMundoPlan.LEVEL_Y; y < HuecoMundoPlan.DOME_TOP_Y; y++) {
            List<BlockPos> steps = new ArrayList<>(), standing = new ArrayList<>();
            for (int x = -HuecoMundoPlan.WELL_HALF; x < HuecoMundoPlan.WELL_HALF; x++) {
                for (int z = -HuecoMundoPlan.WELL_HALF; z < HuecoMundoPlan.WELL_HALF; z++) {
                    HuecoMundoPlan.Hall here = HuecoMundoPlan.hallsAt(x, y, z);
                    if (here == north || here == east || here == south || here == west || here == tiles) {
                        (here == tiles ? standing : steps).add(new BlockPos(x, y, z));
                    }
                }
            }
            List<BlockPos> under = lower;
            helper.assertTrue(steps.size() == 2 && steps.get(0).distManhattan(steps.get(1)) == 1
                            && (under.isEmpty() || steps.stream().anyMatch(step -> under.stream()
                            .anyMatch(was -> was.distManhattan(step) == 2 && was.getY() == step.getY() - 1))),
                    "the stair's steps at y " + y + " are " + steps + ", over " + under);
            standing.addAll(steps);
            lower = standing;
            for (BlockPos step : steps) {
                for (int h = 1; h <= 3; h++) {
                    BlockPos over = step.above(h);
                    helper.assertTrue(open(over.getX(), over.getY(), over.getZ()),
                            "the stair's step at " + step.toShortString() + " has no headroom at " + over.toShortString());
                }
            }
        }

        // Nothing shut in: air flooded from the ground between the dome and the wall reaches every cell under the dome
        // and in the fifth tower's room that stands open once the shell and the halls are laid.
        int minX = -64, minY = HuecoMundoPlan.LEVEL_Y, minZ = -64, sizeX = 128, sizeY = 112, sizeZ = 128;
        boolean[] seen = new boolean[sizeX * sizeY * sizeZ], air = new boolean[seen.length];
        for (int cell = 0; cell < air.length; cell++) {
            air[cell] = open(cell / (sizeY * sizeZ) + minX, cell % sizeY + minY, cell / sizeY % sizeZ + minZ);
        }
        int[] queue = new int[seen.length];
        int head = 0, tail = 0;
        int start = ((0 - minX) * sizeZ + (63 - minZ)) * sizeY;
        seen[start] = true;
        queue[tail++] = start;
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (head < tail) {
            int cell = queue[head++], x = cell / (sizeY * sizeZ), z = cell / sizeY % sizeZ, y = cell % sizeY;
            for (int[] side : sides) {
                int nx = x + side[0], ny = y + side[1], nz = z + side[2];
                int next = (nx * sizeZ + nz) * sizeY + ny;
                if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && nz >= 0 && nz < sizeZ && !seen[next] && air[next]) {
                    seen[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        int shut = 0, room = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int y = 0; y < sizeY; y++) {
                    int wy = y + minY, cell = (x * sizeZ + z) * sizeY + y;
                    if (HuecoMundoPlan.enclosed(x + minX, wy, z + minZ) && air[cell]) {
                        shut += seen[cell] ? 0 : 1;
                        room += seen[cell] && wy >= HuecoMundoPlan.DOME_TOP_Y ? 1 : 0;
                    }
                }
            }
        }
        helper.assertTrue(shut == 0 && room == 9_426, shut + " cells stay shut in, and " + room
                + " of the throne room's air are reached, not 9426");
        helper.succeed();
    }

    // ---- H4. The sky block ----

    /**
     * Test H4. Las Noches' sky drops itself to a pickaxe alone, is in that tag alone, is as hard as the mod's stone, shows
     * light blue on a map, gives the fullest light and is drawn unshaded, is made by no recipe, refuses a spawn on itself,
     * and follows the trim in the creative tab.
     */
    @GameTest(template = "empty")
    public static void lasNochesSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A spot in the test's own box, for the calls that ask where the block would be; none of them reads it.
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        Player bare = helper.makeMockPlayer(GameType.SURVIVAL);
        Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        double hardest = Math.max(BleachConfig.LETZT_PFEIL_MAX_HARDNESS.getDefault(),
                Math.max(BleachConfig.GETSUGA_MAX_HARDNESS.getDefault(), BleachConfig.CERO_MAX_HARDNESS.getDefault()));
        Block block = ModBlocks.LAS_NOCHES_SKY.get();
        BlockState state = block.defaultBlockState();
        ResourceLocation id = BleachMod.id("las_noches_sky");
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(block).equals(id)
                && BuiltInRegistries.ITEM.getKey(block.asItem()).equals(id), id + " is not a block with its item");
        List<ItemStack> drops = Block.getDrops(state, level, at, null, miner, miner.getMainHandItem());
        helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0), new ItemStack(block)), id + " drops " + drops);
        helper.assertTrue(state.requiresCorrectToolForDrops() && !bare.hasCorrectToolForDrops(state, level, at)
                && miner.hasCorrectToolForDrops(state, level, at), id + " is not mined as the mod's stone is");
        Set<String> tags = state.getTags().map(TagKey::location)
                .filter(tag -> tag.getNamespace().equals("minecraft") || tag.getNamespace().equals(BleachMod.MODID))
                .map(ResourceLocation::toString).collect(Collectors.toSet());
        helper.assertTrue(tags.equals(Set.of("minecraft:mineable/pickaxe")), id + " is in the tags " + tags);
        helper.assertTrue(block.defaultDestroyTime() == 3.5F && block.getExplosionResistance() == 9.0F
                        && !GetsugaTensho.cuttable(state, level, at, hardest)
                        && GetsugaTensho.cuttable(state, level, at, 3.5),
                id + " is not 3.5 hard and 9 strong against blasts, beyond every art's default cap of " + hardest);
        helper.assertTrue(block.defaultMapColor() == MapColor.COLOR_LIGHT_BLUE && state.getLightEmission(level, at) == 15
                        && state.emissiveRendering(level, at) && state.getSoundType(level, at, null) == SoundType.STONE,
                id + " is not light blue on a map, fully lit, drawn unshaded and of stone's sound");
        RegistryAccess access = level.registryAccess();
        helper.assertTrue(level.getRecipeManager().getRecipes().stream()
                .noneMatch(recipe -> recipe.value().getResultItem(access).is(block.asItem())), id + " is crafted");
        helper.assertFalse(state.isValidSpawn(level, at, ModEntities.SPIDER_HOLLOW.get()), id + " lets a Hollow spawn on it");
        List<Item> tab = ModItems.ITEMS.getEntries().stream().<Item>map(DeferredHolder::get).toList();
        helper.assertTrue(tab.indexOf(ModItems.LAS_NOCHES_SKY.get()) == tab.indexOf(ModItems.LAS_NOCHES_TRIM.get()) + 1,
                "the creative tab does not follow Las Noches' trim with its sky");
        helper.succeed();
    }
}
