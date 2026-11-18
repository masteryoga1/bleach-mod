package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GateKeeper;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.world.GateState;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the gatekeepers in the real Soul Society: the keepers' batch works one gate a test, and every test that
 * works a keeper sends it away at its end, so a later batch finds each gate's keeper whole at its post, made again
 * with the same UUID.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GateKeeperTests {
    private static final String BATCH = "gate_keeper";
    private static final int EAST = 0, SOUTH = 1, WEST = 2, NORTH = 3;

    private GateKeeperTests() {
    }

    // ---- Helpers ----

    /** This gate's keeper by the UUID its gates keep, or null while none stands alive. */
    static GateKeeper keeper(ServerLevel soul, int gate) {
        UUID id = GateState.get(soul).keeper(gate);
        return id != null && soul.getEntity(id) instanceof GateKeeper keeper && keeper.isAlive() ? keeper : null;
    }

    /** Asserts this gate has one keeper, its own, whole and unbeaten at its post, and gives it back. */
    static GateKeeper assertKeeper(GameTestHelper helper, ServerLevel soul, int gate) {
        GateKeeper keeper = keeper(soul, gate);
        List<GateKeeper> all = soul.getEntitiesOfClass(GateKeeper.class, AABB.ofSize(GateState.post(gate), 48, 24, 48));
        helper.assertTrue(keeper != null && all.equals(List.of(keeper)) && keeper.gate() == gate
                        && keeper.position().distanceToSqr(GateState.post(gate)) < 0.25
                        && keeper.getHealth() == keeper.getMaxHealth() && !keeper.isBeaten(),
                "gate " + gate + "'s keepers are " + all);
        return keeper;
    }

    /** Sends this gate's keeper away when the test ends, passed or failed, so its gates make it again whole. */
    static void sendAway(ShakonmakuTests.Cleanup held, int gate) {
        held.atEnd(() -> {
            GateKeeper keeper = keeper(held.soul, gate);
            if (keeper != null) {
                keeper.discard();
            }
        });
    }

    /**
     * Waits for this gate down and its keeper; sends that keeper away and waits for its gates to make it again, whole at
     * its post, into {@code keeper}; and, if asked, quiets it, so that a test starts from a keeper as a batch finds it.
     */
    static GameTestSequence fresh(GameTestHelper helper, ServerLevel soul, int gate, GateKeeper[] keeper, boolean quiet) {
        return helper.startSequence()
                .thenWaitUntil(() -> GateTests.assertDown(helper, GateState.get(soul), gate))
                .thenWaitUntil(() -> helper.assertTrue(keeper(soul, gate) != null, "gate " + gate + " has no keeper yet"))
                .thenExecute(() -> keeper(soul, gate).discard())
                .thenWaitUntil(() -> keeper[0] = assertKeeper(helper, soul, gate))
                .thenExecute(() -> keeper[0].setNoAi(quiet));
    }

    /** A soul of this kind joined in survival and set down here in Soul Society. */
    static ServerPlayer joined(GameTestHelper helper, ServerLevel soul, SoulData data, Vec3 at) {
        ServerPlayer player = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, at);
        player.setData(SoulAttachments.SOUL, data);
        return player;
    }

    /** Reads this keeper back as if it had yielded so many ticks ago. */
    private static void yieldedAgo(GateKeeper keeper, long ticks) {
        CompoundTag saved = keeper.saveWithoutId(new CompoundTag());
        saved.putLong("BeatenAt", keeper.level().getGameTime() - ticks);
        keeper.load(saved);
    }

    // ---- The body ----

    /**
     * Hakutōmon's keeper, quiet, takes no harm but a blow, none from the world or without a living attacker; a player's
     * blow lands and heals once no other has landed for half a minute, and the one that would kill it leaves it at a tenth
     * and it yields, arms raised, past any blow. Half a minute on it kneels and ten minutes on it stands whole, and killed
     * it is made again whole with its UUID, leaving nothing behind.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1100)
    public static void aKeeperIsHurtOnlyByABlowAndYieldsAtATenth(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = GateTests.hold(helper, WEST);
        sendAway(held, WEST);
        ServerLevel soul = held.soul;
        ServerPlayer[] fighter = new ServerPlayer[1];
        GateKeeper[] keeper = new GateKeeper[1];
        float[] wounded = new float[1];
        fresh(helper, soul, WEST, keeper, true)
                .thenExecute(() -> fighter[0] = joined(helper, soul, ShinigamiTests.soul(SoulState.SHINIGAMI, 400.0F),
                        new Vec3(-521.5, 72.0, 4.5)))
                .thenIdle(2)
                .thenExecute(() -> {
                    GateKeeper k = keeper[0];
                    DamageSources sources = soul.damageSources();
                    for (DamageSource none : List.of(sources.fall(), sources.inFire(), sources.lava(), sources.freeze(),
                            sources.drown(), sources.inWall(), sources.cactus(), sources.sweetBerryBush(), sources.wither(),
                            sources.magic(), sources.generic(), sources.lightningBolt(), sources.mobAttack(k))) {
                        helper.assertTrue(!k.hurt(none, 50.0F) && k.getHealth() == k.getMaxHealth(),
                                none.getMsgId() + " hurt it");
                    }
                    helper.assertTrue(k.hurt(sources.playerAttack(fighter[0]), 5.0F) && k.getHealth() < k.getMaxHealth(),
                            "a player's blow did not land");
                    wounded[0] = k.getHealth();
                })
                // Wounded but not beaten, it heals only once no blow has landed on it for half a minute.
                .thenIdle(GateKeeper.REST_TICKS - 20)
                .thenExecute(() -> helper.assertTrue(keeper[0].getHealth() == wounded[0], "it healed while struck"))
                .thenWaitUntil(() -> helper.assertTrue(keeper[0].getHealth() > wounded[0], "a wounded keeper never heals"))
                .thenExecute(() -> {
                    GateKeeper k = keeper[0];
                    DamageSources sources = soul.damageSources();
                    float floor = k.getMaxHealth() * GateKeeper.YIELD_SHARE;
                    k.invulnerableTime = 0;
                    helper.assertTrue(k.hurt(sources.playerAttack(fighter[0]), 10_000.0F) && k.isAlive()
                                    && Math.abs(k.getHealth() - floor) < 1.0E-3F && k.isBeaten() && k.armsUp()
                                    && TravelTests.lines(fighter[0], GateKeeper.YIELDS_KEY) == 1,
                            "the killing blow left it at " + k.getHealth());
                    k.invulnerableTime = 0;
                    helper.assertTrue(!k.hurt(sources.playerAttack(fighter[0]), 5.0F), "a blow landed on a beaten keeper");
                    helper.assertTrue(k.getType().getCategory() == MobCategory.MISC && k.requiresCustomPersistence()
                            && !k.isPushable() && !(k instanceof Enemy) && k.getPickResult() == null, "the keeper's kind");
                    yieldedAgo(k, GateKeeper.HOLD_TICKS);
                })
                .thenWaitUntil(() -> helper.assertTrue(keeper[0].getPose() == Pose.CROUCHING && !keeper[0].armsUp()
                        && keeper[0].isBeaten(), "half a minute on, the keeper does not kneel"))
                .thenExecute(() -> yieldedAgo(keeper[0], GateKeeper.HEAL_TICKS))
                .thenWaitUntil(() -> helper.assertTrue(keeper[0].getPose() == Pose.STANDING && !keeper[0].isBeaten()
                        && keeper[0].getHealth() == keeper[0].getMaxHealth(), "ten minutes on, the keeper is not whole"))
                .thenExecute(() -> keeper[0].kill())
                .thenWaitUntil(() -> helper.assertTrue(keeper(soul, WEST) != null, "no keeper came back"))
                .thenExecute(() -> {
                    GateKeeper again = assertKeeper(helper, soul, WEST);
                    AABB ground = AABB.ofSize(GateState.post(WEST), 32, 16, 32);
                    helper.assertTrue(again != keeper[0] && again.getUUID().equals(keeper[0].getUUID())
                                    && soul.getEntitiesOfClass(ItemEntity.class, ground).isEmpty()
                                    && soul.getEntitiesOfClass(ExperienceOrb.class, ground).isEmpty(),
                            "the keeper came back as " + again.getUUID() + ", or left something behind");
                })
                .thenSucceed();
    }
}
