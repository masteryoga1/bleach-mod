package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.SeireiteiPlan.Street;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Seireitei's streets: the plan's avenues, ring roads, lane walls and court square, kept off every reserve
 * and leaving every way a soul walks open, and the same laid in the real Soul Society, where a soul still lands at y 72.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class StreetsTests {
    /** Seireitei's columns, x and z from -512 to 511: every one whose centre lies inside the wall's inner face. */
    private static final int FROM = -SeireiteiPlan.SEIREITEI_RADIUS, SIDE = 2 * SeireiteiPlan.SEIREITEI_RADIUS;
    /** The smoke's lawn spot (ClientSmokeTest.LAWN_TP, client code a test may not load): 16.5, 72, 472.5. */
    private static final Vec3 LAWN = new Vec3(16.5, SeireiteiPlan.PLATEAU_Y, SeireiteiPlan.SEIREITEI_RADIUS - 39.5);
    /** The chunk the real-world test holds, with the 24 about it: the south avenue, the outer ring and the lawn spot. */
    private static final int SOUTH_X = 0, SOUTH_Z = 27;
    /** Room for the float steps of a position. */
    private static final double SAME = 1.0E-6;

    private StreetsTests() {
    }

    // ---- Helpers ----

    /** The plan's streets over Seireitei's square, worked out once for the pure tests. */
    private static final class Plan {
        static final Street[] STREETS = new Street[SIDE * SIDE];

        static {
            for (int i = 0; i < STREETS.length; i++) {
                STREETS[i] = SeireiteiPlan.street(FROM + i / SIDE, FROM + i % SIDE);
            }
        }

        static Street at(int x, int z) {
            return STREETS[index(x, z)];
        }
    }

    /** This column's place in a square of Seireitei's columns, x major. */
    private static int index(int x, int z) {
        return (x - FROM) * SIDE + z - FROM;
    }

    /** Asserts what the plan lays on each of these columns, given as {x, z, Street ordinal}. */
    private static void lays(GameTestHelper helper, int[][] columns) {
        for (int[] c : columns) {
            Street want = Street.values()[c[2]];
            helper.assertTrue(SeireiteiPlan.street(c[0], c[1]) == want, "the plan lays "
                    + SeireiteiPlan.street(c[0], c[1]) + " at " + c[0] + ", " + c[1] + ", not " + want);
        }
    }

    /** Steps on the way from the start to each open column, walking square to the grid; -1 where none leads. */
    private static int[] walk(boolean[] open, int start) {
        int[] steps = new int[open.length], queue = new int[open.length];
        Arrays.fill(steps, -1);
        int head = 0, tail = 0;
        steps[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int at = queue[head++], i = at / SIDE, j = at % SIDE;
            for (int next : new int[] {i > 0 ? at - SIDE : -1, i < SIDE - 1 ? at + SIDE : -1, j > 0 ? at - 1 : -1,
                    j < SIDE - 1 ? at + 1 : -1}) {
                if (next >= 0 && open[next] && steps[next] < 0) {
                    steps[next] = steps[at] + 1;
                    queue[tail++] = next;
                }
            }
        }
        return steps;
    }

    /** Whether the world holds this column as the plan lays it, from the lawn's top block to the air over it. */
    private static boolean laidAsPlanned(ServerLevel soul, int x, int z) {
        BlockPos top = new BlockPos(x, SeireiteiPlan.PLATEAU_Y - 1, z);
        Street street = SeireiteiPlan.street(x, z);
        int height = street == Street.WALL ? SeireiteiPlan.LANE_WALL_HEIGHT : street == Street.LANTERN ? 2 : 0;
        Block under = switch (street) {
            case NONE, WALL -> Blocks.GRASS_BLOCK;
            case MARK -> Blocks.CHISELED_STONE_BRICKS;
            case PAVING, LANTERN -> Blocks.STONE_BRICKS;
        };
        // Grass under a wall may turn to dirt on a random tick, so only a wall's courses are read.
        boolean right = (street == Street.WALL || soul.getBlockState(top).is(under))
                && soul.getBlockState(top.above(height + 1)).isAir()
                && soul.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) == SeireiteiPlan.PLATEAU_Y + height;
        if (street == Street.LANTERN) {
            right &= soul.getBlockState(top.above()).is(Blocks.STONE_BRICK_WALL)
                    && soul.getBlockState(top.above(2)).is(Blocks.LANTERN);
        }
        for (int y = 1; street == Street.WALL && y <= height; y++) {
            Block course = y == 1 ? ModBlocks.BLACK_PLASTER.get() : y < height ? ModBlocks.WHITE_PLASTER.get()
                    : ModBlocks.ROOF_TILES.get();
            right &= soul.getBlockState(top.above(y)).is(course);
        }
        return right;
    }

    /** The first column of these chunks the world does not hold as the plan lays it, or null. */
    @Nullable
    private static String misLaid(ServerLevel soul, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        for (int x = minChunkX << 4; x < (maxChunkX + 1) << 4; x++) {
            for (int z = minChunkZ << 4; z < (maxChunkZ + 1) << 4; z++) {
                if (!laidAsPlanned(soul, x, z)) {
                    BlockPos top = new BlockPos(x, SeireiteiPlan.PLATEAU_Y - 1, z);
                    return x + ", " + z + " (" + SeireiteiPlan.street(x, z) + "): " + soul.getBlockState(top) + " under "
                            + soul.getBlockState(top.above());
                }
            }
        }
        return null;
    }

    // ---- The plan ----

    /**
     * Nothing is laid at the wall or beyond, no lane wall on a kept column, and paving on one only in a gate's forecourt,
     * the court, which is paved whole about its marked arrival with eight lanterns, or a compound's margin on its gate's
     * path; the hill, the Academy's grounds and every site keep their lawn.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theStreetsKeepOffTheReserves(GameTestHelper helper) {
        String beyond = null, walled = null, paved = null;
        int[] court = new int[Street.values().length];
        for (int x = FROM; x < FROM + SIDE; x++) {
            for (int z = FROM; z < FROM + SIDE; z++) {
                Street street = Plan.at(x, z);
                SeireiteiPlan.Reserve kept = SeireiteiPlan.reservedBy(x, z);
                String group = kept == null ? "" : kept.group();
                boolean onPath = group.equals("barracks") && SeireiteiPlan.onGatePath(x, z);
                if (group.equals("court")) {
                    court[street.ordinal()]++;
                }
                if (street != Street.NONE && SeireiteiPlan.radius(x, z) >= SeireiteiPlan.SEIREITEI_RADIUS
                        && beyond == null) {
                    beyond = x + ", " + z;
                } else if (street == Street.WALL && kept != null && walled == null) {
                    walled = x + ", " + z + " in " + kept;
                } else if (street != Street.NONE && kept != null && !group.equals("gate") && !group.equals("court")
                        && !onPath && paved == null) {
                    paved = x + ", " + z + " in " + kept;
                }
            }
        }
        helper.assertTrue(beyond == null, "the streets lay something at the wall or beyond, at " + beyond);
        helper.assertTrue(walled == null, "a lane wall stands on a kept column at " + walled);
        helper.assertTrue(paved == null, "the streets pave a column kept for another's works at " + paved);
        helper.assertTrue(court[Street.NONE.ordinal()] == 0 && court[Street.WALL.ordinal()] == 0
                        && court[Street.MARK.ordinal()] == 1 && court[Street.LANTERN.ordinal()] == 8,
                "the court's 49 by 49 columns are not a paved square with one mark and eight lanterns: "
                        + Arrays.toString(court));
        helper.succeed();
    }

    /**
     * A lane wall stands at a planned lane's edge and is open mid-side; none stands on an avenue or a ring road, which
     * are paved wherever no reserve keeps them; and each compound's gate has a path 4 wide from its step to a road. The
     * loop over every avenue and ring column is U10's squeeze guard: it fails if the Academy's grounds or a diagonal
     * compound's reserve ever reaches the outer ring.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aLaneWallStandsWhereThePlanLaysOne(GameTestHelper helper) {
        int none = Street.NONE.ordinal(), paving = Street.PAVING.ordinal(), wall = Street.WALL.ordinal();
        lays(helper, new int[][] {
                // A block's west edge beside the lane x 286 to 289, its opening at z 46 to 49, and a block's middle.
                {290, 40, wall}, {289, 40, none}, {290, 45, wall}, {290, 46, none}, {290, 49, none}, {290, 50, wall},
                {300, 40, none},
                // The east avenue, 10 wide, walled each side; the south one; the north one, ending before the First's.
                {300, -5, paving}, {300, 4, paving}, {300, 5, wall}, {300, -6, wall}, {-5, 300, paving}, {5, 300, wall},
                {0, -197, paving}, {0, -196, paving}, {5, -200, wall},
                // The inner ring from the hill's reserve, on a diagonal; the outer one past the Academy's grounds.
                {98, 98, none}, {99, 99, paving}, {102, 102, paving}, {103, 103, none}, {296, 297, paving},
                // The Academy's landing, its gate and the lawn before it.
                {-194, 231, none}, {-192, 231, none}, {-191, 231, none},
                // The doorway's mouth, the gap and the wall beside it, the smoke's lawn spot.
                {0, 511, paving}, {0, 512, none}, {8, 512, none}, {16, 472, none}});
        String onRoad = null;
        for (int x = FROM; x < FROM + SIDE && onRoad == null; x++) {
            for (int z = FROM; z < FROM + SIDE && onRoad == null; z++) {
                double r = SeireiteiPlan.radius(x, z);
                boolean road = r < SeireiteiPlan.SEIREITEI_RADIUS && r >= SeireiteiPlan.HILL_RESERVE_RADIUS
                        && (SeireiteiPlan.avenue(x, z) || SeireiteiPlan.ring(x, z));
                if (road && Plan.at(x, z) != Street.PAVING) {
                    onRoad = x + ", " + z + " is " + Plan.at(x, z);
                }
            }
        }
        helper.assertTrue(onRoad == null, "an avenue or ring road is not paved: " + onRoad);
        List<SeireiteiPlan.Site> barracks = SeireiteiPlan.sites("barracks");
        List<BoundingBox> paths = SeireiteiPlan.gatePaths();
        helper.assertTrue(paths.size() == barracks.size(), "there are " + paths.size() + " gate paths for "
                + barracks.size() + " compounds");
        for (int i = 0; i < barracks.size(); i++) {
            SeireiteiPlan.Site site = barracks.get(i);
            BoundingBox path = paths.get(i);
            Direction out = site.rotation().rotate(Direction.NORTH);
            int along = out.getAxis() == Direction.Axis.X ? path.getXSpan() : path.getZSpan();
            BlockPos corner = new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z());
            boolean right = path.getXSpan() * path.getZSpan() == along * SeireiteiPlan.BARRACKS_GATEWAY_WIDTH
                    && !site.footprint().intersects(path);
            // Each edge of the gateway: its step outside the path, the path from the next row on, then a road.
            for (int edge : new int[] {0, SeireiteiPlan.BARRACKS_GATEWAY_WIDTH - 1}) {
                BlockPos step = corner.offset(new BlockPos(SeireiteiPlan.BARRACKS_GATEWAY_X + edge, 0, 0)
                        .rotate(site.rotation()));
                BlockPos road = step.relative(out, along + 1);
                right &= !path.isInside(step) && path.isInside(step.relative(out)) && path.isInside(step.relative(out, along))
                        && !path.isInside(road) && Plan.at(road.getX(), road.getZ()) == Street.PAVING;
            }
            helper.assertTrue(right, site.template() + "'s gate path " + path + " does not run from its step to a road");
        }
        helper.succeed();
    }

    /**
     * No lane wall or lantern shuts a soul in: from the court's arrival, every open column of Seireitei off the mesa and
     * the sites is reached, and each gate's doorway, the Academy's gate, the smoke's lawn spot, each wicket's way and each
     * compound's gate is as many steps away as it would be with no lane walls at all.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void noWallCutsTheWaysOfSeireitei(GameTestHelper helper) {
        List<BoundingBox> prints = SeireiteiPlan.SITES.stream().map(SeireiteiPlan.Site::footprint).toList();
        boolean[] open = new boolean[SIDE * SIDE], unwalled = new boolean[SIDE * SIDE];
        int count = 0;
        for (int x = FROM; x < FROM + SIDE; x++) {
            for (int z = FROM; z < FROM + SIDE; z++) {
                double r = SeireiteiPlan.radius(x, z);
                Street street = Plan.at(x, z);
                boolean free = r < SeireiteiPlan.SEIREITEI_RADIUS && r >= SeireiteiPlan.MESA_RADIUS
                        && street != Street.LANTERN;
                for (int i = 0; free && i < prints.size(); i++) {
                    free = !prints.get(i).isInside(x, SeireiteiPlan.PLATEAU_Y, z);
                }
                unwalled[index(x, z)] = free;
                open[index(x, z)] = free && street != Street.WALL;
                count += open[index(x, z)] ? 1 : 0;
            }
        }
        BlockPos court = BlockPos.containing(TravelLogic.COURT);
        int start = index(court.getX(), court.getZ());
        int[] walled = walk(open, start), bare = walk(unwalled, start);
        long reached = Arrays.stream(walled).filter(steps -> steps >= 0).count();
        helper.assertTrue(reached == count, (count - reached) + " open columns of Seireitei cannot be walked to from the "
                + "court");
        List<BlockPos> ways = new ArrayList<>(List.of(new BlockPos(0, 0, 511), new BlockPos(511, 0, 0),
                new BlockPos(-512, 0, 0), new BlockPos(0, 0, -512), AcademyLocator.GATE.east(),
                BlockPos.containing(LAWN), new BlockPos(0, 0, 300)));
        List<SeireiteiPlan.Site> houses = SeireiteiPlan.sites("gate");
        for (int gate = 0; gate < houses.size(); gate++) {
            // Each wicket's way, 7 in from it toward the centre: on the forecourt's lawn inside the wall's inner face.
            ways.add(GateState.wicket(gate).relative(houses.get(gate).rotation().rotate(Direction.NORTH), 7));
        }
        for (SeireiteiPlan.Site site : SeireiteiPlan.sites("barracks")) {
            // Each compound's gate: its gateway's first column, the row out from its step.
            ways.add(new BlockPos(site.x(), 0, site.z()).offset(new BlockPos(SeireiteiPlan.BARRACKS_GATEWAY_X, 0, -1)
                    .rotate(site.rotation())));
        }
        for (BlockPos way : ways) {
            int at = index(way.getX(), way.getZ());
            helper.assertTrue(walled[at] >= 0 && walled[at] == bare[at], "the way at " + way.toShortString() + " is "
                    + walled[at] + " steps from the court with the lane walls and " + bare[at] + " without");
        }
        helper.succeed();
    }

    // ---- In the world ----

    /**
     * Twenty-five chunks of the real Soul Society about the south avenue hold their streets as the plan lays them: paving
     * as the lawn's top block, so a soul lands at y 72 on it and on the smoke's lawn spot, and walls on the lawn.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theStreetsAreLaidAsPlanned(GameTestHelper helper) {
        ServerLevel soul = ShakonmakuTests.hold(helper, SOUTH_X, SOUTH_Z).soul;
        int[] kinds = new int[Street.values().length];
        for (int x = (SOUTH_X - 2) << 4; x < (SOUTH_X + 3) << 4; x++) {
            for (int z = (SOUTH_Z - 2) << 4; z < (SOUTH_Z + 3) << 4; z++) {
                kinds[SeireiteiPlan.street(x, z).ordinal()]++;
            }
        }
        helper.assertTrue(kinds[Street.PAVING.ordinal()] > 0 && kinds[Street.WALL.ordinal()] > 0,
                "the chunks hold no paving or no wall: " + Arrays.toString(kinds));
        String wrong = misLaid(soul, SOUTH_X - 2, SOUTH_Z - 2, SOUTH_X + 2, SOUTH_Z + 2);
        helper.assertTrue(wrong == null, "the streets are not laid as planned at " + wrong);
        Player soulThere = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Vec3 spot : List.of(LAWN, new Vec3(0.5, SeireiteiPlan.PLATEAU_Y, 470.5))) {
            Vec3 landed = TravelEvents.landing(soulThere, soul, spot);
            helper.assertTrue(landed != null && landed.distanceTo(spot) < SAME, "a soul sent to " + spot + " lands at "
                    + landed);
        }
        helper.succeed();
    }

    /**
     * The court's chunks in the real Soul Society hold its square as the plan lays it, so a soul sent to the court lands
     * on its marked block at y 72, and a gate opened there, whichever way its opener faces, meets no block.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theCourtSquareStandsAboutItsArrival(GameTestHelper helper) {
        BlockPos court = BlockPos.containing(TravelLogic.COURT);
        ServerLevel soul = ShakonmakuTests.hold(helper, court.getX() >> 4, court.getZ() >> 4).soul;
        int half = SeireiteiPlan.COURT_RESERVE_HALF;
        String wrong = misLaid(soul, (court.getX() - half) >> 4, (court.getZ() - half) >> 4, (court.getX() + half) >> 4,
                (court.getZ() + half) >> 4);
        helper.assertTrue(wrong == null && SeireiteiPlan.street(court.getX(), court.getZ()) == Street.MARK,
                "the court square is not laid as planned at " + wrong);
        Player arriving = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 landed = TravelEvents.landing(arriving, soul, TravelLogic.COURT);
        helper.assertTrue(landed != null && landed.distanceTo(TravelLogic.COURT) < SAME, "a soul sent to the court lands at "
                + landed);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            arriving.moveTo(TravelLogic.COURT.x, TravelLogic.COURT.y, TravelLogic.COURT.z, facing.toYRot(), 0.0F);
            helper.assertFalse(soul.getBlockCollisions(null, Senkaimon.boxFor(arriving, arriving)).iterator().hasNext(),
                    "a gate opened at the court facing " + facing + " meets a block");
        }
        helper.succeed();
    }
}
