package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.ChainEvents;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests for death, the Chain of Fate and Hollowfication. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ChainTests {
    private static final float EPSILON = 1.0E-3F;

    private ChainTests() {
    }

    private static SoulData plus(int chainTicks) {
        SoulData data = new SoulData();
        SoulLogic.initialize(data);
        SoulLogic.changeState(data, SoulState.PLUS);
        data.setChainTicks(chainTicks);
        return data;
    }

    @GameTest(template = "empty")
    public static void awareHumanDiesIntoAPlus(GameTestHelper helper) {
        SoulData human = new SoulData();
        SoulLogic.initialize(human);
        human.setReiatsuReleased(true);
        SoulData after = SoulLogic.afterDeath(human);

        helper.assertTrue(after.state() == SoulState.PLUS, "an aware Human became " + after.state());
        helper.assertTrue(after.chainMaxTicks() == SoulLogic.chainLifeTicks() && after.chainTicks() == after.chainMaxTicks(),
                "the new chain was " + after.chainTicks() + " of " + after.chainMaxTicks());
        float expected = human.maxReiryoku() * (float) BleachConfig.get(BleachConfig.RESPAWN_REIRYOKU_FRACTION);
        helper.assertTrue(Math.abs(after.reiryoku() - expected) < EPSILON, "reiryoku after death was " + after.reiryoku());
        helper.assertFalse(after.isReiatsuReleased(), "the release outlasted death");
        helper.assertTrue(human.state() == SoulState.HUMAN, "afterDeath changed the old soul");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unawareHumanStaysHuman(GameTestHelper helper) {
        SoulData human = new SoulData();
        SoulLogic.initialize(human);
        human.setMaxReiryoku((float) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD) - 1.0F);
        SoulData after = SoulLogic.afterDeath(human);
        helper.assertTrue(after.state() == SoulState.HUMAN, "an unaware Human became " + after.state());
        helper.assertTrue(after.chainMaxTicks() == 0, "an unaware Human got a chain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dyingAsAPlusCostsChain(GameTestHelper helper) {
        int cost = BleachConfig.get(BleachConfig.PLUS_DEATH_CHAIN_COST_SECONDS) * 20;
        SoulData full = plus(SoulLogic.chainLifeTicks());
        SoulData after = SoulLogic.afterDeath(full);
        helper.assertTrue(after.state() == SoulState.PLUS, "a Plus became " + after.state() + " by dying");
        helper.assertTrue(after.chainTicks() == SoulLogic.chainLifeTicks() - cost, "chain after death was " + after.chainTicks());

        SoulData almostGone = SoulLogic.afterDeath(plus(10));
        helper.assertTrue(almostGone.chainTicks() == 1, "dying should leave the last link, left " + almostGone.chainTicks());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chainErodesAtItsSpeed(GameTestHelper helper) {
        SoulData data = plus(100);
        helper.assertFalse(SoulLogic.erodeChain(data, 2.0), "a long chain broke");
        helper.assertTrue(data.chainTicks() == 98, "double speed left " + data.chainTicks());
        SoulLogic.erodeChain(data, 0.5);
        helper.assertTrue(data.chainTicks() == 98, "half a tick should not wear a link yet");
        SoulLogic.erodeChain(data, 0.5);
        helper.assertTrue(data.chainTicks() == 97, "two half ticks should wear one, left " + data.chainTicks());

        SoulData last = plus(1);
        helper.assertTrue(SoulLogic.erodeChain(last, 1.0), "the last link did not break");
        helper.assertTrue(SoulLogic.erodeChain(plus(0), 1.0), "an empty chain should break at once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void encroachmentStartsNearTheEnd(GameTestHelper helper) {
        int life = SoulLogic.chainLifeTicks();
        double fraction = BleachConfig.get(BleachConfig.ENCROACHMENT_FRACTION);
        helper.assertFalse(SoulLogic.isEncroaching(plus((int) (life * fraction) + 20)), "Encroachment came too early");
        helper.assertTrue(SoulLogic.isEncroaching(plus((int) (life * fraction) - 20)), "Encroachment did not start");
        SoulData human = new SoulData();
        SoulLogic.initialize(human);
        helper.assertFalse(SoulLogic.isEncroaching(human), "a Human without a chain is encroaching");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hollowficationAwakensTheHollow(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
        SoulData data = plus(0);
        data.setReiatsuReleased(true);
        float before = data.maxReiryoku();
        SoulLogic.hollowfy(pig, data);

        helper.assertTrue(data.state() == SoulState.HOLLOW, "state after Hollowfication was " + data.state());
        helper.assertTrue(data.chainMaxTicks() == 0 && data.chainTicks() == 0, "a Hollow still has a chain");
        helper.assertTrue(data.tier(PowerPath.HOLLOW) == 1, "Hollow tier was " + data.tier(PowerPath.HOLLOW));
        float grown = (float) (before + BleachConfig.get(BleachConfig.HOLLOWFICATION_GROWTH));
        helper.assertTrue(Math.abs(data.maxReiryoku() - grown) < EPSILON, "max reiryoku was " + data.maxReiryoku());
        helper.assertTrue(data.reiryoku() == data.maxReiryoku(), "a new Hollow should start full");
        helper.assertFalse(data.isReiatsuReleased(), "the release outlasted Hollowfication");
        helper.assertTrue(pig.hasEffect(MobEffects.BLINDNESS), "no blindness from Hollowfication");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandStatesBringTheirParts(GameTestHelper helper) {
        SoulData data = new SoulData();
        SoulLogic.initialize(data);
        SoulLogic.changeState(data, SoulState.PLUS);
        helper.assertTrue(data.chainMaxTicks() == SoulLogic.chainLifeTicks(), "a Plus by command got no chain");
        SoulLogic.changeState(data, SoulState.HOLLOW);
        helper.assertTrue(data.chainMaxTicks() == 0, "a Hollow by command kept its chain");
        helper.assertTrue(data.tier(PowerPath.HOLLOW) == 1, "a Hollow by command has no Hollow tier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chainlessPlusGetsAChain(GameTestHelper helper) {
        SoulData data = new SoulData();
        SoulLogic.initialize(data);
        data.setState(SoulState.PLUS);
        helper.assertTrue(SoulLogic.ensureChain(data), "a Plus without a chain was not given one");
        helper.assertTrue(data.chainMaxTicks() == SoulLogic.chainLifeTicks() && data.chainTicks() == data.chainMaxTicks(),
                "the repaired chain was " + data.chainTicks() + " of " + data.chainMaxTicks());
        helper.assertFalse(SoulLogic.ensureChain(data), "a Plus with a chain got another");
        SoulData human = new SoulData();
        SoulLogic.initialize(human);
        helper.assertFalse(SoulLogic.ensureChain(human), "a Human was given a chain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void encroachmentDamageTypeLoads(GameTestHelper helper) {
        DamageSource source = ChainEvents.encroachment(helper.getLevel());
        helper.assertTrue(source.typeHolder().is(ChainEvents.ENCROACHMENT), "wrong damage type");
        helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR), "Encroachment should ignore armor");
        helper.succeed();
    }
}
