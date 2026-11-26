package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.ShriekerComing;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Shrieker's coming at night: what counts as one about, the wait after a death, the test server's refusal,
 * the roll that draws it for a strong soul only, and the world's rules. A test that could open a tear for real runs in a
 * batch of its own and first waits for the ground about to tick; the rest change the server's row or its rules within one
 * call and put them back, and count Shriekers only against the count before.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ShriekerComingTests {
    private static final String SHRIEKER = ShriekerComing.ID;
    private static final long DAY = BossLedger.TICKS_PER_DAY;
    /** Over [bosses.shrieker] min_max_reiryoku (200). */
    private static final float STRONG = 300.0F;
    /** Aware, but under the line. */
    private static final float WEAK = 100.0F;
    /** Further than a tear's ground is sought (24) and its rise, so every column it may try ticks. */
    private static final double REACH = 28.0;
    /** Room for the first wait on the ground past the tear's 30 ticks before its Shrieker drops. */
    private static final int TIMEOUT = 340;
    private static final int GIVE_UP_TICK = TIMEOUT - 10;

    private ShriekerComingTests() {
    }

    /** A player joined here in survival with a Human soul strong enough to draw the Shrieker, and no grace. */
    private static ServerPlayer strong(GameTestHelper helper, Vec3 at) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, at);
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, STRONG));
        return player;
    }

    /** Runs this with mob spawning on and no instructor's shelter, within one call, as game rules are the server's. */
    private static void inPlay(ServerLevel level, Runnable body) {
        GameRules.BooleanValue spawning = level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean was = spawning.get();
        try {
            spawning.set(true, level.getServer());
            KidoTests.withConfig(BleachConfig.ACADEMY_SAFE_RADIUS, 0, body);
        } finally {
            spawning.set(was, level.getServer());
        }
    }

    /** Forces the ground about this soul for its tear, fails with this line near the end, and gives back what clears up. */
    private static Runnable holdGround(GameTestHelper helper, ServerPlayer soul, String failure) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, soul.position(), REACH);
        Runnable clear = () -> {
            QuincyArtsTests.discardAll(BossLedgerTests.riftsFor(level.getServer(), soul));
            QuincyArtsTests.discardAll(BossLedgerTests.drawnBy(level, soul));
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            forced.clear();
        };
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail(failure);
        });
        return clear;
    }

    /** Up to three of its comings for this soul, as open ground is sought at random: on the field one of three finds it. */
    private static boolean comes(ServerPlayer soul, SoulData data, long now) {
        for (int i = 0; i < 3; i++) {
            if (ShriekerComing.come(soul, data, now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counted about: a live Shrieker, and a tear that still carries one though it has not opened; not a tear of another
     * kind, nor a Shrieker at no health. While one is about, its coming is refused with its row free, before any tear is
     * sought; the ground here may not tick yet, so {@link #aForcedRollAtNightBringsOneForAStrongSoulOnly} shows the same
     * refusal on ground just shown open. The server's row, reset first and after, in one call.
     */
    @GameTest(template = "field")
    public static void oneShriekerAboutOrInItsTearStopsASecond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BossLedger ledger = BossLedger.get(server);
        long now = level.getGameTime();
        ServerPlayer soul = strong(helper, new Vec3(8.5, 2.0, 4.5));
        List<Entity> made = new ArrayList<>();
        try {
            ledger.reset(SHRIEKER);
            int before = ShriekerComing.BOSS.present(server);
            Shrieker live = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(8.5, 6.0, 8.5));
            made.add(live);
            helper.assertTrue(ShriekerComing.BOSS.present(server) == before + 1, "a live Shrieker is not counted");
            helper.assertFalse(ShriekerComing.BOSS.mayCome(server, BossLedger.now(server))
                    || ShriekerComing.come(soul, SoulLogic.get(soul), now)
                    || !BossLedgerTests.riftsFor(server, soul).isEmpty(), "a second came while one was about");
            for (HollowKind kind : List.of(HollowKind.SHRIEKER, HollowKind.FISHBONE_D)) {
                GargantaRift dormant = GargantaRift.arrival(level, helper.absoluteVec(new Vec3(4.0 * made.size(), 8.0, 4.0)),
                        now + 100, List.of(kind), soul, GargantaEvents.riftSize(kind));
                helper.assertTrue(level.addFreshEntity(dormant), "the world refused a tear");
                made.add(dormant);
            }
            helper.assertTrue(ShriekerComing.BOSS.present(server) == before + 2,
                    "a tear carrying one is not counted, or another kind's is");
            live.setHealth(0.0F);
            helper.assertTrue(ShriekerComing.BOSS.present(server) == before + 1, "a Shrieker at no health is counted");
            made.forEach(Entity::discard);
            helper.assertTrue(ShriekerComing.BOSS.present(server) == before, "the count did not fall back");
        } finally {
            made.forEach(Entity::discard);
            QuincyArtsTests.discardAll(BossLedgerTests.riftsFor(server, soul));
            ledger.reset(SHRIEKER);
        }
        helper.succeed();
    }

    /**
     * With {@code return_days} at 1, a Shrieker killed now keeps the next away until the tick a day on, not one before,
     * and its coming is refused meanwhile with no tear and no cooldown set (on open ground too, in
     * {@link #aForcedRollAtNightBringsOneForAStrongSoulOnly}). The server's row, reset first and after, read on the
     * ledger's clock in the call that kills it.
     */
    @GameTest(template = "field")
    public static void aDeadShriekerKeepsTheNextAwayUntilItsDay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BossLedger ledger = BossLedger.get(server);
        ServerPlayer soul = strong(helper, new Vec3(8.5, 2.0, 4.5));
        SoulData data = SoulLogic.get(soul);
        long last = data.hunt().lastArrivalAt();
        KidoTests.withConfig(BleachConfig.SHRIEKER_RETURN_DAYS, 1, () -> {
            try {
                ledger.reset(SHRIEKER);
                long now = BossLedger.now(server);
                helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(8.5, 6.0, 8.5)).kill();
                helper.assertTrue(!ledger.mayCome(SHRIEKER, now + DAY - 1) && ledger.mayCome(SHRIEKER, now + DAY),
                        "after a death it waits " + ledger.waitLeft(SHRIEKER, now) + " ticks, not a day");
                helper.assertFalse(ShriekerComing.come(soul, data, helper.getLevel().getGameTime())
                        || !BossLedgerTests.riftsFor(server, soul).isEmpty() || data.hunt().lastArrivalAt() != last,
                        "one came within its day away, or the refusal left a tear or set the cooldown");
            } finally {
                QuincyArtsTests.discardAll(BossLedgerTests.riftsFor(server, soul));
                ledger.reset(SHRIEKER);
            }
        });
        helper.succeed();
    }

    /**
     * On the game-test server, with the ground ticking, spawning on, none about and its row free, a strong soul's roll at
     * night draws the Shrieker and the world and the soul allow a tear, yet the server's own refusal stops it: no tear.
     * Were that guard lost from {@link ShriekerComing#tryComing}, a real tear would open here. The tick asks the same
     * refusal before it rolls, so no test drives the roll from the tick; where it sits there is checked by reading. A
     * batch of its own, as it frees the server's row and counts every Shrieker.
     */
    @GameTest(template = "field", batch = "shrieker_test_server", timeoutTicks = TIMEOUT)
    public static void theTestServersRollBringsNoShrieker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer soul = strong(helper, new Vec3(8.5, 2.0, 8.5));
        Vec3 start = soul.position();
        Runnable clear = holdGround(helper, soul, "the ground around the soul never started ticking in time");
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, HollowBaitTests.allTicking(level, start, REACH),
                        "the ground around the soul never started ticking"))
                .thenExecute(HollowBaitTests.orClear(() -> inPlay(level, () -> {
                    BossLedger ledger = BossLedger.get(server);
                    try {
                        ledger.reset(SHRIEKER);
                        SoulData data = SoulLogic.get(soul);
                        long now = level.getGameTime();
                        int about = ShriekerComing.BOSS.present(server);
                        helper.assertTrue(about == 0, about + " Shriekers are about from earlier tests");
                        helper.assertTrue(ShriekerComing.draws(data, true, 0.0)
                                && GargantaEvents.mayArriveInPlay(soul, data, now), "nothing here would bring one anyway");
                        helper.assertFalse(GargantaEvents.mayArriveNear(soul, data, now)
                                || ShriekerComing.tryComing(soul, data, now, true, 0.0)
                                || !BossLedgerTests.riftsFor(server, soul).isEmpty(), "the test server's roll brought one");
                    } finally {
                        ledger.reset(SHRIEKER);
                    }
                    clear.run();
                }), clear))
                .thenSucceed();
    }

    /**
     * The roll draws it only at night, for a soul over the line that is no Hollow, and under the share; a lower line in
     * the config lets the weaker soul draw it. With none about and its row free, a strong soul's coming opens one loud
     * tear, beeping as any natural one, carrying one Shrieker; it sets the soul's cooldown, and the Shrieker comes out
     * hunting that soul. On that ground, just shown open, its coming is refused while the tear is about and while its
     * days away run, and comes again once its row is free, so the refusals are its own and not the ground's. A batch of
     * its own, as it frees the server's row and counts every Shrieker.
     */
    @GameTest(template = "field", batch = "shrieker_coming", timeoutTicks = TIMEOUT)
    public static void aForcedRollAtNightBringsOneForAStrongSoulOnly(GameTestHelper helper) {
        double share = BleachConfig.get(BleachConfig.SHRIEKER_ARRIVAL_SHARE);
        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, STRONG);
        SoulData weak = ShinigamiTests.soul(SoulState.HUMAN, WEAK);
        helper.assertTrue(ShriekerComing.draws(human, true, 0.0) && !ShriekerComing.draws(human, false, 0.0)
                && !ShriekerComing.draws(human, true, share) && !ShriekerComing.draws(weak, true, 0.0)
                && !ShriekerComing.draws(ShinigamiTests.soul(SoulState.HOLLOW, STRONG), true, 0.0),
                "the roll drew it by day, at the share, for a weak soul or for a Hollow, or not for a strong soul");
        KidoTests.withConfig(BleachConfig.SHRIEKER_MIN_MAX_REIRYOKU, WEAK, () -> helper.assertTrue(
                ShriekerComing.draws(weak, true, 0.0), "a lower line in the config did not let the weaker soul draw it"));
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer soul = strong(helper, new Vec3(8.5, 2.0, 8.5));
        Vec3 start = soul.position();
        Runnable clear = holdGround(helper, soul, "the Shrieker never came out for the strong soul");
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, HollowBaitTests.allTicking(level, start, REACH),
                        "the ground around the soul never started ticking"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    BossLedger ledger = BossLedger.get(server);
                    SoulData data = SoulLogic.get(soul);
                    long now = level.getGameTime();
                    try {
                        ledger.reset(SHRIEKER);
                        int about = ShriekerComing.BOSS.present(server);
                        helper.assertTrue(about == 0, about + " Shriekers are about from earlier tests");
                        boolean came = comes(soul, data, now);
                        List<GargantaRift> rifts = BossLedgerTests.riftsFor(server, soul);
                        helper.assertTrue(came && rifts.size() == 1 && !rifts.get(0).isQuiet()
                                && rifts.get(0).pending().equals(List.of(HollowKind.SHRIEKER)),
                                "the coming tore " + rifts.size() + " Garganta, not one loud one carrying a Shrieker");
                        helper.assertTrue(data.hunt().lastArrivalAt() == now, "the coming did not set the soul's cooldown");
                        helper.assertFalse(comes(soul, data, now + 1) || data.hunt().lastArrivalAt() != now
                                || BossLedgerTests.riftsFor(server, soul).size() != 1,
                                "a second came, or the cooldown was set, while the first's tear was about");
                        QuincyArtsTests.discardAll(BossLedgerTests.riftsFor(server, soul));
                        ledger.markAway(SHRIEKER, BossLedger.now(server), 1, List.of());
                        helper.assertFalse(comes(soul, data, now + 2) || data.hunt().lastArrivalAt() != now
                                || !BossLedgerTests.riftsFor(server, soul).isEmpty(), "one came within its days away");
                        ledger.reset(SHRIEKER);
                        helper.assertTrue(comes(soul, data, now) && BossLedgerTests.riftsFor(server, soul).size() == 1,
                                "none came on the same ground once its row was free again");
                    } finally {
                        ledger.reset(SHRIEKER);
                    }
                }, clear))
                .thenWaitUntil(() -> helper.assertFalse(BossLedgerTests.drawnBy(level, soul).isEmpty(),
                        "no Shrieker has come out of the tear"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    List<Shrieker> out = BossLedgerTests.drawnBy(level, soul);
                    helper.assertTrue(out.size() == 1 && out.get(0).getTarget() == soul, out.size()
                            + " Shriekers came, hunting " + out.get(0).getTarget());
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    /**
     * Its coming keeps the world's rules for every tear: one may open on Normal with mob spawning on, but not on Peaceful,
     * nor with spawning off. It reads {@link GargantaEvents#mayArriveInPlay}, as the test server refuses every tear: the
     * tick asks {@link GargantaEvents#mayArriveNear} at its top, and {@link ShriekerComing#tryComing} asks it again, so
     * these rules are the coming's only gate to the world. In one call, the difficulty and the rule put back after; in the
     * test server's batch, as it changes the server's difficulty.
     */
    @GameTest(template = "field", batch = "shrieker_test_server")
    public static void peacefulAndMobSpawningOffBringNoShrieker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer soul = strong(helper, new Vec3(8.5, 2.0, 4.5));
        SoulData data = SoulLogic.get(soul);
        long now = level.getGameTime();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        try {
            inPlay(level, () -> {
                server.setDifficulty(Difficulty.NORMAL, true);
                helper.assertTrue(GargantaEvents.mayArriveInPlay(soul, data, now), "no tear may open on Normal");
                server.setDifficulty(Difficulty.PEACEFUL, true);
                helper.assertFalse(GargantaEvents.mayArriveInPlay(soul, data, now), "a tear may open on Peaceful");
                server.setDifficulty(Difficulty.NORMAL, true);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                helper.assertFalse(GargantaEvents.mayArriveInPlay(soul, data, now), "a tear may open with spawning off");
            });
        } finally {
            server.setDifficulty(difficulty, true);
        }
        helper.succeed();
    }
}
