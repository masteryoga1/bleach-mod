package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.RukongaiEvents;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Rukongai's map: which district of which region each column of Soul Society lies in, the names a soul is
 * told of where it is, and the place named on the action bar, which waits for any other line there to have had its time.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class RukongaiTests {
    private static final int EAST = Rukongai.Region.EAST.ordinal(), SOUTH = Rukongai.Region.SOUTH.ordinal(),
            WEST = Rukongai.Region.WEST.ordinal(), NORTH = Rukongai.Region.NORTH.ordinal();
    /** Where a row gives no region to check. */
    private static final int ANY = -1;
    /** Each block column {x, z} with the district it lies in and its region, from the plan's edges outward. */
    private static final int[][] EDGES = {
            {0, 0, -1, ANY},
            // Along +x: Seireitei's edge, the wall's band, the first districts, the last and the wilds.
            {511, 0, -1, EAST}, {512, 0, 0, EAST}, {519, 0, 0, EAST}, {520, 0, 1, EAST}, {551, 0, 1, EAST},
            {552, 0, 2, EAST}, {3079, 0, 80, EAST}, {3080, 0, 81, EAST},
            // Mirrored, where a block's centre lies half a block the other way.
            {-521, 0, 1, WEST}, {-520, 0, 0, WEST}, {0, -521, 1, NORTH}, {0, -520, 0, NORTH},
            // On a diagonal the tie goes to East or West; beside one, the axis the centre lies nearer.
            {367, 367, 0, EAST}, {368, 368, 1, EAST}, {-369, -369, 1, WEST}, {368, -369, 1, EAST}, {-369, 368, 1, WEST},
            {100, 99, -1, EAST}, {99, 100, -1, SOUTH}, {-101, 99, -1, WEST},
            // The south gate's gap, and the spot outside the wall the Academy's sense is tested from.
            {0, 515, 0, SOUTH}, {-192, 531, 2, SOUTH},
            // A district on the way, the six canon names, and West's 64th and 1st, which have none.
            {1000, 0, 16, EAST}, {2550, 0, 64, EAST}, {2930, 0, 76, EAST}, {0, 3000, 78, SOUTH}, {0, -3030, 79, NORTH},
            {0, -3060, 80, NORTH}, {-605, -105, 3, WEST}, {-2551, 0, 64, WEST}, {-530, 0, 1, WEST},
            // The wilds, near and far.
            {0, -3100, 81, NORTH}, {9000, 100, 266, EAST}};
    private static final String SEIREITEI_KEY = "message.bleach.place.seireitei";
    private static final String SEIREIHEKI_KEY = "message.bleach.place.seireiheki";

    private RukongaiTests() {
    }

    /**
     * Soul Society's chunks within 2 of this one, loaded now and held entity-ticking, as {@code TravelTests.holdCourt}
     * holds the court's, so a soul set down there ticks at once. Never released: the test server ends with the run.
     */
    private static void hold(ServerLevel soul, ChunkPos centre) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                soul.getChunk(centre.x + x, centre.z + z);
            }
        }
        soul.getChunkSource().addRegionTicket(TicketType.FORCED, centre, 2, centre);
    }

    /** "(x, z)", for a failure to name a column. */
    private static String column(int x, int z) {
        return "(" + x + ", " + z + ")";
    }

    /**
     * Asserts that this line has the key and these arguments: a string stands for a translated line with that key, and
     * anything else must equal the argument.
     */
    private static void assertKey(GameTestHelper helper, Component line, String key, Object... args) {
        helper.assertTrue(line.getContents() instanceof TranslatableContents, "the line " + line + " is not translated");
        TranslatableContents contents = (TranslatableContents) line.getContents();
        Object[] actual = contents.getArgs();
        helper.assertTrue(contents.getKey().equals(key) && actual.length == args.length,
                "the line " + line + " is not " + key + " with " + args.length + " arguments");
        for (int i = 0; i < args.length; i++) {
            boolean same = args[i] instanceof String wanted
                    ? actual[i] instanceof Component part && part.getContents() instanceof TranslatableContents words
                            && words.getKey().equals(wanted)
                    : args[i].equals(actual[i]);
            helper.assertTrue(same, "argument " + (i + 1) + " of " + key + " is " + actual[i] + ", not " + args[i]);
        }
    }

    // ---- The map ----

    /**
     * Each column's district and region at the plan's edges: Seireitei, the wall's band, every district's first and last
     * blocks on both sides, the diagonals and the wilds. Then the place codes a soul is told of once each.
     */
    @GameTest(template = "empty")
    public static void districtsAndRegionsAtTheirEdges(GameTestHelper helper) {
        helper.assertTrue(Rukongai.SEIREITEI == -1 && Rukongai.WALL == 0 && Rukongai.WILDS == 81
                && Rukongai.NOWHERE == Integer.MIN_VALUE, "the map's rings are numbered " + Rukongai.SEIREITEI + ", "
                + Rukongai.WALL + ", " + Rukongai.WILDS + " and " + Rukongai.NOWHERE);
        for (int[] row : EDGES) {
            int district = Rukongai.district(row[0], row[1]);
            helper.assertTrue(district == row[2], "column " + column(row[0], row[1]) + " lies in district " + district
                    + ", not " + row[2]);
            if (row[3] != ANY) {
                Rukongai.Region region = Rukongai.region(row[0], row[1]);
                helper.assertTrue(region.ordinal() == row[3], "column " + column(row[0], row[1]) + " lies in the "
                        + region + " region, not the " + Rukongai.Region.values()[row[3]]);
            }
        }
        for (int d = 1; d <= SeireiteiPlan.DISTRICTS; d++) {
            int start = SeireiteiPlan.districtStart(d);
            // A block whose west face lies on a district's start has its centre half a block inside it, on either side.
            helper.assertTrue(Rukongai.district(start, 0) == d && Rukongai.district(start - 1, 0) == d - 1,
                    "district " + d + " starts at " + start + " east: " + Rukongai.district(start, 0) + ", "
                            + Rukongai.district(start - 1, 0));
            helper.assertTrue(Rukongai.district(-start - 1, 0) == d && Rukongai.district(-start, 0) == d - 1
                            && Rukongai.region(-start - 1, 0) == Rukongai.Region.WEST,
                    "district " + d + " starts at " + start + " west: " + Rukongai.district(-start - 1, 0) + ", "
                            + Rukongai.district(-start, 0) + " in the " + Rukongai.region(-start - 1, 0) + " region");
        }
        int[][] starts = {{3, 584}, {64, 2536}, {76, 2920}, {78, 2984}, {79, 3016}, {80, 3048}};
        for (int[] start : starts) {
            helper.assertTrue(SeireiteiPlan.districtStart(start[0]) == start[1], "district " + start[0] + " starts at "
                    + SeireiteiPlan.districtStart(start[0]) + ", not " + start[1]);
        }
        // Each ring of a region's wilds is one place with the next, and a column on a diagonal's other side is South's.
        int[][] places = {{0, 0, -1}, {0, 515, 0}, {2550, 0, 64}, {-605, -105, 2003}, {0, -3100, 3081}, {9000, 100, 81},
                {368, 368, 1}, {368, 369, 1001}};
        for (int[] place : places) {
            int code = Rukongai.place(place[0], place[1]);
            helper.assertTrue(code == place[2], "column " + column(place[0], place[1]) + " is place " + code + ", not "
                    + place[2]);
        }
        helper.succeed();
    }

    /**
     * What a soul is told of where it is: Seireitei, the Seireiheki, a district of its region, with the six names canon
     * gives at grade B and no others, and the wilds. The four gates stand in the wall's band on their own regions' axes.
     */
    @GameTest(template = "empty")
    public static void placeNamesAndCanonNames(GameTestHelper helper) {
        assertKey(helper, Rukongai.placeName(100, 100), SEIREITEI_KEY);
        assertKey(helper, Rukongai.placeName(0, 515), SEIREIHEKI_KEY);
        assertKey(helper, Rukongai.placeName(1000, 0), "message.bleach.place.district", "message.bleach.place.region.east",
                Integer.valueOf(16));
        assertKey(helper, Rukongai.placeName(-605, -105), "message.bleach.place.district.named",
                "message.bleach.place.region.west", Integer.valueOf(3), "message.bleach.place.name.hokutan");
        assertKey(helper, Rukongai.placeName(2550, 0), "message.bleach.place.district.named",
                "message.bleach.place.region.east", Integer.valueOf(64), "message.bleach.place.name.sabitsura");
        // Sabitsura is East's: West's 64th district has no name.
        assertKey(helper, Rukongai.placeName(-2551, 0), "message.bleach.place.district", "message.bleach.place.region.west",
                Integer.valueOf(64));
        assertKey(helper, Rukongai.placeName(0, -3100), "message.bleach.place.wilds", "message.bleach.place.region.north");

        Map<String, String> named = new HashMap<>();
        for (Rukongai.Region region : Rukongai.Region.values()) {
            for (int d = 1; d <= SeireiteiPlan.DISTRICTS; d++) {
                String name = Rukongai.canonName(region, d);
                if (name != null) {
                    named.put(region + " " + d, name);
                }
            }
        }
        Map<String, String> canon = Map.of("WEST 3", "hokutan", "EAST 64", "sabitsura", "EAST 76", "sakahone",
                "SOUTH 78", "inuzuri", "NORTH 79", "kusajishi", "NORTH 80", "zaraki");
        helper.assertTrue(named.equals(canon), "the names canon gives are " + named + ", not " + canon);
        // The names graded C are left out, and Sabitsura's other side is not named.
        helper.assertTrue(Rukongai.canonName(Rukongai.Region.WEST, 1) == null
                        && Rukongai.canonName(Rukongai.Region.EAST, 37) == null
                        && Rukongai.canonName(Rukongai.Region.EAST, 62) == null
                        && Rukongai.canonName(Rukongai.Region.WEST, 64) == null,
                "a district that canon names only at grade C, or West's 64th, has a name");

        BlockPos[] gates = {new BlockPos(516, 72, 0), new BlockPos(0, 72, 516), new BlockPos(-516, 72, 0),
                new BlockPos(0, 72, -516)};
        String[] gateKeys = {"message.bleach.place.gate.east", "message.bleach.place.gate.south",
                "message.bleach.place.gate.west", "message.bleach.place.gate.north"};
        for (Rukongai.Region region : Rukongai.Region.values()) {
            BlockPos gate = region.gate();
            helper.assertTrue(gate.equals(gates[region.ordinal()]), region + "'s gate stands at " + gate.toShortString()
                    + ", not " + gates[region.ordinal()].toShortString());
            helper.assertTrue(Rukongai.region(gate.getX(), gate.getZ()) == region
                            && Rukongai.district(gate.getX(), gate.getZ()) == Rukongai.WALL,
                    region + "'s gate lies in the " + Rukongai.region(gate.getX(), gate.getZ()) + " region, in ring "
                            + Rukongai.district(gate.getX(), gate.getZ()));
            assertKey(helper, region.gateName(), gateKeys[region.ordinal()]);
        }
        helper.assertTrue(BleachConfig.DISTRICT_NAMES.getDefault(), "the action bar names no places by default");
        helper.succeed();
    }

    // ---- The place line ----

    /**
     * When a soul is told of its place: a new one, once nothing has been on its action bar for 60 ticks, and again after
     * leaving Soul Society, but not with the server's names off, which marks nothing.
     */
    @GameTest(template = "empty")
    public static void placeLinesWaitForAQuietActionBar(GameTestHelper helper) {
        helper.assertTrue(RukongaiEvents.QUIET_TICKS == 60, "a place waits " + RukongaiEvents.QUIET_TICKS + " ticks");
        SoulData data = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        helper.assertTrue(RukongaiEvents.tells(data, -1, 1000L), "a soul just come into Seireitei was not told");
        helper.assertFalse(RukongaiEvents.tells(data, -1, 1020L), "a soul still in Seireitei was told again");
        // Telling stamps no line itself: the line it lets through is stamped as it is sent.
        helper.assertTrue(RukongaiEvents.tells(data, 0, 1040L), "a soul come into the wall's band was not told");
        helper.assertFalse(RukongaiEvents.tells(data, Rukongai.NOWHERE, 1060L), "a soul outside Soul Society was told");
        helper.assertTrue(RukongaiEvents.tells(data, 0, 1080L), "a soul back in the wall's band was not told again");
        RukongaiEvents.noteActionBar(data, 2000L);
        helper.assertFalse(RukongaiEvents.tells(data, 5, 2059L), "a place covered another line 59 ticks after it");
        helper.assertTrue(RukongaiEvents.tells(data, 5, 2060L), "a place was not told 60 ticks after another line");
        boolean before = BleachConfig.get(BleachConfig.DISTRICT_NAMES);
        try {
            BleachConfig.DISTRICT_NAMES.set(false);
            helper.assertFalse(RukongaiEvents.tells(data, 6, 3000L), "a place was told with the names off");
            BleachConfig.DISTRICT_NAMES.set(true);
            helper.assertTrue(RukongaiEvents.tells(data, 6, 3000L), "a place refused with the names off was marked told");
        } finally {
            BleachConfig.DISTRICT_NAMES.set(before);
        }
        helper.succeed();
    }

    /**
     * A real soul in Soul Society is told it is in Seireitei, then, in the south gate's gap, that it is in the Seireiheki,
     * but not until another line has shown its 60 ticks. Out of Soul Society and back to the same gap, it is told again.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aSoulIsToldWhereItIsInSoulSociety(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        // The two columns' chunks: a lawn in Seireitei, and the south gate's gap, grass at 71 and air above.
        hold(soul, new ChunkPos(6, 6));
        hold(soul, new ChunkPos(0, 32));
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        long[] otherAt = {0L};
        int[] leftAt = {0};
        helper.startSequence()
                .thenExecute(() -> player.teleportTo(soul, 100.5, SeireiteiPlan.PLATEAU_Y, 100.5, 0.0F, 0.0F))
                // Told once it is there: no other line has come in the last 60 ticks.
                .thenWaitUntil(() -> helper.assertTrue(TravelTests.lines(player, SEIREITEI_KEY) == 1L,
                        "the soul in Seireitei was told " + TravelTests.said(player) + " at " + TravelTests.where(player)))
                .thenExecute(() -> {
                    player.displayClientMessage(Component.literal("another line"), true);
                    otherAt[0] = player.level().getGameTime();
                    player.teleportTo(soul, 0.5, SeireiteiPlan.PLATEAU_Y, 515.5, 0.0F, 0.0F);
                })
                // The south gate's gap is the Seireiheki's band, but the other line is still showing.
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(TravelTests.lines(player, SEIREIHEKI_KEY) == 0L,
                        "the place covered another line 40 ticks after it: " + TravelTests.said(player)))
                .thenWaitUntil(() -> helper.assertTrue(TravelTests.lines(player, SEIREIHEKI_KEY) == 1L,
                        "the soul in the wall's gap was never told: " + TravelTests.said(player) + " at "
                                + TravelTests.where(player)))
                .thenExecute(() -> helper.assertTrue(player.level().getGameTime() - otherAt[0] >= RukongaiEvents.QUIET_TICKS,
                        "the place came " + (player.level().getGameTime() - otherAt[0]) + " ticks after another line"))
                // Each place told once so far, so the count below can only grow by the way back.
                .thenExecute(() -> helper.assertTrue(TravelTests.lines(player, SEIREITEI_KEY) == 1L
                                && TravelTests.lines(player, SEIREIHEKI_KEY) == 1L,
                        "before leaving, the soul was told " + TravelTests.said(player)))
                // Out of Soul Society and back to the same gap: told again only if leaving forgot the place, as the
                // gap was the last place told and is the same place on the way back.
                .thenExecute(() -> {
                    Vec3 home = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
                    player.teleportTo(helper.getLevel(), home.x, home.y, home.z, 0.0F, 0.0F);
                    leftAt[0] = player.tickCount;
                })
                // Its place is read every 20 of its own ticks, so once 20 have passed one reading was in the Overworld.
                .thenWaitUntil(() -> helper.assertTrue(player.tickCount >= leftAt[0] + 20,
                        "the soul has ticked " + (player.tickCount - leftAt[0]) + " times in the Overworld"))
                .thenExecute(() -> player.teleportTo(soul, 0.5, SeireiteiPlan.PLATEAU_Y, 515.5, 0.0F, 0.0F))
                .thenWaitUntil(() -> helper.assertTrue(TravelTests.lines(player, SEIREIHEKI_KEY) == 2L
                                && TravelTests.lines(player, SEIREITEI_KEY) == 1L,
                        "the soul back in the wall's gap was told " + TravelTests.said(player) + " at "
                                + TravelTests.where(player)))
                .thenSucceed();
    }
}
