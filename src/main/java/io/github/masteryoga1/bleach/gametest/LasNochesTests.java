package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import io.github.masteryoga1.bleach.world.LasNoches;
import io.github.masteryoga1.bleach.world.gen.LasNochesPiece;
import io.github.masteryoga1.bleach.world.gen.LasNochesStructure;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for Las Noches: the plan's shell, the structure that sets it out about Hueco Mundo's centre, the stamp that lays
 * it a chunk at a time, its blocks and its data. The test server's Hueco Mundo is made with structures off, as an old
 * world's chunks were, so worldgen lays none of it there; tests B, C and E lay a chunk or two of it, by hand or by the
 * stamp, and take them away again as they end, so they run in a batch of their own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class LasNochesTests {
    private static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("las_noches"));
    private static final TagKey<Biome> STARTS_IN = TagKey.create(Registries.BIOME, BleachMod.id("has_structure/las_noches"));
    /** Test B's chunk, x and z -32 to -17: the shoulder tower about (-28, -28), the dome under it and the air it shuts in. */
    private static final ChunkPos SHOULDER = new ChunkPos(-2, -2);
    /** Test C's chunks: the east gateway's southern half and the wall south of it, and the dome's foot west of them. */
    private static final ChunkPos EAST = new ChunkPos(7, 0), DOME_FOOT = new ChunkPos(3, 0);
    /** Test E's chunk: the north gateway's eastern half and the wall east of it, x 0 to 15 and z -128 to -113. */
    private static final ChunkPos NORTH = new ChunkPos(0, -8);
    /** The three blocks, with the colour each shows on a map. */
    private static final Map<String, MapColor> BLOCKS = Map.of("las_noches_stone", MapColor.QUARTZ,
            "las_noches_tiles", MapColor.QUARTZ, "las_noches_trim", MapColor.COLOR_BLACK);
    private static final double SAME = 1.0E-6;

    private LasNochesTests() {
    }

    // ---- Shared helpers ----

    /** The test server's real Hueco Mundo. */
    static ServerLevel hueco(GameTestHelper helper) {
        ServerLevel hueco = helper.getLevel().getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        helper.assertTrue(hueco != null, "the test server has no Hueco Mundo");
        return hueco;
    }

    /** Las Noches' structure, as the registries hold it. */
    static Structure lasNoches(GameTestHelper helper, ServerLevel hueco) {
        Structure structure = hueco.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LasNoches.STRUCTURE);
        helper.assertTrue(structure != null, "the structure " + LasNoches.STRUCTURE.location() + " did not load");
        return structure;
    }

    /** The structure set out afresh from this chunk, as worldgen or a command sets it out, where these biomes allow. */
    static StructureStart generate(ServerLevel hueco, Structure structure, ChunkPos chunk,
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

    /** Whether this chunk holds the reference to Las Noches that worldgen and the stamp leave once it is laid. */
    private static boolean referenced(ServerLevel hueco, Structure structure, ChunkPos pos) {
        return hueco.getChunk(pos.x, pos.z).getReferencesForStructure(structure).contains(LasNoches.HOME.toLong());
    }

    /** The stamp's site for this chunk: its sites run across x and then z from the box's first chunk, one chunk each. */
    private static int site(GameTestHelper helper, ChunkPos pos) {
        BoundingBox box = HuecoMundoPlan.SHELL_BOX;
        int site = pos.x - (box.minX() >> 4) + (pos.z - (box.minZ() >> 4)) * ((box.maxX() >> 4) - (box.minX() >> 4) + 1);
        helper.assertTrue(LasNoches.STAMP.covered(site).equals(List.of(pos))
                        && LasNoches.STAMP.startChunk(site).equals(LasNoches.HOME),
                "the stamp's site " + site + " covers " + LasNoches.STAMP.covered(site) + ", not " + pos);
        return site;
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

    /**
     * Asserts that this body set down at this spot stands where expected, on white sand, with no cell of the shell in its
     * box and nothing in its way.
     */
    static void onSandOutside(GameTestHelper helper, ServerLevel hueco, Entity body, @Nullable Vec3 at,
                              Vec3 expected, String what) {
        helper.assertTrue(at != null && at.distanceTo(expected) < SAME, what + " is at " + at + ", not " + expected);
        AABB box = body.getDimensions(Pose.STANDING).makeBoundingBox(at);
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - SAME, box.maxY - SAME, box.maxZ - SAME))) {
            helper.assertTrue(HuecoMundoPlan.shellAt(cell.getX(), cell.getY(), cell.getZ()) == null,
                    what + " stands in the shell at " + cell.toShortString());
        }
        helper.assertTrue(hueco.noCollision(body, box)
                && hueco.getBlockState(BlockPos.containing(at).below()).is(ModBlocks.WHITE_SAND.get()),
                what + " is not free on white sand");
    }

    /** Asserts that a Garganta from this x and z sets this body down here, outside the wall. */
    private static void landsOutside(GameTestHelper helper, ServerLevel hueco, Entity body, double x, double z,
                                     Vec3 expected) {
        String what = "a body " + body.getBbWidth() + " wide from (" + x + ", " + z + ")";
        onSandOutside(helper, hueco, body, TravelEvents.huecoMundoLanding(body, hueco, x, z), expected, what);
    }

    /** A pig with no will of its own, made at this spot of Hueco Mundo and not yet set down there. */
    static Pig pig(GameTestHelper helper, ServerLevel hueco, double x, double y, double z) {
        Pig pig = EntityType.PIG.create(hueco);
        helper.assertTrue(pig != null, "no pig could be made");
        pig.setNoAi(true);
        pig.moveTo(x, y, z, 0.0F, 0.0F);
        return pig;
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
     * go, discards what the test set down, sets every cell of the shell in that chunk back to air, the open air of the
     * level ground there, and takes its reference to Las Noches away.
     */
    private record Held(ServerLevel hueco, ChunkPos chunk, List<Entity> placed) implements GameTestListener {
        /**
         * Holds this chunk and those beside it till the test ends, made now, so a cell laid at its edge reads them. Each
         * is made under this ticket alone, with none of the one-tick tickets a load by getChunk leaves to outlast it.
         */
        static Held hold(GameTestHelper helper, ServerLevel hueco, ChunkPos chunk) {
            Held held = new Held(hueco, chunk, new ArrayList<>());
            helper.testInfo.addListener(held);
            hueco.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 1, chunk);
            // The ticket's levels are set now rather than at the level's next tick, so the chunks may be made under it.
            hueco.getChunkSource().pollTask();
            ChunkPos.rangeClosed(chunk, 1)
                    .forEach(each -> hueco.getChunkSource().getChunkFuture(each.x, each.z, ChunkStatus.FULL, false));
            return held;
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

        /**
         * Lets the chunks go, discards what the test set down, clears the shell's cells and takes the reference away, then
         * has the ticket's removal run at once, so no check of the stamp's still queued finds all nine loaded to lay it.
         */
        private void end() {
            hueco.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 1, chunk);
            TravelTests.discard(placed.toArray(new Entity[0]));
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
            LevelChunk laid = hueco.getChunk(chunk.x, chunk.z);
            Map<Structure, LongSet> others = new HashMap<>(laid.getAllReferences());
            others.remove(hueco.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LasNoches.STRUCTURE));
            laid.setAllReferences(others);
            // Else run at the level's next tick, after the checks queued this tick: a chunk the test has not read in its
            // last two ticks, as no step reads a corner, is then no longer loaded when they run.
            hueco.getChunkSource().pollTask();
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

    // ---- The stamp ----

    /**
     * Test C. The east gateway's chunk and the dome's foot of the real Hueco Mundo, made with structures off as an old
     * world's were, are laid by the stamp once those beside them have loaded, cell for cell as planned, and the chunks
     * beside them, whose own neighbours are not all loaded, are not; a pig where the wall goes, one the dome shuts in and
     * one that joins in the laid wall are set down on the sand where a Garganta from there lands. The chunk keeps its
     * reference and is not laid again, a stone gone or not.
     */
    @GameTest(template = "empty", batch = "las_noches", timeoutTicks = 900)
    public static void theStampLaysAChunkOnce(GameTestHelper helper) {
        ServerLevel hueco = hueco(helper);
        Structure structure = lasNoches(helper, hueco);
        int site = site(helper, EAST);
        site(helper, DOME_FOOT);
        Held east = Held.hold(helper, hueco, EAST), foot = Held.hold(helper, hueco, DOME_FOOT);
        // Where the wall goes, r 121.95 and 10.5 off the gateway's middle, on the sand: as it joins, nothing buries it yet.
        Pig walled = east.add(pig(helper, hueco, 121.5, HuecoMundoPlan.LEVEL_Y, 10.5));
        // Under the dome, 53.4 from its centre: it joins over the shell's box and is moved in, so only the lay frees it.
        Pig shut = foot.add(pig(helper, hueco, 50.5, HuecoMundoPlan.SHELL_BOX.maxY() + 2.0, 5.5));
        shut.moveTo(50.5, HuecoMundoPlan.LEVEL_Y, 5.5, 0.0F, 0.0F);
        Pig joining = pig(helper, hueco, 121.5, HuecoMundoPlan.LEVEL_Y, 8.5);
        helper.startSequence()
                // Patiently, as the stamp waits on chunks asked for by future, which load off the server's thread.
                .thenWaitUntil(() -> GateTests.patiently(helper, referenced(hueco, structure, EAST)
                        && referenced(hueco, structure, DOME_FOOT), "the stamp has not laid " + EAST + " and " + DOME_FOOT))
                .thenExecute(() -> {
                    Map<HuecoMundoPlan.Shell, Integer> gateway = assertLaidAsPlanned(helper, hueco, EAST),
                            dome = assertLaidAsPlanned(helper, hueco, DOME_FOOT);
                    helper.assertTrue(gateway.equals(Map.of(HuecoMundoPlan.Shell.STONE, 664, HuecoMundoPlan.Shell.TILES, 128,
                                    HuecoMundoPlan.Shell.TRIM, 128)) && dome.equals(Map.of(HuecoMundoPlan.Shell.TILES, 878)),
                            "the east gateway's chunk holds " + gateway + " and the dome's foot " + dome);
                    // The wall's stone would stand here in the chunks north and south of it, were they laid.
                    for (BlockPos beside : List.of(new BlockPos(121, 100, 20), new BlockPos(121, 100, -8))) {
                        helper.assertTrue(hueco.getBlockState(beside).isAir()
                                        && !referenced(hueco, structure, new ChunkPos(beside)),
                                "the chunk beside it at " + beside.toShortString() + " was laid too");
                    }
                    // Each out on its bearing to r 136 and grounded, as a Garganta from its spot sets a soul down.
                    onSandOutside(helper, hueco, walled, walled.position(), new Vec3(135.5, HuecoMundoPlan.LEVEL_Y, 11.5),
                            "the pig the wall was laid over");
                    onSandOutside(helper, hueco, shut, shut.position(), new Vec3(135.5, HuecoMundoPlan.LEVEL_Y, 14.5),
                            "the pig the dome shut in");
                    BlockPos wall = new BlockPos(121, 100, 8);
                    helper.assertTrue(hueco.getBlockState(wall).is(ModBlocks.LAS_NOCHES_STONE.get()),
                            "the wall at " + wall.toShortString() + " is " + hueco.getBlockState(wall));
                    hueco.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    helper.assertFalse(LasNoches.STAMP.ensureBuilt(hueco, site), "the stamp laid the chunk again");
                    helper.assertTrue(hueco.getBlockState(wall).isAir() && referenced(hueco, structure, EAST),
                            "the stamp set back a stone of a chunk it had laid, or took its reference");
                    // In the wall's stone, as a soul saved there before it stood joins with its chunk.
                    east.add(joining);
                })
                .thenExecuteAfter(5, () -> onSandOutside(helper, hueco, joining, joining.position(),
                        new Vec3(135.5, HuecoMundoPlan.LEVEL_Y, 9.5), "the pig that joined in the wall"))
                .thenSucceed();
    }

    /**
     * Test E. Once the stamp has laid the north gateway's chunk, a Garganta from inside the wall, from the gateway and
     * from the dome's foot sets a person and a Gillian's body down on the sand outside, as it sets down a Menos or an
     * Arrancar waking there, and Urahara's far end steps further out from the first landing, 4 blocks at a time.
     */
    @GameTest(template = "empty", batch = "las_noches", timeoutTicks = 900)
    public static void arrivalsLandPastTheWall(GameTestHelper helper) {
        ServerLevel hueco = hueco(helper);
        Structure structure = lasNoches(helper, hueco);
        site(helper, NORTH);
        Held.hold(helper, hueco, NORTH);
        // Mock players, never added to the world: a person, and one at scale 6, a Gillian's 3.6 by 10.8.
        Player soul = HuecoMundoTests.mockIn(hueco, new Vec3(10.5, HuecoMundoPlan.LEVEL_Y, -121.5), GargantaTests.arrancar());
        Player giant = HuecoMundoTests.mockIn(hueco, new Vec3(10.5, HuecoMundoPlan.LEVEL_Y, -121.5),
                GargantaTests.arrancar());
        giant.getAttribute(Attributes.SCALE).setBaseValue(6.0);
        giant.refreshDimensions();
        helper.startSequence()
                // Patiently, as the stamp waits on a chunk asked for by future, which loads off the server's thread.
                .thenWaitUntil(() -> GateTests.patiently(helper, referenced(hueco, structure, NORTH),
                        "the north gateway's chunk " + NORTH + " is not laid"))
                .thenExecute(() -> {
                    // The wall stands where the first landing starts, and the gateway is open under its band.
                    helper.assertTrue(hueco.getBlockState(new BlockPos(10, 100, -122)).is(ModBlocks.LAS_NOCHES_STONE.get())
                                    && hueco.getBlockState(new BlockPos(2, 100, -122)).isAir()
                                    && hueco.getBlockState(new BlockPos(2, 111, -122)).is(ModBlocks.LAS_NOCHES_TRIM.get()),
                            "the north wall and its gateway do not stand as the plan has them");
                    Vec3 first = new Vec3(11.5, HuecoMundoPlan.LEVEL_Y, -135.5);
                    Vec3 gateway = new Vec3(2.5, HuecoMundoPlan.LEVEL_Y, -135.5);
                    landsOutside(helper, hueco, soul, 10.5, -121.5, first);
                    landsOutside(helper, hueco, giant, 10.5, -121.5, first);
                    landsOutside(helper, hueco, soul, 2.5, -121.5, gateway);
                    landsOutside(helper, hueco, giant, 2.5, -121.5, gateway);
                    landsOutside(helper, hueco, soul, 2.5, -60.5, new Vec3(5.5, HuecoMundoPlan.LEVEL_Y, -135.5));
                    // As UraharaGates.farEnd steps out: r 140, 144, 148 and 152.
                    double out = Math.hypot(first.x, first.z);
                    List<Vec3> far = List.of(new Vec3(11.5, HuecoMundoPlan.LEVEL_Y, -139.5),
                            new Vec3(12.5, HuecoMundoPlan.LEVEL_Y, -143.5), new Vec3(12.5, HuecoMundoPlan.LEVEL_Y, -147.5),
                            new Vec3(12.5, HuecoMundoPlan.LEVEL_Y, -151.5));
                    for (int step = 1; step <= far.size(); step++) {
                        double further = 1.0 + 4.0 * step / out;
                        onSandOutside(helper, hueco, soul, TravelEvents.groundUnder(soul, hueco, Mth.floor(first.x * further),
                                Mth.floor(first.z * further)), far.get(step - 1), "his far end's step " + step);
                    }
                })
                .thenSucceed();
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
     * Hueco Mundo and in no other world; and nothing else but its halls may stand in Hueco Mundo.
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
        // Nothing but Las Noches and its halls may stand in Hueco Mundo, and only their own sets place them.
        Set<Holder<Biome>> ours = source.possibleBiomes();
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        List<String> standing = structures.stream().filter(each -> each.biomes().stream().anyMatch(ours::contains))
                .map(each -> String.valueOf(structures.getKey(each))).sorted().toList();
        List<String> sets = access.registryOrThrow(Registries.STRUCTURE_SET).entrySet().stream()
                .filter(each -> each.getValue().structures().stream()
                        .anyMatch(entry -> standing.contains(entry.structure().getRegisteredName())))
                .map(each -> each.getKey().location().toString()).sorted().toList();
        List<String> only = List.of(LasNoches.STRUCTURE.location().toString(), LasNoches.HALLS.location().toString());
        helper.assertTrue(standing.equals(only) && sets.equals(only),
                "the structures that may stand in Hueco Mundo are " + standing + ", placed by " + sets);
        helper.succeed();
    }
}
