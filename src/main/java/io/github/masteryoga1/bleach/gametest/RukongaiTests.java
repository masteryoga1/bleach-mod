package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.DevourEvents;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.KonsoTarget;
import io.github.masteryoga1.bleach.soul.PlusEvents;
import io.github.masteryoga1.bleach.soul.RukongaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.soul.TravelRecord;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Rukongai's map: which district of which region each column of Soul Society lies in, the names a soul is
 * told of where it is, and the place named on the action bar, which waits for any other line there to have had its time.
 * And where Konsō sends a soul: a district of the Rukongai drawn by chance, which becomes its home. Then where a soul
 * wakes when it dies, at that home among others, and the worlds where a Plus's chain holds.
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
    private static final String WOKE_KEY = "message.bleach.konso.rukongai";
    private static final String WAY_KEY = "message.bleach.academy_way.gate";
    /** How many draws a test of the odds makes. */
    private static final int DRAWS = 10_000;
    /** Seed 5's first column, and the three a landing there tries after it, all in Hokutan, District 3 of the West. */
    private static final int[][] HOKUTAN = {{-605, -105}, {-525, -288}, {-552, -226}, {-520, -298}};

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
        assertLeads(helper, line, key, args.length, args);
    }

    /** As {@link #assertKey}, for a line of {@code count} arguments whose first ones are these. */
    private static void assertLeads(GameTestHelper helper, Component line, String key, int count, Object... first) {
        helper.assertTrue(line.getContents() instanceof TranslatableContents, "the line " + line + " is not translated");
        TranslatableContents contents = (TranslatableContents) line.getContents();
        Object[] actual = contents.getArgs();
        helper.assertTrue(contents.getKey().equals(key) && actual.length == count,
                "the line " + line + " is not " + key + " with " + count + " arguments");
        for (int i = 0; i < first.length; i++) {
            boolean same = first[i] instanceof String wanted
                    ? actual[i] instanceof Component part && part.getContents() instanceof TranslatableContents words
                            && words.getKey().equals(wanted)
                    : first[i].equals(actual[i]);
            helper.assertTrue(same, "argument " + (i + 1) + " of " + key + " is " + actual[i] + ", not " + first[i]);
        }
    }

    /** Where in this joined player's chat the first line with this key from line {@code from} on is, or -1 for none. */
    private static int chatLine(ServerPlayer player, String key, int from) {
        List<Component> chat = TestPlayers.chat(player);
        for (int i = from; i < chat.size(); i++) {
            if (chat.get(i).getContents() instanceof TranslatableContents words && words.getKey().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** The keys of every line this joined player's chat has shown, oldest first, for a failure to name. */
    private static List<String> chatKeys(ServerPlayer player) {
        return TestPlayers.chat(player).stream()
                .map(line -> line.getContents() instanceof TranslatableContents words ? words.getKey() : line.getString())
                .toList();
    }

    /** How many times this joined player has been told the line with this key in chat. */
    private static long told(ServerPlayer player, String key) {
        return TestPlayers.chat(player).stream()
                .filter(line -> line.getContents() instanceof TranslatableContents words && words.getKey().equals(key))
                .count();
    }

    /** A real Plus joined at this spot in the test's box, with an hour of chain left. */
    private static ServerPlayer chainedPlus(GameTestHelper helper, Vec3 at) {
        ServerPlayer plus = TestPlayers.join(helper, GameType.SURVIVAL, at);
        SoulData data = ShinigamiTests.soul(SoulState.PLUS, 40.0F);
        data.startChain(72_000);
        plus.setData(SoulAttachments.SOUL, data);
        return plus;
    }

    /** An Overworld instructor, spawned for it at (1, 1, 1), performs Konsō on this Plus, and is taken away again. */
    private static void instructorsKonso(GameTestHelper helper, ServerPlayer plus) {
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        try {
            instructor.perform(plus, NpcAction.KONSO);
        } finally {
            // The Overworld one is persistent, and the test grid is never cleared.
            instructor.discard();
        }
    }

    /** What {@link #DRAWS} draws from one seed gave: the first, and how many fell where a test of the odds counts. */
    private record Tally(Rukongai.Draw first, int inner, int outer, int[] regions, int astray) {
    }

    /**
     * {@link #DRAWS} draws at this bias from this seed: the first, how many fell in districts 1 to 20 and past 70, how
     * many in each region, and how many whose block lies outside the district or region drawn.
     */
    private static Tally tally(long seed, double bias) {
        RandomSource random = RandomSource.create(seed);
        Rukongai.Draw first = null;
        int inner = 0, outer = 0, astray = 0;
        int[] regions = new int[Rukongai.Region.values().length];
        for (int i = 0; i < DRAWS; i++) {
            Rukongai.Draw draw = Rukongai.draw(random, bias);
            if (i == 0) {
                first = draw;
            }
            if (draw.district() <= 20) {
                inner++;
            } else if (draw.district() > 70) {
                outer++;
            }
            regions[draw.region().ordinal()]++;
            if (Rukongai.district(draw.x(), draw.z()) != draw.district()
                    || Rukongai.region(draw.x(), draw.z()) != draw.region()) {
                astray++;
            }
        }
        return new Tally(first, inner, outer, regions, astray);
    }

    /**
     * Asserts that a count of {@link #DRAWS} draws lies within {@code spread} of {@code mean}. The count the seed gave
     * when the test was written goes in the message, so a change in the draw's order of calls shows as a changed count.
     */
    private static void assertNear(GameTestHelper helper, int count, int mean, int spread, int seeded, String what) {
        helper.assertTrue(Math.abs(count - mean) <= spread, count + " of " + DRAWS + " draws " + what + " (the seed gave "
                + seeded + " as written), not within " + spread + " of " + mean);
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
                // Half the Seireitei line's own wait first, so the gap would be told within the next 40 ticks unless
                // the other line, noted as it is sent, holds it back.
                .thenIdle(30)
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

    // ---- Where Konsō sends a soul ----

    /**
     * The draw: the district an even chance gives at a bias, each region's middle, and ten thousand seeded draws, every
     * block in the district and region drawn, at the odds the bias sets; seed 5's landing and retries, in Hokutan. Then
     * the settings' defaults, and the home a travel record keeps, saved, read back, and read leniently.
     */
    @GameTest(template = "empty")
    public static void konsoLandingsFavourTheInnerDistricts(GameTestHelper helper) {
        // {u, bias, district}: half the chances fall in the first 20 districts at bias 2, a quarter at bias 1.
        double[][] chances = {{0.0, 2.0, 1}, {0.4999, 2.0, 20}, {0.5, 2.0, 21}, {Math.nextDown(1.0), 2.0, 80},
                {0.25, 1.0, 21}, {Math.nextDown(0.25), 1.0, 20}};
        for (double[] chance : chances) {
            int district = Rukongai.districtFor(chance[0], chance[1]);
            helper.assertTrue(district == (int) chance[2], "a chance of " + chance[0] + " at bias " + chance[1]
                    + " gives district " + district + ", not " + (int) chance[2]);
        }
        double[] bearings = {0.0, Math.PI / 2.0, Math.PI, Math.PI * 1.5};
        for (Rukongai.Region region : Rukongai.Region.values()) {
            helper.assertTrue(Math.abs(region.bearing() - bearings[region.ordinal()]) < 1.0E-9, "the " + region
                    + " region's middle lies at a bearing of " + region.bearing() + ", not " + bearings[region.ordinal()]);
        }

        Tally biased = tally(7L, 2.0);
        helper.assertTrue(biased.astray() == 0, biased.astray() + " of " + DRAWS + " blocks drawn lie outside their district "
                + "or region");
        assertNear(helper, biased.inner(), 5000, 200, 4991, "at bias 2 fell in districts 1 to 20");
        assertNear(helper, biased.outer(), 646, 100, 625, "at bias 2 fell past district 70");
        int[] seeded = {2537, 2494, 2546, 2423};
        for (Rukongai.Region region : Rukongai.Region.values()) {
            assertNear(helper, biased.regions()[region.ordinal()], 2500, 175, seeded[region.ordinal()],
                    "at bias 2 fell in the " + region + " region");
        }
        Rukongai.Draw west33 = new Rukongai.Draw(Rukongai.Region.WEST, 33, -1138, 1066);
        helper.assertTrue(west33.equals(biased.first()), "seed 7's first draw is " + biased.first() + ", not " + west33);
        Tally even = tally(7L, 1.0);
        assertNear(helper, even.inner(), 2500, 200, 2484, "at bias 1 fell in districts 1 to 20");
        assertNear(helper, even.outer(), 1250, 150, 1255, "at bias 1 fell past district 70");

        RandomSource random = RandomSource.create(5L);
        Rukongai.Draw landing = Rukongai.draw(random, 2.0);
        Rukongai.Draw hokutan = new Rukongai.Draw(Rukongai.Region.WEST, 3, HOKUTAN[0][0], HOKUTAN[0][1]);
        helper.assertTrue(hokutan.equals(landing) && "hokutan".equals(Rukongai.canonName(Rukongai.Region.WEST, 3)),
                "seed 5's first draw is " + landing + ", named " + Rukongai.canonName(landing.region(), landing.district())
                        + ", not " + hokutan + ", Hokutan");
        for (int i = 1; i < HOKUTAN.length; i++) {
            Rukongai.Draw retry = Rukongai.pointIn(Rukongai.Region.WEST, 3, random);
            helper.assertTrue(retry.x() == HOKUTAN[i][0] && retry.z() == HOKUTAN[i][1]
                            && Rukongai.district(retry.x(), retry.z()) == 3
                            && Rukongai.region(retry.x(), retry.z()) == Rukongai.Region.WEST,
                    "seed 5's retry " + i + " is " + column(retry.x(), retry.z()) + ", not " + column(HOKUTAN[i][0],
                            HOKUTAN[i][1]) + " in District 3 of the West");
        }

        helper.assertTrue(BleachConfig.KONSO_INNER_BIAS.getDefault() == 2.0
                        && BleachConfig.KONSO_SENDS_TO.getDefault() == KonsoTarget.RUKONGAI
                        && BleachConfig.HOME_WORLD_RESPAWN.getDefault(),
                "the defaults are a bias of " + BleachConfig.KONSO_INNER_BIAS.getDefault() + ", sending to "
                        + BleachConfig.KONSO_SENDS_TO.getDefault() + " and waking at home "
                        + BleachConfig.HOME_WORLD_RESPAWN.getDefault());

        BlockPos block = new BlockPos(-605, 72, -105);
        TravelRecord home = TravelRecord.NONE.withRukongaiHome(block);
        helper.assertTrue(!home.equals(TravelRecord.NONE) && home.rukongaiHome().equals(Optional.of(block)),
                "a record of nothing but a home is " + home);
        Tag saved = TravelRecord.CODEC.encodeStart(NbtOps.INSTANCE, home).getOrThrow();
        TravelRecord read = TravelRecord.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(read.equals(home), "saved " + home + " but read " + read);
        // A record saved before there were homes, and one whose home is not a block.
        CompoundTag old = new CompoundTag();
        old.putLong("butterfly_day", 7L);
        TravelRecord before = TravelRecord.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(before.butterflyDay() == 7L && before.rukongaiHome().isEmpty(), "an old record read as " + before);
        CompoundTag bad = new CompoundTag();
        bad.put("rukongai_home", IntTag.valueOf(3));
        TravelRecord lenient = TravelRecord.CODEC.parse(NbtOps.INSTANCE, bad).getOrThrow();
        helper.assertTrue(lenient.rukongaiHome().isEmpty(), "a home that is no block read as " + lenient);
        helper.assertTrue(home.withButterflyDay(9L).rukongaiHome().equals(home.rukongaiHome())
                        && home.withCrossing(Optional.empty()).rukongaiHome().equals(home.rukongaiHome()),
                "a new butterfly day or crossing lost the home of " + home);
        helper.succeed();
    }

    /**
     * A real Plus freed by an Overworld instructor wakes in a district of the Rukongai, which becomes its home, facing
     * Seireitei and with its bed let go, and is told where it woke and the way to the Academy. With the server sending
     * souls nowhere, another stays where it stood with its bed. The Academy's chunks build it first, for the fallback.
     */
    @GameTest(template = "empty", batch = "konso_move", timeoutTicks = 400)
    public static void anInstructorsKonsoSendsASoulToTheRukongai(GameTestHelper helper) {
        ServerLevel soul = SiteTests.holdAcademy(helper);
        ServerPlayer sent = chainedPlus(helper, new Vec3(2.5, 1.0, 1.5));
        ServerPlayer kept = chainedPlus(helper, new Vec3(4.5, 1.0, 1.5));
        BlockPos keptBed = helper.absolutePos(new BlockPos(3, 1, 3));
        sent.setRespawnPosition(Level.OVERWORLD, helper.absolutePos(new BlockPos(1, 1, 3)), 0.0F, true, false);
        kept.setRespawnPosition(Level.OVERWORLD, keptBed, 0.0F, true, false);
        helper.startSequence()
                .thenWaitUntil(() -> AcademyTests.assertAcademyStands(helper, soul))
                .thenExecute(() -> {
                    instructorsKonso(helper, sent);
                    // A change of world is made at once, inside the Konsō.
                    helper.assertTrue(sent.level() == soul, "the soul is still in " + TravelTests.where(sent));
                    SoulData data = SoulLogic.get(sent);
                    helper.assertTrue(data.state() == SoulState.RUKONGAI && data.chainMaxTicks() == 0,
                            "Konsō left a " + data.state() + " with a chain of " + data.chainMaxTicks());
                    int x = sent.getBlockX(), z = sent.getBlockZ();
                    int district = Rukongai.district(x, z);
                    helper.assertTrue(district >= 1 && district <= SeireiteiPlan.DISTRICTS,
                            "the soul woke in ring " + district + ", at " + TravelTests.where(sent));
                    Optional<BlockPos> home = sent.getData(SoulAttachments.TRAVEL).rukongaiHome();
                    helper.assertTrue(home.equals(Optional.of(sent.blockPosition())),
                            "the soul's home is " + home + ", not the block it woke in, " + sent.blockPosition());
                    helper.assertTrue(sent.getRespawnPosition() == null,
                            "the soul kept its bed at " + sent.getRespawnPosition());
                    // Read against the way to the centre column, not against the yaw's own formula.
                    Vec3 facing = Vec3.directionFromRotation(0.0F, sent.getYRot());
                    double along = facing.dot(new Vec3(-sent.getX(), 0.0, -sent.getZ()).normalize());
                    helper.assertTrue(along > 0.99, "the soul faces " + sent.getYRot() + ", not Seireitei's way ("
                            + along + " along it)");
                    List<Component> chat = TestPlayers.chat(sent);
                    int woke = chatLine(sent, WOKE_KEY, 0);
                    helper.assertTrue(woke >= 0, "the soul was never told where it woke: " + chatKeys(sent));
                    assertKey(helper, chat.get(woke), WOKE_KEY, Rukongai.placeName(x, z));
                    int way = chatLine(sent, WAY_KEY, woke + 1);
                    helper.assertTrue(way >= 0, "the soul was never told the way to the Academy after where it woke: "
                            + chatKeys(sent));
                    assertLeads(helper, chat.get(way), WAY_KEY, 3,
                            "message.bleach.place.gate." + Rukongai.region(x, z).key());
                })
                .thenExecute(() -> {
                    Vec3 stood = kept.position();
                    KonsoTarget before = BleachConfig.get(BleachConfig.KONSO_SENDS_TO);
                    try {
                        BleachConfig.KONSO_SENDS_TO.set(KonsoTarget.NOWHERE);
                        instructorsKonso(helper, kept);
                    } finally {
                        BleachConfig.KONSO_SENDS_TO.set(before);
                    }
                    helper.assertTrue(kept.level() == helper.getLevel() && kept.position().distanceTo(stood) < 0.01,
                            "the soul sent nowhere was moved from " + stood + " to " + TravelTests.where(kept));
                    helper.assertTrue(SoulLogic.get(kept).state() == SoulState.RUKONGAI,
                            "Konsō sending nowhere left a " + SoulLogic.get(kept).state());
                    helper.assertTrue(kept.getData(SoulAttachments.TRAVEL).rukongaiHome().isEmpty(),
                            "the soul sent nowhere has a home at " + kept.getData(SoulAttachments.TRAVEL).rukongaiHome());
                    helper.assertTrue(keptBed.equals(kept.getRespawnPosition()),
                            "the soul sent nowhere has its spawn at " + kept.getRespawnPosition() + ", not its bed");
                })
                .thenSucceed();
    }

    /**
     * Sent on with the draw seeded, a soul wakes in Hokutan, District 3 of the West: on the first column drawn unless its
     * ground refused it, else on one of the three tried after it, standing on the ground, with that block as its home.
     * It is told where it woke and, as it may enrol, the way to the Academy by Hakutōmon.
     */
    @GameTest(template = "empty", batch = "konso_move", timeoutTicks = 200)
    public static void aSeededLandingWakesInHokutan(GameTestHelper helper) {
        ServerLevel soul = SiteTests.holdAcademy(helper);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(2.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        helper.startSequence()
                .thenWaitUntil(() -> AcademyTests.assertAcademyStands(helper, soul))
                .thenExecute(() -> {
                    helper.assertTrue(RukongaiEvents.toRukongai(player, RandomSource.create(5L)),
                            "the seeded landing moved no one");
                    helper.assertTrue(player.level() == soul, "the soul landed in " + TravelTests.where(player));
                    int taken = -1;
                    for (int i = 0; i < HOKUTAN.length && taken < 0; i++) {
                        if (Math.abs(player.getX() - (HOKUTAN[i][0] + 0.5)) < 0.01
                                && Math.abs(player.getZ() - (HOKUTAN[i][1] + 0.5)) < 0.01) {
                            taken = i;
                        }
                    }
                    helper.assertTrue(taken >= 0, "the soul landed at " + player.position()
                            + ", on none of the four columns drawn in Hokutan");
                    // The ground under the feet may be farmland, 15/16 high.
                    int top = soul.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(),
                            player.getBlockZ());
                    helper.assertTrue(Math.abs(player.getY() - top) <= 1.0, "the soul stands at y " + player.getY()
                            + ", not on its column's ground at " + top);
                    Optional<BlockPos> home = player.getData(SoulAttachments.TRAVEL).rukongaiHome();
                    helper.assertTrue(home.equals(Optional.of(player.blockPosition())),
                            "the soul's home is " + home + ", not the block it woke in, " + player.blockPosition());
                    List<Component> chat = TestPlayers.chat(player);
                    int woke = chatLine(player, WOKE_KEY, 0);
                    helper.assertTrue(woke >= 0, "the soul was never told where it woke: " + chatKeys(player));
                    assertKey(helper, chat.get(woke), WOKE_KEY, "message.bleach.place.district.named");
                    Component where = (Component) ((TranslatableContents) chat.get(woke).getContents()).getArgs()[0];
                    assertKey(helper, where, "message.bleach.place.district.named", "message.bleach.place.region.west",
                            Integer.valueOf(3), "message.bleach.place.name.hokutan");
                    int way = chatLine(player, WAY_KEY, woke + 1);
                    helper.assertTrue(way >= 0, "the soul was never told the way to the Academy after where it woke: "
                            + chatKeys(player));
                    if (taken == 0) {
                        // From (-605, -105) to Hakutōmon at (-516, 0): 89 east and 105 south, 137.6 blocks.
                        assertKey(helper, chat.get(way), WAY_KEY, "message.bleach.place.gate.west",
                                "message.bleach.direction.south_east", Long.valueOf(150L));
                    } else {
                        assertLeads(helper, chat.get(way), WAY_KEY, 3, "message.bleach.place.gate.west");
                    }
                })
                .thenSucceed();
    }

    /**
     * A real Hollow that a purifying blow killed wakes as a soul outside Soul Society and is sent on to a district of the
     * Rukongai, as Konsō sends a soul, with that block as its home: told first that it was cleansed, then where it woke.
     * The player is never killed: its respawn's event is posted for it where it stands.
     */
    @GameTest(template = "empty", batch = "konso_move", timeoutTicks = 300)
    public static void aCleansedHollowWakesInTheRukongai(GameTestHelper helper) {
        ServerLevel soul = SiteTests.holdAcademy(helper);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(2.5, 1.0, 1.5));
        SoulData old = ShinigamiTests.soul(SoulState.HOLLOW, 40.0F);
        HollowBody.markPurifiedOnDeath(old);
        helper.assertTrue(old.hollow().purifiedOnDeath(), "a purifying blow did not mark the Hollow");
        // What the Hollow's death leaves: a Rukongai soul that knows it was cleansed.
        player.setData(SoulAttachments.SOUL, SoulLogic.afterDeath(old));
        helper.startSequence()
                .thenWaitUntil(() -> AcademyTests.assertAcademyStands(helper, soul))
                .thenExecute(() -> {
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
                    helper.assertTrue(player.level() == soul, "the cleansed soul is still in " + TravelTests.where(player));
                    helper.assertTrue(SoulLogic.get(player).state() == SoulState.RUKONGAI,
                            "the cleansed Hollow woke as a " + SoulLogic.get(player).state());
                    int district = Rukongai.district(player.getBlockX(), player.getBlockZ());
                    helper.assertTrue(district >= 1 && district <= SeireiteiPlan.DISTRICTS,
                            "the cleansed soul woke in ring " + district + ", at " + TravelTests.where(player));
                    Optional<BlockPos> home = player.getData(SoulAttachments.TRAVEL).rukongaiHome();
                    helper.assertTrue(home.equals(Optional.of(player.blockPosition())),
                            "the cleansed soul's home is " + home + ", not the block it woke in, " + player.blockPosition());
                    int cleansed = chatLine(player, "message.bleach.hollow_player.cleansed", 0);
                    helper.assertTrue(cleansed >= 0 && chatLine(player, WOKE_KEY, cleansed + 1) >= 0,
                            "the cleansed soul was not told it was cleansed and then where it woke: " + chatKeys(player));
                })
                .thenSucceed();
    }

    /**
     * Konsō in Soul Society moves no one: a Plus freed in a district of the Rukongai stays and takes the block it stands
     * in as its home, and one freed in Seireitei stays with none. The offer says the soul will linger there, and
     * elsewhere says where the server sends souls.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void konsoInSoulSocietyMovesNoOne(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        // District 2 of the South on its ground, and Seireitei's lawn, each column's chunk loaded first.
        soul.getChunk(0, 35);
        soul.getChunk(6, 6);
        int top = soul.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 560);
        ServerPlayer rukongai = chainedPlus(helper, new Vec3(1.5, 1.0, 1.5));
        ServerPlayer seireitei = chainedPlus(helper, new Vec3(3.5, 1.0, 1.5));
        rukongai.teleportTo(soul, 0.5, top, 560.5, 0.0F, 0.0F);
        seireitei.teleportTo(soul, 100.5, SeireiteiPlan.PLATEAU_Y, 100.5, 0.0F, 0.0F);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    Vec3 inRukongai = rukongai.position();
                    Vec3 inSeireitei = seireitei.position();
                    helper.assertTrue(ShinigamiEvents.konso(null, rukongai, true)
                            && ShinigamiEvents.konso(null, seireitei, true), "Konsō did not take on a Plus in Soul Society");
                    helper.assertTrue(rukongai.level() == soul && rukongai.position().distanceTo(inRukongai) < 0.01,
                            "the soul freed in the Rukongai was moved from " + inRukongai + " to "
                                    + TravelTests.where(rukongai));
                    helper.assertTrue(seireitei.level() == soul && seireitei.position().distanceTo(inSeireitei) < 0.01,
                            "the soul freed in Seireitei was moved from " + inSeireitei + " to "
                                    + TravelTests.where(seireitei));
                    helper.assertTrue(SoulLogic.get(rukongai).state() == SoulState.RUKONGAI
                                    && SoulLogic.get(seireitei).state() == SoulState.RUKONGAI,
                            "Konsō in Soul Society left a " + SoulLogic.get(rukongai).state() + " and a "
                                    + SoulLogic.get(seireitei).state());
                    BlockPos stands = rukongai.blockPosition();
                    Optional<BlockPos> home = rukongai.getData(SoulAttachments.TRAVEL).rukongaiHome();
                    helper.assertTrue(stands.getX() == 0 && stands.getZ() == 560 && home.equals(Optional.of(stands)),
                            "the soul freed in the Rukongai at " + stands.toShortString() + " has its home at " + home);
                    helper.assertTrue(seireitei.getData(SoulAttachments.TRAVEL).rukongaiHome().isEmpty(),
                            "the soul freed in Seireitei has a home at "
                                    + seireitei.getData(SoulAttachments.TRAVEL).rukongaiHome());
                    helper.assertTrue(chatLine(rukongai, WOKE_KEY, 0) < 0 && chatLine(seireitei, WOKE_KEY, 0) < 0,
                            "a soul freed in Soul Society was told it woke there: " + chatKeys(rukongai) + ", "
                                    + chatKeys(seireitei));

                    String nowhere = "message.bleach.offer.konso.nowhere";
                    helper.assertTrue(RukongaiEvents.offerKey(BleachWorlds.SOUL_SOCIETY).equals(nowhere),
                            "the offer in Soul Society is " + RukongaiEvents.offerKey(BleachWorlds.SOUL_SOCIETY));
                    Map<KonsoTarget, String> offers = Map.of(KonsoTarget.RUKONGAI, "message.bleach.offer.konso.rukongai",
                            KonsoTarget.ACADEMY, "message.bleach.offer.konso", KonsoTarget.NOWHERE, nowhere);
                    KonsoTarget before = BleachConfig.get(BleachConfig.KONSO_SENDS_TO);
                    try {
                        for (KonsoTarget target : KonsoTarget.values()) {
                            BleachConfig.KONSO_SENDS_TO.set(target);
                            String key = RukongaiEvents.offerKey(Level.OVERWORLD);
                            helper.assertTrue(key.equals(offers.get(target)),
                                    "sending souls to " + target + ", the offer in the Overworld is " + key);
                        }
                    } finally {
                        BleachConfig.KONSO_SENDS_TO.set(before);
                    }
                })
                .thenSucceed();
    }

    // ---- Waking at home ----

    /**
     * Where the real respawn event wakes a soul with no spawn of its own, once the Academy's chunks have built it: a
     * Rukongai soul at its home, on the ground there and facing Seireitei; one with none at a home drawn as it dies,
     * which it keeps; a Shinigami at the Senkaimon court, facing south; and a living Substitute where vanilla puts it.
     * The player is never killed.
     */
    @GameTest(template = "empty", batch = "konso_move", timeoutTicks = 300)
    public static void respawnWakesAtHome(GameTestHelper helper) {
        ServerLevel soul = SiteTests.holdAcademy(helper);
        TravelTests.holdCourt(helper);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        // The first of seed 5's Hokutan columns whose ground holds a soul, made now, so the first respawn makes none.
        BlockPos found = null;
        for (int i = 0; i < HOKUTAN.length && found == null; i++) {
            if (RukongaiEvents.groundAt(player, soul, HOKUTAN[i][0], HOKUTAN[i][1]) != null) {
                found = new BlockPos(HOKUTAN[i][0], SeireiteiPlan.PLATEAU_Y, HOKUTAN[i][1]);
            }
        }
        helper.assertTrue(found != null, "none of Hokutan's four columns will hold a soul");
        BlockPos hokutan = found;
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        player.setData(SoulAttachments.TRAVEL, TravelRecord.NONE.withRukongaiHome(hokutan));
        helper.startSequence()
                .thenWaitUntil(() -> AcademyTests.assertAcademyStands(helper, soul))
                .thenExecute(() -> {
                    DimensionTransition spawn = new DimensionTransition(helper.getLevel(), player.position(), Vec3.ZERO,
                            0.0F, 0.0F, DimensionTransition.DO_NOTHING);
                    DimensionTransition home = AcademyTests.respawn(player, spawn, false);
                    Vec3 at = home.pos();
                    int top = soul.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hokutan.getX(), hokutan.getZ());
                    helper.assertTrue(home.newLevel() == soul && Math.abs(at.x - (hokutan.getX() + 0.5)) < 0.01
                                    && Math.abs(at.z - (hokutan.getZ() + 0.5)) < 0.01 && Math.abs(at.y - top) <= 1.0,
                            "the soul at home in Hokutan wakes in " + home.newLevel().dimension().location() + " at " + at
                                    + ", not on its home's ground at y " + top);
                    // Read against the way to the centre column, not against the yaw's own formula.
                    double along = Vec3.directionFromRotation(0.0F, home.yRot())
                            .dot(new Vec3(-at.x, 0.0, -at.z).normalize());
                    helper.assertTrue(along > 0.99, "the soul at home faces " + home.yRot() + ", not Seireitei's way ("
                            + along + " along it)");

                    player.setData(SoulAttachments.TRAVEL, TravelRecord.NONE);
                    DimensionTransition drawn = AcademyTests.respawn(player, spawn, false);
                    BlockPos block = BlockPos.containing(drawn.pos());
                    int district = Rukongai.district(block.getX(), block.getZ());
                    helper.assertTrue(drawn.newLevel() == soul && district >= 1 && district <= SeireiteiPlan.DISTRICTS,
                            "the soul with no home wakes in " + drawn.newLevel().dimension().location() + " at "
                                    + drawn.pos() + ", in ring " + district);
                    // Saved on the soul as it died, for the respawn to carry over.
                    Optional<BlockPos> kept = player.getData(SoulAttachments.TRAVEL).rukongaiHome();
                    helper.assertTrue(kept.equals(Optional.of(block)),
                            "the soul's new home is " + kept + ", not the block it wakes in, " + block);

                    player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F));
                    Vec3 lawn = TravelEvents.landing(player, soul, TravelLogic.COURT);
                    DimensionTransition court = AcademyTests.respawn(player, spawn, false);
                    helper.assertTrue(lawn != null && court.newLevel() == soul && court.pos().distanceTo(lawn) < 0.01
                                    && court.yRot() == 0.0F,
                            "the Shinigami wakes in " + court.newLevel().dimension().location() + " at " + court.pos()
                                    + " facing " + court.yRot() + ", not on the court's lawn at " + lawn);

                    player.setData(SoulAttachments.SOUL, ShinigamiTests.substitute(60.0F));
                    helper.assertTrue(AcademyTests.respawn(player, spawn, false) == spawn,
                            "a living Substitute was moved to Soul Society");
                })
                .thenSucceed();
    }

    /**
     * A soul killed in the Overworld wakes, through the server's own respawn, at its Rukongai home: a new player in the
     * dead one's place, on the home's ground, told so once and set down nowhere else.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aSoulKilledInTheOverworldWakesAtItsHome(GameTestHelper helper) {
        // District 1 of the South Rukongai, outside the south gate: held first, so the respawn makes no ground.
        BlockPos home = new BlockPos(0, SeireiteiPlan.PLATEAU_Y, 530);
        ServerLevel soul = SiteTests.soul(helper);
        hold(soul, new ChunkPos(home));
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        player.setData(SoulAttachments.TRAVEL, TravelRecord.NONE.withRukongaiHome(home));
        ServerPlayer[] woken = new ServerPlayer[1];
        helper.startSequence()
                .thenExecute(() -> {
                    player.kill();
                    helper.assertTrue(player.isDeadOrDying(), "the soul did not die");
                    try {
                        player.connection.handleClientCommand(
                                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    } catch (RuntimeException e) {
                        // A respawn that throws once the new player is listed leaves the connection naming the dead
                        // one; handed on as the respawn would have, so TestPlayers' Leaver still takes the new one off.
                        ServerPlayer listed = soul.getServer().getPlayerList().getPlayer(player.getUUID());
                        if (listed != null && listed != player) {
                            player.connection.player = listed;
                        }
                        throw e;
                    }
                    ServerPlayer now = soul.getServer().getPlayerList().getPlayer(player.getUUID());
                    helper.assertTrue(now != null && now != player && player.isRemoved(),
                            "no new player woke in the dead one's place");
                    woken[0] = now;
                    // TestPlayers' Leaver holds the dead player; its connection, now the woken one's, is what it closes.
                    helper.assertTrue(player.connection == now.connection && now.connection.player == now
                            && !player.hasDisconnected(), "the woken player does not have the dead one's connection");
                    int top = soul.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getX(), home.getZ());
                    helper.assertTrue(now.level() == soul && Math.abs(now.getX() - 0.5) < 0.01
                                    && Math.abs(now.getZ() - 530.5) < 0.01 && Math.abs(now.getY() - top) <= 1.0,
                            "the soul woke at " + TravelTests.where(now) + ", not on its home's ground at y " + top);
                    helper.assertTrue(now.getData(SoulAttachments.TRAVEL).rukongaiHome().equals(Optional.of(home)),
                            "the home was not kept through death: " + now.getData(SoulAttachments.TRAVEL));
                })
                // Told once in chat a tick after waking, and the district on the action bar at its first check.
                .thenWaitUntil(() -> helper.assertTrue(told(woken[0], "message.bleach.respawn.rukongai") == 1
                                && TravelTests.lines(woken[0], "message.bleach.place.district") >= 1,
                        "the woken soul was told " + chatKeys(woken[0]) + " and " + TravelTests.said(woken[0])))
                .thenExecute(() -> {
                    ServerPlayer now = woken[0];
                    helper.assertTrue(Math.abs(now.getX() - 0.5) < 0.01 && Math.abs(now.getZ() - 530.5) < 0.01,
                            "the woken soul was moved again, to " + TravelTests.where(now));
                    helper.assertTrue(told(now, "message.bleach.konso.rukongai") == 0
                                    && told(now, "message.bleach.konso.arrived") == 0
                                    && told(now, "message.bleach.respawn.academy") == 0,
                            "the woken soul was set down twice: " + chatKeys(now));
                })
                .thenSucceed();
    }

    // ---- Where the chain holds ----

    /**
     * A Plus's chain wears only in the Human World and the Dangai, and no Plus rises from a villager anywhere else. A
     * real Plus on Seireitei's lawn, its ground held, keeps its chain as it was, unless it gnaws at it, and back in the
     * Overworld its chain wears again.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theChainHoldsOutsideTheHumanWorld(GameTestHelper helper) {
        for (ResourceKey<Level> world : List.of(Level.OVERWORLD, BleachWorlds.DANGAI)) {
            helper.assertTrue(SoulLogic.chainWearsIn(world), "a Plus's chain holds in " + world.location());
        }
        for (ResourceKey<Level> world : List.of(BleachWorlds.SOUL_SOCIETY, Level.NETHER, Level.END,
                BleachWorlds.HUECO_MUNDO)) {
            helper.assertFalse(SoulLogic.chainWearsIn(world), "a Plus's chain wears in " + world.location());
        }

        ServerLevel soul = SiteTests.soul(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the test server has no Nether");
        // Made in each world but never added to it, so there is nothing to take away.
        for (ServerLevel elsewhere : List.of(nether, soul)) {
            helper.assertTrue(PlusEvents.spawnVillagerPlus(elsewhere, EntityType.VILLAGER.create(elsewhere)) == null,
                    "a villager dead in " + elsewhere.dimension().location() + " left a Plus");
        }
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        Vec3 fell = helper.absoluteVec(new Vec3(3.5, 1.0, 1.5));
        villager.moveTo(fell.x, fell.y, fell.z, 0.0F, 0.0F);
        PlusSpirit left = PlusEvents.spawnVillagerPlus(helper.getLevel(), villager);
        if (left != null) {
            left.discard();
        }
        helper.assertTrue(left != null, "a villager dead in the Overworld left no Plus");

        // Seireitei's lawn at radius 141, grass at 71 and air above, away from the Academy, the court and their NPCs.
        hold(soul, new ChunkPos(new BlockPos(-100, SeireiteiPlan.PLATEAU_Y, 100)));
        ServerPlayer player = chainedPlus(helper, new Vec3(1.5, 1.0, 1.5));
        long[] chain = {0L};
        int[] from = {0};
        helper.startSequence()
                .thenExecute(() -> {
                    player.teleportTo(soul, -99.5, SeireiteiPlan.PLATEAU_Y, 100.5, 0.0F, 0.0F);
                    chain[0] = SoulLogic.get(player).chainTicks();
                    from[0] = player.tickCount;
                })
                .thenWaitUntil(() -> helper.assertTrue(player.tickCount - from[0] >= 40,
                        "the Plus has ticked " + (player.tickCount - from[0]) + " times in Soul Society"))
                .thenExecute(() -> {
                    SoulData data = SoulLogic.get(player);
                    helper.assertTrue(player.level() == soul && data.chainTicks() == chain[0],
                            "in " + TravelTests.where(player) + " the chain went from " + chain[0] + " to "
                                    + data.chainTicks());
                    // The positive control: gnawing at it, the Plus wears its chain in any world.
                    DevourLogic.Gnaw gnaw = DevourEvents.startGnaw(player, data, soul.getGameTime());
                    helper.assertTrue(gnaw == DevourLogic.Gnaw.GNAWING, "the Plus could not gnaw: " + gnaw);
                    chain[0] = data.chainTicks();
                    from[0] = player.tickCount;
                })
                .thenWaitUntil(() -> helper.assertTrue(player.tickCount - from[0] >= 40,
                        "the gnawing Plus has ticked " + (player.tickCount - from[0]) + " times in Soul Society"))
                .thenExecute(() -> {
                    SoulData data = SoulLogic.get(player);
                    // 40 a tick, halved were a Shinigami near, for 40 ticks, less one for where in a tick each read falls.
                    helper.assertTrue(chain[0] - data.chainTicks() >= 780, "gnawing in Soul Society wore the chain from "
                            + chain[0] + " to " + data.chainTicks());
                    DevourEvents.stopGnaw(data);
                    Vec3 back = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
                    player.teleportTo(helper.getLevel(), back.x, back.y, back.z, 0.0F, 0.0F);
                    chain[0] = data.chainTicks();
                    from[0] = player.tickCount;
                })
                .thenWaitUntil(() -> helper.assertTrue(player.tickCount - from[0] >= 40,
                        "the Plus has ticked " + (player.tickCount - from[0]) + " times back in the Overworld"))
                .thenExecute(() -> {
                    SoulData data = SoulLogic.get(player);
                    // One a tick, halved were a Shinigami near, less one.
                    helper.assertTrue(player.level() == helper.getLevel() && chain[0] - data.chainTicks() >= 19,
                            "back in " + TravelTests.where(player) + " the chain went from " + chain[0] + " to "
                                    + data.chainTicks());
                })
                .thenSucceed();
    }
}
