package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GateKeeper;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.soul.GateLogic;
import io.github.masteryoga1.bleach.soul.ShakonmakuEvents;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.GateState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the gatekeepers and the gates' rule in the real Soul Society: the keepers' batch works one gate a test, as
 * the digging batch does, and every test that works a keeper sends it away at its end, so a later batch finds each
 * gate's keeper whole at its post, made again with the same UUID.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GateKeeperTests {
    private static final String BATCH = "gate_keeper", DIG = "gate_dig";
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

    // ---- The rule ----

    /**
     * Who may pass a gate, for each soul the mod knows, under the default config, which allows no Quincy hybrid: those it
     * bars are told why, and a Hollow player passes in creative or spectator but not in survival.
     */
    @GameTest(template = "empty")
    public static void whoMayPassAGate(GameTestHelper helper) {
        SoulData masked = ShinigamiTests.soul(SoulState.SHINIGAMI, 400.0F);
        VisoredLogic.setRecord(masked, VisoredLogic.record(masked).withRootedAt(0L).withSubduedAt(1L)
                .withMaskEndsAt(Long.MAX_VALUE / 2));
        SoulData visored = ShinigamiTests.soul(SoulState.SHINIGAMI, 400.0F);
        VisoredLogic.setRecord(visored, VisoredLogic.record(visored).withRootedAt(0L).withSubduedAt(1L));
        SoulData rukongaiQuincy = QuincyTests.in(SoulState.RUKONGAI, QuincyTests.quincy(Bloodline.GEMISCHT, 1, 50.0F));
        Map<String, SoulData> pass = Map.of("a Shinigami", ShinigamiTests.soul(SoulState.SHINIGAMI, 400.0F),
                "a Substitute", ShinigamiTests.substitute(200.0F), "a Visored, unmasked", visored,
                "a Rukongai soul", ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F),
                "a student", ShinigamiTests.student(60.0F));
        Map<String, SoulData> barred = Map.of("a Human", ShinigamiTests.soul(SoulState.HUMAN, 20.0F),
                "a Plus", ShinigamiTests.soul(SoulState.PLUS, 20.0F),
                "a Quincy", QuincyTests.quincy(Bloodline.GEMISCHT, 1, 200.0F), "a Rukongai Quincy", rukongaiQuincy,
                "a Quincy Shinigami", QuincyTests.withShinigamiPower(QuincyTests.quincy(Bloodline.ECHT, 1, 300.0F)),
                "a Hollow", ShinigamiTests.soul(SoulState.HOLLOW, 200.0F),
                "an Arrancar", ShinigamiTests.soul(SoulState.ARRANCAR, 600.0F), "a Visored, masked", masked);
        pass.forEach((who, data) -> helper.assertTrue(GateLogic.mayPass(data), who + " may not pass"));
        barred.forEach((who, data) -> helper.assertTrue(!GateLogic.mayPass(data), who + " may pass"));
        helper.assertTrue(GateLogic.barredKey(barred.get("a Human")).equals("message.bleach.gate.barred.living")
                        && GateLogic.barredKey(barred.get("a Plus")).equals("message.bleach.gate.barred.plus")
                        && GateLogic.barredKey(rukongaiQuincy).equals("message.bleach.gate.barred.quincy")
                        && GateLogic.barredKey(barred.get("a Hollow")).equals("message.bleach.gate.barred.hollow")
                        && GateLogic.barredKey(masked).equals("message.bleach.gate.barred.hollow"),
                "a barred soul is told the wrong reason");
        Player hollow = helper.makeMockPlayer(GameType.SURVIVAL);
        hollow.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 200.0F));
        Player creative = helper.makeMockPlayer(GameType.CREATIVE), spectator = helper.makeMockPlayer(GameType.SPECTATOR);
        creative.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 200.0F));
        spectator.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 200.0F));
        helper.assertTrue(!GateLogic.mayPass(hollow) && GateLogic.mayPass(creative) && GateLogic.mayPass(spectator),
                "a Hollow player in survival, creative and spectator: " + GateLogic.mayPass(hollow) + ", "
                        + GateLogic.mayPass(creative) + ", " + GateLogic.mayPass(spectator));
        helper.succeed();
    }

    // ---- The wicket ----

    /**
     * Shōryūmon's wicket keeps the rule: a Quincy and a Hollow outside cannot open it and are told why; neither a signal
     * nor a mob's way with doors moves it; a Shinigami opens it, and the Quincy still cannot step through it open; from
     * inside, the Quincy shuts it, opens it and walks out.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void theWicketKeepsThePassRule(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = GateTests.hold(helper, EAST);
        ServerLevel soul = held.soul;
        BlockPos wicket = GateState.wicket(EAST), beside = wicket.north();
        held.atEnd(() -> GateTests.shut(soul, wicket));
        // Along the wicket's way as test 5 walks it: out on the Rukongai's side, against the wicket, past it, inside.
        double x = wicket.getX(), z = wicket.getZ() + 0.6;
        Vec3 out = new Vec3(x + 3.5, 72.0, z), against = new Vec3(x + 1.5, 72.0, z), behind = new Vec3(x + 2.5, 72.0, z);
        Vec3 through = new Vec3(x - 0.5, 72.0, z), inside = new Vec3(x - 2.5, 72.0, z);
        BlockHitResult touch = new BlockHitResult(Vec3.atCenterOf(wicket), Direction.EAST, wicket, false);
        ServerPlayer[] souls = new ServerPlayer[3];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.assertDown(helper, GateState.get(soul), EAST))
                .thenExecute(() -> {
                    // Read only now: the gate down proves the house stamped, so it is the house that is put back.
                    BlockState besideWas = soul.getBlockState(beside);
                    held.atEnd(() -> soul.setBlock(beside, besideWas, Block.UPDATE_ALL));
                    souls[0] = joined(helper, soul, QuincyTests.quincy(Bloodline.GEMISCHT, 1, 200.0F), against);
                    souls[1] = joined(helper, soul, ShinigamiTests.soul(SoulState.HOLLOW, 200.0F), behind);
                    souls[2] = joined(helper, soul, ShinigamiTests.soul(SoulState.SHINIGAMI, 400.0F), out);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    for (int barred = 0; barred < 2; barred++) {
                        InteractionResult used = use(souls[barred], soul, touch);
                        helper.assertTrue(used.consumesAction() && !open(soul, wicket), "a barred soul's hand gave "
                                + used + " and the wicket is " + soul.getBlockState(wicket));
                    }
                    helper.assertTrue(TravelTests.lines(souls[0], "message.bleach.gate.barred.quincy") == 1
                            && TravelTests.lines(souls[1], "message.bleach.gate.barred.hollow") == 1, "the barred were told "
                            + TravelTests.said(souls[0]) + " and " + TravelTests.said(souls[1]));
                    soul.setBlock(beside, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    helper.assertTrue(!open(soul, wicket), "a signal opened the wicket");
                    // Every mob at a door, and a wind charge, opens it this way.
                    ModBlocks.WICKET.get().setOpen(null, soul, soul.getBlockState(wicket), wicket, true);
                    helper.assertTrue(!open(soul, wicket), "a mob's way with doors opened the wicket");
                    InteractionResult used = use(souls[2], soul, touch);
                    helper.assertTrue(used.consumesAction() && open(soul, wicket), "the Shinigami could not open it");
                })
                .thenIdle(1)
                .thenExecute(() -> GateTests.step(helper, souls[0], through, against, "the Quincy stepping in, open"))
                .thenExecute(() -> souls[0].teleportTo(inside.x, inside.y, inside.z))
                .thenIdle(2)
                .thenExecute(() -> {
                    use(souls[0], soul, touch);
                    helper.assertTrue(!open(soul, wicket), "the Quincy inside could not shut it");
                    use(souls[0], soul, touch);
                    helper.assertTrue(open(soul, wicket), "the Quincy inside could not open it");
                    GateTests.step(helper, souls[0], through, through, "the Quincy going to the wicket");
                })
                .thenIdle(1)
                .thenExecute(() -> GateTests.step(helper, souls[0], against, against, "the Quincy leaving by it"))
                .thenSucceed();
    }

    private static InteractionResult use(ServerPlayer player, ServerLevel soul, BlockHitResult touch) {
        return player.gameMode.useItemOn(player, soul, player.getMainHandItem(), InteractionHand.MAIN_HAND, touch);
    }

    /** Whether both halves of the wicket stand open. */
    private static boolean open(ServerLevel soul, BlockPos wicket) {
        return soul.getBlockState(wicket).getValue(DoorBlock.OPEN)
                && soul.getBlockState(wicket.above()).getValue(DoorBlock.OPEN);
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

    // ---- Digging round a gate ----

    /**
     * Shuwaimon down: a Shinigami digging through the left pier, under the gate's row, over the gate by the doorway's head,
     * from outside it and from the slot, or up through the roof on the Rukongai's half, is set back at the gate's outer
     * face or at the lid, however it moves; a pearl that would set it down part through the gate is refused. Every cell
     * dug is put back.
     */
    @GameTest(template = "empty", batch = DIG, timeoutTicks = 400)
    public static void diggingRoundAShutGateLeadsNowhere(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = GateTests.hold(helper, SOUTH);
        ServerLevel soul = held.soul;
        List<BlockPos> dug = new ArrayList<>();
        for (int z = 512; z <= 519; z++) {
            dug.add(new BlockPos(-7, 72, z));
            dug.add(new BlockPos(-7, 73, z));
        }
        for (int z = 515; z <= 518; z++) {
            dug.add(new BlockPos(0, 70, z));
            dug.add(new BlockPos(0, 71, z));
        }
        for (int y = 97; y <= 111; y++) {
            dug.add(new BlockPos(0, y, 517));
            dug.add(new BlockPos(0, y, 518));
        }
        dug.add(new BlockPos(0, 97, 515));
        dug.add(new BlockPos(0, 98, 515));
        // Each way round: where the soul starts, the steps it takes, and the last step, which is set back.
        Vec3[][] ways = {
                {v(-6.5, 72.0, 521.5), v(-6.5, 72.0, 519.5), v(-6.5, 72.0, 517.5), v(-6.5, 72.0, 515.5)},
                {v(0.5, 72.0, 518.5), v(0.5, 70.0, 518.5), v(0.5, 70.0, 517.5), v(0.5, 70.0, 515.5)},
                {v(0.5, 95.0, 518.5), v(0.5, 97.0, 518.5), v(0.5, 97.0, 517.5), v(0.5, 97.0, 516.5)},
                {v(0.5, 96.3, 517.35), v(0.5, 97.1, 515.5)},
                {v(0.5, 105.0, 518.5), v(0.5, 110.0, 518.5), v(0.5, 112.0, 518.5)}};
        ServerPlayer player = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, ways[0][0]);
        GameTestSequence sequence = helper.startSequence()
                .thenWaitUntil(() -> GateTests.assertDown(helper, GateState.get(soul), SOUTH))
                .thenExecute(() -> {
                    // Read only now: the gate down proves the house stamped, so it is the house that is put back.
                    List<BlockState> was = dug.stream().map(soul::getBlockState).toList();
                    held.atEnd(() -> {
                        for (int i = 0; i < dug.size(); i++) {
                            soul.setBlock(dug.get(i), was.get(i), Block.UPDATE_CLIENTS);
                        }
                    });
                    dug.forEach(cell -> soul.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
                });
        for (Vec3[] way : ways) {
            sequence = sequence.thenExecute(() -> player.teleportTo(way[0].x, way[0].y, way[0].z)).thenIdle(2);
            for (int i = 1; i < way.length; i++) {
                Vec3 to = way[i], expected = i == way.length - 1 ? way[i - 1] : way[i];
                sequence = sequence.thenExecute(() -> GateTests.step(helper, player, to, expected, "the soul digging round"))
                        .thenIdle(1);
            }
        }
        sequence.thenExecute(() -> {
                    helper.assertTrue(TravelTests.lines(player, ShakonmakuEvents.TURNED_BACK_KEY) >= 1, "it was never told");
                    ThrownEnderpearl pearl = new ThrownEnderpearl(soul, player);
                    Vec3 into = v(0.5, 73.0, 516.9);
                    helper.assertTrue(EventHooks.onEnderPearlLand(player, into.x, into.y, into.z, pearl, 5.0F,
                            BlockHitResult.miss(into, Direction.NORTH, BlockPos.containing(into))).isCanceled(),
                            "a pearl set the soul down part through the gate");
                })
                .thenSucceed();
    }

    private static Vec3 v(double x, double y, double z) {
        return new Vec3(x, y, z);
    }
}
