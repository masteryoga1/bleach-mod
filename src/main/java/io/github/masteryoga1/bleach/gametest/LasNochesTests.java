package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import io.github.masteryoga1.bleach.world.LasNoches;
import io.github.masteryoga1.bleach.world.gen.LasNochesPiece;
import io.github.masteryoga1.bleach.world.gen.LasNochesStructure;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for Las Noches: the plan's shell, the structure that sets it out about Hueco Mundo's centre, its blocks and its
 * data. The test server's Hueco Mundo is made with structures off, so worldgen lays none of it there; test B lays one
 * chunk of it by hand and takes it away again as it ends, so it runs in a batch of its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class LasNochesTests {
    private static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("las_noches"));
    private static final TagKey<Biome> STARTS_IN = TagKey.create(Registries.BIOME, BleachMod.id("has_structure/las_noches"));
    /** Test B's chunk, x and z -32 to -17: the shoulder tower about (-28, -28), the dome under it and the air it shuts in. */
    private static final ChunkPos SHOULDER = new ChunkPos(-2, -2);
    /** The three blocks, with the colour each shows on a map. */
    private static final Map<String, MapColor> BLOCKS = Map.of("las_noches_stone", MapColor.QUARTZ,
            "las_noches_tiles", MapColor.QUARTZ, "las_noches_trim", MapColor.COLOR_BLACK);

    private LasNochesTests() {
    }

    // ---- Shared helpers ----

    /** The test server's real Hueco Mundo. */
    private static ServerLevel hueco(GameTestHelper helper) {
        ServerLevel hueco = helper.getLevel().getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        helper.assertTrue(hueco != null, "the test server has no Hueco Mundo");
        return hueco;
    }

    /** Las Noches' structure, as the registries hold it. */
    private static Structure lasNoches(GameTestHelper helper, ServerLevel hueco) {
        Structure structure = hueco.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LasNoches.STRUCTURE);
        helper.assertTrue(structure != null, "the structure " + LasNoches.STRUCTURE.location() + " did not load");
        return structure;
    }

    /** The structure set out afresh from this chunk, as worldgen or a command sets it out, where these biomes allow. */
    private static StructureStart generate(ServerLevel hueco, Structure structure, ChunkPos chunk,
                                           Predicate<Holder<Biome>> biomes) {
        ChunkGenerator generator = hueco.getChunkSource().getGenerator();
        return structure.generate(hueco.registryAccess(), generator, generator.getBiomeSource(),
                hueco.getChunkSource().randomState(), hueco.getStructureManager(), hueco.getSeed(), chunk, 0, hueco, biomes);
    }

    /** The one piece of this start, which must be Las Noches'. */
    private static LasNochesPiece piece(GameTestHelper helper, StructureStart start, String what) {
        List<StructurePiece> pieces = start.getPieces();
        helper.assertTrue(start.isValid() && pieces.size() == 1 && pieces.get(0) instanceof LasNochesPiece,
                what + " has " + pieces.size() + " pieces, not Las Noches' one");
        return (LasNochesPiece) pieces.get(0);
    }

    /** Asserts that every cell of this chunk's column of the box holds what the plan lays, or air; counts each kind. */
    private static Map<HuecoMundoPlan.Shell, Integer> assertLaidAsPlanned(GameTestHelper helper, ServerLevel hueco,
                                                                         ChunkPos pos) {
        Map<HuecoMundoPlan.Shell, Integer> laid = new EnumMap<>(HuecoMundoPlan.Shell.class);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                for (int y = HuecoMundoPlan.SHELL_BOX.minY(); y <= HuecoMundoPlan.SHELL_BOX.maxY(); y++) {
                    HuecoMundoPlan.Shell kind = HuecoMundoPlan.shellAt(x, y, z);
                    BlockState found = hueco.getBlockState(at.set(x, y, z));
                    helper.assertTrue(kind == null ? found.isAir() : found == LasNochesPiece.state(kind),
                            at.toShortString() + " holds " + found + ", not the plan's " + kind);
                    if (kind != null) {
                        laid.merge(kind, 1, Integer::sum);
                    }
                }
            }
        }
        return laid;
    }

    // ---- The plan ----

    /** A cell of the plan worked by hand: what the shell lays there, if anything, and whether it shuts the cell in. */
    private record Cell(int x, int y, int z, @Nullable HuecoMundoPlan.Shell kind, boolean shut) {
    }

    /**
     * Test A. The plan's shell with no world: a cell worked by hand on each part, a count of each block over the box and
     * none outside it, the dome and the fifth tower's room shut in, and the arrivals' margin past the wall.
     */
    @GameTest(template = "empty")
    public static void lasNochesFollowsThePlan(GameTestHelper helper) {
        HuecoMundoPlan.Shell stone = HuecoMundoPlan.Shell.STONE, tiles = HuecoMundoPlan.Shell.TILES,
                trim = HuecoMundoPlan.Shell.TRIM;
        List<Cell> cells = List.of(
                // The east wall beside its gateway, 6.5 off the axis, the gateway open at 5.5, and the band over it; the
                // dome's foot (d 62.71) and the air it shuts in; the fifth tower's wall, 9.5 out, and its room; a
                // shoulder tower's cap; a great tower's foot.
                new Cell(121, 96, 6, stone, false), new Cell(121, 96, 5, null, false), new Cell(121, 111, 0, trim, false),
                new Cell(60, 96, 0, tiles, false), new Cell(59, 96, 0, null, true), new Cell(9, 150, 0, stone, false),
                new Cell(0, 144, 0, null, true), new Cell(28, 175, 28, tiles, false), new Cell(65, 96, 65, stone, false));
        for (Cell cell : cells) {
            HuecoMundoPlan.Shell kind = HuecoMundoPlan.shellAt(cell.x(), cell.y(), cell.z());
            boolean shut = HuecoMundoPlan.enclosed(cell.x(), cell.y(), cell.z());
            helper.assertTrue(kind == cell.kind() && shut == cell.shut(), "(" + cell.x() + ", " + cell.y() + ", "
                    + cell.z() + ") lays " + kind + " and is shut in " + shut + ", not " + cell.kind() + " and "
                    + cell.shut());
        }

        // Every cell of the box and a cell past each face: 137,704 stone, 45,080 tiles, 8,672 trim, and none outside it.
        BoundingBox box = HuecoMundoPlan.SHELL_BOX;
        Map<HuecoMundoPlan.Shell, Integer> counted = new EnumMap<>(HuecoMundoPlan.Shell.class);
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                for (int y = box.minY() - 1; y <= box.maxY() + 1; y++) {
                    HuecoMundoPlan.Shell kind = HuecoMundoPlan.shellAt(x, y, z);
                    if (kind != null) {
                        helper.assertTrue(box.isInside(x, y, z), "the shell lays " + kind + " outside its box at ("
                                + x + ", " + y + ", " + z + ")");
                        counted.merge(kind, 1, Integer::sum);
                    }
                }
            }
        }
        helper.assertTrue(counted.equals(Map.of(stone, 137_704, tiles, 45_080, trim, 8_672)),
                "the shell lays " + counted);

        // Shut in: each neighbour of a cell under the dome or in the fifth tower's room, the ground apart, is the shell
        // or shut in too, so nothing walks or flows in; and no cell of the shell is itself shut in.
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int inside = 0;
        for (int x = -HuecoMundoPlan.LAS_NOCHES_RADIUS; x < HuecoMundoPlan.LAS_NOCHES_RADIUS; x++) {
            for (int z = -HuecoMundoPlan.LAS_NOCHES_RADIUS; z < HuecoMundoPlan.LAS_NOCHES_RADIUS; z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    if (!HuecoMundoPlan.enclosed(x, y, z)) {
                        continue;
                    }
                    inside++;
                    helper.assertTrue(HuecoMundoPlan.shellAt(x, y, z) == null, "(" + x + ", " + y + ", " + z
                            + ") is both the shell and shut in by it");
                    for (int[] side : sides) {
                        int sx = x + side[0], sy = y + side[1], sz = z + side[2];
                        helper.assertTrue(sy < HuecoMundoPlan.LEVEL_Y || HuecoMundoPlan.enclosed(sx, sy, sz)
                                        || HuecoMundoPlan.shellAt(sx, sy, sz) != null,
                                "(" + x + ", " + y + ", " + z + ") opens onto (" + sx + ", " + sy + ", " + sz + ")");
                    }
                }
            }
        }
        helper.assertTrue(inside == 323_220, inside + " cells are shut in, not 323,220");

        // The box spans chunks -8 to 7 each way, all within the 8 a start's references reach from the centre's chunk.
        helper.assertTrue(box.equals(new BoundingBox(-124, 96, -124, 123, 207, 123)) && box.minX() >> 4 == -8
                && box.maxX() >> 4 == 7 && box.minZ() >> 4 == -8 && box.maxZ() >> 4 == 7, "the shell's box is " + box);
        // A landing reads the columns its body covers about a point pushed to r 136: from that point's column, at most
        // half a column's diagonal off, out to a Gillian's half width of 1.8 and the half column beyond.
        double nearest = HuecoMundoPlan.ARRIVAL_RADIUS - Math.sqrt(0.5) - 1.8 - 0.5;
        helper.assertTrue(nearest > HuecoMundoPlan.WALL_OUTER_RADIUS, "a landing may read a column " + nearest
                + " out, inside the wall's outer face at " + HuecoMundoPlan.WALL_OUTER_RADIUS);
        helper.succeed();
    }

    // ---- The structure ----

    /**
     * Holds a chunk of the real Hueco Mundo with the chunks beside it, and as its test ends, passed or failed, lets them
     * go and sets every cell of the shell in that chunk back to air, the open air of the level ground there.
     */
    private record Held(ServerLevel hueco, ChunkPos chunk) implements GameTestListener {
        /** Holds this chunk and those beside it till the test ends, made now, so a cell laid at its edge reads them. */
        static void hold(GameTestHelper helper, ServerLevel hueco, ChunkPos chunk) {
            helper.testInfo.addListener(new Held(hueco, chunk));
            hueco.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 1, chunk);
            ChunkPos.rangeClosed(chunk, 1).forEach(each -> hueco.getChunk(each.x, each.z));
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

        /** Lets the chunks go first, then takes the shell's blocks from the chunk. */
        private void end() {
            hueco.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 1, chunk);
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    for (int y = HuecoMundoPlan.SHELL_BOX.minY(); y <= HuecoMundoPlan.SHELL_BOX.maxY(); y++) {
                        if (HuecoMundoPlan.shellAt(x, y, z) != null) {
                            hueco.setBlock(at.set(x, y, z), air, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    /**
     * Test B. Las Noches is set out as worldgen sets it out, in the centre's chunk where its biome allows it: one piece
     * over the plan's box, which a command sets out the same from any chunk. Saved as a chunk saves it, it is read back
     * by its registered ids with its plan's version, and laid into a chunk of the real Hueco Mundo it lays the plan there.
     */
    @GameTest(template = "empty", batch = "las_noches", timeoutTicks = 900)
    public static void lasNochesIsSetOutAboutTheCentre(GameTestHelper helper) {
        ServerLevel hueco = hueco(helper);
        Structure structure = lasNoches(helper, hueco);
        // Its own biomes only, as worldgen asks: the biome under the dome's foot over the centre is las_noches.
        StructureStart start = generate(hueco, structure, LasNoches.HOME, structure.biomes()::contains);
        LasNochesPiece laid = piece(helper, start, "Las Noches set out in the centre's chunk");
        BoundingBox box = HuecoMundoPlan.SHELL_BOX;
        helper.assertTrue(laid.getBoundingBox().equals(box) && start.getBoundingBox().equals(box)
                        && start.getChunkPos().equals(LasNoches.HOME),
                "Las Noches set out in " + start.getChunkPos() + " covers " + start.getBoundingBox() + ", not " + box);
        LasNochesPiece elsewhere = piece(helper, generate(hueco, structure, new ChunkPos(20, -9), biome -> true),
                "Las Noches set out by command from chunk (20, -9)");
        helper.assertTrue(elsewhere.getBoundingBox().equals(box), "Las Noches set out by command covers "
                + elsewhere.getBoundingBox());

        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(hueco);
        CompoundTag tag = start.createTag(context, LasNoches.HOME);
        CompoundTag child = tag.getList("Children", Tag.TAG_COMPOUND).getCompound(0);
        String id = LasNoches.STRUCTURE.location().toString();
        helper.assertTrue(tag.getString("id").equals(id) && child.getString("id").equals(id)
                        && child.getInt("Plan") == LasNochesPiece.PLAN,
                "Las Noches was saved as " + tag.getString("id") + " with a piece " + child);
        StructureStart loaded = StructureStart.loadStaticStart(context, tag, hueco.getSeed());
        helper.assertTrue(loaded != null, "the saved Las Noches could not be read back");
        LasNochesPiece read = piece(helper, loaded, "Las Noches read back");
        helper.assertTrue(read.getBoundingBox().equals(box) && read.plan() == LasNochesPiece.PLAN,
                "Las Noches read back covers " + read.getBoundingBox() + " by the plan's version " + read.plan());

        // Set out from the centre's chunk with every biome allowed and laid in one chunk, the shoulder tower's, of the real
        // Hueco Mundo: its stone and band over the dome's tiles, the air the dome shuts in under them, cell for cell.
        Held.hold(helper, hueco, SHOULDER);
        BoundingBox column = new BoundingBox(SHOULDER.getMinBlockX(), hueco.getMinBuildHeight(), SHOULDER.getMinBlockZ(),
                SHOULDER.getMaxBlockX(), hueco.getMaxBuildHeight(), SHOULDER.getMaxBlockZ());
        generate(hueco, structure, LasNoches.HOME, biome -> true).placeInChunk(hueco, hueco.structureManager(),
                hueco.getChunkSource().getGenerator(), hueco.getRandom(), column, SHOULDER);
        Map<HuecoMundoPlan.Shell, Integer> tally = assertLaidAsPlanned(helper, hueco, SHOULDER);
        helper.assertTrue(tally.equals(Map.of(HuecoMundoPlan.Shell.STONE, 3_183, HuecoMundoPlan.Shell.TILES, 575,
                HuecoMundoPlan.Shell.TRIM, 144)), "the shoulder tower's chunk " + SHOULDER + " holds " + tally);
        helper.succeed();
    }

    // ---- The blocks ----

    /**
     * Test D. The three blocks: each drops itself to the least pickaxe and nothing to a bare hand, sits in the pickaxe's
     * tag alone, is harder than any art carves at its defaults, shows its colour on a map and is made by no recipe; they
     * follow Hueco quartz bricks in the creative tab.
     */
    @GameTest(template = "empty")
    public static void lasNochesBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A spot in the test's own box, for the calls that ask where the block would be; none of them reads it.
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        Player bare = helper.makeMockPlayer(GameType.SURVIVAL);
        Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        double hardest = Math.max(BleachConfig.LETZT_PFEIL_MAX_HARDNESS.getDefault(),
                Math.max(BleachConfig.GETSUGA_MAX_HARDNESS.getDefault(), BleachConfig.CERO_MAX_HARDNESS.getDefault()));
        RegistryAccess access = level.registryAccess();
        BLOCKS.forEach((name, colour) -> {
            ResourceLocation id = BleachMod.id(name);
            Block block = BuiltInRegistries.BLOCK.get(id);
            BlockState state = block.defaultBlockState();
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id)
                    && BuiltInRegistries.ITEM.getKey(block.asItem()).equals(id), id + " is not a block with its item");
            List<ItemStack> drops = Block.getDrops(state, level, at, null, miner, miner.getMainHandItem());
            helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0), new ItemStack(block)),
                    id + " drops " + drops);
            helper.assertTrue(state.requiresCorrectToolForDrops() && !bare.hasCorrectToolForDrops(state, level, at)
                    && miner.hasCorrectToolForDrops(state, level, at), id + " is not mined as the mod's stone is");
            Set<String> tags = state.getTags().map(TagKey::location)
                    .filter(tag -> tag.getNamespace().equals("minecraft") || tag.getNamespace().equals(BleachMod.MODID))
                    .map(ResourceLocation::toString).collect(Collectors.toSet());
            helper.assertTrue(tags.equals(Set.of("minecraft:mineable/pickaxe")), id + " is in the tags " + tags);
            // A server may raise an art's cap to carve it; at the defaults none does.
            helper.assertTrue(block.defaultDestroyTime() == 3.5F && block.getExplosionResistance() == 9.0F
                            && !GetsugaTensho.cuttable(state, level, at, hardest)
                            && GetsugaTensho.cuttable(state, level, at, 3.5),
                    id + " is not 3.5 hard and 9 strong against blasts, beyond every art's default cap of " + hardest);
            helper.assertTrue(block.defaultMapColor() == colour && state.getLightEmission(level, at) == 0
                            && state.getSoundType(level, at, null) == SoundType.STONE,
                    id + " is not " + colour + " on a map, dark and of stone's sound");
            helper.assertTrue(level.getRecipeManager().getRecipes().stream()
                    .noneMatch(recipe -> recipe.value().getResultItem(access).is(block.asItem())), id + " is crafted");
        });
        List<Item> tab = ModItems.ITEMS.getEntries().stream().<Item>map(DeferredHolder::get).toList();
        int bricks = tab.indexOf(ModItems.HUECO_QUARTZ_BRICKS.get());
        helper.assertTrue(tab.subList(bricks + 1, bricks + 4).equals(List.of(ModItems.LAS_NOCHES_STONE.get(),
                        ModItems.LAS_NOCHES_TILES.get(), ModItems.LAS_NOCHES_TRIM.get())),
                "the creative tab does not follow Hueco quartz bricks with Las Noches' stone, tiles and trim");
        helper.succeed();
    }

    // ---- The data ----

    /**
     * Test F. Las Noches' data: its tag holds the one biome it starts in; it is its own type, laid with the surface
     * structures, the land unbent and no spawns of its own; its set rings it once at the centre's chunk for any seed, in
     * Hueco Mundo and in no other world; and nothing else may stand in Hueco Mundo.
     */
    @GameTest(template = "empty")
    public static void lasNochesDataLoads(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess access = server.registryAccess();
        ServerLevel hueco = hueco(helper);
        List<String> tagged = access.registryOrThrow(Registries.BIOME).getTag(STARTS_IN)
                .map(named -> named.stream().map(Holder::getRegisteredName).toList()).orElse(List.of());
        helper.assertTrue(tagged.equals(List.of("bleach:las_noches")), "Las Noches may start in " + tagged);
        Structure structure = lasNoches(helper, hueco);
        helper.assertTrue(structure instanceof LasNochesStructure && structure.type() == ModWorldgen.LAS_NOCHES.get()
                        && structure.biomes().unwrapKey().equals(Optional.of(STARTS_IN))
                        && structure.step() == GenerationStep.Decoration.SURFACE_STRUCTURES
                        && structure.terrainAdaptation() == TerrainAdjustment.NONE && structure.spawnOverrides().isEmpty(),
                "Las Noches is not its own structure on its tag, laid with the surface structures, unbending and spawnless");
        helper.assertTrue(BuiltInRegistries.STRUCTURE_PIECE.getKey(ModWorldgen.LAS_NOCHES_PIECE.get())
                        .equals(LasNoches.STRUCTURE.location()), "Las Noches' piece is not registered as bleach:las_noches");
        Holder<StructureSet> set = access.registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(SET);
        helper.assertTrue(set.value().structures().size() == 1
                        && set.value().structures().get(0).structure().is(LasNoches.STRUCTURE)
                        && set.value().placement() instanceof ConcentricRingsStructurePlacement placed
                        && placed.distance() == 0 && placed.count() == 1 && placed.preferredBiomes().size() == 0,
                "Las Noches' set is not one ring of count 1 and distance 0 that prefers no biome");
        ConcentricRingsStructurePlacement rings = (ConcentricRingsStructurePlacement) set.value().placement();
        // Hueco Mundo's own generator holds the set and rings it at the centre's chunk, and so would one of another seed.
        BiomeSource source = hueco.getChunkSource().getGenerator().getBiomeSource();
        List<ChunkGeneratorStructureState> states = List.of(hueco.getChunkSource().getGeneratorState(),
                ChunkGeneratorStructureState.createForNormal(hueco.getChunkSource().randomState(), 7_391L, source,
                        access.lookupOrThrow(Registries.STRUCTURE_SET)));
        for (ChunkGeneratorStructureState state : states) {
            List<ChunkPos> ring = state.getRingPositionsFor(rings);
            helper.assertTrue(state.possibleStructureSets().stream().anyMatch(possible -> possible.is(SET))
                    && List.of(LasNoches.HOME).equals(ring), "Las Noches is rung at " + ring);
        }
        for (ResourceKey<Level> world : List.of(Level.OVERWORLD, BleachWorlds.SOUL_SOCIETY)) {
            ServerLevel other = server.getLevel(world);
            helper.assertFalse(other == null || other.getChunkSource().getGeneratorState().possibleStructureSets().stream()
                    .anyMatch(possible -> possible.is(SET)), "Las Noches may start in " + world.location());
        }
        // Nothing but Las Noches may stand in Hueco Mundo, and only its own set places it.
        Set<Holder<Biome>> ours = source.possibleBiomes();
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        List<String> standing = structures.stream().filter(each -> each.biomes().stream().anyMatch(ours::contains))
                .map(each -> String.valueOf(structures.getKey(each))).sorted().toList();
        List<String> sets = access.registryOrThrow(Registries.STRUCTURE_SET).entrySet().stream()
                .filter(each -> each.getValue().structures().stream()
                        .anyMatch(entry -> standing.contains(entry.structure().getRegisteredName())))
                .map(each -> each.getKey().location().toString()).sorted().toList();
        List<String> only = List.of(LasNoches.STRUCTURE.location().toString());
        helper.assertTrue(standing.equals(only) && sets.equals(only),
                "the structures that may stand in Hueco Mundo are " + standing + ", placed by " + sets);
        helper.succeed();
    }
}
