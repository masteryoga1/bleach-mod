package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests for the rules of the Shinigami path in {@link ShinigamiLogic}. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ShinigamiTests {
    private static final float EPSILON = 1.0E-3F;
    private static final long NOW = 100_000L;
    private static final int TICKS_PER_MINUTE = 1200;

    private ShinigamiTests() {
    }

    static SoulData soul(SoulState state, float maxReiryoku) {
        SoulData data = new SoulData();
        SoulLogic.initialize(data);
        SoulLogic.changeState(data, state);
        data.setMaxReiryoku(maxReiryoku);
        data.setReiryoku(maxReiryoku);
        return data;
    }

    static SoulData substitute(float maxReiryoku) {
        SoulData data = soul(SoulState.HUMAN, maxReiryoku);
        data.setTier(PowerPath.SHINIGAMI, 1);
        return data;
    }

    static SoulData student(float maxReiryoku) {
        SoulData data = soul(SoulState.RUKONGAI, maxReiryoku);
        ShinigamiLogic.enroll(data);
        return data;
    }

    /** A Shinigami who lent their power at {@link #NOW}. */
    static SoulData lentGiver() {
        SoulData giver = soul(SoulState.SHINIGAMI, 200.0F);
        ShinigamiLogic.lend(giver, NOW);
        return giver;
    }

    @GameTest(template = "empty")
    public static void changeStateToShinigamiGrantsATier(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 40.0F);
        helper.assertTrue(data.tier(PowerPath.SHINIGAMI) == 1, "a Shinigami by command has tier " + data.tier(PowerPath.SHINIGAMI));
        data.setTier(PowerPath.SHINIGAMI, 3);
        SoulLogic.changeState(data, SoulState.SHINIGAMI);
        helper.assertTrue(data.tier(PowerPath.SHINIGAMI) == 3, "changing state lowered a tier");
        SoulData rukongai = soul(SoulState.RUKONGAI, 40.0F);
        helper.assertTrue(rukongai.chainMaxTicks() == 0, "a Rukongai soul has a chain");
        helper.assertTrue(rukongai.state().isSpirit(), "a Rukongai soul is not a spirit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void konsoFreesAPlus(GameTestHelper helper) {
        SoulData plus = soul(SoulState.PLUS, 40.0F);
        plus.setReiatsuReleased(true);
        plus.setLevel(Stat.HOHO, 4);
        helper.assertTrue(ShinigamiLogic.konso(plus), "Konsō did not take");
        helper.assertTrue(plus.state() == SoulState.RUKONGAI, "Konsō made a " + plus.state());
        helper.assertTrue(plus.chainMaxTicks() == 0 && plus.chainTicks() == 0, "the chain stayed after Konsō");
        helper.assertFalse(plus.isReiatsuReleased(), "the release outlasted Konsō");
        helper.assertTrue(plus.level(Stat.HOHO) == 4 && Math.abs(plus.maxReiryoku() - 40.0F) < EPSILON,
                "Konsō changed the soul's training");

        SoulData human = soul(SoulState.HUMAN, 40.0F);
        helper.assertFalse(ShinigamiLogic.konso(human), "Konsō worked on a living Human");
        helper.assertTrue(human.state() == SoulState.HUMAN, "a failed Konsō changed the Human");
        helper.assertFalse(ShinigamiLogic.konso(soul(SoulState.HOLLOW, 40.0F)), "Konsō worked on a Hollow");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void konsoRewardIsCappedPerDay(GameTestHelper helper) {
        SoulData shinigami = soul(SoulState.SHINIGAMI, 100.0F);
        int perDay = BleachConfig.get(BleachConfig.KONSO_REWARDED_PER_DAY);
        double growth = BleachConfig.get(BleachConfig.KONSO_GROWTH);
        for (int i = 0; i < perDay; i++) {
            helper.assertTrue(ShinigamiLogic.rewardKonso(shinigami, NOW + i), "Konsō " + (i + 1) + " was not rewarded");
        }
        float grown = (float) (100.0 + perDay * growth);
        helper.assertTrue(Math.abs(shinigami.maxReiryoku() - grown) < EPSILON, "max reiryoku was " + shinigami.maxReiryoku());
        helper.assertFalse(ShinigamiLogic.rewardKonso(shinigami, NOW + perDay), "a Konsō past the daily limit was rewarded");
        helper.assertTrue(Math.abs(shinigami.maxReiryoku() - grown) < EPSILON, "an unrewarded Konsō grew reiryoku");
        helper.assertTrue(ShinigamiLogic.rewardKonso(shinigami, NOW + 24_000L), "a new day did not reset the count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyShinigamiPowerPerformsKonso(GameTestHelper helper) {
        helper.assertFalse(ShinigamiLogic.canPerformKonso(soul(SoulState.HUMAN, 400.0F), NOW), "a Human performed Konsō");
        helper.assertTrue(ShinigamiLogic.canPerformKonso(substitute(60.0F), NOW), "a Substitute could not perform Konsō");
        helper.assertTrue(ShinigamiLogic.canPerformKonso(soul(SoulState.SHINIGAMI, 60.0F), NOW), "a Shinigami could not perform Konsō");
        helper.assertFalse(ShinigamiLogic.canPerformKonso(lentGiver(), NOW + 1), "a Shinigami without their power performed Konsō");
        helper.assertFalse(ShinigamiLogic.canPerformKonso(student(60.0F), NOW), "a student performed Konsō");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enrollmentNeedsAPassedOnSoul(GameTestHelper helper) {
        SoulData rukongai = soul(SoulState.RUKONGAI, 40.0F);
        helper.assertTrue(ShinigamiLogic.canEnroll(rukongai), "a Rukongai soul could not enroll");
        helper.assertFalse(ShinigamiLogic.isStudent(rukongai), "a Rukongai soul was a student before enrolling");
        ShinigamiLogic.enroll(rukongai);
        helper.assertTrue(ShinigamiLogic.isStudent(rukongai), "enrolling did not make a student");
        helper.assertFalse(ShinigamiLogic.canEnroll(rukongai), "a student could enroll again");
        for (SoulState state : List.of(SoulState.HUMAN, SoulState.PLUS, SoulState.SHINIGAMI, SoulState.HOLLOW)) {
            helper.assertFalse(ShinigamiLogic.canEnroll(soul(state, 40.0F)), "a " + state + " could enroll");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void graduationChecksTheZankensoki(GameTestHelper helper) {
        SoulData data = student(40.0F);
        data.setLevel(Stat.ZANJUTSU, 4);
        data.setLevel(Stat.HAKUDA, 5);
        data.setLevel(Stat.HOHO, 5);
        data.setLevel(Stat.KIDO, 5);
        helper.assertTrue(ShinigamiLogic.graduationShortfall(data).equals(List.of(Stat.ZANJUTSU)),
                "short of Zanjutsu, the exams found " + ShinigamiLogic.graduationShortfall(data));
        helper.assertFalse(ShinigamiLogic.canGraduate(data), "graduated without the Zanjutsu");

        data.setLevel(Stat.ZANJUTSU, 5);
        data.setLevel(Stat.KIDO, 4);
        data.setLevel(Stat.REIATSU_CONTROL, 50);
        helper.assertTrue(ShinigamiLogic.graduationShortfall(data).equals(List.of(Stat.KIDO)),
                "short of Kidō, the exams found " + ShinigamiLogic.graduationShortfall(data));

        data.setLevel(Stat.KIDO, 5);
        data.setReiryoku(5.0F);
        helper.assertTrue(ShinigamiLogic.canGraduate(data), "a ready student could not graduate");
        helper.assertFalse(ShinigamiLogic.canGraduate(soul(SoulState.RUKONGAI, 40.0F)), "a soul graduated without enrolling");
        ShinigamiLogic.graduate(data);
        helper.assertTrue(data.state() == SoulState.SHINIGAMI, "a graduate became a " + data.state());
        helper.assertTrue(data.tier(PowerPath.SHINIGAMI) == 1, "a graduate has Shinigami tier " + data.tier(PowerPath.SHINIGAMI));
        float grown = (float) (40.0 + BleachConfig.get(BleachConfig.AWAKENING_GROWTH));
        helper.assertTrue(Math.abs(data.maxReiryoku() - grown) < EPSILON, "a graduate's max reiryoku was " + data.maxReiryoku());
        helper.assertTrue(data.reiryoku() == data.maxReiryoku(), "a graduate did not start full");
        helper.assertFalse(data.shinigami().enrolled(), "a graduate is still enrolled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void substituteKeepsTheirBody(GameTestHelper helper) {
        SoulData giver = soul(SoulState.SHINIGAMI, 200.0F);
        SoulData human = soul(SoulState.HUMAN, 40.0F);
        float gain = ShinigamiLogic.transferFromShinigami(giver, human, NOW);

        helper.assertTrue(Math.abs(giver.maxReiryoku() - 100.0F) < EPSILON, "the giver kept " + giver.maxReiryoku());
        helper.assertTrue(Math.abs(giver.shinigami().lentReiryoku() - 100.0F) < EPSILON, "the giver lent " + giver.shinigami().lentReiryoku());
        helper.assertTrue(giver.reiryoku() == 0.0F, "the giver kept reiryoku " + giver.reiryoku());
        helper.assertTrue(ShinigamiLogic.isPowerLent(giver, NOW + 1), "the giver's power is not marked lent");
        helper.assertTrue(giver.shinigami().gavePowerAt() == NOW, "the crime was not remembered");

        float growth = (float) BleachConfig.get(BleachConfig.AWAKENING_GROWTH);
        helper.assertTrue(Math.abs(gain - growth) < EPSILON, "the Human gained " + gain);
        helper.assertTrue(human.state() == SoulState.HUMAN, "the Human became a " + human.state());
        helper.assertTrue(human.tier(PowerPath.SHINIGAMI) == 1, "the Human has Shinigami tier " + human.tier(PowerPath.SHINIGAMI));
        helper.assertTrue(Math.abs(human.maxReiryoku() - (40.0F + growth)) < EPSILON, "the Human's max was " + human.maxReiryoku());
        helper.assertTrue(human.reiryoku() == human.maxReiryoku(), "the Human did not start full");
        helper.assertTrue(ShinigamiLogic.isSubstitute(human), "the Human is not a Substitute");
        helper.assertFalse(human.state().isSpirit(), "a Substitute became a spirit");

        SoulData weak = substitute(10.0F);
        helper.assertTrue(SoulLogic.isAware(weak), "a Substitute with little reiryoku cannot see spirits");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loanEndsAndRestoresTheGiver(GameTestHelper helper) {
        SoulData giver = lentGiver();
        long until = giver.shinigami().powerLentUntil();
        helper.assertTrue(until == NOW + (long) BleachConfig.get(BleachConfig.GIVER_RECOVERY_MINUTES) * TICKS_PER_MINUTE,
                "the loan ends at " + until);
        helper.assertFalse(ShinigamiLogic.endLoanIfDue(giver, until - 1), "the loan ended early");
        helper.assertTrue(Math.abs(giver.maxReiryoku() - 100.0F) < EPSILON, "an early check restored " + giver.maxReiryoku());
        helper.assertTrue(ShinigamiLogic.endLoanIfDue(giver, until), "the loan did not end on time");
        helper.assertTrue(Math.abs(giver.maxReiryoku() - 200.0F) < EPSILON, "the giver got back to " + giver.maxReiryoku());
        helper.assertTrue(giver.shinigami().powerLentUntil() == 0L && giver.shinigami().lentReiryoku() == 0.0F,
                "the loan was not cleared");
        helper.assertFalse(ShinigamiLogic.isPowerLent(giver, until), "the power still counts as lent");
        helper.assertFalse(ShinigamiLogic.endLoanIfDue(giver, until + 100), "a finished loan ended twice");
        helper.assertTrue(giver.shinigami().gavePowerAt() == NOW, "the crime was forgotten");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyStrongShinigamiGivePower(GameTestHelper helper) {
        float needed = (float) (2.0 * BleachConfig.get(BleachConfig.STARTING_REIRYOKU));
        helper.assertTrue(ShinigamiLogic.canGivePower(soul(SoulState.SHINIGAMI, needed), NOW), "a strong enough Shinigami could not give");
        helper.assertFalse(ShinigamiLogic.canGivePower(soul(SoulState.SHINIGAMI, needed - 1.0F), NOW), "a weak Shinigami gave power");
        helper.assertFalse(ShinigamiLogic.canGivePower(substitute(400.0F), NOW), "a Substitute gave power");
        helper.assertFalse(ShinigamiLogic.canGivePower(lentGiver(), NOW + 1), "a Shinigami gave power twice");
        helper.assertFalse(ShinigamiLogic.canGivePower(student(400.0F), NOW), "a student gave power");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyAwareLivingHumansReceivePower(GameTestHelper helper) {
        helper.assertTrue(ShinigamiLogic.canReceivePower(soul(SoulState.HUMAN, 40.0F)), "an aware Human could not receive power");
        float unaware = (float) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD) - 1.0F;
        helper.assertFalse(ShinigamiLogic.canReceivePower(soul(SoulState.HUMAN, unaware)), "an unaware Human received power");
        helper.assertFalse(ShinigamiLogic.canReceivePower(substitute(40.0F)), "a Substitute received power again");
        for (SoulState state : List.of(SoulState.PLUS, SoulState.SHINIGAMI, SoulState.HOLLOW, SoulState.RUKONGAI)) {
            helper.assertFalse(ShinigamiLogic.canReceivePower(soul(state, 40.0F)), "a " + state + " received power");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lentGiverRegeneratesAtHalfSpeed(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
        SoulData giver = lentGiver();
        double lent = ShinigamiLogic.regenMultiplier(pig, giver, NOW + 1);
        helper.assertTrue(Math.abs(lent - BleachConfig.get(BleachConfig.LENT_REGEN_MULTIPLIER)) < EPSILON,
                "a lent giver regenerates at " + lent);
        helper.assertTrue(ShinigamiLogic.regenMultiplier(pig, soul(SoulState.SHINIGAMI, 200.0F), NOW) == 1.0,
                "a Shinigami with their power regenerates slower");

        SoulData whole = soul(SoulState.SHINIGAMI, 100.0F);
        whole.setReiryoku(0.0F);
        giver.setReiryoku(0.0F);
        SoulLogic.regenTick(whole, false, 1.0);
        SoulLogic.regenTick(giver, false, lent);
        helper.assertTrue(Math.abs(giver.reiryoku() - whole.reiryoku() * lent) < 1.0E-5F,
                "half-speed regeneration gave " + giver.reiryoku() + " against " + whole.reiryoku());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void substituteWhoDiesBecomesAShinigami(GameTestHelper helper) {
        SoulData after = SoulLogic.afterDeath(substitute(60.0F));
        helper.assertTrue(after.state() == SoulState.SHINIGAMI, "a Substitute died into a " + after.state());
        helper.assertTrue(after.chainMaxTicks() == 0, "a dead Substitute got a chain");
        helper.assertTrue(after.tier(PowerPath.SHINIGAMI) == 1, "a dead Substitute lost their tier");
        helper.assertTrue(SoulLogic.afterDeath(soul(SoulState.HUMAN, 60.0F)).state() == SoulState.PLUS,
                "a plain Human no longer dies into a Plus");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rukongaiSoulSurvivesDeath(GameTestHelper helper) {
        SoulData after = SoulLogic.afterDeath(student(60.0F));
        helper.assertTrue(after.state() == SoulState.RUKONGAI, "a student died into a " + after.state());
        helper.assertTrue(ShinigamiLogic.isStudent(after), "a student who died is no longer enrolled");
        helper.assertTrue(after.chainMaxTicks() == 0, "a dead Rukongai soul got a chain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nextStepFollowsThePath(GameTestHelper helper) {
        helper.assertTrue(ShinigamiLogic.nextStepKey(soul(SoulState.HUMAN, 40.0F), NOW).equals("human"), "wrong step for a Human");
        helper.assertTrue(ShinigamiLogic.nextStepKey(substitute(40.0F), NOW).equals("substitute"), "wrong step for a Substitute");
        helper.assertTrue(ShinigamiLogic.nextStepKey(soul(SoulState.PLUS, 40.0F), NOW).equals("plus"), "wrong step for a Plus");
        helper.assertTrue(ShinigamiLogic.nextStepKey(soul(SoulState.RUKONGAI, 40.0F), NOW).equals("rukongai"), "wrong step for a Rukongai soul");
        helper.assertTrue(ShinigamiLogic.nextStepKey(student(40.0F), NOW).equals("student"), "wrong step for a student");
        helper.assertTrue(ShinigamiLogic.nextStepKey(soul(SoulState.SHINIGAMI, 40.0F), NOW).equals("shinigami"), "wrong step for a Shinigami");
        helper.assertTrue(ShinigamiLogic.nextStepKey(lentGiver(), NOW + 1).equals("lent"), "wrong step for a lent giver");
        helper.assertTrue(ShinigamiLogic.nextStepKey(soul(SoulState.HOLLOW, 40.0F), NOW).equals("hollow"), "wrong step for a Hollow");
        helper.assertTrue(ShinigamiLogic.nextStepKey(soul(SoulState.ARRANCAR, 40.0F), NOW).equals("arrancar"), "wrong step for an Arrancar");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woundedEncounterNeedsGrowth(GameTestHelper helper) {
        float needed = (float) BleachConfig.get(BleachConfig.ENCOUNTER_MIN_REIRYOKU);
        SoulData human = soul(SoulState.HUMAN, needed);
        helper.assertTrue(ShinigamiLogic.canMeetWoundedShinigami(human, NOW), "a strong enough Human met no one");
        helper.assertFalse(ShinigamiLogic.canMeetWoundedShinigami(soul(SoulState.HUMAN, needed - 1.0F), NOW),
                "a weak Human met a wounded Shinigami");
        helper.assertFalse(ShinigamiLogic.canMeetWoundedShinigami(substitute(400.0F), NOW), "a Substitute met another giver");

        human.setShinigami(human.shinigami().withWoundedMetAt(NOW));
        long cooldown = (long) BleachConfig.get(BleachConfig.ENCOUNTER_COOLDOWN_MINUTES) * TICKS_PER_MINUTE;
        helper.assertFalse(ShinigamiLogic.canMeetWoundedShinigami(human, NOW + cooldown - 1), "met again during the cooldown");
        helper.assertTrue(ShinigamiLogic.canMeetWoundedShinigami(human, NOW + cooldown), "the cooldown never ended");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compassPointsTheWay(GameTestHelper helper) {
        helper.assertTrue(ShinigamiLogic.compassKey(0.0, -100.0).equals("message.bleach.direction.north"), "negative Z is not north");
        helper.assertTrue(ShinigamiLogic.compassKey(100.0, 100.0).equals("message.bleach.direction.south_east"), "wrong south-east");
        helper.assertTrue(ShinigamiLogic.compassKey(100.0, 0.0).equals("message.bleach.direction.east"), "positive X is not east");
        helper.assertTrue(ShinigamiLogic.compassKey(-100.0, -90.0).equals("message.bleach.direction.north_west"), "wrong north-west");
        helper.assertTrue(ShinigamiLogic.compassKey(-1.0, 100.0).equals("message.bleach.direction.south"), "wrong south");
        helper.assertTrue(ShinigamiLogic.roundedDistance(10.0) == 50L, "a short way was not rounded up to 50");
        helper.assertTrue(ShinigamiLogic.roundedDistance(424.0) == 400L, "424 blocks rounded to " + ShinigamiLogic.roundedDistance(424.0));
        helper.assertTrue(ShinigamiLogic.roundedDistance(476.0) == 500L, "476 blocks rounded to " + ShinigamiLogic.roundedDistance(476.0));
        helper.succeed();
    }
}
