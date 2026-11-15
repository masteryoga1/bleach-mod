package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.world.GateHouses;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import io.github.masteryoga1.bleach.world.gen.SitePiece;
import io.github.masteryoga1.bleach.world.gen.SitePlacement;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Seireiheki's gate houses in the real Soul Society, which the test server, making no structures, has only
 * from the stamp. Tests A and B read all four houses; C to E, in a batch of their own, each work on a house no other test
 * then holds, D and E taking theirs down. Every test lets go of its chunks and what it set down however it ends, and a
 * house taken down is built again.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GateHouseTests {
    private static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("seireitei_gate"));
    /** The houses by the plan's order, the Rukongai's regions'. */
    private static final int EAST = 0, SOUTH = 1, WEST = 2, NORTH = 3;

    private GateHouseTests() {
    }

    // ---- Helpers ----

    /** This place of a house's template, turned and set at its corner on the lawn. */
    private static BlockPos at(int house, int x, int y, int z) {
        SeireiteiPlan.Site site = GateHouses.STAMP.sites().get(house);
        BlockPos corner = new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z());
        return corner.offset(new BlockPos(x, y, z).rotate(site.rotation()));
    }

    /** Whether the template leaves this place open: the doorway, the slot over it in the gate's row, the wicket's way. */
    private static boolean open(int x, int y, int z) {
        int w = SeireiteiPlan.GATE_HALF_WIDTH, door = SeireiteiPlan.GATE_DOOR_HALF_WIDTH;
        int head = SeireiteiPlan.GATE_DOOR_TOP_Y - SeireiteiPlan.PLATEAU_Y;
        boolean doorway = x >= w - door && x < w + door;
        return doorway && (y < head || z == SeireiteiPlan.GATE_ROW && y < head + SeireiteiPlan.GATE_LIFT)
                || x == w + door + 1 && y < SeireiteiPlan.GATE_WICKET_ROWS;
    }

    /** The gate houses' structure, as Soul Society's registries hold it. */
    private static Structure gate(GameTestHelper helper, ServerLevel soul) {
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(GateHouses.STRUCTURE);
        helper.assertTrue(structure != null, "the structure " + GateHouses.STRUCTURE.location() + " did not load");
        return structure;
    }

    /** Asserts that this house is placed: its start valid and both of its chunks referring to it. */
    private static void assertPlaced(GameTestHelper helper, ServerLevel soul, int house, String when) {
        Structure structure = gate(helper, soul);
        ChunkPos home = GateHouses.STAMP.startChunk(house);
        StructureStart start = soul.getChunk(home.x, home.z).getStartForStructure(structure);
        helper.assertTrue(start != null && start.isValid(), "house " + house + " has no valid start " + when);
        for (ChunkPos pos : GateHouses.STAMP.covered(house)) {
            helper.assertTrue(soul.getChunk(pos.x, pos.z).getReferencesForStructure(structure).contains(home.toLong()),
                    "chunk " + pos + " holds no reference to house " + house + " " + when);
        }
    }

    /**
     * Asserts that this house stands as the plan lays it, every block of its box: open where the template leaves it
     * open, else neither air nor Sekkiseki; the wall's Sekkiseki beside it across the gap; and open air over it all.
     * The generator's own blocks stand at its own places, turned with the house: plinth, pier, roof, stairs, posts, wicket.
     */
    private static void assertHouse(GameTestHelper helper, ServerLevel soul, int house, String when) {
        // As gate_house() in make_structures.py lays them.
        Rotation turn = GateHouses.STAMP.sites().get(house).rotation();
        laid(helper, soul, house, new BlockPos(0, 0, 3), state -> state.is(ModBlocks.BLACK_PLASTER.get()), when);
        laid(helper, soul, house, new BlockPos(0, 5, 3), state -> state.is(ModBlocks.WHITE_PLASTER.get()), when);
        laid(helper, soul, house, new BlockPos(8, 39, 3), state -> state.is(ModBlocks.ROOF_TILES.get()), when);
        laid(helper, soul, house, new BlockPos(8, 39, 0), stair(turn.rotate(Direction.SOUTH)), when);
        laid(helper, soul, house, new BlockPos(8, 39, 7), stair(turn.rotate(Direction.NORTH)), when);
        for (BlockPos post : List.of(GateHouses.LEFT_POST, GateHouses.RIGHT_POST)) {
            laid(helper, soul, house, post, state -> state.is(Blocks.STRIPPED_DARK_OAK_LOG), when);
        }
        // The wicket's way at x 14: two rows open through the pier, from face to face, under a block.
        for (int z = 0; z < GateHouses.SIZE.getZ(); z++) {
            laid(helper, soul, house, new BlockPos(14, 0, z), BlockState::isAir, when);
            laid(helper, soul, house, new BlockPos(14, 1, z), BlockState::isAir, when);
            laid(helper, soul, house, new BlockPos(14, 2, z), state -> !state.isAir(), when);
        }
        Block sekkiseki = ModBlocks.SEKKISEKI.get();
        int width = GateHouses.SIZE.getX(), height = GateHouses.SIZE.getY();
        for (int x = -1; x <= width; x++) {
            for (int y = 0; y <= height; y++) {
                for (int z = 0; z < GateHouses.SIZE.getZ(); z++) {
                    BlockState state = soul.getBlockState(at(house, x, y, z));
                    boolean right = y == height ? state.isAir() : x < 0 || x == width ? state.is(sekkiseki)
                            : open(x, y, z) ? state.isAir() : !state.isAir() && !state.is(sekkiseki);
                    helper.assertTrue(right, "house " + house + "'s (" + x + ", " + y + ", " + z + ") at "
                            + at(house, x, y, z).toShortString() + " is " + state + " " + when);
                }
            }
        }
    }

    /** Asserts that this place of a house's template, turned and set, holds the block expected. */
    private static void laid(GameTestHelper helper, ServerLevel soul, int house, BlockPos place, Predicate<BlockState> right,
                             String when) {
        BlockState state = soul.getBlockState(at(house, place.getX(), place.getY(), place.getZ()));
        helper.assertTrue(right.test(state), "house " + house + "'s " + place.toShortString() + " is " + state + " " + when);
    }

    /** Whether a block is a roof stair facing this way. */
    private static Predicate<BlockState> stair(Direction facing) {
        return state -> state.is(ModBlocks.ROOF_TILE_STAIRS.get()) && state.getValue(StairBlock.FACING) == facing;
    }

    /** Takes away what says this house was placed, its start and both chunks' references, and leaves its blocks. */
    private static void forget(GameTestHelper helper, ServerLevel soul, int house) {
        Structure structure = gate(helper, soul);
        for (ChunkPos pos : GateHouses.STAMP.covered(house)) {
            LevelChunk chunk = soul.getChunk(pos.x, pos.z);
            Map<Structure, LongSet> others = new HashMap<>(chunk.getAllReferences());
            others.remove(structure);
            chunk.setAllReferences(others);
        }
        ChunkPos home = GateHouses.STAMP.startChunk(house);
        soul.getChunk(home.x, home.z).setStartForStructure(structure, StructureStart.INVALID_START);
    }

    /** Sets every block of this house's box to air, as the gap stood before there were houses. */
    private static void clear(ServerLevel soul, int house) {
        for (int x = 0; x < GateHouses.SIZE.getX(); x++) {
            for (int y = 0; y < GateHouses.SIZE.getY(); y++) {
                for (int z = 0; z < GateHouses.SIZE.getZ(); z++) {
                    soul.setBlock(at(house, x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Builds this house afresh, whatever a test left of it: forgotten, its posts gone, and stamped again. */
    private static void restore(GameTestHelper helper, ServerLevel soul, int house) {
        forget(helper, soul, house);
        for (BlockPos post : List.of(GateHouses.LEFT_POST, GateHouses.RIGHT_POST)) {
            soul.setBlock(at(house, post.getX(), post.getY(), post.getZ()), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
        }
        GateHouses.STAMP.ensureBuilt(soul, house);
    }

    /** A cow that stays where it is put, not yet set down. */
    private static Cow cow(ServerLevel soul, Vec3 at) {
        Cow cow = EntityType.COW.create(soul);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return cow;
    }

    /** Whether this point lies in a house's doorway across the gap: nearer the gate's axis than the doorway's edge. */
    private static boolean inDoorway(int house, Vec3 point) {
        BoundingBox box = GateHouses.box(house);
        double across = box.getXSpan() > box.getZSpan() ? point.x : point.z;
        return box.isInside(BlockPos.containing(point)) && Math.abs(across) < SeireiteiPlan.GATE_DOOR_HALF_WIDTH;
    }

    /** Soul Society's chunks within 2 of this house's start, made now and held as the Shakonmaku's tests hold theirs. */
    private static ShakonmakuTests.Cleanup hold(GameTestHelper helper, int house) {
        ChunkPos home = GateHouses.STAMP.startChunk(house);
        return ShakonmakuTests.hold(helper, home.x, home.z);
    }

    // ---- The plan ----

    /**
     * Test A. The gate houses' set is placed by the plan, in Soul Society only, at the four sites' chunks and none about
     * them; each site's box is exactly its gap, clear of the wall and the membrane's gateway below the wall's top, faces
     * the Rukongai with its wicket on the right coming in, and is laid out there by the structure as the stamp reads it.
     */
    @GameTest(template = "empty", batch = "gate")
    public static void gateSitesFollowThePlan(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        Structure structure = gate(helper, soul);
        StructureSet set = soul.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(SET).value();
        helper.assertTrue(set.placement() instanceof SitePlacement plan && plan.group().equals("gate")
                        && set.structures().size() == 1 && set.structures().get(0).structure().is(GateHouses.STRUCTURE),
                "the gate houses' set is not the gate group's alone: " + set.placement());
        helper.assertTrue(soul.getChunkSource().getGeneratorState().possibleStructureSets().stream().anyMatch(s -> s.is(SET))
                        && helper.getLevel().getChunkSource().getGeneratorState().possibleStructureSets().stream()
                        .noneMatch(s -> s.is(SET)), "the gate houses' set is not Soul Society's alone");
        var template = soul.getStructureManager().get(BleachMod.id("gate/house"));
        helper.assertTrue(template.isPresent() && template.get().getSize().equals(GateHouses.SIZE),
                "the gate house's template is not " + GateHouses.SIZE.toShortString());
        List<SeireiteiPlan.Site> sites = SeireiteiPlan.sites("gate");
        helper.assertTrue(sites.equals(GateHouses.STAMP.sites()) && sites.size() == Rukongai.Region.values().length,
                "the plan has " + sites.size() + " gate sites, not the stamp's four");
        ChunkGenerator generator = soul.getChunkSource().getGenerator();
        for (Rukongai.Region region : Rukongai.Region.values()) {
            int house = region.ordinal();
            ChunkPos home = GateHouses.STAMP.startChunk(house);
            for (ChunkPos near : ChunkPos.rangeClosed(home, 1).toList()) {
                helper.assertTrue(set.placement().isStructureChunk(soul.getChunkSource().getGeneratorState(), near.x, near.z)
                        == near.equals(home), "chunk " + near + " is wrongly where house " + house + " starts or not");
            }
            BoundingBox box = GateHouses.box(house);
            helper.assertTrue(box.isInside(region.gate()), region + "'s gate " + region.gate().toShortString()
                    + " is not in house " + house + " " + box);
            // Exactly the gap: every column of the box is the gap's, and every column round it is not.
            for (int x = -1; x <= GateHouses.SIZE.getX(); x++) {
                for (int z = -1; z <= GateHouses.SIZE.getZ(); z++) {
                    BlockPos column = at(house, x, 0, z);
                    boolean inBox = x >= 0 && x < GateHouses.SIZE.getX() && z >= 0 && z < GateHouses.SIZE.getZ();
                    double r = SeireiteiPlan.radius(column.getX(), column.getZ());
                    boolean gap = r >= SeireiteiPlan.SEIREITEI_RADIUS && r < SeireiteiPlan.WALL_OUTER_RADIUS
                            && !SeireiteiPlan.inWall(column.getX(), column.getZ());
                    helper.assertTrue(inBox == gap && (!inBox || Shakonmaku.side(column.getX(), SeireiteiPlan.WALL_TOP_Y
                            - 1, column.getZ()) == Shakonmaku.Side.GATEWAY), "column " + column.toShortString()
                            + (inBox ? " is" : " is not") + " in house " + house);
                }
            }
            // Its front, z 7, is the wall's outer row; its wicket is on the right of a soul coming in.
            BlockPos front = at(house, 8, 0, GateHouses.SIZE.getZ() - 1), wicket = at(house, 14, 0, 0);
            int outX = Integer.signum(region.gate().getX()), outZ = Integer.signum(region.gate().getZ());
            double right = outZ * (wicket.getX() + 0.5) - outX * (wicket.getZ() + 0.5);
            helper.assertTrue(SeireiteiPlan.radius(front.getX(), front.getZ()) >= SeireiteiPlan.WALL_OUTER_RADIUS - 1
                    && right > 0, "house " + house + " faces " + front.toShortString() + " with its wicket at "
                    + wicket.toShortString());
            // As worldgen lays it, in its own biomes only, at its corner on the gap's lawn.
            StructureStart start = structure.generate(soul.registryAccess(), generator, generator.getBiomeSource(),
                    soul.getChunkSource().randomState(), soul.getStructureManager(), soul.getSeed(), home, 0, soul,
                    structure.biomes()::contains);
            helper.assertTrue(start.isValid() && start.getPieces().get(0) instanceof SitePiece piece
                            && piece.getRotation() == sites.get(house).rotation()
                            && piece.getBoundingBox().equals(box)
                            && piece.templatePosition().getY() == SeireiteiPlan.PLATEAU_Y,
                    "house " + house + " is laid out as " + start.getPieces() + ", not over " + box);
            List<ChunkPos> covered = ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                    new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList();
            helper.assertTrue(covered.equals(GateHouses.STAMP.covered(house)) && covered.size() == 2,
                    "house " + house + " covers " + covered + ", not the stamp's " + GateHouses.STAMP.covered(house));
        }
        helper.succeed();
    }

    // ---- The houses ----

    /**
     * Test B. All four houses stand once their chunks load, each as the plan lays it, turned to face the Rukongai: the
     * doorway, the slot and the wicket's way open, no Sekkiseki in them, and the wall's Sekkiseki on both sides.
     */
    @GameTest(template = "empty", batch = "gate", timeoutTicks = 200)
    public static void theGateHousesStandAsThePlanLaysThem(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        // One house a step, so that no tick of the test makes more than one house's chunks.
        var sequence = helper.startSequence();
        for (int each = EAST; each <= NORTH; each++) {
            int house = each;
            sequence = sequence.thenExecute(() -> hold(helper, house))
                    .thenWaitUntil(() -> assertPlaced(helper, soul, house, "from its chunks' loads alone"))
                    .thenExecute(() -> assertHouse(helper, soul, house, "once it stood"));
        }
        sequence.thenSucceed();
    }

    /**
     * Test C. A Shinigami walks out of Seireitei through the south house's doorway, step by step across the gap, and
     * back; a step from the Rukongai into the left pier is put back.
     */
    @GameTest(template = "empty", batch = "gate_house", timeoutTicks = 200)
    public static void aSoulWalksThroughADoorway(GameTestHelper helper) {
        ServerLevel soul = hold(helper, SOUTH).soul;
        Vec3[] way = {new Vec3(0.5, 72.0, 509.5), new Vec3(0.5, 72.0, 513.5), new Vec3(0.5, 72.0, 517.5),
                new Vec3(0.5, 72.0, 521.5)};
        Vec3 beside = new Vec3(-6.5, 72.0, 521.5), pier = new Vec3(-6.5, 72.0, 517.5);
        ServerPlayer player = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, way[0]);
        int[] order = {1, 2, 3, 2, 1, 0, 1, 2, 3};
        var sequence = helper.startSequence()
                .thenWaitUntil(() -> assertPlaced(helper, soul, SOUTH, "before the walk"))
                .thenIdle(5);
        for (int step : order) {
            sequence = sequence.thenExecute(() -> {
                ShakonmakuTests.move(player, way[step]);
                helper.assertTrue(player.position().distanceToSqr(way[step]) < 1.0E-6, "the soul stepping to "
                        + way[step] + " through the doorway is at " + player.position());
            }).thenIdle(1);
        }
        sequence.thenExecute(() -> ShakonmakuTests.move(player, beside))
                .thenIdle(1)
                .thenExecute(() -> {
                    ShakonmakuTests.move(player, pier);
                    helper.assertTrue(player.position().distanceToSqr(beside) < 1.0E-6, "the soul stepping into the left pier"
                            + " is at " + player.position() + ", not put back at " + beside);
                })
                .thenSucceed();
    }

    // ---- The stamp ----

    /**
     * Test D. The west house stands from its chunks' loads, and a whole house is left alone. One whose blocks stand with
     * nothing saying so, as a {@code /place} leaves it, regains its start and references and is not built again; with
     * one chunk's post gone too, only that chunk's part is built; and in an old world's open gap the whole house is.
     */
    @GameTest(template = "empty", batch = "gate_house", timeoutTicks = 200)
    public static void theStampBuildsAGateHouseInAnOldChunk(GameTestHelper helper) {
        ServerLevel soul = hold(helper, WEST).soul;
        // A stone in the doorway in each chunk, beside that chunk's post: only that chunk's part built afresh clears it.
        BlockPos left = at(WEST, 4, 0, 2), right = at(WEST, 11, 0, 2);
        BlockPos leftPost = at(WEST, GateHouses.LEFT_POST.getX(), 0, 0);
        helper.assertTrue(new ChunkPos(left).equals(new ChunkPos(leftPost))
                        && !new ChunkPos(right).equals(new ChunkPos(left)), "the stones at " + left.toShortString()
                + " and " + right.toShortString() + " are not one in each chunk");
        helper.startSequence()
                .thenWaitUntil(() -> assertPlaced(helper, soul, WEST, "from its chunks' loads alone"))
                .thenExecute(() -> {
                    try {
                        helper.assertFalse(GateHouses.STAMP.ensureBuilt(soul, WEST), "the stamp built over a whole house");
                        forget(helper, soul, WEST);
                        soul.setBlock(left, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        soul.setBlock(right, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        helper.assertFalse(GateHouses.STAMP.ensureBuilt(soul, WEST),
                                "the stamp built over a house that stood");
                        assertPlaced(helper, soul, WEST, "once the stamp found it standing");
                        helper.assertTrue(soul.getBlockState(left).is(Blocks.STONE)
                                        && soul.getBlockState(right).is(Blocks.STONE),
                                "the stamp built again over a house that stood");

                        forget(helper, soul, WEST);
                        soul.setBlock(leftPost, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        helper.assertTrue(GateHouses.STAMP.ensureBuilt(soul, WEST),
                                "the stamp built nothing of a half-built house");
                        helper.assertTrue(soul.getBlockState(left).isAir() && soul.getBlockState(right).is(Blocks.STONE),
                                "the stamp did not build the one chunk's part alone: " + soul.getBlockState(left) + " and "
                                        + soul.getBlockState(right));
                        soul.setBlock(right, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        assertHouse(helper, soul, WEST, "once half of it was built again");

                        forget(helper, soul, WEST);
                        clear(soul, WEST);
                        helper.assertTrue(GateHouses.STAMP.ensureBuilt(soul, WEST), "the stamp built nothing in an open gap");
                        assertPlaced(helper, soul, WEST, "once built in an open gap");
                        assertHouse(helper, soul, WEST, "once built in an open gap");
                        helper.assertFalse(GateHouses.STAMP.ensureBuilt(soul, WEST), "the stamp built the whole house again");
                    } finally {
                        restore(helper, soul, WEST);
                    }
                })
                .thenSucceed();
    }

    /**
     * Test E. In an old world's open north gap, souls where the house goes are moved out on their own half as it is built:
     * a player in a pier into the doorway, a cow high on the lawn's half onto the roof and one high on the Rukongai's
     * half into the doorway, and one in the doorway is let be. Then a cow joining in a pier, as one saved in the gap
     * before the house stood comes back, is moved into the doorway once the tick is done, and the one there still is not.
     */
    @GameTest(template = "empty", batch = "gate_house", timeoutTicks = 200)
    public static void aSoulInAGapIsMovedOutOnItsOwnHalf(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = hold(helper, NORTH);
        ServerLevel soul = held.soul;
        Vec3 inPier = Vec3.atBottomCenterOf(at(NORTH, 1, 0, 3)), high = Vec3.atBottomCenterOf(at(NORTH, 6, 33, 2));
        Vec3 highOut = Vec3.atBottomCenterOf(at(NORTH, 6, 33, 5)), inDoor = Vec3.atBottomCenterOf(at(NORTH, 8, 0, 6));
        Vec3 joins = Vec3.atBottomCenterOf(at(NORTH, 1, 10, 5));
        Cow[] cows = new Cow[2];
        helper.startSequence()
                .thenWaitUntil(() -> assertPlaced(helper, soul, NORTH, "from its chunks' loads alone"))
                .thenExecute(() -> {
                    try {
                        forget(helper, soul, NORTH);
                        clear(soul, NORTH);
                        ServerPlayer player = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, inPier);
                        Cow up = held.add(cow(soul, high)), upOut = held.add(cow(soul, highOut));
                        cows[0] = held.add(cow(soul, inDoor));
                        helper.assertTrue(GateHouses.STAMP.ensureBuilt(soul, NORTH), "the stamp built nothing in the gap");
                        assertHouse(helper, soul, NORTH, "built about the souls in its gap");
                        helper.assertTrue(soul.noCollision(player) && inDoorway(NORTH, player.position())
                                        && player.getY() == SeireiteiPlan.PLATEAU_Y,
                                "the player standing where the pier went is at " + player.position());
                        helper.assertTrue(soul.noCollision(up) && up.getY() == SeireiteiPlan.WALL_TOP_Y,
                                "the cow high on the lawn's half is at " + up.position() + ", not on the roof");
                        helper.assertTrue(soul.noCollision(upOut) && inDoorway(NORTH, upOut.position())
                                        && upOut.getZ() == highOut.z, "the cow high on the Rukongai's half is at "
                                        + upOut.position() + ", not in the doorway on its own half");
                        helper.assertTrue(cows[0].position().equals(inDoor), "the cow in the doorway was moved to "
                                + cows[0].position());
                    } finally {
                        restore(helper, soul, NORTH);
                    }
                    cows[1] = held.add(cow(soul, joins));
                    helper.assertFalse(soul.noCollision(cows[1]), "the cow set in the pier at " + joins + " is not in it");
                })
                // A failed step above leaves the cows unset and runs this at once: it must fail, not throw.
                .thenWaitUntil(() -> helper.assertTrue(cows[0] != null && cows[1] != null && soul.noCollision(cows[1])
                        && inDoorway(NORTH, cows[1].position()) && cows[0].position().equals(inDoor),
                        "the cow that joined in the pier is at " + (cows[1] == null ? "nowhere" : cows[1].position())
                                + " and the one in the doorway at " + (cows[0] == null ? "nowhere" : cows[0].position())))
                .thenSucceed();
    }
}
