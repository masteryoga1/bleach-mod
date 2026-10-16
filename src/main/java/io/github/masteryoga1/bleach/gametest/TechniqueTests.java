package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.ReishiFootingBlock;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModBlocks;
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
import net.minecraft.world.level.block.state.BlockState;
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
}
