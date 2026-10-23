package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Ransōtengai: when sneak and the Quincy Form key mean it, why it cannot be woven, what a
 * woven body is freed from and kept from, what the weave costs and how it ends, the Elder's trial,
 * and the older uses of the key that it must leave alone. {@link QuincyRansotengai} runs on mock
 * players from {@link ZanpakutoTests}, which are not in the world and never ticked, so each tick is
 * called by hand with the time it stands for.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class RansotengaiTests {
    private static final long NOW = 100_000L;
    private static final double EPSILON = 1.0E-3;
    private static final int TICKS_PER_SECOND = 20;
    private static final String REFUSED = "message.bleach.ransotengai.refused.";

    private RansotengaiTests() {
    }

    // ---- Helpers ----

    /** A living Echt Human of the third tier with 200 reiryoku who has learned Ransōtengai. */
    private static SoulData knownThirdTier() {
        return QuincyNpcTests.known(QuincyArtsTests.thirdTier());
    }

    /** The same soul with the threads woven since {@code since}, on its record only. */
    private static SoulData woven(SoulData data, long since) {
        return QuincyArtsTests.forms(data, QuincyArtsLogic.arts(data).forms().withRansotengaiSince(since));
    }

    /** The same soul with the Elder's trial open until {@code until}. */
    private static SoulData inTrial(SoulData data, long until) {
        return QuincyNpcTests.teaching(data, data.quincy().teaching().withTrialUntil(until));
    }

    private static long readyAt(SoulData data) {
        return QuincyArtsLogic.arts(data).forms().ransotengaiReadyAt();
    }

    private static long cooldownTicks() {
        return (long) BleachConfig.get(BleachConfig.RANSOTENGAI_COOLDOWN_SECONDS) * TICKS_PER_SECOND;
    }

    private static boolean holdsAgainstKnockback(Player player) {
        AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        return knockback != null && knockback.hasModifier(QuincyRansotengai.KNOCKBACK_ID);
    }

    private static void assertEffect(GameTestHelper helper, Player player, MobEffectInstance expected, String what) {
        MobEffectInstance effect = player.getEffect(expected.getEffect());
        helper.assertTrue(effect != null && effect.getAmplifier() == expected.getAmplifier()
                        && effect.getDuration() == expected.getDuration(),
                what + " is " + effect + ", not " + expected);
    }

    // ---- When the key means it ----

    @GameTest(template = "empty")
    public static void ransotengaiClaimsTheKeyWhenFailing(GameTestHelper helper) {
        Player unknown = ZanpakutoTests.mock(helper, QuincyArtsTests.thirdTier());
        helper.assertFalse(QuincyRansotengai.claimsKey(unknown, SoulLogic.get(unknown), NOW),
                "the key means Ransōtengai to a soul that never learned it");
        Player healthy = ZanpakutoTests.mock(helper, knownThirdTier());
        helper.assertFalse(QuincyRansotengai.claimsKey(healthy, SoulLogic.get(healthy), NOW),
                "the key means Ransōtengai to a healthy body");
        Player failing = ZanpakutoTests.mock(helper, knownThirdTier());
        failing.setHealth(0.2F * failing.getMaxHealth());
        helper.assertTrue(QuincyRansotengai.claimsKey(failing, SoulLogic.get(failing), NOW),
                "the key does not mean Ransōtengai at a fifth of the health");
        Player slowed = ZanpakutoTests.mock(helper, knownThirdTier());
        slowed.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        helper.assertTrue(QuincyRansotengai.claimsKey(slowed, SoulLogic.get(slowed), NOW),
                "the key does not mean Ransōtengai to a slowed body");
        Player trial = ZanpakutoTests.mock(helper, inTrial(QuincyArtsTests.thirdTier(), NOW + 100L));
        helper.assertTrue(QuincyRansotengai.claimsKey(trial, SoulLogic.get(trial), NOW),
                "the key does not mean Ransōtengai in the Elder's trial");
        Player wovenNow = ZanpakutoTests.mock(helper, woven(knownThirdTier(), NOW - 10L));
        helper.assertTrue(QuincyRansotengai.claimsKey(wovenNow, SoulLogic.get(wovenNow), NOW),
                "the key does not let the woven threads go");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ransotengaiStartRefusals(GameTestHelper helper) {
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, knownThirdTier());
        Map<String, Player> souls = new LinkedHashMap<>();
        Map<String, String> expected = new LinkedHashMap<>();
        souls.put("in creative mode", creative);
        expected.put("in creative mode", REFUSED + "mode");
        souls.put("sealed", ZanpakutoTests.mock(helper, QuincyArtsTests.sealed(knownThirdTier(), 0L)));
        expected.put("sealed", REFUSED + "sealed");
        SoulData poisoned = knownThirdTier();
        poisoned.setQuincy(poisoned.quincy().withPoison(QuincyLogic.POISON_BLOCK));
        souls.put("poisoned", ZanpakutoTests.mock(helper, poisoned));
        expected.put("poisoned", REFUSED + "poison");
        souls.put("in Vollständig", ZanpakutoTests.mock(helper, QuincyArtsTests.voll(knownThirdTier())));
        expected.put("in Vollständig", REFUSED + "vollstandig");
        SoulData waiting = knownThirdTier();
        QuincyArtsTests.forms(waiting, QuincyArtsLogic.arts(waiting).forms().withRansotengaiReadyAt(NOW + 100L));
        souls.put("still waiting after a collapse", ZanpakutoTests.mock(helper, waiting));
        expected.put("still waiting after a collapse", REFUSED + "cooldown");
        SoulData drained = knownThirdTier();
        drained.setReiryoku(0.15F * drained.maxReiryoku());
        souls.put("with 15% of the reiryoku", ZanpakutoTests.mock(helper, drained));
        expected.put("with 15% of the reiryoku", REFUSED + "reiryoku");
        souls.put("all clear", ZanpakutoTests.mock(helper, knownThirdTier()));
        expected.put("all clear", null);
        souls.forEach((who, player) -> {
            String refusal = QuincyRansotengai.startRefusal(player, SoulLogic.get(player), NOW, false);
            helper.assertTrue(Objects.equals(refusal, expected.get(who)), "a soul " + who + " was refused with " + refusal
                    + ", not " + expected.get(who));
        });
        Player forced = ZanpakutoTests.mock(helper, waiting);
        String refusal = QuincyRansotengai.startRefusal(forced, waiting, NOW, true);
        helper.assertTrue(refusal == null, "the command was refused with " + refusal + " during the wait");
        helper.succeed();
    }

    // ---- The weave ----

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void weavingFreesAndHolds(GameTestHelper helper) {
        SoulData data = knownThirdTier();
        Player mock = ZanpakutoTests.mock(helper, data);
        mock.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        helper.assertTrue(QuincyRansotengai.start(mock, data, NOW, false), "the threads were not woven");
        helper.assertTrue(QuincyRansotengai.isWoven(data), "the soul is not woven");
        helper.assertFalse(mock.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the threads did not shake off Slowness");
        mock.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        helper.assertFalse(mock.hasEffect(MobEffects.WEAKNESS), "Weakness took hold of a woven body");
        double knockback = mock.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(knockback >= 1.0, "a woven body resists knockback only " + knockback);
        mock.setHealth(10.0F);
        mock.heal(4.0F);
        helper.assertTrue(Math.abs(mock.getHealth() - 10.0F) < EPSILON, "a woven body healed to " + mock.getHealth());
        helper.assertFalse(SoulEvents.mayRegenerate(mock, data), "a woven body's reiryoku refills");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void weavingDrainsAndCollapses(GameTestHelper helper) {
        SoulData data = knownThirdTier();
        Player mock = ZanpakutoTests.mock(helper, data);
        helper.assertTrue(QuincyRansotengai.start(mock, data, NOW, false), "the threads were not woven");
        QuincyRansotengai.tick(mock, data, NOW + QuincyRansotengai.DRAIN_INTERVAL);
        helper.assertTrue(Math.abs(data.reiryoku() - 195.0F) < EPSILON, "a second of the weave left " + data.reiryoku() + " of 200");

        SoulData hybrid = QuincyTests.withShinigamiPower(knownThirdTier());
        Player hybridMock = ZanpakutoTests.mock(helper, hybrid);
        helper.assertTrue(QuincyRansotengai.start(hybridMock, hybrid, NOW, false), "a hybrid's threads were not woven");
        QuincyRansotengai.tick(hybridMock, hybrid, NOW + QuincyRansotengai.DRAIN_INTERVAL);
        helper.assertTrue(Math.abs(hybrid.reiryoku() - 192.5F) < EPSILON,
                "a second of a hybrid's weave left " + hybrid.reiryoku() + " of 200");

        data.setReiryoku(0.4F);
        long dryAt = NOW + 2L * QuincyRansotengai.DRAIN_INTERVAL;
        QuincyRansotengai.tick(mock, data, dryAt);
        int collapse = BleachConfig.get(BleachConfig.RANSOTENGAI_COLLAPSE_SECONDS) * TICKS_PER_SECOND;
        helper.assertFalse(QuincyRansotengai.isWoven(data), "a dry pool still holds the threads");
        helper.assertTrue(data.reiryoku() == 0.0F, "running dry left " + data.reiryoku());
        assertEffect(helper, mock, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, collapse, 1), "the collapse's Slowness");
        assertEffect(helper, mock, new MobEffectInstance(MobEffects.WEAKNESS, collapse, 1), "the collapse's Weakness");
        helper.assertTrue(readyAt(data) == dryAt + cooldownTicks(),
                "the threads hold again at " + readyAt(data) + ", not " + (dryAt + cooldownTicks()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void weavingEndsQuietlyOnEndAll(GameTestHelper helper) {
        SoulData data = knownThirdTier();
        Player mock = ZanpakutoTests.mock(helper, data);
        helper.assertTrue(QuincyRansotengai.start(mock, data, NOW, false), "the threads were not woven");
        long before = readyAt(data);
        QuincyArtsEvents.endAll(mock, data);
        helper.assertFalse(QuincyRansotengai.isWoven(data), "ending every art left the threads woven");
        helper.assertFalse(mock.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "ending every art brought the collapse");
        helper.assertTrue(readyAt(data) == before, "ending every art set a wait until " + readyAt(data));
        helper.assertFalse(holdsAgainstKnockback(mock), "ending every art left the hold against knockback");

        SoulData sealed = knownThirdTier();
        Player sealedMock = ZanpakutoTests.mock(helper, sealed);
        helper.assertTrue(QuincyRansotengai.start(sealedMock, sealed, NOW, false), "the threads were not woven");
        QuincyArtsTests.sealed(sealed, NOW);
        QuincyRansotengai.tick(sealedMock, sealed, NOW + 1L);
        helper.assertFalse(QuincyRansotengai.isWoven(sealed), "a sealed power still holds the threads");
        helper.assertTrue(sealedMock.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the seal tore the threads away with no collapse");
        helper.assertTrue(readyAt(sealed) == NOW + 1L + cooldownTicks(), "the seal set no wait: " + readyAt(sealed));

        SoulData voll = knownThirdTier();
        Player vollMock = ZanpakutoTests.mock(helper, voll);
        helper.assertTrue(QuincyRansotengai.start(vollMock, voll, NOW, false), "the threads were not woven");
        helper.assertTrue(QuincyArtsEvents.startVollstandig(vollMock, voll, NOW, true), "Vollständig did not start");
        helper.assertFalse(QuincyRansotengai.isWoven(voll), "Vollständig began with the threads still woven");
        helper.succeed();
    }

    /** The trial's wait is waived: the threads hold at once, even in the wait after a collapse. */
    @GameTest(template = "empty")
    public static void trialTeachesAfterAHundredTicks(GameTestHelper helper) {
        SoulData data = QuincyArtsTests.thirdTier();
        QuincyArtsTests.forms(data, QuincyArtsLogic.arts(data).forms().withRansotengaiReadyAt(NOW + 1000L));
        Player mock = ZanpakutoTests.mock(helper, data);
        QuincyRansotengai.beginTrial(mock, data, NOW);
        MobEffectInstance bound = mock.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(bound != null && bound.getAmplifier() == 3, "the trial bound the body with " + bound);
        helper.assertTrue(QuincyRansotengai.trialOpen(data, NOW), "the trial is not open");
        long waitBefore = readyAt(data);

        helper.assertTrue(QuincyRansotengai.start(mock, data, NOW, false), "the threads were not woven in the trial");
        long hold = (long) BleachConfig.get(BleachConfig.RANSOTENGAI_TRIAL_HOLD_SECONDS) * TICKS_PER_SECOND;
        QuincyRansotengai.tick(mock, data, NOW + hold);
        helper.assertTrue(QuincyRansotengai.isKnown(data), "the trial did not teach Ransōtengai");
        helper.assertFalse(QuincyRansotengai.isWoven(data), "the threads stayed woven after the trial");
        helper.assertFalse(QuincyRansotengai.trialOpen(data, NOW + hold), "the trial is still open");
        helper.assertTrue(data.quincy().teaching().trialUntil() == ShinigamiProgress.NEVER,
                "the trial closes at " + data.quincy().teaching().trialUntil());
        helper.assertFalse(mock.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || mock.hasEffect(MobEffects.WEAKNESS),
                "passing the trial brought the collapse");
        helper.assertTrue(readyAt(data) == waitBefore, "passing the trial set a wait until " + readyAt(data));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void formKeyKeepsOldUses(GameTestHelper helper) {
        SoulData data = QuincyArtsTests.gloved(knownThirdTier(), false);
        Player mock = ZanpakutoTests.mock(helper, data);
        QuincyArtsEvents.onFormKey(mock, true, NOW);
        helper.assertFalse(QuincyArtsLogic.gloveWorn(data), "sneak and the key no longer take the glove off a healthy body");
        helper.assertTrue(QuincyArtsTests.carried(mock, ModItems.SANREI_GLOVE.get()) == 1, "the glove taken off did not come back");
        helper.assertFalse(QuincyRansotengai.isWoven(data), "sneak and the key wove the threads in a healthy body");

        QuincyArtsTests.gloved(data, false);
        mock.setHealth(0.2F * mock.getMaxHealth());
        QuincyArtsEvents.onFormKey(mock, true, NOW + 10L);
        helper.assertTrue(QuincyRansotengai.isWoven(data), "sneak and the key did not weave the threads in a failing body");
        helper.assertTrue(QuincyArtsLogic.gloveWorn(data), "weaving the threads took the glove off");

        helper.assertTrue(QuincyArtsEvents.startVollstandig(mock, data, NOW + 20L, true), "Vollständig did not start");
        QuincyArtsEvents.onFormKey(mock, true, NOW + 30L);
        helper.assertTrue(QuincyArtsLogic.sklavereiOn(data), "sneak and the key in Vollständig did not start Sklaverei");
        QuincyArtsEvents.onFormKey(mock, true, NOW + 40L);
        helper.assertFalse(QuincyArtsLogic.sklavereiOn(data), "sneak and the key in Vollständig did not stop Sklaverei");
        helper.assertFalse(QuincyRansotengai.isWoven(data), "sneak and the key in Vollständig wove the threads");
        helper.succeed();
    }
}
