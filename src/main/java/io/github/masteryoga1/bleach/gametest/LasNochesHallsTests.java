package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.soul.HuecoMundoSpawns;
import io.github.masteryoga1.bleach.soul.SafeLanding;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import io.github.masteryoga1.bleach.world.LasNoches;
import io.github.masteryoga1.bleach.world.gen.LasNochesHallsPiece;
import io.github.masteryoga1.bleach.world.gen.LasNochesHallsStructure;
import io.github.masteryoga1.bleach.world.gen.LasNochesPiece;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Las Noches' halls: their table and what it cuts from the shell, the halls laid after the shell as worldgen lays them
 * and by their stamp in a world made before them, the sky block, and that nothing spawns inside.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class LasNochesHallsTests {
    private static final TagKey<Biome> STARTS_IN =
            TagKey.create(Registries.BIOME, BleachMod.id("has_structure/las_noches_halls"));
    private static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("las_noches_halls"));
    /** Test H2's chunks, x and z -16 to 15: the fifth tower's foot, its stair, the crown it climbs through, the throne. */
    private static final List<ChunkPos> CENTRE = List.of(new ChunkPos(-1, -1), new ChunkPos(0, -1), new ChunkPos(-1, 0),
            new ChunkPos(0, 0));
    /** Test H3's chunk, x -16 to -1 and z 48 to 63: the south door's western half, its corridor, the floor beside it. */
    private static final ChunkPos SOUTH = new ChunkPos(-1, 3);
    /** The chunk west of it, the dome's foot and floor, which the shell reaches in H3 and the halls do not. */
    private static final ChunkPos BESIDE = new ChunkPos(-2, 3);
    private static final double SAME = 1.0E-6;

    private LasNochesHallsTests() {
    }

    // ---- Shared helpers ----

    /** The halls' structure, as the registries hold it. */
    private static Structure halls(GameTestHelper helper, ServerLevel hueco) {
        Structure structure = hueco.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LasNoches.HALLS);
        helper.assertTrue(structure != null, "the structure " + LasNoches.HALLS.location() + " did not load");
        return structure;
    }

    /** Whether this chunk holds this structure's reference from the centre's chunk, as worldgen and the stamps leave it. */
    static boolean marked(ServerLevel hueco, ResourceKey<Structure> key, ChunkPos pos) {
        Structure structure = hueco.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        return hueco.getChunk(pos.x, pos.z).getReferencesForStructure(structure).contains(LasNoches.HOME.toLong());
    }

    /** What stands in this cell once the shell, then the halls if {@code halls}, are laid over the level ground. */
    private static BlockState planned(int x, int y, int z, boolean halls) {
        HuecoMundoPlan.Hall hall = halls ? HuecoMundoPlan.hallsAt(x, y, z) : null;
        if (hall != null) {
            return LasNochesHallsPiece.state(hall);
        }
        HuecoMundoPlan.Shell shell = HuecoMundoPlan.shellAt(x, y, z);
        return shell != null ? LasNochesPiece.state(shell)
                : y == HuecoMundoPlan.FLOOR_Y ? ModBlocks.WHITE_SAND.get().defaultBlockState()
                : Blocks.AIR.defaultBlockState();
    }

    /**
     * Asserts that every cell of this chunk's column from the floor to the shell's top holds what the shell, then the
     * halls if {@code halls}, lay there; counts each kind of the halls' cells.
     */
    static Map<HuecoMundoPlan.Hall, Integer> assertLaid(GameTestHelper helper, ServerLevel hueco, ChunkPos pos,
                                                       boolean halls) {
        Map<HuecoMundoPlan.Hall, Integer> laid = new EnumMap<>(HuecoMundoPlan.Hall.class);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                for (int y = HuecoMundoPlan.FLOOR_Y; y <= HuecoMundoPlan.SHELL_BOX.maxY(); y++) {
                    BlockState found = hueco.getBlockState(at.set(x, y, z)), expected = planned(x, y, z, halls);
                    helper.assertTrue(found == expected, at.toShortString() + " holds " + found + ", not " + expected);
                    HuecoMundoPlan.Hall hall = HuecoMundoPlan.hallsAt(x, y, z);
                    if (halls && hall != null) {
                        laid.merge(hall, 1, Integer::sum);
                    }
                }
            }
        }
        return laid;
    }

    /**
     * Holds chunks of the real Hueco Mundo under tickets of its own, made now, and as its test ends, passed or failed,
     * lets them go, discards what the test set down, sets the shell's and the halls' cells of the chunks it clears back to
     * the level ground, air over white sand, and takes both references away.
     */
    record Hold(ServerLevel hueco, List<ChunkPos> centres, List<Integer> radii, List<ChunkPos> cleared,
                List<Entity> placed) implements GameTestListener {
        static Hold hold(GameTestHelper helper, ServerLevel hueco, List<ChunkPos> centres, int radius,
                         List<ChunkPos> cleared) {
            Hold hold = new Hold(hueco, new ArrayList<>(), new ArrayList<>(), cleared, new ArrayList<>());
            helper.testInfo.addListener(hold);
            centres.forEach(centre -> hold.widen(centre, radius));
            return hold;
        }

        /** Holds this chunk and those within this many of it as well, made now, as Held does in LasNochesTests. */
        void widen(ChunkPos centre, int radius) {
            centres.add(centre);
            radii.add(radius);
            hueco.getChunkSource().addRegionTicket(TicketType.FORCED, centre, radius, centre);
            hueco.getChunkSource().pollTask();
            ChunkPos.rangeClosed(centre, radius)
                    .forEach(each -> hueco.getChunkSource().getChunkFuture(each.x, each.z, ChunkStatus.FULL, false));
        }

        /** Sets this down in Hueco Mundo, to be discarded as the test ends. */
        <T extends Entity> T add(T entity) {
            placed.add(entity);
            hueco.addFreshEntity(entity);
            return entity;
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {
        }

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            end();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            end();
        }

        @Override
        public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
        }

        /** As Held's end: the tickets first, and their removal run at once, so no stamp's queued check lays a chunk again. */
        private void end() {
            for (int i = 0; i < centres.size(); i++) {
                hueco.getChunkSource().removeRegionTicket(TicketType.FORCED, centres.get(i), radii.get(i), centres.get(i));
            }
            TravelTests.discard(placed.toArray(new Entity[0]));
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (ChunkPos chunk : cleared) {
                for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                        for (int y = HuecoMundoPlan.FLOOR_Y; y <= HuecoMundoPlan.SHELL_BOX.maxY(); y++) {
                            if (HuecoMundoPlan.shellAt(x, y, z) != null || HuecoMundoPlan.hallsAt(x, y, z) != null) {
                                hueco.setBlock(at.set(x, y, z), y == HuecoMundoPlan.FLOOR_Y
                                        ? ModBlocks.WHITE_SAND.get().defaultBlockState() : Blocks.AIR.defaultBlockState(),
                                        Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
                LevelChunk laid = hueco.getChunk(chunk.x, chunk.z);
                Map<Structure, LongSet> others = new HashMap<>(laid.getAllReferences());
                others.remove(hueco.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LasNoches.STRUCTURE));
                others.remove(hueco.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LasNoches.HALLS));
                laid.setAllReferences(others);
            }
            hueco.getChunkSource().pollTask();
        }
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

    // ---- H2. The halls in a new world ----

    /**
     * Test H2. The halls' data: their tag holds the one biome they start in; they are their own structure, laid with
     * the strongholds' step after the shell's, the land unbent and no spawns of their own; their set rings them once at
     * the centre's chunk, in Hueco Mundo alone. Set out as worldgen sets them out, saved and read back by their ids and
     * plan, and laid in the centre's four chunks after the shell as worldgen orders the two, they lay the plan there; a
     * safe landing over the court is refused until a chunk holds the halls' reference, then taken; and a pig joining on
     * the throne's seat is let be, while one joining in a step of the dais is set down outside the wall.
     */
    @GameTest(template = "empty", batch = "las_noches_halls", timeoutTicks = 900)
    public static void hallsAreLaidAfterTheShell(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess access = server.registryAccess();
        ServerLevel hueco = LasNochesTests.hueco(helper);
        List<String> tagged = access.registryOrThrow(Registries.BIOME).getTag(STARTS_IN)
                .map(named -> named.stream().map(Holder::getRegisteredName).toList()).orElse(List.of());
        helper.assertTrue(tagged.equals(List.of("bleach:las_noches")), "the halls may start in " + tagged);
        Structure halls = halls(helper, hueco), shell = LasNochesTests.lasNoches(helper, hueco);
        helper.assertTrue(halls instanceof LasNochesHallsStructure && halls.type() == ModWorldgen.LAS_NOCHES_HALLS.get()
                        && halls.biomes().unwrapKey().equals(Optional.of(STARTS_IN))
                        && halls.step() == GenerationStep.Decoration.STRONGHOLDS
                        && halls.step().ordinal() > shell.step().ordinal()
                        && halls.terrainAdaptation() == TerrainAdjustment.NONE && halls.spawnOverrides().isEmpty(),
                "the halls are not their own structure on their tag, laid after the shell, unbending and spawnless");
        helper.assertTrue(BuiltInRegistries.STRUCTURE_PIECE.getKey(ModWorldgen.LAS_NOCHES_HALLS_PIECE.get())
                .equals(LasNoches.HALLS.location()), "the halls' piece is not registered as bleach:las_noches_halls");
        Holder<StructureSet> set = access.registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(SET);
        helper.assertTrue(set.value().structures().size() == 1
                        && set.value().structures().get(0).structure().is(LasNoches.HALLS)
                        && set.value().placement() instanceof ConcentricRingsStructurePlacement placed
                        && placed.distance() == 0 && placed.count() == 1 && placed.preferredBiomes().size() == 0,
                "the halls' set is not one ring of count 1 and distance 0 that prefers no biome");
        ConcentricRingsStructurePlacement rings = (ConcentricRingsStructurePlacement) set.value().placement();
        BiomeSource source = hueco.getChunkSource().getGenerator().getBiomeSource();
        for (ChunkGeneratorStructureState state : List.of(hueco.getChunkSource().getGeneratorState(),
                ChunkGeneratorStructureState.createForNormal(hueco.getChunkSource().randomState(), 7_391L, source,
                        access.lookupOrThrow(Registries.STRUCTURE_SET)))) {
            List<ChunkPos> ring = state.getRingPositionsFor(rings);
            helper.assertTrue(state.possibleStructureSets().stream().anyMatch(possible -> possible.is(SET))
                    && List.of(LasNoches.HOME).equals(ring), "the halls are rung at " + ring);
        }
        for (ResourceKey<Level> world : List.of(Level.OVERWORLD, BleachWorlds.SOUL_SOCIETY)) {
            ServerLevel other = server.getLevel(world);
            helper.assertFalse(other == null || other.getChunkSource().getGeneratorState().possibleStructureSets().stream()
                    .anyMatch(possible -> possible.is(SET)), "the halls may start in " + world.location());
        }

        // Set out from the centre's chunk on their own biome, as worldgen sets them out: one piece over their box, whose
        // chunks, -4 to 3 each way, all lie within the 8 that references reach from the start's chunk.
        StructureStart start = LasNochesTests.generate(hueco, halls, LasNoches.HOME, halls.biomes()::contains);
        BoundingBox box = HuecoMundoPlan.HALLS_BOX;
        helper.assertTrue(start.isValid() && start.getPieces().size() == 1
                        && start.getPieces().get(0) instanceof LasNochesHallsPiece piece && piece.getBoundingBox().equals(box)
                        && start.getBoundingBox().equals(box) && start.getChunkPos().equals(LasNoches.HOME)
                        && Stream.of(box.minX(), box.maxX(), box.minZ(), box.maxZ())
                        .allMatch(edge -> Math.abs(edge >> 4) <= 8),
                "the halls set out in " + start.getChunkPos() + " cover " + start.getBoundingBox() + ", not " + box);
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(hueco);
        CompoundTag tag = start.createTag(context, LasNoches.HOME);
        CompoundTag child = tag.getList("Children", Tag.TAG_COMPOUND).getCompound(0);
        String id = LasNoches.HALLS.location().toString();
        helper.assertTrue(tag.getString("id").equals(id) && child.getString("id").equals(id)
                && child.getInt("Plan") == LasNochesHallsPiece.PLAN, "the halls were saved as " + tag);
        StructureStart loaded = StructureStart.loadStaticStart(context, tag, hueco.getSeed());
        helper.assertTrue(loaded != null && loaded.getPieces().get(0) instanceof LasNochesHallsPiece read
                        && read.getBoundingBox().equals(box) && read.plan() == LasNochesHallsPiece.PLAN,
                "the saved halls could not be read back by their plan");

        // The shell's start and the halls', laid in each chunk in the order of their steps, as worldgen lays a chunk's
        // structures: the shell, then the halls over it.
        Hold hold = Hold.hold(helper, hueco, CENTRE, 1, CENTRE);
        List<StructureStart> starts = Stream.of(start, LasNochesTests.generate(hueco, shell, LasNoches.HOME, biome -> true))
                .sorted(Comparator.comparingInt(each -> each.getStructure().step().ordinal())).toList();
        Map<HuecoMundoPlan.Hall, Integer> tally = new EnumMap<>(HuecoMundoPlan.Hall.class);
        for (ChunkPos chunk : CENTRE) {
            BoundingBox column = new BoundingBox(chunk.getMinBlockX(), hueco.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX(), hueco.getMaxBuildHeight(), chunk.getMaxBlockZ());
            starts.forEach(each -> each.placeInChunk(hueco, hueco.structureManager(), hueco.getChunkSource().getGenerator(),
                    hueco.getRandom(), column, chunk));
            assertLaid(helper, hueco, chunk, true).forEach((kind, count) -> tally.merge(kind, count, Integer::sum));
        }
        // 116 of the halls' 128 stairs: the other 12, the meeting hall's seats, are in chunks (-1, -3) to (0, -2).
        helper.assertTrue(tally.equals(Map.of(HuecoMundoPlan.Hall.CUT, 12, HuecoMundoPlan.Hall.STONE, 11_170,
                        HuecoMundoPlan.Hall.TILES, 1_030, HuecoMundoPlan.Hall.TRIM, 4, HuecoMundoPlan.Hall.SKY, 4_380,
                        HuecoMundoPlan.Hall.STAIR_NORTH, 24, HuecoMundoPlan.Hall.STAIR_EAST, 24,
                        HuecoMundoPlan.Hall.STAIR_SOUTH, 44, HuecoMundoPlan.Hall.STAIR_WEST, 24,
                        HuecoMundoPlan.Hall.SLAB, 2)),
                "the centre's four chunks hold " + tally);

        // Over the court's floor in (0, 0): shut in while the chunk lacks the halls' reference, then open.
        Pig pig = LasNochesTests.pig(helper, hueco, 12.5, 100.0, 12.5);
        Vec3 over = new Vec3(12.5, 100.0, 12.5);
        helper.assertTrue(SafeLanding.landingSpot(pig, hueco, over) == null, "a soul was set down in the sealed dome");
        hueco.getChunk(0, 0).addReferenceForStructure(halls, LasNoches.HOME.toLong());
        Vec3 landed = SafeLanding.landingSpot(pig, hueco, over);
        helper.assertTrue(landed != null && landed.distanceTo(new Vec3(12.5, HuecoMundoPlan.LEVEL_Y, 12.5)) < SAME,
                "a soul over the court was set down at " + landed);

        // Burial is judged by the blocks' shapes: on the throne's seat, the low half of a stair, a pig is not buried, and
        // 0.3 up in the dais's first step, its high half too, one is, and is set down outside the wall once it joins.
        Pig seated = hold.add(LasNochesTests.pig(helper, hueco, 0.5, 147.5, 5.05)),
                stepped = hold.add(LasNochesTests.pig(helper, hueco, 0.5, 144.3, 2.5));
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, stepped.getZ() > HuecoMundoPlan.HALLS_BOX.maxZ(),
                        "the pig in the dais's step was not moved"))
                .thenExecute(() -> {
                    LasNochesTests.onSandOutside(helper, hueco, stepped, stepped.position(), new Vec3(26.5, 96.0, 133.5),
                            "the pig in the dais's step");
                    helper.assertTrue(seated.position().distanceTo(new Vec3(0.5, 147.5, 5.05)) < SAME,
                            "the pig on the throne's seat was moved to " + seated.position());
                })
                .thenSucceed();
    }

    // ---- H3. The halls in an old world ----

    /**
     * Test H3. In a world made before the halls, the south door's chunk is laid by their stamp only once the shell's stamp
     * has laid it and the 8 beside it, cell for cell as planned, and once only; the chunk beside, whose own neighbours
     * lack the shell, keeps the shell alone and a safe landing there is refused while one on the open floor is taken. A
     * pig set where a pier goes and one the shell shuts in beside it are set down outside the wall; one on the floor stays.
     */
    @GameTest(template = "empty", batch = "las_noches", timeoutTicks = 900)
    public static void theHallsStampWaitsForTheShell(GameTestHelper helper) {
        ServerLevel hueco = LasNochesTests.hueco(helper);
        int site = (SOUTH.x - (HuecoMundoPlan.HALLS_BOX.minX() >> 4))
                + (SOUTH.z - (HuecoMundoPlan.HALLS_BOX.minZ() >> 4)) * 8;
        helper.assertTrue(LasNoches.HALLS_STAMP.covered(site).equals(List.of(SOUTH))
                        && LasNoches.HALLS_STAMP.startChunk(site).equals(LasNoches.HOME),
                "the halls' site " + site + " is not " + SOUTH);
        Hold hold = Hold.hold(helper, hueco, List.of(SOUTH), 1, ChunkPos.rangeClosed(SOUTH, 1).toList());
        Pig pier = LasNochesTests.pig(helper, hueco, -4.5, HuecoMundoPlan.SHELL_BOX.maxY() + 2.0, 52.5),
                floor = LasNochesTests.pig(helper, hueco, -8.5, HuecoMundoPlan.SHELL_BOX.maxY() + 2.0, 52.5),
                shut = LasNochesTests.pig(helper, hueco, -20.5, HuecoMundoPlan.SHELL_BOX.maxY() + 2.0, 50.5);
        Pig buried = LasNochesTests.pig(helper, hueco, -4.5, HuecoMundoPlan.LEVEL_Y, 52.5),
                standing = LasNochesTests.pig(helper, hueco, -10.5, HuecoMundoPlan.LEVEL_Y, 54.5);
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, marked(hueco, LasNoches.STRUCTURE, SOUTH),
                        "the shell's stamp has not laid " + SOUTH))
                .thenExecuteAfter(5, () -> {
                    // The shell alone: the eight beside it lack it, so the halls wait.
                    helper.assertFalse(marked(hueco, LasNoches.HALLS, SOUTH),
                            "the halls were laid before the shell about them");
                    assertLaid(helper, hueco, SOUTH, false);
                    // Joining above the shell's box and moved in, so only a lay frees them.
                    for (Pig pig : List.of(pier, floor, shut)) {
                        hold.add(pig).moveTo(pig.getX(), HuecoMundoPlan.LEVEL_Y, pig.getZ(), 0.0F, 0.0F);
                    }
                    hold.widen(SOUTH, 2);
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, marked(hueco, LasNoches.HALLS, SOUTH),
                        "the halls' stamp has not laid " + SOUTH))
                .thenExecute(() -> {
                    helper.assertTrue(ChunkPos.rangeClosed(SOUTH, 1).allMatch(pos -> marked(hueco, LasNoches.STRUCTURE, pos)),
                            "the halls were laid before the shell stood in the eight beside them");
                    Map<HuecoMundoPlan.Hall, Integer> tally = assertLaid(helper, hueco, SOUTH, true);
                    helper.assertTrue(tally.equals(Map.of(HuecoMundoPlan.Hall.CUT, 108, HuecoMundoPlan.Hall.STONE, 30,
                                    HuecoMundoPlan.Hall.TILES, 262, HuecoMundoPlan.Hall.TRIM, 16,
                                    HuecoMundoPlan.Hall.SKY, 329)),
                            "the south door's chunk holds " + tally);
                    helper.assertFalse(marked(hueco, LasNoches.HALLS, BESIDE), "the chunk beside it was laid too");
                    assertLaid(helper, hueco, BESIDE, false);
                    LasNochesTests.onSandOutside(helper, hueco, pier, pier.position(), new Vec3(-11.5, 96.0, 135.5),
                            "the pig a pier was laid over");
                    LasNochesTests.onSandOutside(helper, hueco, shut, shut.position(), new Vec3(-51.5, 96.0, 126.5),
                            "the pig the shell shut in beside the halls");
                    helper.assertTrue(floor.position().distanceTo(new Vec3(-8.5, 96.0, 52.5)) < SAME
                                    && hueco.getBlockState(floor.blockPosition().below())
                                    .is(ModBlocks.LAS_NOCHES_TILES.get()),
                            "the pig on the floor was moved to " + floor.position());
                    // Still shut in beside, open here.
                    helper.assertTrue(SafeLanding.landingSpot(floor, hueco, new Vec3(-20.5, 100.0, 52.5)) == null,
                            "a soul was set down where the shell still shuts it in");
                    Vec3 open = SafeLanding.landingSpot(floor, hueco, new Vec3(-10.5, 100.0, 54.5));
                    helper.assertTrue(open != null && open.distanceTo(new Vec3(-10.5, 96.0, 54.5)) < SAME,
                            "a soul over the halls' floor was set down at " + open);
                    // Never laid twice: a broken tile stays broken and the chunk keeps its reference.
                    BlockPos tile = new BlockPos(-12, HuecoMundoPlan.FLOOR_Y, 56);
                    hueco.setBlock(tile, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    helper.assertFalse(LasNoches.HALLS_STAMP.ensureBuilt(hueco, site),
                            "the halls' stamp laid the chunk again");
                    helper.assertTrue(hueco.getBlockState(tile).isAir() && marked(hueco, LasNoches.HALLS, SOUTH),
                            "the halls' stamp set back a tile of a chunk it had laid, or took its reference");
                    // Joining in a pier's stone, as one saved there before the halls stood, and on the open floor.
                    hold.add(buried);
                    hold.add(standing);
                })
                .thenExecuteAfter(5, () -> {
                    LasNochesTests.onSandOutside(helper, hueco, buried, buried.position(), new Vec3(-11.5, 96.0, 135.5),
                            "the pig that joined in a pier");
                    helper.assertTrue(standing.position().distanceTo(new Vec3(-10.5, 96.0, 54.5)) < SAME,
                            "the pig that joined on the halls' floor was moved to " + standing.position());
                })
                .thenSucceed();
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

    // ---- H5. Spawns ----

    /**
     * Test H5. Nothing spawns naturally in the halls: the biomes Hueco Mundo's own source gives the hall, a corridor, the
     * court, the stair, the throne room and the air under the lining list no creature of any kind, the halls bring no
     * spawns of their own, and Hueco Mundo's ground rule refuses their floor's tiles and sky, where it takes white sand.
     */
    @GameTest(template = "empty")
    public static void nothingSpawnsInTheHalls(GameTestHelper helper) {
        ServerLevel hueco = LasNochesTests.hueco(helper);
        BiomeSource source = hueco.getChunkSource().getGenerator().getBiomeSource();
        for (BlockPos spot : List.of(new BlockPos(0, 96, -28), new BlockPos(0, 96, 50), new BlockPos(12, 96, 12),
                new BlockPos(3, 110, -4), new BlockPos(0, 150, 0), new BlockPos(40, 125, 0))) {
            Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(spot.getX()), QuartPos.fromBlock(spot.getY()),
                    QuartPos.fromBlock(spot.getZ()), hueco.getChunkSource().randomState().sampler());
            String name = biome.getRegisteredName();
            String expected = spot.getY() < HuecoMundoPlan.DOME_TOP_Y ? "bleach:las_noches" : "bleach:las_noches_grounds";
            helper.assertTrue(name.equals(expected), spot.toShortString() + " lies in " + name);
            for (MobCategory category : MobCategory.values()) {
                helper.assertTrue(biome.value().getMobSettings().getMobs(category).isEmpty(),
                        name + " lists " + category.getName() + " at " + spot.toShortString());
            }
        }
        helper.assertTrue(halls(helper, hueco).spawnOverrides().isEmpty(), "the halls bring spawns of their own");
        // The floor's tiles and sky in a row along the test's own box, then white sand, which the rule takes; all three are
        // set back to air however the test ends.
        BlockState[] grounds = {ModBlocks.LAS_NOCHES_TILES.get().defaultBlockState(),
                ModBlocks.LAS_NOCHES_SKY.get().defaultBlockState(), ModBlocks.WHITE_SAND.get().defaultBlockState()};
        try {
            for (int i = 0; i < grounds.length; i++) {
                BlockPos ground = new BlockPos(i, 1, 1);
                helper.setBlock(ground, grounds[i]);
                boolean takes = HuecoMundoSpawns.onHollowGround(helper.getLevel(), helper.absolutePos(ground.above()));
                helper.assertTrue(takes == (i == 2), grounds[i] + (takes ? " takes" : " refuses") + " a Hollow's spawn");
            }
        } finally {
            for (int i = 0; i < grounds.length; i++) {
                helper.setBlock(new BlockPos(i, 1, 1), Blocks.AIR);
            }
        }
        helper.succeed();
    }
}
