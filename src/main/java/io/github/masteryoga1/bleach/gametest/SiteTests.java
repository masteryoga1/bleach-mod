package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.world.AcademySite;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.SiteStamp;
import io.github.masteryoga1.bleach.world.gen.SitePiece;
import io.github.masteryoga1.bleach.world.gen.SitePlacement;
import io.github.masteryoga1.bleach.world.gen.SiteStructure;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Soul Society's sites: the placement that names the chunks they start in, the structure that sets each site's
 * template at its corner and turn, and the Academy in Seireitei, which a world whose chunks there were made before it, or
 * made with structures off as the test server's are, has built once both of its chunks are loaded. Test C changes the
 * test world's Academy, so A to C run in a batch of their own; test D reads a stamp's numbers with no world, in any batch.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SiteTests {
    private static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("seireitei_academy"));
    /** The chunk the plan's Academy starts in, the one holding its corner. */
    private static final ChunkPos HOME = new ChunkPos(-12, 14);
    /** Where the Academy's template is set from: its corner, on the lawn. */
    private static final BlockPos CORNER = new BlockPos(-192, 72, 224);
    /** The Academy's box: its template, 15 by 9 by 17, turned clockwise about the corner. */
    private static final BoundingBox BOX = new BoundingBox(-208, 72, 224, -192, 80, 238);
    /** The hall's Tailoring Table and the gate's north post: the block the stamp knows each chunk's part by. */
    private static final BlockPos TABLE = new BlockPos(-205, 73, 227), POST = new BlockPos(-192, 73, 229);
    /** The hall's door, which faces the gate. */
    private static final BlockPos DOOR = new BlockPos(-199, 73, 231);
    /** Where the instructor stands in the hall, facing its door. */
    private static final Vec3 INSTRUCTOR = new Vec3(-203.5, 73.0, 231.5);

    private SiteTests() {
    }

    // ---- Shared helpers ----

    /** The test server's real Soul Society. */
    static ServerLevel soul(GameTestHelper helper) {
        ServerLevel soul = helper.getLevel().getServer().getLevel(BleachWorlds.SOUL_SOCIETY);
        helper.assertTrue(soul != null, "the test server has no Soul Society");
        return soul;
    }

    /**
     * Soul Society, with the 25 chunks about the Academy's hall loaded now and then held by a forced ticket that is never
     * let go, as {@code TravelTests.holdCourt} holds the court's: loaded first, so no ground about the Academy is made off
     * the thread while a test changes its chunks.
     */
    static ServerLevel holdAcademy(GameTestHelper helper) {
        ServerLevel soul = soul(helper);
        ChunkPos hall = AcademySite.COVERED.get(0);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                soul.getChunk(hall.x + x, hall.z + z);
            }
        }
        soul.getChunkSource().addRegionTicket(TicketType.FORCED, hall, 2, hall);
        return soul;
    }

    /** The Academy's structure, as Soul Society's registries hold it. */
    private static Structure academy(GameTestHelper helper, ServerLevel soul) {
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(AcademySite.STRUCTURE);
        helper.assertTrue(structure != null, "the structure " + AcademySite.STRUCTURE.location() + " did not load");
        return structure;
    }

    /** The structure laid out afresh in this chunk, as worldgen or a command lays it, where these biomes allow. */
    private static StructureStart generate(ServerLevel soul, Structure structure, ChunkPos chunk,
                                           Predicate<Holder<Biome>> biomes) {
        ChunkGenerator generator = soul.getChunkSource().getGenerator();
        return structure.generate(soul.registryAccess(), generator, generator.getBiomeSource(),
                soul.getChunkSource().randomState(), soul.getStructureManager(), soul.getSeed(), chunk, 0, soul, biomes);
    }

    /** The one piece of this start, which must be a site's. */
    private static SitePiece piece(GameTestHelper helper, StructureStart start, String what) {
        List<StructurePiece> pieces = start.getPieces();
        helper.assertTrue(start.isValid() && pieces.size() == 1 && pieces.get(0) instanceof SitePiece,
                what + " has " + pieces.size() + " pieces, not one site piece");
        return (SitePiece) pieces.get(0);
    }

    /** Every instructor standing in the Academy's box. */
    private static List<AcademyInstructor> instructors(ServerLevel soul) {
        return soul.getEntitiesOfClass(AcademyInstructor.class, AABB.of(BOX));
    }

    /** Asserts that the Academy stands whole: its start valid, both of its chunks referring to it, both marker blocks. */
    private static void assertStands(GameTestHelper helper, ServerLevel soul, Structure structure, String when) {
        StructureStart start = soul.getChunk(HOME.x, HOME.z).getStartForStructure(structure);
        helper.assertTrue(start != null && start.isValid(), "the Academy has no valid start in chunk " + HOME + " " + when);
        for (ChunkPos pos : AcademySite.COVERED) {
            helper.assertTrue(soul.getChunk(pos.x, pos.z).getReferencesForStructure(structure).contains(HOME.toLong()),
                    "chunk " + pos + " holds no reference to the Academy " + when);
        }
        helper.assertTrue(soul.getBlockState(TABLE).is(ModBlocks.TAILORING_TABLE.get())
                        && soul.getBlockState(POST).is(Blocks.STRIPPED_DARK_OAK_LOG),
                "the Academy's table at " + TABLE.toShortString() + " is " + soul.getBlockState(TABLE)
                        + " and its gate post at " + POST.toShortString() + " is " + soul.getBlockState(POST) + " " + when);
    }

    /** Takes away what says the Academy was placed, its start and both chunks' references, and leaves its blocks. */
    private static void forget(ServerLevel soul, Structure structure) {
        for (ChunkPos pos : AcademySite.COVERED) {
            LevelChunk chunk = soul.getChunk(pos.x, pos.z);
            Map<Structure, LongSet> others = new HashMap<>(chunk.getAllReferences());
            others.remove(structure);
            chunk.setAllReferences(others);
        }
        soul.getChunk(HOME.x, HOME.z).setStartForStructure(structure, StructureStart.INVALID_START);
    }

    /** Sets this block back, with no updates about it, unless it stands there already. */
    private static void putBack(ServerLevel soul, BlockPos pos, BlockState state) {
        if (!soul.getBlockState(pos).is(state.getBlock())) {
            soul.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- The plan ----

    /**
     * Test A. The Academy's set is placed by the plan: it may stand in Soul Society and not in the Overworld, it starts
     * in the one chunk the plan names and in none about it, and the plan's one Academy is the template turned clockwise.
     */
    @GameTest(template = "empty", batch = "site")
    public static void siteChunksFollowThePlan(GameTestHelper helper) {
        ServerLevel soul = soul(helper);
        StructureSet set = soul.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(SET).value();
        helper.assertTrue(set.placement() instanceof SitePlacement plan && plan.group().equals("academy"),
                "the Academy's set is not placed by the plan's academy group: " + set.placement());
        List<StructureSet.StructureSelectionEntry> entries = set.structures();
        helper.assertTrue(entries.size() == 1 && entries.get(0).structure().is(AcademySite.STRUCTURE),
                "the Academy's set holds " + entries.size() + " structures, not the Academy alone");
        ChunkGeneratorStructureState state = soul.getChunkSource().getGeneratorState();
        helper.assertTrue(state.possibleStructureSets().stream().anyMatch(possible -> possible.is(SET)),
                "Soul Society cannot hold the Academy's set");
        helper.assertFalse(helper.getLevel().getChunkSource().getGeneratorState().possibleStructureSets().stream()
                .anyMatch(possible -> possible.is(SET)), "the Overworld can hold the Academy's set");
        // The world's own state, as the placement's frequency check reads its seed.
        StructurePlacement placement = set.placement();
        for (int x = HOME.x - 2; x <= HOME.x + 2; x++) {
            for (int z = HOME.z - 2; z <= HOME.z + 2; z++) {
                boolean starts = placement.isStructureChunk(state, x, z);
                helper.assertTrue(starts == (x == HOME.x && z == HOME.z),
                        "chunk (" + x + ", " + z + ") is " + (starts ? "" : "not ") + "where the Academy starts");
            }
        }
        helper.assertFalse(placement.isStructureChunk(state, 0, 0), "the Academy starts in the centre column's chunk");

        List<SeireiteiPlan.Site> sites = SeireiteiPlan.sites("academy");
        helper.assertTrue(sites.size() == 1, "the plan's academy group has " + sites.size() + " sites, not one");
        SeireiteiPlan.Site site = sites.get(0);
        helper.assertTrue(site.rotation() == Rotation.CLOCKWISE_90 && site.template().equals(BleachMod.id("academy/academy"))
                && site.chunk().equals(HOME), "the plan's Academy is " + site);
        helper.assertTrue(site.equals(SeireiteiPlan.siteAt("academy", HOME.x, HOME.z)),
                "the plan names " + SeireiteiPlan.siteAt("academy", HOME.x, HOME.z) + " in chunk " + HOME);
        helper.assertTrue(SeireiteiPlan.siteAt("academy", HOME.x - 1, HOME.z) == null
                        && SeireiteiPlan.siteAt("court", HOME.x, HOME.z) == null,
                "the plan names a site in the hall's chunk, or a court in the Academy's");
        helper.succeed();
    }

    /**
     * Test B. The Academy's structure sets its one piece as the plan says: the template turned clockwise at its corner on
     * the lawn, over the two chunks the stamp reads, and, placed anywhere else, at that chunk's corner on the ground. The
     * piece is saved with its turn and read back the same.
     */
    @GameTest(template = "empty", batch = "site")
    public static void siteStructureFollowsThePlan(GameTestHelper helper) {
        ServerLevel soul = soul(helper);
        Structure structure = academy(helper, soul);
        helper.assertTrue(structure instanceof SiteStructure site && site.group().equals("academy")
                && structure.type() == ModWorldgen.SITE.get(), "the Academy is not the plan's academy site: " + structure);
        // Its own biomes only: the noise's biome at the corner must be one of them.
        StructureStart start = generate(soul, structure, HOME, structure.biomes()::contains);
        SitePiece laid = piece(helper, start, "the Academy laid out in its chunk");
        BoundingBox box = laid.getBoundingBox();
        helper.assertTrue(laid.getRotation() == Rotation.CLOCKWISE_90 && laid.templatePosition().equals(CORNER)
                        && box.equals(BOX), "the Academy is turned " + laid.getRotation() + " at "
                        + laid.templatePosition().toShortString() + " over " + box + ", not clockwise at "
                        + CORNER.toShortString() + " over " + BOX);
        Set<ChunkPos> covered = ChunkPos.rangeClosed(new ChunkPos(new BlockPos(box.minX(), 0, box.minZ())),
                new ChunkPos(new BlockPos(box.maxX(), 0, box.maxZ()))).collect(Collectors.toSet());
        helper.assertTrue(covered.equals(Set.copyOf(AcademySite.COVERED)) && AcademySite.startChunk().equals(HOME),
                "the Academy covers " + covered + " from " + HOME + ", not the stamp's " + AcademySite.COVERED + " from "
                        + AcademySite.startChunk());

        // Off the plan, as a command places it: the same template and turn, at that chunk's corner on the ground.
        ChunkPos away = new ChunkPos(10, -10);
        SitePiece elsewhere = piece(helper, generate(soul, structure, away, biome -> true),
                "the Academy placed off the plan");
        int ground = soul.getChunkSource().getGenerator().getFirstFreeHeight(away.getMinBlockX(), away.getMinBlockZ(),
                Heightmap.Types.WORLD_SURFACE_WG, soul, soul.getChunkSource().randomState());
        BlockPos corner = new BlockPos(160, ground, -160);
        helper.assertTrue(elsewhere.templatePosition().equals(corner) && elsewhere.getRotation() == Rotation.CLOCKWISE_90,
                "the Academy off the plan is turned " + elsewhere.getRotation() + " at "
                        + elsewhere.templatePosition().toShortString() + ", not clockwise at " + corner.toShortString());

        // Saved as a chunk saves it, and read back.
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(soul);
        CompoundTag tag = start.createTag(context, HOME);
        CompoundTag child = tag.getList("Children", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertTrue(tag.getString("id").equals(AcademySite.STRUCTURE.location().toString())
                        && child.getString("id").equals(BleachMod.id("site").toString())
                        && child.getString("Rot").equals(Rotation.CLOCKWISE_90.name()),
                "the Academy was saved as " + tag.getString("id") + " with a piece " + child.getString("id")
                        + " turned " + child.getString("Rot"));
        StructureStart loaded = StructureStart.loadStaticStart(context, tag, soul.getSeed());
        helper.assertTrue(loaded != null, "the saved Academy could not be read back");
        SitePiece read = piece(helper, loaded, "the Academy read back");
        helper.assertTrue(read.getRotation() == laid.getRotation() && read.templatePosition().equals(CORNER)
                        && read.getBoundingBox().equals(box), "the Academy read back is turned " + read.getRotation()
                        + " at " + read.templatePosition().toShortString() + " over " + read.getBoundingBox());
        helper.succeed();
    }

    // ---- The stamp ----

    /**
     * Test C. The test server makes no structures, so the Academy stands only because its two chunks loaded: nothing
     * here builds it. A whole Academy is left alone. One whose blocks stand with nothing saying so, as a {@code /place}
     * leaves it, regains its start and references and is not built again; one half built, with its start and references
     * gone and neither marker block placed, is built again, its hall with a second instructor, which goes at the end.
     * The tests in {@link AcademyTests} that send a soul there wait for it to stand first, so whichever batch runs
     * first, only the chunks' loads build it.
     */
    @GameTest(template = "empty", batch = "site", timeoutTicks = 100)
    public static void theSeireiteiAcademyStands(GameTestHelper helper) {
        ServerLevel soul = holdAcademy(helper);
        Structure structure = academy(helper, soul);
        // The checks the chunks' loads queued run once this tick is done.
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertStands(helper, soul, structure, "from its chunks' loads alone");
                    helper.assertFalse(instructors(soul).isEmpty(),
                            "the Academy built from its chunks' loads has no instructor");
                })
                .thenExecute(() -> {
                    List<AcademyInstructor> standing = instructors(soul);
                    AcademyInstructor first = standing.isEmpty() ? null : standing.get(0);
                    try {
                        helper.assertTrue(standing.size() == 1 && first.position().distanceTo(INSTRUCTOR) < 1.0,
                                standing.size() + " instructors stand in the Academy, the first at "
                                        + (first == null ? "none" : first.position()) + ", not one at " + INSTRUCTOR);
                        BlockState door = soul.getBlockState(DOOR);
                        helper.assertTrue(door.is(ModBlocks.SHOJI_DOOR.get())
                                        && door.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST,
                                "the hall's door at " + DOOR.toShortString() + " is " + door + ", not a door facing west");
                        helper.assertFalse(AcademySite.ensureBuilt(soul), "the stamp built over an Academy that was whole");

                        // As a /place leaves it: the blocks and the instructor stand, but no start or reference.
                        forget(soul, structure);
                        helper.assertFalse(AcademySite.ensureBuilt(soul), "the stamp built over the Academy's own blocks");
                        assertStands(helper, soul, structure, "once the stamp found its blocks standing");
                        helper.assertTrue(instructors(soul).size() == 1, "the stamp found the Academy standing and left "
                                + instructors(soul).size() + " instructors, not one");

                        // A half-built old world: no start or references, and neither chunk's part placed.
                        forget(soul, structure);
                        soul.setBlock(TABLE, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        soul.setBlock(POST, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        helper.assertTrue(AcademySite.ensureBuilt(soul), "the stamp built nothing of a half-built Academy");
                        assertStands(helper, soul, structure, "once the stamp built it again");
                        List<AcademyInstructor> both = instructors(soul);
                        helper.assertTrue(both.size() == 2 && both.contains(first), "the hall built again left "
                                + both.size() + " instructors, not the first and its own");
                        helper.assertFalse(AcademySite.ensureBuilt(soul), "the stamp built the whole Academy again");
                        helper.assertTrue(instructors(soul).size() == 2, "the stamp left " + instructors(soul).size()
                                + " instructors once the Academy was whole, not two");
                    } finally {
                        // However it ends, the Academy's marker blocks stand again and its first instructor is alone.
                        putBack(soul, TABLE, ModBlocks.TAILORING_TABLE.get().defaultBlockState());
                        putBack(soul, POST, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
                        instructors(soul).stream().filter(other -> other != first).forEach(Entity::discard);
                    }
                })
                .thenSucceed();
    }

    // ---- Any site's stamp ----

    /**
     * Test D. A stamp reads each of its sites by its own corner and turn, as worked by hand: two made-up sites of one
     * template, unturned and counter-clockwise, and the Academy clockwise. It needs no world, so it runs in any batch.
     */
    @GameTest(template = "empty")
    public static void aStampTurnsEachSite(GameTestHelper helper) {
        SiteStamp.Marker low = new SiteStamp.Marker(new BlockPos(3, 1, 6), () -> Blocks.STONE);
        SiteStamp.Marker high = new SiteStamp.Marker(new BlockPos(12, 2, 1), () -> Blocks.STONE);
        SiteStamp made = new SiteStamp(BleachWorlds.SOUL_SOCIETY, AcademySite.STRUCTURE, "A made-up site",
                List.of(new SeireiteiPlan.Site("made", 100, -40, BleachMod.id("made"), Rotation.NONE),
                        new SeireiteiPlan.Site("made", -50, 300, BleachMod.id("made"), Rotation.COUNTERCLOCKWISE_90)),
                new Vec3i(16, 40, 8), List.of(low, high), SiteStamp.Placed.NOTHING);
        // Unturned: x 100 to 115 and z -40 to -33, chunks 6 and 7 of row -3; a marker (x, y, z) lands at the corner plus it.
        stampReads(helper, made, 0, List.of(new ChunkPos(6, -3), new ChunkPos(7, -3)), new ChunkPos(6, -3),
                new BoundingBox(100, 72, -40, 115, 111, -33), new BlockPos(100, 72, -40), List.of(low, high),
                List.of(new BlockPos(103, 73, -34), new BlockPos(112, 74, -39)));
        // Counter-clockwise, (x, z) to (z, -x): x -50 to -43 and z 285 to 300, four chunks along x a row at a time.
        stampReads(helper, made, 1, List.of(new ChunkPos(-4, 17), new ChunkPos(-3, 17), new ChunkPos(-4, 18),
                        new ChunkPos(-3, 18)), new ChunkPos(-4, 18), new BoundingBox(-50, 72, 285, -43, 111, 300),
                new BlockPos(-50, 72, 300), List.of(low, high),
                List.of(new BlockPos(-44, 73, 297), new BlockPos(-49, 74, 288)));
        // The Academy clockwise, (x, z) to (-z, x), 15 by 9 by 17: the chunks, box and marker blocks the tests above read.
        SiteStamp.Marker table = new SiteStamp.Marker(new BlockPos(3, 1, 13), ModBlocks.TAILORING_TABLE);
        SiteStamp.Marker post = new SiteStamp.Marker(new BlockPos(5, 1, 0), () -> Blocks.STRIPPED_DARK_OAK_LOG);
        SiteStamp academy = new SiteStamp(BleachWorlds.SOUL_SOCIETY, AcademySite.STRUCTURE, "The Academy",
                SeireiteiPlan.sites("academy"), new Vec3i(15, 9, 17), List.of(table, post), SiteStamp.Placed.NOTHING);
        stampReads(helper, academy, 0, List.of(new ChunkPos(-13, 14), new ChunkPos(-12, 14)), HOME, BOX, CORNER,
                List.of(table, post), List.of(TABLE, POST));
        helper.assertTrue(AcademySite.COVERED.equals(academy.covered(0)),
                "the Academy's own stamp covers " + AcademySite.COVERED + ", not " + academy.covered(0));
        helper.succeed();
    }

    /** Asserts that this stamp reads this site as worked by hand: its chunks, its start's, its box and its markers. */
    private static void stampReads(GameTestHelper helper, SiteStamp stamp, int index, List<ChunkPos> covered, ChunkPos start,
                                   BoundingBox box, BlockPos corner, List<SiteStamp.Marker> markers, List<BlockPos> at) {
        BoundingBox read = stamp.box(index, corner.getY());
        helper.assertTrue(stamp.covered(index).equals(covered) && stamp.startChunk(index).equals(start) && read.equals(box),
                "site " + index + " covers " + stamp.covered(index) + " from " + stamp.startChunk(index) + " over " + read
                        + ", not " + covered + " from " + start + " over " + box);
        for (int i = 0; i < markers.size(); i++) {
            BlockPos landed = stamp.markerAt(index, markers.get(i), corner);
            helper.assertTrue(landed.equals(at.get(i)), "site " + index + "'s marker " + i + " lands at "
                    + landed.toShortString() + ", not " + at.get(i).toShortString());
        }
    }
}
