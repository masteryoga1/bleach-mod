package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Menos;
import io.github.masteryoga1.bleach.entity.VastoLorde;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.MenosEvolution;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritClip;
import io.github.masteryoga1.bleach.soul.VastoLordeComing;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for where a Vasto Lorde comes by itself, and how many: the souls far out that draw one, and how often it looks;
 * on Hueco Mundo's dunes past its radius and never in the Forest of Menos, in chunks whose creatures tick, unseen by
 * every soul, reading no chunk it would load or wait on; at most max_alive by its own count; and the roll that brings
 * one on its chance. Each in Hueco Mundo holds chunks of its own far out, made before it asks, and leaves no Vasto Lorde
 * behind it; the count's test, which counts every one there, runs in a batch of its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class VastoLordeComingTests {
    /** T2's centre, held 3 about, so chunks 62 to 64 by -1 to 1 tick; and T4's, held 2 about, so only it ticks. */
    private static final ChunkPos WHERE = new ChunkPos(63, 0);
    /** T3's, 8 chunks south and held 3 about: x 992 to 1039 and z 112 to 159 tick. */
    private static final ChunkPos SIGHT = new ChunkPos(63, 8);
    /** T3's chunk held but still being made: 32 chunks from every other hold, so none about it is made either. */
    private static final ChunkPos UNMADE = new ChunkPos(67, 40);
    /** T6's, 8 chunks north and held 3 about: x 992 to 1039 and z -144 to -97 tick. */
    private static final ChunkPos ROLL = new ChunkPos(63, -8);
    /** T2's looks are about the north edge of the chunks whose creatures tick, so a fifth of each band lies in them. */
    private static final Vec3 CORNER = new Vec3(1_000.5, 100.0, -15.5);
    /** T3's are about their east edge, so much of each band lies where creatures do not tick, in chunks loaded or not. */
    private static final Vec3 EAST_EDGE = new Vec3(1_039.5, 100.0, 135.5);
    /** Head turns that face west, toward lower x, and east. */
    private static final float WEST = 90.0F, EAST = -90.0F;

    private VastoLordeComingTests() {
    }

    // ---- T1. The souls that draw one ----

    /**
     * A soul draws one when alive, playing by survival's rules, seeing spirits, at least min_radius out and above the
     * Forest of Menos, a Hollow and an Arrancar as well; a look falls once every look_seconds; and the defaults of both.
     * Its souls are in no world.
     */
    @GameTest(template = "empty")
    public static void aSoulFarOutOnTheDunesDrawsOne(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.VASTO_LORDE_LOOK_SECONDS.getDefault() == 60
                && BleachConfig.VASTO_LORDE_CHANCE.getDefault() == 0.1, "the look's config defaults changed");
        helper.assertTrue(VastoLordeComing.lookNow(0, 60) && VastoLordeComing.lookNow(1_200, 60)
                        && !VastoLordeComing.lookNow(1, 60) && !VastoLordeComing.lookNow(1_199, 60)
                        && !VastoLordeComing.lookNow(600, 60) && VastoLordeComing.lookNow(200, 10),
                "a look does not fall once every look_seconds");
        for (SoulState state : List.of(SoulState.SHINIGAMI, SoulState.HOLLOW, SoulState.ARRANCAR)) {
            helper.assertTrue(VastoLordeComing.draws(soul(helper, GameType.SURVIVAL, state, 1_000.5, 100.0, 0.5))
                            && VastoLordeComing.draws(soul(helper, GameType.SURVIVAL, state, 1_000.5, 80.0, 0.5)),
                    "a soul in the state " + state + " far out on the dunes drew none");
        }
        Player dead = soul(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 1_000.5, 100.0, 0.5);
        dead.setHealth(0.0F);
        Map.of("at r 999.5", soul(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 999.5, 100.0, 0.5),
                "in the Forest of Menos at y 79", soul(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 1_000.5, 79.0, 0.5),
                "in creative", soul(helper, GameType.CREATIVE, SoulState.SHINIGAMI, 1_000.5, 100.0, 0.5),
                "in spectator", soul(helper, GameType.SPECTATOR, SoulState.SHINIGAMI, 1_000.5, 100.0, 0.5),
                "who sees no spirits", soul(helper, GameType.SURVIVAL, SoulState.HUMAN, 1_000.5, 100.0, 0.5),
                "who is dead", dead)
                .forEach((which, soul) -> helper.assertFalse(VastoLordeComing.draws(soul), "a soul " + which + " drew one"));
        helper.succeed();
    }

    // ---- T2. Where one comes ----

    /**
     * One comes only on the dunes' top at least min_radius from the centre, 1,000 by default, and 2 at most by default:
     * not on sand at r 999.5 until min_radius is 999, not on the Forest of Menos's floor under sand where one comes, not
     * floating over that sand or inside it, and not on sand in a chunk loaded whose creatures do not tick. A look brings
     * one onto a column's top, far out, where creatures tick, 24 to 48 across from the point it looks about.
     */
    @GameTest(template = "empty", batch = "vasto_lorde_where", timeoutTicks = 900)
    public static void oneComesOnlyOnTheDunesPastItsRadius(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.VASTO_LORDE_MIN_RADIUS.getDefault() == 1_000
                && BleachConfig.VASTO_LORDE_MAX_ALIVE.getDefault() == 2, "the coming's config defaults changed");
        helper.assertTrue(!VastoLordeComing.farOut(999, 0) && VastoLordeComing.farOut(1_000, 0)
                        && !VastoLordeComing.farOut(706, 706) && VastoLordeComing.farOut(707, 707),
                "far out is not r 1,000 about the centre");
        KidoTests.withConfig(BleachConfig.VASTO_LORDE_MIN_RADIUS, 1_100, () -> helper.assertFalse(
                VastoLordeComing.farOut(1_000, 0), "r 1,000.5 is far out with min_radius 1,100"));
        ServerLevel hueco = LasNochesTests.hueco(helper);
        LasNochesHallsTests.Hold hold = held(helper, hueco, WHERE, 3);
        helper.startSequence()
                .thenWaitUntil(() -> ready(helper, hueco, WHERE, 3))
                .thenExecute(() -> open(hueco, 16, () -> {
                    BlockPos inside = sand(helper, hueco, 999, -16, spot -> true);
                    helper.assertFalse(came(at(hueco, inside)), "one came at r " + HuecoMundoPlan.radius(999, inside.getZ()));
                    KidoTests.withConfig(BleachConfig.VASTO_LORDE_MIN_RADIUS, 999, () -> helper.assertTrue(
                            came(at(hueco, inside)), "none came there at min_radius 999"));
                    BlockPos over = sand(helper, hueco, 1_004, -16, spot -> forestFloor(hueco, spot));
                    BlockPos floor = over.atY(HuecoMundoPlan.CAVERN_FLOOR_Y);
                    helper.assertFalse(came(at(hueco, floor)), "one came on the forest's floor at " + floor.toShortString());
                    helper.assertFalse(came(at(hueco, over.above(3))), "one came floating over the sand");
                    helper.assertFalse(came(at(hueco, over.below())), "one came inside the sand");
                    helper.assertTrue(came(at(hueco, over)), "none came on the sand over it at " + over.toShortString());
                    BlockPos idle = sand(helper, hueco, 1_044, -16, spot -> true);
                    helper.assertFalse(came(at(hueco, idle)), "one came where no creature ticks: " + idle.toShortString());
                    int bodies = 0;
                    for (int i = 0; i < 8; i++) {
                        VastoLorde made = kept(hold, VastoLordeComing.comeNear(hueco, hueco.getRandom(), CORNER, List.of()));
                        if (made != null) {
                            bodies++;
                            BlockPos cell = made.blockPosition();
                            helper.assertTrue(cell.getY() == hueco.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                            cell.getX(), cell.getZ()) && VastoLordeComing.farOut(cell.getX(), cell.getZ())
                                            && hueco.isPositionEntityTicking(cell) && inBand(made, CORNER),
                                    "a look brought one off the dunes' top or out of its band: " + made.position());
                            made.discard();
                        }
                    }
                    helper.assertTrue(bodies >= 4, "only " + bodies + " of 8 looks brought one");
                }))
                .thenSucceed();
    }

    // ---- T3. Unseen, and no chunk waited on ----

    /**
     * One comes only unseen: not for a soul 30 to its side in open air facing it, nor for one whose eyes are 30 straight
     * over it, as it is behind neither; but for that soul turned away, or facing it with its eyes 20 under the ground; and
     * never within 24 of a soul, though behind it. Looks about the east edge of the chunks whose creatures tick bring one
     * only in them and load none of the chunks past those loaded. The sight walk counts a chunk held but still being made
     * as hiding what lies past it, and neither makes it nor waits on it.
     */
    @GameTest(template = "empty", batch = "vasto_lorde_where", timeoutTicks = 900)
    public static void oneComesOnlyUnseenAndWaitsOnNoChunk(GameTestHelper helper) {
        ServerLevel hueco = LasNochesTests.hueco(helper);
        LasNochesHallsTests.Hold hold = held(helper, hueco, SIGHT, 3);
        List<ChunkPos> beyond = IntStream.rangeClosed(SIGHT.z - 3, SIGHT.z + 3).mapToObj(z -> new ChunkPos(SIGHT.x + 4, z))
                .toList();
        helper.startSequence()
                .thenWaitUntil(() -> ready(helper, hueco, SIGHT, 3))
                .thenExecute(() -> open(hueco, 16, () -> {
                    BlockPos spot = sand(helper, hueco, 1_020, 112, top -> inSight(hueco, beside(top), top));
                    Vec3 feet = Vec3.atBottomCenterOf(spot), eyes = beside(spot);
                    Vec3 buried = new Vec3(eyes.x, hueco.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            spot.getX() + 30, spot.getZ()) - 20.0, eyes.z);
                    helper.assertFalse(came(at(hueco, spot, soul(helper, eyes, WEST))),
                            "one came in the sight of a soul 30 to its side facing it, at " + spot.toShortString());
                    helper.assertTrue(came(at(hueco, spot, soul(helper, eyes, EAST))), "none came behind a soul turned away");
                    helper.assertTrue(came(at(hueco, spot, soul(helper, buried, WEST))),
                            "none came hidden from a soul facing it with its eyes under the ground");
                    helper.assertFalse(came(at(hueco, spot, soul(helper, feet.add(10.0, 10.0, 0.0), EAST))),
                            "one came behind a soul within 24 of it");
                    helper.assertFalse(came(at(hueco, spot, soul(helper, feet.add(0.0, 30.0, 0.0), WEST))),
                            "one came under the open eyes of a soul 30 over it");
                    helper.assertTrue(beyond.stream().noneMatch(pos -> hueco.hasChunk(pos.x, pos.z)),
                            "the chunks past those held east of T3's centre were loaded before its looks");
                    for (int i = 0; i < 12; i++) {
                        VastoLorde made = kept(hold, VastoLordeComing.comeNear(hueco, hueco.getRandom(), EAST_EDGE,
                                List.of()));
                        helper.assertTrue(made == null || hueco.isPositionEntityTicking(made.blockPosition()),
                                "a look brought one where creatures do not tick");
                        TravelTests.discard(made);
                    }
                    helper.assertTrue(beyond.stream().noneMatch(pos -> hueco.hasChunk(pos.x, pos.z)),
                            "a look loaded a chunk past those held east of T3's centre");
                }))
                .thenExecute(() -> {
                    hueco.getChunkSource().addRegionTicket(TicketType.FORCED, UNMADE, 0, UNMADE);
                    try {
                        hueco.getChunkSource().pollTask();
                        helper.assertTrue(hueco.hasChunk(UNMADE.x, UNMADE.z) && unmade(hueco),
                                "the chunk held at " + UNMADE + " is not still being made");
                        Vec3 eyes = new Vec3(UNMADE.getMinBlockX() + 12.5, 200.0, UNMADE.getMinBlockZ() + 7.5);
                        helper.assertTrue(VastoLordeComing.unseen(hueco, eyes.add(-11.0, 0.0, 0.0), List.of(soul(helper, eyes,
                                WEST))), "a sight line through a chunk still being made was clear");
                        helper.assertTrue(unmade(hueco), "the sight walk made the chunk it would have waited on");
                    } finally {
                        hueco.getChunkSource().removeRegionTicket(TicketType.FORCED, UNMADE, 0, UNMADE);
                    }
                })
                .thenSucceed();
    }

    // ---- T4. How many ----

    /**
     * At most max_alive come, by their own count in Hueco Mundo alone: two come and a third waits until one is gone; the
     * Menos count and the players' Vasto Lorde stay as they were, one in the Overworld counts for none and none may come
     * there, and the players' cap at 0 keeps none away; none comes with max_alive 0, in Peaceful or with mob spawning
     * off. Past 2 and in those three, neither a look nor a forced roll brings one either, to a soul that draws one some 29
     * to 45 from every column whose creatures tick. It counts every Vasto Lorde in Hueco Mundo, so it runs alone and
     * clears any first.
     */
    @GameTest(template = "empty", batch = "vasto_lorde_count", timeoutTicks = 900)
    public static void atMostTwoComeApartFromTheMenosAndThePlayers(GameTestHelper helper) {
        ServerLevel hueco = LasNochesTests.hueco(helper);
        MinecraftServer server = hueco.getServer();
        LasNochesHallsTests.Hold hold = held(helper, hueco, WHERE, 2);
        Player drawer = soul(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 1_052.5, 100.0, 8.5);
        drawer.setYHeadRot(EAST);
        helper.startSequence()
                .thenWaitUntil(() -> ready(helper, hueco, WHERE, 2))
                .thenExecute(() -> open(hueco, 2, () -> {
                    helper.assertTrue(VastoLordeComing.draws(drawer), "T4's soul far out draws none");
                    hueco.getEntities(ModEntities.VASTO_LORDE.get(), Entity::isAlive).forEach(Entity::discard);
                    int menos = Menos.aliveIn(hueco), players = MenosEvolution.VastoLordes.get(server).count();
                    BlockPos one = sand(helper, hueco, 1_010, 0, spot -> true);
                    BlockPos two = sand(helper, hueco, 1_015, 0, spot -> true);
                    BlockPos three = sand(helper, hueco, 1_020, 0, spot -> true);
                    VastoLorde first = kept(hold, at(hueco, one));
                    VastoLorde second = kept(hold, at(hueco, two));
                    helper.assertTrue(first != null && second != null && VastoLorde.aliveIn(hueco) == 2,
                            "two did not come with max_alive 2");
                    helper.assertFalse(came(at(hueco, three)), "a third came past 2");
                    refused(helper, hueco, drawer, "past 2");
                    int lordes = MenosEvolution.VastoLordes.get(server).count();
                    helper.assertTrue(Menos.aliveIn(hueco) == menos && lordes == players,
                            "the two counted as Menos or as players' Vasto Lorde");
                    VastoLorde abroad = helper.spawnWithNoFreeWill(ModEntities.VASTO_LORDE.get(), 1, 2, 1);
                    try {
                        helper.assertTrue(VastoLorde.aliveIn(hueco) == 2 && !VastoLordeComing.mayCome(helper.getLevel()),
                                "one in the Overworld counted in Hueco Mundo, or one may come in the Overworld");
                    } finally {
                        abroad.discard();
                    }
                    first.discard();
                    VastoLorde third = kept(hold, at(hueco, three));
                    helper.assertTrue(third != null && VastoLorde.aliveIn(hueco) == 2, "none came once one was gone");
                    TravelTests.discard(second, third);
                    KidoTests.withConfig(BleachConfig.VASTO_MAX, 0, () -> helper.assertTrue(
                            came(at(hueco, one)), "the players' cap of 0 kept one away"));
                    KidoTests.withConfig(BleachConfig.VASTO_LORDE_MAX_ALIVE, 0, () -> {
                        helper.assertFalse(came(at(hueco, one)), "one came with max_alive 0");
                        refused(helper, hueco, drawer, "with max_alive 0");
                    });
                    server.setDifficulty(Difficulty.PEACEFUL, true);
                    helper.assertFalse(came(at(hueco, one)), "one came in Peaceful");
                    refused(helper, hueco, drawer, "in Peaceful");
                    server.setDifficulty(Difficulty.NORMAL, true);
                    hueco.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    helper.assertFalse(came(at(hueco, one)), "one came with mob spawning off");
                    refused(helper, hueco, drawer, "with mob spawning off");
                }))
                .thenSucceed();
    }

    // ---- T6. The roll ----

    /**
     * A forced roll brings one only for a soul that draws one, 24 to 48 from it and unseen by every soul there, and a
     * drawn roll only under its chance. Its souls are in no world: the one it comes to buried in the sand, so its eyes
     * see nothing, a creative one 100 off, buried too, and two who see no spirits, high over the first and back to back,
     * so that no column is behind both. While both watch, none comes but where a quartz tree hides it from them; with the
     * one facing east gone, it comes behind the other.
     */
    @GameTest(template = "empty", batch = "vasto_lorde_where", timeoutTicks = 900)
    public static void aRollBringsOneNearASoulFarOutOnItsChance(GameTestHelper helper) {
        ServerLevel hueco = LasNochesTests.hueco(helper);
        LasNochesHallsTests.Hold hold = held(helper, hueco, ROLL, 3);
        Player drawer = soul(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 1_000.5, 84.0, -143.5);
        Player creative = soul(helper, GameType.CREATIVE, SoulState.SHINIGAMI, 1_000.5, 84.0, -43.5);
        // On a cell's edge, so every column lies east or west of them; low enough that all the band is in their sight.
        Vec3 over = new Vec3(1_000.0, 236.0, -143.5);
        Player east = soul(helper, over, EAST), west = soul(helper, over, WEST);
        helper.startSequence()
                .thenWaitUntil(() -> ready(helper, hueco, ROLL, 3))
                .thenExecute(() -> open(hueco, 16, () -> {
                    // Alone: the creative one's band reaches no ground that ticks, so beside it a roll that drew any
                    // soul would get by one run in eight.
                    helper.assertFalse(came(rolled(hueco, null, List.of(west))),
                            "a forced roll came to a soul who sees no spirits");
                    // Its creatures tick to z -97, so one 24 to 48 from the first stands 53 or more from the other.
                    VastoLorde near = kept(hold, rolled(hueco, null, List.of(creative, drawer)));
                    helper.assertTrue(near != null && inBand(near, drawer.position())
                                    && Math.hypot(near.getX() - creative.getX(), near.getZ() - creative.getZ()) > 50.0,
                            "a forced roll brought none 24 to 48 from the soul that drew one, away from the other");
                    TravelTests.discard(near);
                    VastoLorde watched = kept(hold, VastoLordeComing.roll(hueco, hueco.getRandom(), true,
                            List.of(drawer, east, west)));
                    helper.assertTrue(watched == null || !inSight(hueco, west.getEyePosition(), watched.blockPosition()),
                            "a forced roll brought one in sight of two back to back over the soul that drew it");
                    TravelTests.discard(watched);
                    VastoLorde behind = kept(hold, rolled(hueco, null, List.of(drawer, west)));
                    helper.assertTrue(behind != null && (behind.getX() > west.getX()
                                    || !inSight(hueco, west.getEyePosition(), behind.blockPosition())),
                            "a forced roll brought none behind the one left of the two over the soul that drew it");
                    TravelTests.discard(behind);
                    KidoTests.withConfig(BleachConfig.VASTO_LORDE_CHANCE, 0.5, () -> {
                        helper.assertFalse(came(rolled(hueco, 0.5, List.of(drawer))), "one came on a draw at its chance");
                        helper.assertTrue(came(rolled(hueco, Math.nextDown(0.5), List.of(drawer))),
                                "none came on a draw under its chance");
                    });
                }))
                .thenSucceed();
    }

    // ---- Helpers ----

    /**
     * Holds the chunks within this many of this centre far out in Hueco Mundo for the test, made now, and discards what
     * it kept as the test ends. A radius of 3 ticks the creatures of the 3 by 3 about the centre, and 2 only the centre's.
     */
    static LasNochesHallsTests.Hold held(GameTestHelper helper, ServerLevel hueco, ChunkPos centre, int radius) {
        return LasNochesHallsTests.Hold.hold(helper, hueco, List.of(centre), radius, List.of());
    }

    /** Waits until every chunk held about this centre is made, and those whose creatures tick have them loaded. */
    static void ready(GameTestHelper helper, ServerLevel hueco, ChunkPos centre, int radius) {
        GateTests.patiently(helper, ChunkPos.rangeClosed(centre, radius)
                        .allMatch(pos -> hueco.getChunkSource().getChunkNow(pos.x, pos.z) != null)
                        && ChunkPos.rangeClosed(centre, radius - 2).allMatch(pos -> hueco.areEntitiesLoaded(pos.toLong())
                        && hueco.isPositionEntityTicking(pos.getWorldPosition())),
                "the chunks about " + centre + " never loaded with their creatures ticking");
    }

    /**
     * Runs this with Hueco Mundo on Normal, mob spawning on, as the test server has it off, and this many Vasto Lorde
     * allowed, and puts all three back.
     */
    static void open(ServerLevel hueco, int most, Runnable body) {
        MinecraftServer server = hueco.getServer();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        GameRules.BooleanValue spawning = hueco.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean wasSpawning = spawning.get();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            spawning.set(true, server);
            KidoTests.withConfig(BleachConfig.VASTO_LORDE_MAX_ALIVE, most, body);
        } finally {
            spawning.set(wasSpawning, server);
            server.setDifficulty(difficulty, true);
        }
    }

    /**
     * The spot on the first of the 16 columns at this x from this z on that is topped with white sand under 40 blocks of
     * open sky, and that this finds fit.
     */
    static BlockPos sand(GameTestHelper helper, ServerLevel hueco, int x, int fromZ, Predicate<BlockPos> fit) {
        for (int z = fromZ; z < fromZ + 16; z++) {
            BlockPos spot = new BlockPos(x, hueco.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (hueco.getBlockState(spot.below()).is(ModBlocks.WHITE_SAND.get())
                    && IntStream.range(0, 40).allMatch(up -> hueco.getBlockState(spot.above(up)).isAir()) && fit.test(spot)) {
                return spot;
            }
        }
        helper.fail("no column at x " + x + " from z " + fromZ + " is topped with white sand as the test needs");
        return BlockPos.ZERO;
    }

    /** Whether the Forest of Menos's floor under this column is open, with white sandstone under it. */
    private static boolean forestFloor(ServerLevel hueco, BlockPos column) {
        BlockPos floor = column.atY(HuecoMundoPlan.CAVERN_FLOOR_Y);
        return hueco.getBlockState(floor).isAir() && hueco.getBlockState(floor.above()).isAir()
                && hueco.getBlockState(floor.below()).is(ModBlocks.WHITE_SANDSTONE.get());
    }

    /** The eyes high over the dunes 30 east of this spot that T3's soul beside it looks from. */
    private static Vec3 beside(BlockPos spot) {
        return new Vec3(spot.getX() + 30.5, 200.0, spot.getZ() + 0.5);
    }

    /** Whether nothing blocks the sight lines from these eyes to the head and the chest of one standing here. */
    private static boolean inSight(ServerLevel hueco, Vec3 eyes, BlockPos spot) {
        return DoubleStream.of(2.0, 0.9).allMatch(up -> SpiritClip.clip(hueco, new ClipContext(eyes,
                Vec3.atBottomCenterOf(spot).add(0.0, up, 0.0), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty())).block().getType() == HitResult.Type.MISS);
    }

    /** A soul in no world with its eyes here and its head turned this way, as a mock player's never turns by itself. */
    private static Player soul(GameTestHelper helper, Vec3 eyes, float yaw) {
        Player soul = helper.makeMockPlayer(GameType.SURVIVAL);
        soul.moveTo(eyes.x, eyes.y - soul.getEyeHeight(), eyes.z, 0.0F, 0.0F);
        soul.setYHeadRot(yaw);
        return soul;
    }

    /** A soul in this state, by this mode's rules, standing here in no world, its head turned south: aware unless Human. */
    private static Player soul(GameTestHelper helper, GameType mode, SoulState state, double x, double y, double z) {
        Player soul = helper.makeMockPlayer(mode);
        soul.setData(SoulAttachments.SOUL, ShinigamiTests.soul(state, state == SoulState.HUMAN ? 0.0F : 100.0F));
        soul.moveTo(x, y, z, 0.0F, 0.0F);
        return soul;
    }

    /** Whether T3's held chunk is not yet made. */
    private static boolean unmade(ServerLevel hueco) {
        return hueco.getChunkSource().getChunkNow(UNMADE.x, UNMADE.z) == null;
    }

    /** One set down standing here, with these souls about, if it may come. */
    @Nullable
    private static VastoLorde at(ServerLevel hueco, BlockPos spot, Player... souls) {
        return VastoLordeComing.spawnAt(hueco, spot, List.of(souls));
    }

    /** Whether one came; one that did is discarded at once. */
    private static boolean came(@Nullable VastoLorde made) {
        TravelTests.discard(made);
        return made != null;
    }

    /** What came, kept for the hold to discard as the test ends, pass or fail, if the test has not by then. */
    @Nullable
    private static VastoLorde kept(LasNochesHallsTests.Hold hold, @Nullable VastoLorde made) {
        if (made != null) {
            hold.placed().add(made);
        }
        return made;
    }

    /** Whether this one stands 24 to 48 across from this point, as a look about it sets one down, give or take a cell. */
    private static boolean inBand(VastoLorde body, Vec3 about) {
        double across = Math.hypot(body.getX() - about.x, body.getZ() - about.z);
        return across > VastoLordeComing.NEAREST - 1.0 && across < VastoLordeComing.FURTHEST + 1.0;
    }

    /** Asserts that neither a look about this soul nor a roll forced for it alone brings one, three times, as this says. */
    private static void refused(GameTestHelper helper, ServerLevel hueco, Player drawer, String why) {
        for (int i = 0; i < 3; i++) {
            helper.assertFalse(came(VastoLordeComing.comeNear(hueco, hueco.getRandom(), drawer.position(),
                    List.of(drawer))), "a look brought one " + why);
            helper.assertFalse(came(VastoLordeComing.roll(hueco, hueco.getRandom(), true, List.of(drawer))),
                    "a forced roll brought one " + why);
        }
    }

    /**
     * A roll for these souls, forced if no draw is given, else with its chance drawn as this; tried three times, in case
     * every column of a look missed the ticking chunks.
     */
    @Nullable
    private static VastoLorde rolled(ServerLevel hueco, @Nullable Double draw, List<Player> souls) {
        RandomSource random = draw == null ? hueco.getRandom() : new LegacyRandomSource(0L) {
            @Override
            public double nextDouble() {
                return draw;
            }
        };
        VastoLorde made = null;
        for (int i = 0; made == null && i < 3; i++) {
            made = VastoLordeComing.roll(hueco, random, draw == null, souls);
        }
        return made;
    }
}
