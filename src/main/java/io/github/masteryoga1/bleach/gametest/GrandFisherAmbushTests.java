package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.Pesquisa;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Grand Fisher's ambush: a lure laid alone, unfelt, that waits for her prey and fades if none comes; and her
 * body bursting out of the ground beside her at the prey's coming or blow, or nothing coming with no ground there or on
 * Peaceful. Each test asks only about its own lures, bodies and players, by object.
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
}
