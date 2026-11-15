package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.soul.DangaiLogic;
import io.github.masteryoga1.bleach.soul.FlashStep;
import io.github.masteryoga1.bleach.soul.ShakonmakuEvents;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritClip;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import io.github.masteryoga1.bleach.world.Shakonmaku.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Shakonmaku in the real Soul Society, in a batch of their own at places on the wall's ring far enough apart
 * that no test sees another's ripples, each letting go of every creature, boat and arrow it set down when it ends. Souls
 * move by move packets the server handles as a client's own, so only the server's rules are in the way.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ShakonmakuTests {
    private static final String BATCH = "shakonmaku";
    /** Room for the float steps of a position. */
    private static final double SAME = 1.0E-6;
    /** How far a test's ticket holds Soul Society's chunks: its own and the ring about it tick entities. */
    private static final int HOLD_DISTANCE = 3;

    private ShakonmakuTests() {
    }

    // ---- Helpers ----

    /**
     * Soul Society's chunks within 2 of this one, made now; this one and the ring about it are held entity-ticking until
     * the test ends.
     */
    private static Cleanup hold(GameTestHelper helper, int chunkX, int chunkZ) {
        ServerLevel soul = SiteTests.soul(helper);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                soul.getChunk(chunkX + x, chunkZ + z);
            }
        }
        ChunkPos centre = new ChunkPos(chunkX, chunkZ);
        soul.getChunkSource().addRegionTicket(TicketType.FORCED, centre, HOLD_DISTANCE, centre);
        Cleanup cleanup = new Cleanup(soul, centre);
        helper.testInfo.addListener(cleanup);
        return cleanup;
    }

    /** A Shinigami (who sees spirits) joined in this game mode and set down here in Soul Society, able to fly. */
    private static ServerPlayer soulAt(GameTestHelper helper, ServerLevel soul, GameType mode, Vec3 at) {
        ServerPlayer player = TestPlayers.join(helper, mode, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, 400.0F));
        player.getAbilities().mayfly = true;
        player.teleportTo(soul, at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** Moves the player here as its client would: a move packet, handled by the server as it handles every one. */
    private static void move(ServerPlayer player, Vec3 to) {
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(to.x, to.y, to.z, false));
    }

    /** Asserts the plan put each point on the side named, before a test leans on it. */
    private static void on(GameTestHelper helper, Side side, Vec3... points) {
        for (Vec3 point : points) {
            helper.assertTrue(Shakonmaku.side(point) == side, point + " is " + Shakonmaku.side(point) + ", not " + side);
        }
    }

    /** Asserts this entity stands at the place expected, to the float steps of a position. */
    private static void at(GameTestHelper helper, Entity entity, Vec3 expected, String what) {
        helper.assertTrue(entity.position().distanceToSqr(expected) < SAME, what + " is at " + entity.position()
                + ", not " + expected);
    }

    /** How many times this joined soul has been told the membrane turned it back. */
    private static long told(ServerPlayer player) {
        return TravelTests.lines(player, ShakonmakuEvents.TURNED_BACK_KEY);
    }

    /** How many ripples of the membrane this watched player has been sent since it was last asked. */
    private static long ripples(ServerPlayer watcher) {
        return TestPlayers.takeEffects(watcher).stream().filter(packet -> packet instanceof ClientboundLevelParticlesPacket
                spray && spray.getParticle().getType() == ParticleTypes.SCRAPE).count();
    }

    /** Lets a test's chunks go and takes away what it set down in Soul Society when it passes or fails, however it ends. */
    private static final class Cleanup implements GameTestListener {
        private final ServerLevel soul;
        private final ChunkPos centre;
        private final List<Entity> placed = new ArrayList<>();

        private Cleanup(ServerLevel soul, ChunkPos centre) {
            this.soul = soul;
            this.centre = centre;
        }

        /** Sets this down in Soul Society, to be taken away when the test ends. */
        <T extends Entity> T add(T entity) {
            placed.add(entity);
            soul.addFreshEntity(entity);
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

        /** Takes away what the test set down and lets its chunks go. */
        private void end() {
            TravelTests.discard(placed.toArray(new Entity[0]));
            soul.getChunkSource().removeRegionTicket(TicketType.FORCED, centre, HOLD_DISTANCE, centre);
        }
    }

    // ---- Bodies ----

    /**
     * Over the west gate at y 120, far above its gap: a Shinigami flying outward across the sphere is put back where it
     * flew from, told why and shown the ripple, after a step nearer is let be.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void aSoulHighOverAGateIsTurnedBack(GameTestHelper helper) {
        ServerLevel soul = hold(helper, -33, 0).soul;
        Vec3 from = new Vec3(-524.5, 120.0, 0.5), nearer = new Vec3(-525.4, 120.0, 0.5), out = new Vec3(-526.9, 120.0, 0.5);
        on(helper, Side.INSIDE, from, nearer);
        on(helper, Side.OUTSIDE, out);
        ServerPlayer shinigami = soulAt(helper, soul, GameType.SURVIVAL, from);
        TestPlayers.watchEffects(shinigami);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    move(shinigami, nearer);
                    at(helper, shinigami, nearer, "the Shinigami a step nearer");
                    ripples(shinigami);
                    move(shinigami, out);
                })
                .thenExecute(() -> {
                    at(helper, shinigami, nearer, "the Shinigami that flew out over the gate");
                    helper.assertTrue(told(shinigami) == 1 && ripples(shinigami) > 0, "the Shinigami was told "
                            + TravelTests.said(shinigami) + " and shown no ripple, or not told once");
                })
                .thenSucceed();
    }

    /**
     * Over the south gate at y 120, a soul steering a boat across the sphere goes back with it, still aboard, its fall
     * kept and told why, and so does a soul a friend in creative mode steers; a creative soul's own boat crosses with
     * the cow it carries, as do souls in creative and spectator mode flying.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void aMountGoesBackWithItsRider(GameTestHelper helper) {
        Cleanup held = hold(helper, 0, 32);
        ServerLevel soul = held.soul;
        Vec3 from = new Vec3(0.5, 120.0, 524.5), out = new Vec3(0.5, 119.0, 526.9), aside = new Vec3(3.0, 0.0, 0.0);
        Vec3[] rows = {Vec3.ZERO, aside, aside.scale(2.0), aside.scale(-1.0), aside.scale(-2.0)};
        for (Vec3 row : rows) {
            on(helper, Side.INSIDE, from.add(row));
            on(helper, Side.OUTSIDE, out.add(row));
        }
        ServerPlayer rider = soulAt(helper, soul, GameType.SURVIVAL, from);
        ServerPlayer creative = soulAt(helper, soul, GameType.CREATIVE, from.add(rows[1]));
        ServerPlayer friend = soulAt(helper, soul, GameType.CREATIVE, from.add(rows[2]));
        ServerPlayer carried = soulAt(helper, soul, GameType.SURVIVAL, from.add(rows[2]));
        ServerPlayer flier = soulAt(helper, soul, GameType.CREATIVE, from.add(rows[3]));
        ServerPlayer spectator = soulAt(helper, soul, GameType.SPECTATOR, from.add(rows[4]));
        Boat boat = held.add(boat(soul, from));
        Boat other = held.add(boat(soul, from.add(rows[1])));
        Boat shared = held.add(boat(soul, from.add(rows[2])));
        Cow cow = EntityType.COW.create(soul);
        helper.assertTrue(cow != null, "no cow");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(rider.startRiding(boat, true) && creative.startRiding(other, true)
                            && friend.startRiding(shared, true) && carried.startRiding(shared, true),
                            "a soul would not board");
                    // Set down only once the creative soul is aboard: a boat no player steers takes a creature beside it.
                    Vec3 cowAt = from.add(rows[1]);
                    cow.moveTo(cowAt.x, cowAt.y, cowAt.z, 0.0F, 0.0F);
                    held.add(cow);
                    helper.assertTrue(cow.startRiding(other, true), "the cow would not board");
                })
                // Aboard, and steering once the connection's tick has seen it so.
                .thenIdle(5)
                .thenExecute(() -> {
                    // 10 blocks into a fall: the refused move, a block down, neither adds to it nor forgives it.
                    boat.fallDistance = 10.0F;
                    rider.connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(boat(soul, out)));
                    creative.connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(boat(soul, out.add(rows[1]))));
                    friend.connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(boat(soul, out.add(rows[2]))));
                    move(flier, out.add(rows[3]));
                    move(spectator, out.add(rows[4]));
                })
                .thenExecute(() -> {
                    at(helper, boat, from, "the boat steered out");
                    at(helper, shared, from.add(rows[2]), "the boat a friend in creative steered out");
                    helper.assertTrue(boat.fallDistance >= 10.0F && boat.fallDistance < 10.5F, "the boat has fallen "
                            + boat.fallDistance + " (kept at about 10)");
                    helper.assertTrue(rider.getVehicle() == boat && told(rider) == 1 && told(carried) == 1, "the rider is on "
                            + rider.getVehicle() + ", told " + TravelTests.said(rider) + " and " + TravelTests.said(carried));
                    helper.assertTrue(rider.distanceTo(boat) < 1.0F && friend.distanceTo(shared) < 1.0F, "the pilots are at "
                            + rider.position() + " and " + friend.position() + ", off their boats");
                    at(helper, other, out.add(rows[1]), "the boat steered by a soul in creative");
                    helper.assertTrue(cow.getVehicle() == other, "the cow a soul in creative carries is on "
                            + cow.getVehicle());
                    at(helper, flier, out.add(rows[3]), "the soul in creative");
                    at(helper, spectator, out.add(rows[4]), "the spectator");
                    helper.assertTrue(told(creative) + told(friend) + told(flier) + told(spectator) == 0,
                            "a soul free to cross was told");
                })
                .thenSucceed();
    }

    /** A boat that floats where it is put: here, made but not yet set down. */
    private static Boat boat(ServerLevel soul, Vec3 at) {
        Boat boat = new Boat(EntityType.BOAT, soul);
        boat.setNoGravity(true);
        boat.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return boat;
    }

    /**
     * Through the north gate's gap at y 95 a soul flies out and back in, each step let be, the gap between; then, out
     * in front of the gate, rising past the wall's top into the membrane over the strip, it is turned back.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void aSoulPassesThroughAGateBothWays(GameTestHelper helper) {
        ServerLevel soul = hold(helper, 0, -33).soul;
        Vec3[] way = {new Vec3(0.5, 95.0, -509.5), new Vec3(0.5, 95.0, -513.5), new Vec3(0.5, 95.0, -517.5),
                new Vec3(0.5, 95.0, -521.5)};
        Vec3 risen = new Vec3(0.5, 104.0, -521.5), over = new Vec3(0.5, 113.0, -521.5);
        on(helper, Side.INSIDE, way[0], over);
        on(helper, Side.GATEWAY, way[1], way[2]);
        on(helper, Side.OUTSIDE, way[3], risen);
        ServerPlayer player = soulAt(helper, soul, GameType.SURVIVAL, way[0]);
        int[] order = {1, 2, 3, 2, 1, 0, 1, 2, 3};
        var sequence = helper.startSequence().thenIdle(5);
        for (int step : order) {
            sequence = sequence.thenExecute(() -> {
                move(player, way[step]);
                at(helper, player, way[step], "the soul stepping through the gate");
            }).thenIdle(1);
        }
        sequence.thenExecute(() -> move(player, risen))
                .thenIdle(1)
                .thenExecute(() -> {
                    move(player, over);
                    at(helper, player, risen, "the soul rising over the gate's mouth");
                    helper.assertTrue(told(player) == 1, "the soul was told " + TravelTests.said(player));
                })
                .thenSucceed();
    }

    /**
     * On the wall's top at the north-east, a soul steps out past its outer edge, still under the membrane, and then
     * cannot drop to the strip; off the inner edge it drops into Seireitei. A cow set beyond the edge over the strip is
     * held there as it falls, on the membrane's side, its fall forgotten.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void nothingLeavesOverTheWallsTop(GameTestHelper helper) {
        Cleanup held = hold(helper, 23, -24);
        Vec3 top = new Vec3(364.5, 112.0, -364.5), past = new Vec3(368.5, 112.0, -368.5);
        Vec3 drop = new Vec3(368.5, 111.0, -368.5), inner = new Vec3(360.5, 112.0, -360.5);
        Vec3 below = new Vec3(360.5, 104.0, -360.5), cowAt = new Vec3(370.5, 113.0, -370.5);
        on(helper, Side.INSIDE, top, past, inner, below, cowAt);
        on(helper, Side.OUTSIDE, drop);
        helper.assertTrue(SeireiteiPlan.inWall(364, -365), "the wall does not stand under " + top);
        ServerPlayer player = soulAt(helper, held.soul, GameType.SURVIVAL, top);
        Cow cow = EntityType.COW.create(held.soul);
        helper.assertTrue(cow != null, "no cow");
        cow.moveTo(cowAt.x, cowAt.y, cowAt.z, 0.0F, 0.0F);
        held.add(cow);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    move(player, past);
                    move(player, drop);
                    at(helper, player, past, "the soul past the wall's outer edge");
                    helper.assertTrue(told(player) == 1, "the soul was told " + TravelTests.said(player));
                })
                .thenIdle(2)
                .thenExecute(() -> move(player, top))
                .thenIdle(1)
                .thenExecute(() -> move(player, inner))
                .thenIdle(1)
                .thenExecute(() -> {
                    move(player, below);
                    at(helper, player, below, "the soul off the inner edge");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(!cow.isRemoved() && cow.getY() >= SeireiteiPlan.WALL_TOP_Y && cow.getY() < cowAt.y - 0.5
                                    && Shakonmaku.side(cow.position()) == Side.INSIDE && cow.fallDistance < 1.0F,
                            "the cow is at " + cow.position() + ", fallen " + cow.fallDistance);
                })
                .thenSucceed();
    }

    /**
     * Over the strip on the membrane's lid at the south-east, a soul that cannot fly, 10 blocks into a fall, falls against
     * it for over five seconds, each fall turned back: it is never hurt or kicked for floating, nor let off its fall.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void aSoulFallingOnTheLidIsNotKicked(GameTestHelper helper) {
        ServerLevel soul = hold(helper, 23, 23).soul;
        Vec3 lid = new Vec3(368.5, 112.5, 368.5);
        on(helper, Side.INSIDE, lid);
        on(helper, Side.OUTSIDE, lid.subtract(0.0, 0.6, 0.0));
        helper.assertFalse(SeireiteiPlan.inWall(368, 368), "the wall stands under " + lid);
        ServerPlayer player = soulAt(helper, soul, GameType.SURVIVAL, lid);
        player.getAbilities().mayfly = false;
        float[] health = new float[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    health[0] = player.getHealth();
                    player.fallDistance = 10.0F;
                })
                // Falling as a client falls, past the 80 ticks a soul may float before vanilla kicks it.
                .thenExecuteFor(110, () -> {
                    helper.assertFalse(player.hasDisconnected(), "the soul was kicked");
                    move(player, player.position().subtract(0.0, 0.4, 0.0));
                })
                .thenExecute(() -> helper.assertTrue(!player.hasDisconnected() && player.getHealth() >= health[0]
                        && player.fallDistance >= 10.0F && player.fallDistance < 11.0F
                        && Shakonmaku.side(player.position()) == Side.INSIDE && told(player) > 0, "the soul is at "
                        + player.position() + " with " + player.getHealth() + " health, fallen " + player.fallDistance
                        + " (kept at about 10), told " + TravelTests.said(player)))
                .thenSucceed();
    }

    /** A soul on the strip at the wall's foot (r 524) is outside: it rises and goes out into the Rukongai, and back. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void aSoulOnTheStripWalksOut(GameTestHelper helper) {
        ServerLevel soul = hold(helper, 32, 6).soul;
        int ground = soul.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 514, 100);
        Vec3 start = new Vec3(514.5, ground, 100.5);
        helper.assertTrue(ground < 90 && SeireiteiPlan.radius(514, 100) > SeireiteiPlan.WALL_OUTER_RADIUS + 4,
                "the strip's ground is at " + ground);
        // Up by 8 a step over any tree (vanilla refuses a move of over 10 blocks in a tick), then out and back to the wall.
        double y = ground + 16.0;
        Vec3[] way = {new Vec3(514.5, ground + 8.0, 100.5), new Vec3(514.5, y, 100.5), new Vec3(522.5, y, 100.5),
                new Vec3(530.5, y, 100.5), new Vec3(522.5, y, 100.5), new Vec3(514.5, y, 100.5), new Vec3(510.5, y, 100.5)};
        on(helper, Side.OUTSIDE, start);
        on(helper, Side.OUTSIDE, way);
        ServerPlayer player = soulAt(helper, soul, GameType.SURVIVAL, start);
        var sequence = helper.startSequence().thenIdle(5);
        for (Vec3 step : way) {
            sequence = sequence.thenExecute(() -> {
                move(player, step);
                at(helper, player, step, "the soul from the strip");
            }).thenIdle(1);
        }
        sequence.thenExecute(() -> helper.assertTrue(told(player) == 0, "the soul was told " + TravelTests.said(player)))
                .thenSucceed();
    }

    // ---- Leaps, arts, thrown things and flash steps ----

    /**
     * From Seireitei's lawn, a soul's ender pearl landing on the strip outside takes it nowhere, and it is told why; a
     * chorus fruit's leap that would drop it on the strip is refused, and leaps that land on the lawn are not.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void noLeapCrossesTheMembrane(GameTestHelper helper) {
        Cleanup held = hold(helper, -23, 22);
        ServerLevel soul = held.soul;
        Vec3 lawn = new Vec3(-356.5, SeireiteiPlan.PLATEAU_Y, 356.5), onLawn = new Vec3(-350.5, 76.0, 350.5);
        int ground = soul.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -371, 370);
        Vec3 strip = new Vec3(-370.5, ground + 1.5, 370.5);
        on(helper, Side.INSIDE, lawn, onLawn);
        on(helper, Side.OUTSIDE, strip, new Vec3(strip.x, ground, strip.z));
        ServerPlayer player = soulAt(helper, soul, GameType.SURVIVAL, lawn);
        ThrownEnderpearl[] pearl = new ThrownEnderpearl[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    pearl[0] = new ThrownEnderpearl(soul, player);
                    pearl[0].moveTo(strip.x, strip.y, strip.z, 0.0F, 0.0F);
                    pearl[0].setDeltaMovement(0.0, -0.6, 0.0);
                    held.add(pearl[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(pearl[0].isRemoved(), "the pearl has not landed"))
                .thenExecute(() -> {
                    at(helper, player, lawn, "the soul whose pearl landed outside");
                    helper.assertTrue(told(player) == 1, "the soul was told " + TravelTests.said(player));
                    helper.assertTrue(EventHooks.onChorusFruitTeleport(player, strip.x, 80.0, strip.z).isCanceled(),
                            "a chorus fruit's leap onto the strip was let be");
                    helper.assertFalse(EventHooks.onChorusFruitTeleport(player, onLawn.x, onLawn.y, onLawn.z).isCanceled(),
                            "a chorus fruit's leap across the lawn was refused");
                    BlockHitResult hit = BlockHitResult.miss(onLawn, Direction.UP, BlockPos.containing(onLawn));
                    helper.assertFalse(EventHooks.onEnderPearlLand(player, onLawn.x, onLawn.y, onLawn.z, pearl[0], 5.0F, hit)
                            .isCanceled(), "a pearl landing on the lawn was refused");
                })
                .thenSucceed();
    }

    /**
     * Out of Seireitei at y 120 over the east gate: a Cero comes undone where it meets the membrane, breaking nothing;
     * Pesquisa's line meets it there too, though no gate's box would. A vanilla arrow stops there, short of it, and
     * falls; one with no weight comes undone.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void artsComeUndoneAndArrowsFallAtTheMembrane(GameTestHelper helper) {
        Cleanup held = hold(helper, 32, 0);
        ServerLevel soul = held.soul;
        double face = 526.0;
        on(helper, Side.INSIDE, new Vec3(face - 0.01, 120.5, 0.5), new Vec3(face - 0.01, 120.5, 6.5));
        on(helper, Side.OUTSIDE, new Vec3(face + 0.01, 120.5, 0.5), new Vec3(face + 0.01, 120.5, 6.5));
        ServerPlayer watcher = soulAt(helper, soul, GameType.SURVIVAL, new Vec3(516.5, 120.0, 9.5));
        TestPlayers.watchEffects(watcher);
        CeroBeam.Spec spec = new CeroBeam.Spec(10.0F, 16.0, 0.5F, 3.0F, 0.5F, 8, 0.0F, 0xFFFFFF, 1, 0, true);
        Vec3 east = new Vec3(1.0, 0.0, 0.0);
        Arrow[] arrows = new Arrow[2];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    CeroBeam.Shot shot = CeroBeam.fire(soul, null, null, new Vec3(520.5, 120.5, 0.5), east, spec);
                    helper.assertTrue(shot.negated() && !shot.hitBlock() && shot.blocksBroken() == 0
                            && Math.abs(shot.to().x - face) < 1.0E-3, "the Cero over the gate: " + shot);
                    BlockHitResult line = SpiritClip.firstOn(soul, new Vec3(520.5, 120.5, 0.5), new Vec3(530.5, 120.5, 0.5));
                    helper.assertTrue(line instanceof SpiritClip.Membrane && Math.abs(line.getLocation().x - face) < 1.0E-3,
                            "Pesquisa's line met " + line);
                    helper.assertFalse(SpiritClip.meets(soul, new AABB(524.0, 120.0, 0.0, 528.0, 124.0, 2.0)),
                            "a gate's box across the membrane meets Sekkiseki");
                    for (int i = 0; i < 2; i++) {
                        arrows[i] = new Arrow(EntityType.ARROW, soul);
                        arrows[i].setNoGravity(i == 1);
                        arrows[i].moveTo(524.3, 120.5, 3.5 + 3.0 * i, 0.0F, 0.0F);
                        arrows[i].setDeltaMovement(1.5, 0.0, 0.0);
                        held.add(arrows[i]);
                    }
                    ripples(watcher);
                })
                // Long enough for the stopped arrow to have fallen well below where it stopped.
                .thenIdle(12)
                .thenExecute(() -> {
                    Arrow fell = arrows[0];
                    helper.assertTrue(!fell.isRemoved() && fell.getX() > face - 0.02 && fell.getX() < face
                                    && Math.abs(fell.getDeltaMovement().x) < SAME && fell.getY() < 120.0
                                    && fell.getPersistentData().getBoolean("bleach:shakonmaku_fallen"),
                            "the arrow is at " + fell.position() + ", moving " + fell.getDeltaMovement());
                    helper.assertTrue(arrows[1].isRemoved() && arrows[1].getX() < face, "the weightless arrow is at "
                            + arrows[1].position() + (arrows[1].isRemoved() ? ", gone" : ""));
                    helper.assertTrue(ripples(watcher) >= 2, "the arrows showed no ripple");
                })
                .thenSucceed();
    }

    /**
     * Beside the north gate at y 130, a soul's flash step outward ends short of the membrane (within one of its quarter
     * steps), on its own side; in creative mode it goes its whole way.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void aFlashStepStopsShortOfTheMembrane(GameTestHelper helper) {
        ServerLevel soul = hold(helper, -1, -33).soul;
        Vec3 start = new Vec3(-5.5, 130.0, -520.5), north = new Vec3(0.0, 0.0, -1.0), face = new Vec3(-5.5, 130.0, -525.0);
        on(helper, Side.INSIDE, start, face);
        on(helper, Side.OUTSIDE, face.add(0.0, 0.0, -0.01));
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            Player body = helper.makeMockPlayer(mode);
            AABB box = body.getBoundingBox().move(start.subtract(body.position()));
            Vec3 end = FlashStep.sweep(soul, body, box, north, 8.0, false).end();
            boolean whole = end.distanceTo(start.add(north.scale(8.0))) < 1.0E-3;
            boolean stopped = Shakonmaku.side(end) == Side.INSIDE && end.distanceTo(face) <= 0.25 + 1.0E-3
                    && Math.abs(end.x - face.x) < 1.0E-3 && end.z >= face.z - 1.0E-3;
            helper.assertTrue(mode == GameType.CREATIVE ? whole : stopped, "a " + mode.getName() + " flash step ended at "
                    + end);
        }
        helper.succeed();
    }

    // ---- Where souls are sent ----

    /**
     * Every way the mod sends a soul into the Rukongai lands it outside: every Rukongai home drawn, a Dangai lane's end,
     * a Kōtotsu's throw (anywhere it may come down) and a sneaking Garganta's landing, at any height the world has. The
     * ways into Seireitei land inside, the plan's worked cells lie on their sides, and a step of no end is let be at once.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void everyWayIntoTheRukongaiLandsOutside(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        RandomSource random = RandomSource.create(7L);
        double bias = BleachConfig.get(BleachConfig.KONSO_INNER_BIAS);
        List<Rukongai.Draw> homes = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            homes.add(Rukongai.draw(random, bias));
        }
        for (Rukongai.Region region : Rukongai.Region.values()) {
            for (int district = 1; district <= SeireiteiPlan.DISTRICTS; district++) {
                for (int i = 0; i < 20; i++) {
                    homes.add(Rukongai.pointIn(region, district, random));
                }
            }
        }
        // A home's ground: below the wall's top this near (the strip stands within 2 of the plateau), any height beyond.
        for (Rukongai.Draw home : homes) {
            int highest = SeireiteiPlan.radius(home.x(), home.z()) < SeireiteiPlan.SHAKONMAKU_RADIUS
                    ? SeireiteiPlan.WALL_TOP_Y : 320;
            for (int y = 0; y < highest; y++) {
                check(wrong, "home", new Vec3(home.x() + 0.5, y, home.z() + 0.5), Side.OUTSIDE);
            }
        }
        for (int degree = 0; degree < 360; degree++) {
            float bearing = (float) Math.toRadians(degree + 0.5);
            Vec3 edge = new Vec3(SeireiteiPlan.SHAKONMAKU_RADIUS * Math.cos(bearing), 0.0,
                    SeireiteiPlan.SHAKONMAKU_RADIUS * Math.sin(bearing));
            helper.assertTrue(DangaiLogic.outsideWall(edge.x * 1.000001, edge.z * 1.000001), "the throw's edge moved");
            Vec3 garganta = TravelLogic.rukongaiOnBearing(edge.x, edge.z);
            Vec3 near = DangaiLogic.overDistrict(1, bearing);
            Vec3 far = DangaiLogic.overDistrict(DangaiLogic.EXIT_DISTRICTS, bearing);
            for (int y = 0; y < 320; y++) {
                check(wrong, "throw", new Vec3(edge.x * 1.000001, y, edge.z * 1.000001), Side.OUTSIDE);
                check(wrong, "Garganta", new Vec3(garganta.x, y, garganta.z), Side.OUTSIDE);
                check(wrong, "the first lane's end", new Vec3(near.x, y, near.z), Side.OUTSIDE);
                check(wrong, "the last lane's end", new Vec3(far.x, y, far.z), Side.OUTSIDE);
            }
        }
        check(wrong, "the Senkaimon court", new Vec3(SeireiteiPlan.SENKAIMON_COURT_X + 0.5, SeireiteiPlan.PLATEAU_Y,
                SeireiteiPlan.SENKAIMON_COURT_Z + 0.5), Side.INSIDE);
        check(wrong, "the Academy", new Vec3(SeireiteiPlan.ACADEMY_LANDING_X, SeireiteiPlan.ACADEMY_FLOOR_Y,
                SeireiteiPlan.ACADEMY_LANDING_Z), Side.INSIDE);
        // The plan's worked cells: the sphere beside the west gap and off the south axis, the inner face off and in the
        // south gap, the gap's outer face and its edges (the smoke test's), the wall's top, the strip, the lawn, the sky.
        int[][] inside = {{-525, 120, 0}, {-526, 120, 0}, {12, 120, 525}, {20, 90, 511}, {0, 90, 511}, {16, 112, 512},
                {20, 112, 516}, {20, 112, 521}, {0, 112, 516}, {0, 72, 510}, {0, 0, 511}, {0, 300, 470}};
        int[][] gateway = {{0, 90, 512}, {0, 90, 519}, {7, 90, 512}, {-8, 90, 512}, {0, 72, 516}, {0, 111, 516}};
        int[][] outside = {{-527, 120, 0}, {12, 120, 526}, {20, 90, 512}, {0, 90, 520}, {20, 90, 519}, {8, 90, 512},
                {-9, 90, 512}, {16, 111, 512}, {20, 72, 523}, {20, 111, 521}, {0, 72, 521}, {0, 72, 524}, {20, 50, 516},
                {0, 300, 480}, {521, 72, 30}};
        Map.of(Side.INSIDE, inside, Side.GATEWAY, gateway, Side.OUTSIDE, outside).forEach((side, cells) -> {
            for (int[] cell : cells) {
                check(wrong, "the plan's cell", new Vec3(cell[0] + 0.5, cell[1], cell[2] + 0.5), side);
            }
        });
        helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong, first " + wrong.subList(0, Math.min(5, wrong.size())));
        // Only the line's part within the far sphere is walked: a step to no end, or a billion blocks out, ends at once.
        Vec3 high = new Vec3(0.5, 120.0, 524.5), strip = new Vec3(0.5, 72.0, 528.5);
        helper.assertTrue(Shakonmaku.crossing(high, new Vec3(Double.POSITIVE_INFINITY, 120.0, 524.5)) == null
                && Shakonmaku.crossing(strip, strip.add(0.0, 0.0, 1.0E9)) == null, "an endless or far step crossed");
        BlockHitResult out = Shakonmaku.crossing(high, high.add(0.0, 0.0, 1.0E9));
        helper.assertTrue(out != null && Math.abs(out.getLocation().z - 526.0) < SAME, "a far step out crossed at " + out);
        helper.succeed();
    }

    /** Notes this point as wrong unless the plan puts it on the side named. */
    private static void check(List<String> wrong, String what, Vec3 at, Side side) {
        if (Shakonmaku.side(at) != side) {
            wrong.add(what + " " + at + " is " + Shakonmaku.side(at));
        }
    }
}
