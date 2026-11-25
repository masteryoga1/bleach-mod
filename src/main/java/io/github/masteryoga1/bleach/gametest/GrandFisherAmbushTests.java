package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.GrandFisherComing;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.Pesquisa;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Grand Fisher's ambush: a lure laid alone, unfelt, that waits for her prey and fades if none comes; her
 * body bursting out of the ground beside her at the prey's coming or blow, or nothing coming with no ground there or on
 * Peaceful; and the roll that lays her, likelier in rain, the world and the souls it keeps to, and the roll itself. Each
 * test asks only about its own lures, bodies and players, by object.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrandFisherAmbushTests {
    /** A soul who sees spirits, and a blow that would not kill a lure of 15. */
    private static final float AWARE = 40.0F;
    private static final float BLOW = 6.0F;
    /** Where a burst body may land from its lure, its centre to hers: 2 to 6 blocks, give or take a block centre's move. */
    private static final double BURST_NEAREST = 1.25;
    private static final double BURST_FURTHEST = 6.75;
    /** Ticks enough for a lure laid alone to have looked about her for her prey twice. */
    private static final int LOOKED = 10;
    private static final double CHANCE_EPSILON = 1.0E-9;
    /** How far short of its chance a drawn roll is set, to meet it. */
    private static final double EDGE = 1.0E-6;

    private GrandFisherAmbushTests() {
    }

    /** A lure laid alone at this spot in the test's box, as the ambush lays one. */
    private static GrandFisherLure laid(GameTestHelper helper, Vec3 at) {
        GrandFisherLure lure = GrandFisherLure.grow(helper.getLevel(), helper.absoluteVec(at), 0.0F);
        helper.assertTrue(lure != null && lure.isWaiting(), "the world refused a lure laid alone, or she does not wait");
        return lure;
    }

    /** Asserts this lure's body has burst out beside her: alive, 2 to 6 blocks off, holding her on her stalk. */
    private static GrandFisher burstBeside(GameTestHelper helper, GrandFisherLure lure) {
        GrandFisher body = lure.body();
        helper.assertTrue(body != null && body.isAlive() && body.lure() == lure && !lure.isFading(),
                "no body came out for the lure, or it does not hold her on her stalk");
        double off = body.position().subtract(lure.position()).horizontalDistance();
        helper.assertTrue(off >= BURST_NEAREST && off <= BURST_FURTHEST, "the body burst out " + off + " blocks from her");
        return body;
    }

    /**
     * Laid alone, a lure waits: a soul who sees her nine blocks off brings nothing, and no Pesquisa feels her, as her body
     * hides its reiatsu in the ground; one whose wait is over fades. A blow on her from a soul who sees her is spent
     * bringing her body bursting out beside her, set on her striker, and she is felt once it is out; her prey coming
     * within a few blocks of another brings hers out too, set on that prey. Under the open sky, as a body seeks open
     * ground.
     */
    @GameTest(template = "field", skyAccess = true, timeoutTicks = 300)
    public static void herPreyComingNearOrStrikingHerBringsHerBodyOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GrandFisherLure approached = laid(helper, new Vec3(4.5, 2.0, 12.5));
        GrandFisherLure struck = laid(helper, new Vec3(12.5, 2.0, 12.5));
        GrandFisherLure forgotten = laid(helper, new Vec3(12.5, 2.0, 3.5));
        // Her wait as good as over.
        forgotten.tickCount = BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_WAIT_SECONDS) * 20;
        ServerPlayer prey = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(4.5, 2.0, 3.5));
        prey.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, AWARE));
        Player caster = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, 400.0F, new Vec3(8.5, 2.0, 12.5));
        GrandFisher[] bodies = new GrandFisher[2];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, approached.tickCount > LOOKED,
                        "the laid lures never ticked"))
                .thenExecute(() -> {
                    helper.assertTrue(approached.isWaiting() && struck.isWaiting(),
                            "a soul nine blocks off brought a body out");
                    long now = level.getGameTime();
                    helper.assertFalse(Pesquisa.senses(caster, approached, now) || Pesquisa.senses(caster, struck, now),
                            "Pesquisa felt a lure with no body");
                    helper.assertTrue(forgotten.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                            "a lure whose wait was over did not fade");
                    Player striker = HollowTests.soulPlayer(helper, SoulState.HUMAN, AWARE);
                    float whole = struck.getHealth();
                    helper.assertTrue(struck.hurt(level.damageSources().playerAttack(striker), BLOW)
                            && struck.getHealth() == whole, "the blow on the lure did not land, or hurt her");
                    bodies[0] = burstBeside(helper, struck);
                    helper.assertTrue(bodies[0].getTarget() == striker, "the body that burst out is not set on its striker");
                    bodies[0].removeFreeWill();
                    helper.assertTrue(Pesquisa.senses(caster, struck, now), "Pesquisa missed a lure with her body out");
                    Vec3 near = helper.absoluteVec(new Vec3(4.5, 2.0, 9.5));
                    prey.teleportTo(level, near.x, near.y, near.z, 0.0F, 0.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(approached.body() != null,
                        "her prey came within three blocks of the lure and no body came out"))
                .thenExecute(() -> {
                    bodies[1] = burstBeside(helper, approached);
                    helper.assertTrue(bodies[1].getTarget() == prey, "the body that burst out is not set on her prey");
                    for (GrandFisher body : bodies) {
                        body.lure().discard();
                        body.discard();
                    }
                })
                .thenSucceed();
    }

    /**
     * Under a roof, as in a cave, there is no open ground beside her for her body: a blow on a lure laid alone brings
     * nothing out, and she fades with no death.
     */
    @GameTest(template = "field")
    public static void withNoGroundBesideHerNothingComes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GrandFisherLure lure = laid(helper, new Vec3(8.5, 2.0, 8.5));
        int before = level.getEntities(ModEntities.GRAND_FISHER.get(), GrandFisher::isAlive).size();
        Player striker = HollowTests.soulPlayer(helper, SoulState.HUMAN, AWARE);
        helper.assertTrue(lure.hurt(level.damageSources().playerAttack(striker), BLOW), "the blow on the lure did not land");
        int after = level.getEntities(ModEntities.GRAND_FISHER.get(), GrandFisher::isAlive).size();
        helper.assertTrue(lure.getRemovalReason() == Entity.RemovalReason.DISCARDED && lure.deathTime == 0 && after == before,
                "with no ground beside her the lure went by " + lure.getRemovalReason() + ", and " + (after - before)
                        + " bodies came");
        helper.succeed();
    }

    /**
     * On Peaceful, where its body would vanish at once, a blow on a lure laid alone brings nothing out of the open ground
     * beside her, and she fades; another laid alone, struck by none, fades on her own next tick (set within this one call
     * and put back, so no other tick sees it). Under the open sky, so the ground is there.
     */
    @GameTest(template = "field", skyAccess = true)
    public static void onPeacefulNothingComes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        GrandFisherLure lure = laid(helper, new Vec3(8.5, 2.0, 8.5));
        GrandFisherLure idle = laid(helper, new Vec3(3.5, 2.0, 3.5));
        Player striker = HollowTests.soulPlayer(helper, SoulState.HUMAN, AWARE);
        int bodies = level.getEntities(ModEntities.GRAND_FISHER.get(), GrandFisher::isAlive).size();
        boolean calm;
        Entity.RemovalReason idled;
        try {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            calm = lure.hurt(level.damageSources().playerAttack(striker), BLOW)
                    && lure.getRemovalReason() == Entity.RemovalReason.DISCARDED
                    && level.getEntities(ModEntities.GRAND_FISHER.get(), GrandFisher::isAlive).size() == bodies;
            idle.tick();
            idled = idle.getRemovalReason();
        } finally {
            server.setDifficulty(difficulty, true);
            TravelTests.discard(lure.body(), lure, idle.body(), idle);
        }
        helper.assertTrue(calm, "on Peaceful a blow on a lure laid alone brought her body out, or she did not fade");
        helper.assertTrue(idled == Entity.RemovalReason.DISCARDED && idle.deathTime == 0,
                "on Peaceful a lure laid alone went by " + idled + " on her own tick, not faded");
        helper.succeed();
    }

    /**
     * The roll that lays a lure: likelier in rain by the config's weight. Its world: the Overworld only, never on
     * Peaceful or with mobs not spawning (each set within this one call and put back, so no tick sees it). Its prey: a
     * soul by survival's rules of at least the config's reiryoku, no Arrancar.
     */
    @GameTest(template = "field")
    public static void theAmbushIsLikelierInRainAndKeepsToItsWorld(GameTestHelper helper) {
        double dry = GrandFisherComing.chance(false);
        double wet = GrandFisherComing.chance(true);
        double weighed = Math.min(1.0, dry * BleachConfig.get(BleachConfig.GRAND_FISHER_RAIN_WEIGHT));
        helper.assertTrue(dry > 0.0 && wet > dry && Math.abs(wet - weighed) < CHANCE_EPSILON,
                "the roll is " + dry + " dry and " + wet + " in rain, not " + weighed);

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        GameRules.BooleanValue spawning = level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean wasSpawning = spawning.get();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        List<Boolean> allowed;
        try {
            spawning.set(true, server);
            server.setDifficulty(Difficulty.NORMAL, true);
            boolean open = GrandFisherComing.worldAllows(level);
            boolean below = nether != null && GrandFisherComing.worldAllows(nether);
            server.setDifficulty(Difficulty.PEACEFUL, true);
            boolean peaceful = GrandFisherComing.worldAllows(level);
            server.setDifficulty(Difficulty.NORMAL, true);
            spawning.set(false, server);
            allowed = List.of(open, below, peaceful, GrandFisherComing.worldAllows(level));
        } finally {
            spawning.set(wasSpawning, server);
            server.setDifficulty(difficulty, true);
        }
        helper.assertTrue(allowed.equals(List.of(true, false, false, false)),
                "the Overworld, the Nether, Peaceful and no mob spawning allow it: " + allowed);

        float strong = (float) BleachConfig.get(BleachConfig.GRAND_FISHER_AMBUSH_MIN_REIRYOKU);
        Vec3 at = new Vec3(4.5, 2.0, 4.5);
        helper.assertTrue(GrandFisherComing.hunts(HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN,
                strong, at)), "Grand Fisher does not hunt a strong aware Human");
        helper.assertFalse(GrandFisherComing.hunts(HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN,
                strong - 1.0F, at)), "Grand Fisher hunts a soul weaker than the config's");
        helper.assertFalse(GrandFisherComing.hunts(HollowPlayerTests.soulPlayer(helper, GameType.CREATIVE, SoulState.HUMAN,
                strong, at)), "Grand Fisher hunts a soul in creative");
        helper.assertFalse(GrandFisherComing.hunts(ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, strong, at)),
                "Grand Fisher hunts an Arrancar");
        helper.succeed();
    }

    /**
     * The roll itself, all but its minute and its refusal on a game-test server: forced past its chance, it lays one lure
     * alone 12 to 24 blocks from a soul it hunts, and none while she is about, while the ledger keeps it away, on Peaceful or
     * with mobs not spawning; unforced, a draw at its dry chance lays none and one just under lays her, so a chance of 0 lays
     * none (the row and the world's rules each set within this one call and put back). A batch of its own, as whether it may
     * come counts every Grand Fisher and lure on the server: with no other test running, those left by finished tests are
     * taken away first. Under the open sky, with the ground about the soul ticking, as a lure seeks open ground.
     */
    @GameTest(template = "field", batch = "grand_fisher_roll", skyAccess = true, timeoutTicks = 400)
    public static void aForcedRollLaysOneLureForASoulItHunts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer soul = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 8.5));
        float strong = (float) BleachConfig.get(BleachConfig.GRAND_FISHER_AMBUSH_MIN_REIRYOKU);
        soul.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, strong));
        Vec3 start = soul.position();
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, start, GrandFisherTests.LAID_FURTHEST);
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper,
                        HollowBaitTests.allTicking(level, start, GrandFisherTests.LAID_FURTHEST),
                        "the ground about the soul never started ticking"))
                .thenExecute(() -> {
                    for (ServerLevel world : server.getAllLevels()) {
                        TravelTests.discard(world.getEntities(ModEntities.GRAND_FISHER.get(), Entity::isAlive)
                                .toArray(new Entity[0]));
                        TravelTests.discard(world.getEntities(ModEntities.GRAND_FISHER_LURE.get(), Entity::isAlive)
                                .toArray(new Entity[0]));
                    }
                    BossLedger ledger = BossLedger.get(server);
                    GameRules.BooleanValue spawning = level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
                    boolean wasSpawning = spawning.get();
                    Difficulty difficulty = server.getWorldData().getDifficulty();
                    List<GrandFisherLure> laid = new ArrayList<>();
                    try {
                        ledger.reset(GrandFisherComing.ID);
                        spawning.set(true, server);
                        server.setDifficulty(Difficulty.NORMAL, true);
                        // Any other soul it hunts would be one left joined by a finished test: she may be laid for it.
                        List<ServerPlayer> hunted = level.players().stream().filter(GrandFisherComing::hunts).toList();
                        GrandFisherLure lure = forcedRoll(server, laid);
                        int waiting = level.getEntities(ModEntities.GRAND_FISHER_LURE.get(), GrandFisherLure::isWaiting)
                                .size();
                        helper.assertTrue(hunted.contains(soul) && lure != null && waiting == 1
                                        && hunted.stream().anyMatch(prey -> laidFor(prey, lure)),
                                "a forced roll laid " + waiting + " lures, none 12 to 24 blocks from a soul it hunts");
                        helper.assertTrue(forcedRoll(server, laid) == null, "a forced roll laid a second lure");
                        lure.discard();
                        ledger.markAway(GrandFisherComing.ID, BossLedger.now(server), 1, List.of());
                        boolean away = forcedRoll(server, laid) == null;
                        ledger.reset(GrandFisherComing.ID);
                        server.setDifficulty(Difficulty.PEACEFUL, true);
                        boolean peaceful = forcedRoll(server, laid) == null;
                        server.setDifficulty(Difficulty.NORMAL, true);
                        spawning.set(false, server);
                        boolean still = forcedRoll(server, laid) == null;
                        helper.assertTrue(away && peaceful && still, "a forced roll laid a lure while the ledger kept"
                                + " it away, on Peaceful or with mobs not spawning: " + !away + ", " + !peaceful + ", "
                                + !still);
                        spawning.set(true, server);
                        // Unforced, only a draw under its chance lays her, so a chance of 0 lays none: the dry
                        // chance, as no rain falls here.
                        double dry = GrandFisherComing.chance(false);
                        boolean missed = drawnRoll(server, laid, dry) == null;
                        boolean drawn = drawnRoll(server, laid, dry - EDGE) != null;
                        helper.assertTrue(!level.isRainingAt(soul.blockPosition()) && missed && drawn, "a draw at the"
                                + " dry chance laid a lure, or one just under it laid none: " + !missed + ", " + !drawn);
                    } finally {
                        ledger.reset(GrandFisherComing.ID);
                        spawning.set(wasSpawning, server);
                        server.setDifficulty(difficulty, true);
                        TravelTests.discard(laid.toArray(new Entity[0]));
                        forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                    }
                })
                .thenSucceed();
    }

    /** A roll forced past its chance; the lure it laid, if any, kept for the test's end to discard. */
    @Nullable
    private static GrandFisherLure forcedRoll(MinecraftServer server, List<GrandFisherLure> laid) {
        GrandFisherLure lure = GrandFisherComing.roll(server, server.overworld().getRandom(), true);
        if (lure != null) {
            laid.add(lure);
        }
        return lure;
    }

    /** A roll whose chance is drawn as this; the lure it laid, if any, kept for the test's end to discard. */
    @Nullable
    private static GrandFisherLure drawnRoll(MinecraftServer server, List<GrandFisherLure> laid, double draw) {
        RandomSource random = new LegacyRandomSource(0L) {
            @Override
            public double nextDouble() {
                return draw;
            }
        };
        GrandFisherLure lure = GrandFisherComing.roll(server, random, false);
        if (lure != null) {
            laid.add(lure);
        }
        return lure;
    }

    /** Whether this lure was laid as for this soul: 12 to 24 blocks off it across the ground. */
    private static boolean laidFor(Player prey, GrandFisherLure lure) {
        double off = lure.position().subtract(prey.position()).horizontalDistance();
        return off >= GrandFisherTests.LAID_NEAREST && off <= GrandFisherTests.LAID_FURTHEST;
    }
}
