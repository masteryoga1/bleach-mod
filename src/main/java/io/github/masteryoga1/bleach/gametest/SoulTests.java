package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests for the soul rules in {@link SoulLogic} and the saved form of {@link SoulData}. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SoulTests {
    private static final float EPSILON = 1.0E-3F;

    private SoulTests() {
    }

    @GameTest(template = "empty")
    public static void soulDataSurvivesSaving(GameTestHelper helper) {
        SoulData data = new SoulData();
        data.markInitialized();
        data.setState(SoulState.SHINIGAMI);
        data.setMaxReiryoku(80.0F);
        data.setReiryoku(12.5F);
        data.setLevel(Stat.ZANJUTSU, 3);
        data.addXp(Stat.ZANJUTSU, 7, 50);
        data.setTier(PowerPath.SHINIGAMI, 1);
        data.setLastCloseCallTime(1234L);
        data.startChain(1000);
        data.setChainTicks(400);
        data.setReiatsuReleased(true);

        Tag saved = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        SoulData loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

        helper.assertTrue(loaded.isInitialized(), "initialized flag was lost");
        helper.assertTrue(loaded.state() == SoulState.SHINIGAMI, "state was " + loaded.state());
        helper.assertTrue(Math.abs(loaded.maxReiryoku() - 80.0F) < EPSILON, "max reiryoku was " + loaded.maxReiryoku());
        helper.assertTrue(Math.abs(loaded.reiryoku() - 12.5F) < EPSILON, "reiryoku was " + loaded.reiryoku());
        helper.assertTrue(loaded.level(Stat.ZANJUTSU) == 3, "Zanjutsu level was " + loaded.level(Stat.ZANJUTSU));
        helper.assertTrue(loaded.xp(Stat.ZANJUTSU) == 7, "Zanjutsu XP was " + loaded.xp(Stat.ZANJUTSU));
        helper.assertTrue(loaded.tier(PowerPath.SHINIGAMI) == 1, "Shinigami tier was " + loaded.tier(PowerPath.SHINIGAMI));
        helper.assertTrue(loaded.level(Stat.HOHO) == 0, "untrained Hohō should stay at 0");
        helper.assertTrue(loaded.lastCloseCallTime() == 1234L, "close-call cooldown was " + loaded.lastCloseCallTime());
        helper.assertTrue(loaded.chainMaxTicks() == 1000 && loaded.chainTicks() == 400,
                "chain was " + loaded.chainTicks() + " of " + loaded.chainMaxTicks());
        helper.assertTrue(loaded.isReiatsuReleased(), "released reiatsu was lost");

        SoulData soul = new SoulData();
        soul.markInitialized();
        soul.setState(SoulState.RUKONGAI);
        ShinigamiProgress progress = new ShinigamiProgress(true, 11L, 22L, 33.5F, 44L, 55L, 6L, 7,
                Optional.of(new BlockPos(100, 64, -200)));
        soul.setShinigami(progress);
        SoulData reloaded = SoulData.CODEC.parse(NbtOps.INSTANCE,
                SoulData.CODEC.encodeStart(NbtOps.INSTANCE, soul).getOrThrow()).getOrThrow();
        helper.assertTrue(reloaded.state() == SoulState.RUKONGAI, "a Rukongai soul loaded as " + reloaded.state());
        helper.assertTrue(reloaded.shinigami().equals(progress), "Shinigami progress loaded as " + reloaded.shinigami());
        helper.assertTrue(reloaded.copy().shinigami().equals(progress), "copying lost the Shinigami progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownNamesDoNotWipeTheSoul(GameTestHelper helper) {
        // A save written by a different version, with a state, a stat and a power this version lacks.
        CompoundTag saved = new CompoundTag();
        saved.putString("state", "visored");
        saved.putBoolean("initialized", true);
        saved.putFloat("max_reiryoku", 50.0F);
        saved.putFloat("reiryoku", 20.0F);
        CompoundTag levels = new CompoundTag();
        levels.putInt("zanjutsu", 4);
        levels.putInt("flight", 9);
        saved.put("stat_levels", levels);
        CompoundTag tiers = new CompoundTag();
        tiers.putInt("quincy", 2);
        tiers.putInt("bount", 1);
        saved.put("path_tiers", tiers);
        CompoundTag shinigami = new CompoundTag();
        shinigami.putString("enrolled", "oops");
        shinigami.putInt("asauchi_issued_at", 5);
        saved.put("shinigami", shinigami);

        SoulData loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.state() == SoulState.HUMAN, "an unknown state should fall back to Human");
        helper.assertTrue(loaded.isInitialized(), "initialized flag was lost");
        helper.assertTrue(Math.abs(loaded.maxReiryoku() - 50.0F) < EPSILON, "max reiryoku was " + loaded.maxReiryoku());
        helper.assertTrue(Math.abs(loaded.reiryoku() - 20.0F) < EPSILON, "reiryoku was " + loaded.reiryoku());
        helper.assertTrue(loaded.level(Stat.ZANJUTSU) == 4, "Zanjutsu level was " + loaded.level(Stat.ZANJUTSU));
        helper.assertTrue(loaded.tier(PowerPath.QUINCY) == 2, "Quincy tier was " + loaded.tier(PowerPath.QUINCY));
        helper.assertFalse(loaded.shinigami().enrolled(), "a bad enrolment flag was not dropped");
        helper.assertTrue(loaded.shinigami().asauchiIssuedAt() == 5L,
                "one bad Shinigami field wiped the rest: " + loaded.shinigami());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void statXpLevelsUp(GameTestHelper helper) {
        SoulData data = new SoulData();
        // Level 0 to 1 needs 20 XP and level 1 to 2 needs 60, so 85 XP is two levels with 5 left over.
        int gained = data.addXp(Stat.HAKUDA, 85, 50);
        helper.assertTrue(gained == 2, "expected 2 levels, got " + gained);
        helper.assertTrue(data.level(Stat.HAKUDA) == 2, "Hakuda level was " + data.level(Stat.HAKUDA));
        helper.assertTrue(data.xp(Stat.HAKUDA) == 5, "Hakuda XP was " + data.xp(Stat.HAKUDA));

        int capped = data.addXp(Stat.HAKUDA, 1_000_000, 3);
        helper.assertTrue(capped == 1 && data.level(Stat.HAKUDA) == 3, "level should stop at the cap");
        helper.assertTrue(data.addXp(Stat.HAKUDA, 100, 3) == 0, "XP past the cap should do nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reiryokuRegenerates(GameTestHelper helper) {
        SoulData data = new SoulData();
        data.setMaxReiryoku(100.0F);
        float expectedPerSecond = (float) (100.0 * BleachConfig.get(BleachConfig.REGEN_FRACTION_PER_SECOND));

        for (int tick = 0; tick < 20; tick++) {
            SoulLogic.regenTick(data, false);
        }
        helper.assertTrue(Math.abs(data.reiryoku() - expectedPerSecond) < EPSILON,
                "expected " + expectedPerSecond + " after one second, got " + data.reiryoku());

        float beforeRest = data.reiryoku();
        for (int tick = 0; tick < 20; tick++) {
            SoulLogic.regenTick(data, true);
        }
        float rested = data.reiryoku() - beforeRest;
        float expectedRested = expectedPerSecond * (float) BleachConfig.get(BleachConfig.REST_REGEN_MULTIPLIER);
        helper.assertTrue(Math.abs(rested - expectedRested) < EPSILON,
                "expected " + expectedRested + " while resting, got " + rested);

        data.setReiryoku(99.99F);
        SoulLogic.regenTick(data, true);
        helper.assertTrue(data.reiryoku() <= data.maxReiryoku(), "reiryoku went over the maximum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runningDryExhausts(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
        SoulData data = new SoulData();
        data.setMaxReiryoku(10.0F);
        data.setReiryoku(10.0F);

        helper.assertFalse(SoulLogic.trySpend(pig, data, 11.0F), "spent more reiryoku than it had");
        helper.assertTrue(Math.abs(data.reiryoku() - 10.0F) < EPSILON, "a failed spend still used reiryoku");
        helper.assertTrue(SoulLogic.trySpend(pig, data, 4.0F), "could not spend 4 of 10");
        helper.assertFalse(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "exhausted before running dry");
        helper.assertTrue(SoulLogic.trySpend(pig, data, 6.0F), "could not spend the last 6");
        helper.assertTrue(data.reiryoku() == 0.0F, "reiryoku should be empty, was " + data.reiryoku());
        helper.assertTrue(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "no Slowness after running dry");
        helper.assertTrue(pig.hasEffect(MobEffects.WEAKNESS), "no Weakness after running dry");
        helper.assertFalse(SoulLogic.trySpend(pig, data, 1.0F), "spent reiryoku while empty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hohoRaisesSpeed(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
        AttributeInstance speed = pig.getAttribute(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed != null, "pig has no movement speed attribute");
        double before = speed.getValue();

        SoulData data = new SoulData();
        data.setLevel(Stat.HOHO, 10);
        SoulLogic.applyStatModifiers(pig, data);
        double expected = speed.getBaseValue() * 10 * BleachConfig.get(BleachConfig.HOHO_SPEED_PER_LEVEL);
        helper.assertTrue(Math.abs(speed.getValue() - before - expected) < 1.0E-6,
                "Hohō 10 added " + (speed.getValue() - before) + " speed, expected " + expected);

        data.setLevel(Stat.HOHO, 0);
        SoulLogic.applyStatModifiers(pig, data);
        helper.assertFalse(speed.hasModifier(SoulLogic.HOHO_SPEED_ID), "Hohō modifier stayed at level 0");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void growthStopsAtCap(GameTestHelper helper) {
        double cap = BleachConfig.get(BleachConfig.MAX_REIRYOKU);
        SoulData data = new SoulData();
        data.setMaxReiryoku((float) cap - 1.0F);
        SoulLogic.grow(data, 5.0);
        helper.assertTrue(Math.abs(data.maxReiryoku() - cap) < EPSILON, "max reiryoku went past the cap: " + data.maxReiryoku());

        SoulData human = new SoulData();
        SoulLogic.initialize(human);
        helper.assertTrue(human.reiryoku() == human.maxReiryoku(), "a new soul should start full");
        helper.assertTrue(SoulLogic.isAware(human), "a new Human should be spiritually aware by default");
        helper.succeed();
    }
}
