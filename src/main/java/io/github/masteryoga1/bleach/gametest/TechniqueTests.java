package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.ReishiFootingBlock;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.soul.FlashStep;
import io.github.masteryoga1.bleach.soul.FootingState;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests for the Shinigami techniques: reishi footing and Shunpo. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class TechniqueTests {
    private static final long NOW = 10_000L;
    private static final float EPSILON = 1.0E-5F;

    private TechniqueTests() {
    }

    // ---- Reishi footing ----

    @GameTest(template = "empty")
    public static void reishiFootingNeedsTraining(GameTestHelper helper) {
        int min = BleachConfig.get(BleachConfig.FOOTING_MIN_HOHO_LEVEL);
        SoulData shinigami = ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F);
        if (min > 0) {
            shinigami.setLevel(Stat.HOHO, min - 1);
            helper.assertFalse(ShinigamiLogic.canUseReishiFooting(shinigami, NOW), "footing came before the Hohō it needs");
        }
        shinigami.setLevel(Stat.HOHO, min);
        helper.assertTrue(ShinigamiLogic.canUseReishiFooting(shinigami, NOW), "a trained Shinigami cannot use footing");

        SoulData student = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        ShinigamiLogic.enroll(student);
        student.setLevel(Stat.HOHO, min);
        helper.assertTrue(ShinigamiLogic.canUseReishiFooting(student, NOW), "a trained student cannot use footing");

        SoulData substitute = ShinigamiTests.substitute(60.0F);
        substitute.setLevel(Stat.HOHO, min);
        helper.assertTrue(ShinigamiLogic.canUseReishiFooting(substitute, NOW), "a trained Substitute cannot use footing");

        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 200.0F);
        human.setLevel(Stat.HOHO, 50);
        helper.assertFalse(ShinigamiLogic.canUseReishiFooting(human, NOW), "a Human stood on reishi");
        SoulData hollow = ShinigamiTests.soul(SoulState.HOLLOW, 200.0F);
        hollow.setTier(PowerPath.HOLLOW, 1);
        hollow.setLevel(Stat.HOHO, 50);
        helper.assertFalse(ShinigamiLogic.canUseReishiFooting(hollow, NOW), "a Hollow stood on reishi");

        shinigami.setShinigami(shinigami.shinigami().withPowerLentUntil(NOW + 1));
        helper.assertFalse(ShinigamiLogic.canUseReishiFooting(shinigami, NOW), "a Shinigami whose power is lent stood on reishi");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reishiFootingHoldsOnlyItsUser(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 2, 1);
        helper.setBlock(relative, ModBlocks.REISHI_FOOTING.get());
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getBlockState(relative);
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.01, pos.getZ() + 0.5);
        FootingState footing = player.getData(SoulAttachments.FOOTING);

        helper.assertTrue(collision(state, level, pos, player).isEmpty(), "footing held a player not using it");
        footing.active = true;
        footing.anchorY = pos.getY();
        helper.assertTrue(collision(state, level, pos, player) == ReishiFootingBlock.PLATE, "footing did not hold its user");
        footing.anchorY = pos.getY() - 1;
        helper.assertTrue(collision(state, level, pos, player).isEmpty(), "footing held at the wrong height");
        footing.anchorY = pos.getY();
        player.setShiftKeyDown(true);
        helper.assertTrue(collision(state, level, pos, player).isEmpty(), "footing held a sneaking player");
        player.setShiftKeyDown(false);
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        helper.assertTrue(collision(state, level, pos, player).isEmpty(), "footing held a player from below");

        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 3, 2, 3);
        helper.assertTrue(collision(state, level, pos, pig).isEmpty(), "footing held a pig");
        helper.assertTrue(state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty(), "footing collides with nothing in particular");
        helper.assertTrue(state.getShape(level, pos, CollisionContext.of(player)).isEmpty(), "footing can be aimed at");
        helper.succeed();
    }

    private static VoxelShape collision(BlockState state, ServerLevel level, BlockPos pos, Entity entity) {
        return state.getCollisionShape(level, pos, CollisionContext.of(entity));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reishiFootingFadesAway(GameTestHelper helper) {
        helper.setBlock(1, 2, 1, ModBlocks.REISHI_FOOTING.get());
        helper.succeedWhen(() -> helper.assertBlockNotPresent(ModBlocks.REISHI_FOOTING.get(), 1, 2, 1));
    }

    @GameTest(template = "empty")
    public static void landingOnFootingDealsNoDamage(GameTestHelper helper) {
        helper.setBlock(1, 2, 1, ModBlocks.REISHI_FOOTING.get());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        float health = player.getHealth();
        ModBlocks.REISHI_FOOTING.get().fallOn(helper.getLevel(), helper.getLevel().getBlockState(pos), pos, player, 10.0F);
        helper.assertTrue(player.getHealth() == health, "landing on footing hurt");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void footingDrainShrinksWithHoho(GameTestHelper helper) {
        SoulData data = ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F);
        double full = 200.0 * BleachConfig.get(BleachConfig.FOOTING_DRAIN_FRACTION_PER_SECOND) / 20.0;
        double perLevel = BleachConfig.get(BleachConfig.FOOTING_HOHO_DRAIN_REDUCTION_PER_LEVEL);
        data.setLevel(Stat.HOHO, 3);
        float novice = ShinigamiLogic.footingDrainPerTick(data);
        data.setLevel(Stat.HOHO, 30);
        float adept = ShinigamiLogic.footingDrainPerTick(data);
        data.setLevel(Stat.HOHO, 50);
        float master = ShinigamiLogic.footingDrainPerTick(data);
        helper.assertTrue(novice <= full + EPSILON && (perLevel <= 0.0 || adept < novice), "Hohō did not lower the drain: "
                + novice + " at 3, " + adept + " at 30");
        double expected = full * Math.max(0.25, 1.0 - 50 * perLevel);
        helper.assertTrue(Math.abs(master - expected) < EPSILON, "the drain at Hohō 50 is " + master + ", not " + expected);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void footingAnchorIsUnderTheFeet(GameTestHelper helper) {
        helper.assertTrue(ShinigamiLogic.footingAnchor(64.0) == 63, "feet at 64.0 anchor at " + ShinigamiLogic.footingAnchor(64.0));
        helper.assertTrue(ShinigamiLogic.footingAnchor(64.7) == 63, "feet at 64.7 anchor at " + ShinigamiLogic.footingAnchor(64.7));
        helper.assertTrue(ShinigamiLogic.footingAnchor(63.99) == 62, "feet at 63.99 anchor at " + ShinigamiLogic.footingAnchor(63.99));
        helper.assertTrue(ShinigamiLogic.footingAnchor(-0.5) == -2, "feet at -0.5 anchor at " + ShinigamiLogic.footingAnchor(-0.5));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void regenerationPausesOnFooting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SoulData data = ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F);
        player.setData(SoulAttachments.SOUL, data);
        helper.assertTrue(SoulEvents.mayRegenerate(player, data), "a resting Shinigami does not regenerate");
        player.getData(SoulAttachments.FOOTING).active = true;
        helper.assertFalse(SoulEvents.mayRegenerate(player, data), "reiryoku refills while standing on footing");
        helper.succeed();
    }

    // ---- Shunpo ----

    @GameTest(template = "empty")
    public static void flashStepDistanceGrowsWithHoho(GameTestHelper helper) {
        double base = BleachConfig.get(BleachConfig.SHUNPO_BASE_DISTANCE);
        double perLevel = BleachConfig.get(BleachConfig.SHUNPO_DISTANCE_PER_HOHO_LEVEL);
        double max = BleachConfig.get(BleachConfig.SHUNPO_MAX_DISTANCE);
        SoulData data = ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F);
        for (int hoho : new int[] {5, 10, 50}) {
            data.setLevel(Stat.HOHO, hoho);
            double expected = Math.min(max, base + hoho * perLevel);
            double distance = ShinigamiLogic.flashStepDistance(data, -1);
            helper.assertTrue(Math.abs(distance - expected) < EPSILON, "Hohō " + hoho + " flash steps " + distance
                    + " blocks, not " + expected);
        }
        data.setLevel(Stat.HOHO, 10);
        double full = ShinigamiLogic.flashStepDistance(data, -1);
        double slowed = ShinigamiLogic.flashStepDistance(data, 1);
        helper.assertTrue(Math.abs(slowed - full * 0.5) < EPSILON, "Slowness II left " + slowed + " of " + full + " blocks");
        helper.assertTrue(ShinigamiLogic.flashStepDistance(data, 3) < 1.0, "Slowness IV still allows a flash step");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flashStepCostAndCooldownScale(GameTestHelper helper) {
        int start = BleachConfig.get(BleachConfig.SHUNPO_MIN_HOHO_LEVEL);
        int mastery = BleachConfig.get(BleachConfig.SHUNPO_MASTERY_HOHO_LEVEL);
        double fraction = BleachConfig.get(BleachConfig.SHUNPO_COST_FRACTION);
        double masterFraction = BleachConfig.get(BleachConfig.SHUNPO_MASTER_COST_FRACTION);
        SoulData data = ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F);

        data.setLevel(Stat.HOHO, start);
        float cost = ShinigamiLogic.flashStepCost(data);
        helper.assertTrue(Math.abs(cost - Math.max(1.0, 200.0 * fraction)) < EPSILON,
                "the first flash step costs " + cost);
        int cooldown = ShinigamiLogic.flashStepCooldownTicks(data);
        helper.assertTrue(cooldown == BleachConfig.get(BleachConfig.SHUNPO_COOLDOWN_TICKS),
                "the first flash step cools down for " + cooldown + " ticks");

        data.setLevel(Stat.HOHO, mastery);
        float masterCost = ShinigamiLogic.flashStepCost(data);
        helper.assertTrue(Math.abs(masterCost - Math.max(1.0, 200.0 * masterFraction)) < EPSILON,
                "a master's flash step costs " + masterCost);
        int masterCooldown = ShinigamiLogic.flashStepCooldownTicks(data);
        helper.assertTrue(masterCooldown == BleachConfig.get(BleachConfig.SHUNPO_MASTER_COOLDOWN_TICKS),
                "a master's flash step cools down for " + masterCooldown + " ticks");

        SoulData faint = ShinigamiTests.soul(SoulState.SHINIGAMI, 10.0F);
        faint.setLevel(Stat.HOHO, start);
        float faintCost = ShinigamiLogic.flashStepCost(faint);
        helper.assertTrue(Math.abs(faintCost - Math.max(1.0, 10.0 * fraction)) < EPSILON,
                "a faint soul's flash step costs " + faintCost + ", not at least 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyTrainedSoulsFlashStep(GameTestHelper helper) {
        int min = BleachConfig.get(BleachConfig.SHUNPO_MIN_HOHO_LEVEL);
        SoulData shinigami = ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F);
        if (min > 0) {
            shinigami.setLevel(Stat.HOHO, min - 1);
            helper.assertFalse(ShinigamiLogic.canFlashStep(shinigami, NOW), "Shunpo came before the Hohō it needs");
        }
        shinigami.setLevel(Stat.HOHO, min);
        helper.assertTrue(ShinigamiLogic.canFlashStep(shinigami, NOW), "a trained Shinigami cannot flash step");

        SoulData student = ShinigamiTests.student(40.0F);
        student.setLevel(Stat.HOHO, min);
        helper.assertTrue(ShinigamiLogic.canFlashStep(student, NOW), "a trained student cannot flash step");

        SoulData substitute = ShinigamiTests.substitute(60.0F);
        if (min > 0) {
            substitute.setLevel(Stat.HOHO, min - 1);
            helper.assertFalse(ShinigamiLogic.canFlashStep(substitute, NOW), "an untrained Substitute flash stepped");
        }
        substitute.setLevel(Stat.HOHO, min);
        helper.assertTrue(ShinigamiLogic.canFlashStep(substitute, NOW), "a trained Substitute cannot flash step");

        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 200.0F);
        human.setLevel(Stat.HOHO, 50);
        helper.assertFalse(ShinigamiLogic.canFlashStep(human, NOW), "a Human flash stepped");
        SoulData hollow = ShinigamiTests.soul(SoulState.HOLLOW, 200.0F);
        hollow.setTier(PowerPath.HOLLOW, 1);
        hollow.setLevel(Stat.HOHO, 50);
        helper.assertFalse(ShinigamiLogic.canFlashStep(hollow, NOW), "a Hollow flash stepped");

        shinigami.setShinigami(shinigami.shinigami().withPowerLentUntil(NOW + 1));
        helper.assertFalse(ShinigamiLogic.canFlashStep(shinigami, NOW), "a Shinigami whose power is lent flash stepped");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flashStepDirectionFollowsInput(GameTestHelper helper) {
        assertNear(helper, FlashStep.direction(0.0F, 0.0F, 1.0F, 0.0F, false), new Vec3(0.0, 0.0, 1.0), "forward at yaw 0");
        assertNear(helper, FlashStep.direction(90.0F, 0.0F, 1.0F, 0.0F, false), new Vec3(-1.0, 0.0, 0.0), "forward at yaw 90");
        assertNear(helper, FlashStep.direction(0.0F, 0.0F, 0.0F, 1.0F, false), new Vec3(1.0, 0.0, 0.0), "left at yaw 0");
        assertNear(helper, FlashStep.direction(0.0F, 0.0F, 0.0F, 0.0F, false), new Vec3(0.0, 0.0, 1.0), "no input");
        assertNear(helper, FlashStep.direction(0.0F, 0.0F, -1.0F, 0.0F, true), new Vec3(0.0, 0.0, -1.0), "back in the air");
        Vec3 ground = FlashStep.direction(0.0F, -45.0F, 1.0F, 0.0F, false);
        helper.assertTrue(ground.y == 0.0, "a grounded flash step left the ground: " + ground);
        Vec3 air = FlashStep.direction(0.0F, -80.0F, 1.0F, 0.0F, true);
        helper.assertTrue(Math.abs(air.y - Math.sqrt(0.5)) < 1.0E-3 && air.z > 0.0,
                "an upward flash step in the air was not held to 45°: " + air);
        helper.succeed();
    }

    private static void assertNear(GameTestHelper helper, Vec3 actual, Vec3 expected, String what) {
        helper.assertTrue(actual.distanceTo(expected) < 1.0E-3, what + " goes " + actual + ", not " + expected);
    }

    // The lane template: a 12 by 3 stone floor at relative y 1, open above, running along +X.

    @GameTest(template = "lane")
    public static void flashStepStopsAtWalls(GameTestHelper helper) {
        helper.setBlock(5, 2, 1, Blocks.STONE);
        helper.setBlock(5, 3, 1, Blocks.STONE);
        Vec3 end = dashAlongLane(helper, 2.0, 7.0, true).end();
        helper.assertTrue(end.x + 0.3 <= 5.0 + EPSILON, "the flash step went into the wall, ending at " + end);

        helper.setBlock(5, 2, 1, Blocks.IRON_BARS);
        helper.setBlock(5, 3, 1, Blocks.IRON_BARS);
        Vec3 barred = dashAlongLane(helper, 2.0, 7.0, true).end();
        helper.assertTrue(barred.x + 0.3 <= 5.5, "the flash step went through iron bars, ending at " + barred);
        helper.succeed();
    }

    @GameTest(template = "lane")
    public static void flashStepClimbsAStep(GameTestHelper helper) {
        helper.setBlock(5, 2, 1, Blocks.STONE);
        Vec3 onStep = dashAlongLane(helper, 2.0, 4.0, true).end();
        helper.assertTrue(onStep.x > 5.0 && Math.abs(onStep.y - 3.0) < EPSILON, "the flash step did not climb the step: " + onStep);
        FlashStep.DashPath past = dashAlongLane(helper, 2.0, 7.0, true);
        helper.assertTrue(past.end().x > 7.5 && Math.abs(past.end().y - 2.0) < EPSILON && past.supported(),
                "the flash step did not come down past the step: " + past);
        helper.succeed();
    }

    @GameTest(template = "lane")
    public static void flashStepCrossesAGap(GameTestHelper helper) {
        helper.setBlock(5, 1, 1, Blocks.AIR);
        helper.setBlock(5, 0, 1, Blocks.AIR);
        FlashStep.DashPath path = dashAlongLane(helper, 2.0, 7.0, true);
        helper.assertTrue(path.end().x > 6.0 && Math.abs(path.end().y - 2.0) < EPSILON && path.supported(),
                "the flash step did not carry on across the gap: " + path);
        helper.succeed();
    }

    @GameTest(template = "lane")
    public static void flashStepStopsBeforeLava(GameTestHelper helper) {
        helper.setBlock(5, 1, 1, Blocks.LAVA);
        Vec3 end = dashAlongLane(helper, 2.0, 7.0, true).end();
        helper.assertTrue(end.x < 5.0, "the flash step ran out over lava, ending at " + end);
        helper.succeed();
    }

    @GameTest(template = "lane")
    public static void flashStepCrossesABridgeOverLava(GameTestHelper helper) {
        for (int x = 2; x <= 9; x++) {
            helper.setBlock(x, 0, 1, Blocks.LAVA);
        }
        FlashStep.DashPath path = dashAlongLane(helper, 2.0, 6.0, true);
        helper.assertTrue(Math.abs(path.end().x - 7.5) < EPSILON && Math.abs(path.end().y - 2.0) < EPSILON && path.supported(),
                "lava under the floor stopped the flash step at " + path);
        helper.succeed();
    }

    @GameTest(template = "lane")
    public static void flashStepRunsItsLength(GameTestHelper helper) {
        FlashStep.DashPath path = dashAlongLane(helper, 2.0, 6.0, true);
        helper.assertTrue(Math.abs(path.end().x - 7.5) < EPSILON && Math.abs(path.end().y - 2.0) < EPSILON && path.supported(),
                "a clear 6 block flash step ended at " + path);
        FlashStep.DashPath air = dashAlongLane(helper, 3.0, 6.0, false);
        helper.assertTrue(Math.abs(air.end().x - 7.5) < EPSILON && Math.abs(air.end().y - 3.0) < EPSILON && !air.supported(),
                "a clear 6 block flash step in the air ended at " + air);
        helper.succeed();
    }

    /**
     * Sweeps a flash step along the lane from x 1.5, with the feet at relative height {@code feetY}.
     * The end comes back in relative coordinates.
     */
    private static FlashStep.DashPath dashAlongLane(GameTestHelper helper, double feetY, double distance, boolean grounded) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 start = helper.absoluteVec(new Vec3(1.5, feetY, 1.5));
        player.setPos(start.x, start.y, start.z);
        FlashStep.DashPath path = FlashStep.sweep(helper.getLevel(), player, player.getBoundingBox(),
                new Vec3(1.0, 0.0, 0.0), distance, grounded);
        return new FlashStep.DashPath(helper.relativeVec(path.end()), path.supported());
    }
}
