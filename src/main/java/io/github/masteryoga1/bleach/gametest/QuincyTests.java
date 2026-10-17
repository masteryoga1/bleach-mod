package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyProgress;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Quincy: the bloodline and its saved form, the Quincy Cross and the training that
 * awakens the blood, the Heilig Bogen's numbers and the reishi that pays for it, Hirenkyaku and
 * Quincy footing, Blut, Hollow poison, what death does to a Quincy and how the Shinigami greet one.
 * The rules in {@link QuincyLogic} are called directly, and {@link QuincyEvents} runs on mock
 * players from {@link ZanpakutoTests}. The arrows fly for real on the {@code field} template, a 16
 * by 16 stone floor open to the sky, at creatures standing on it at relative y 2. A mock that is not
 * in the world owns them.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuincyTests {
    private static final float EPSILON = 1.0E-4F;
    private static final int ROLLS = 2000;
    private static final Vec3 SOUTH = new Vec3(0.0, 0.0, 1.0);
    private static final Vec3 NORTH = new Vec3(0.0, 0.0, -1.0);
    private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 DOWN = new Vec3(0.0, -1.0, 0.0);

    /** One soul, and what the Quincy Cross makes of it. */
    private record Draw(String who, SoulData data, String refusal, boolean canUse, boolean canAwaken) {
    }

    private QuincyTests() {
    }

    // ---- Shared helpers ----

    /** A living Human with this blood and Quincy tier, full of this much reiryoku, the bloodline question settled. */
    static SoulData quincy(Bloodline bloodline, int tier, float maxReiryoku) {
        SoulData data = ShinigamiTests.soul(SoulState.HUMAN, maxReiryoku);
        data.setQuincy(data.quincy().withBloodline(bloodline).withDecided(true));
        data.setTier(PowerPath.QUINCY, tier);
        return data;
    }

    /** The same soul moved into this state, with what the state brings (see {@link SoulLogic#changeState}). */
    private static SoulData in(SoulState state, SoulData data) {
        SoulLogic.changeState(data, state);
        return data;
    }

    /** The same soul with a Shinigami's first tier as well, as a Substitute has. */
    private static SoulData withShinigamiPower(SoulData data) {
        data.setTier(PowerPath.SHINIGAMI, 1);
        return data;
    }

    /** A creature that stands where it is put and does nothing, not even fall or reel from a blow. */
    private static <T extends Mob> T still(T mob) {
        mob.setNoAi(true);
        return mob;
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON, what + " is " + actual + ", not " + expected);
    }

    /** A mock Quincy standing on the floor at this spot in the box, to own the arrows a test looses. */
    private static Player archer(GameTestHelper helper, SoulData data, double x, double z) {
        Player owner = ZanpakutoTests.mock(helper, data);
        Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
        owner.setPos(at.x, at.y, at.z);
        return owner;
    }

    /** A mock Gemischt Quincy of the first tier with 200 reiryoku, at this spot in the box. */
    private static Player archer(GameTestHelper helper, double x, double z) {
        return archer(helper, quincy(Bloodline.GEMISCHT, 1, 200.0F), x, z);
    }

    /**
     * Looses a fully drawn arrow from a point in the world, along a direction in the box, dead
     * straight, with the damage, pierce and speed pinned.
     */
    private static HeiligPfeil loose(GameTestHelper helper, LivingEntity owner, Vec3 from, Vec3 direction, float damage,
                                     int pierce, float speed) {
        Vec3 heading = helper.absoluteVec(direction).subtract(helper.absoluteVec(Vec3.ZERO));
        return HeiligPfeil.launch(helper.getLevel(), owner, from, heading, 1.0F, damage, pierce, speed, 0.0F);
    }

    /** Sends away every Hollow near this soul, so none left in the world by an earlier test stirs its blood. */
    private static void clearHollowsNear(GameTestHelper helper, Player player) {
        double range = QuincyLogic.AWAKENING_HOLLOW_RANGE + 1.0;
        for (Hollow hollow : helper.getLevel().getEntitiesOfClass(Hollow.class, player.getBoundingBox().inflate(range))) {
            hollow.discard();
        }
    }

    // ---- The bloodline ----

    @GameTest(template = "empty")
    public static void quincyProgressSurvivesSaving(GameTestHelper helper) {
        SoulData data = ShinigamiTests.soul(SoulState.HUMAN, 40.0F);
        QuincyProgress progress = new QuincyProgress(Bloodline.ECHT, true, 77, 12.5F, 30.0F, BlutMode.VENE, 3, true);
        data.setQuincy(progress);

        Tag saved = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        SoulData loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.quincy().equals(progress), "Quincy progress loaded as " + loaded.quincy());
        helper.assertTrue(data.copy().quincy().equals(progress), "copying lost the Quincy progress");
        helper.assertTrue(data.forClient().quincy().equals(progress), "the client's copy lost the Quincy progress");

        // One bad value falls back on its own and keeps the rest.
        helper.assertTrue(saved instanceof CompoundTag, "a soul did not save as a compound tag");
        CompoundTag bad = ((CompoundTag) saved).copy();
        CompoundTag quincy = new CompoundTag();
        quincy.putString("bloodline", "nonsense");
        quincy.putInt("training", 12);
        bad.put("quincy", quincy);
        QuincyProgress read = SoulData.CODEC.parse(NbtOps.INSTANCE, bad).getOrThrow().quincy();
        helper.assertTrue(read.bloodline() == Bloodline.NONE, "an unknown bloodline loaded as " + read.bloodline());
        helper.assertTrue(read.training() == 12, "a bad bloodline wiped the training: " + read);

        // A save from before Quincy blood had no Quincy progress at all.
        CompoundTag older = ((CompoundTag) saved).copy();
        older.remove("quincy");
        SoulData old = SoulData.CODEC.parse(NbtOps.INSTANCE, older).getOrThrow();
        helper.assertTrue(old.quincy().equals(QuincyProgress.NONE), "a save without Quincy progress loaded " + old.quincy());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldQuincySavesSettleSilently(GameTestHelper helper) {
        SoulData before = ShinigamiTests.soul(SoulState.HUMAN, 40.0F);
        before.setTier(PowerPath.QUINCY, 2);
        Tag saved = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, before).getOrThrow();
        helper.assertTrue(saved instanceof CompoundTag, "a soul did not save as a compound tag");
        CompoundTag older = ((CompoundTag) saved).copy();
        older.remove("quincy");
        SoulData data = SoulData.CODEC.parse(NbtOps.INSTANCE, older).getOrThrow();

        helper.assertTrue(data.tier(PowerPath.QUINCY) == 2, "an old Quincy tier loaded as " + data.tier(PowerPath.QUINCY));
        helper.assertTrue(data.quincy().bloodline() == Bloodline.NONE, "an old save loaded a bloodline of its own");
        helper.assertTrue(QuincyLogic.bloodline(data) == Bloodline.GEMISCHT,
                "a Quincy tier without a bloodline counts as " + QuincyLogic.bloodline(data));
        helper.assertFalse(QuincyLogic.needsBloodlineChoice(data), "an old Quincy would be asked about their blood");

        helper.assertTrue(QuincyLogic.settleOldSave(data), "an old Quincy save was not settled");
        helper.assertTrue(data.quincy().bloodline() == Bloodline.GEMISCHT && data.quincy().decided(),
                "an old Quincy save settled as " + data.quincy());
        helper.assertFalse(QuincyLogic.settleOldSave(data), "a settled save was settled again");

        helper.assertTrue(QuincyLogic.needsBloodlineChoice(ShinigamiTests.soul(SoulState.HUMAN, 40.0F)),
                "a new Human is not asked about their blood");
        helper.assertFalse(QuincyLogic.needsBloodlineChoice(ShinigamiTests.soul(SoulState.PLUS, 40.0F)),
                "a Plus is asked about their blood");
        helper.assertFalse(QuincyLogic.needsBloodlineChoice(ShinigamiTests.substitute(40.0F)),
                "a Substitute is asked about their blood");
        SoulData decided = ShinigamiTests.soul(SoulState.HUMAN, 40.0F);
        decided.setQuincy(decided.quincy().withDecided(true));
        helper.assertFalse(QuincyLogic.needsBloodlineChoice(decided), "a Human who answered is asked again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodlineRollsFollowTheChoice(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < ROLLS; i++) {
            Bloodline rolled = QuincyLogic.rollBloodline(BloodlineChoice.HUMAN, random);
            helper.assertTrue(rolled == Bloodline.NONE, "answering Human gave " + rolled + " blood");
        }
        int echt = 0;
        for (int i = 0; i < ROLLS; i++) {
            Bloodline rolled = QuincyLogic.rollBloodline(BloodlineChoice.QUINCY, random);
            helper.assertTrue(rolled != Bloodline.NONE, "answering Quincy gave no Quincy blood");
            if (rolled == Bloodline.ECHT) {
                echt++;
            }
        }
        double echtShare = echt / (double) ROLLS;
        helper.assertTrue(echtShare >= 0.20 && echtShare <= 0.30, "answering Quincy gave Echt blood " + echtShare + " of the time");
        int quincy = 0;
        for (int i = 0; i < ROLLS; i++) {
            if (QuincyLogic.rollBloodline(BloodlineChoice.FATE, random) != Bloodline.NONE) {
                quincy++;
            }
        }
        double quincyShare = quincy / (double) ROLLS;
        helper.assertTrue(quincyShare >= 0.03 && quincyShare <= 0.075,
                "letting fate decide gave Quincy blood " + quincyShare + " of the time");
        helper.succeed();
    }

    // ---- The Quincy Cross and training ----

    @GameTest(template = "empty")
    public static void onlyQuincyBloodDrawsTheBow(GameTestHelper helper) {
        String prefix = "message.bleach.quincy_cross.";
        List<Draw> draws = List.of(
                new Draw("Human without Quincy blood", ShinigamiTests.soul(SoulState.HUMAN, 40.0F), prefix + "no_blood", false, false),
                new Draw("latent Human", quincy(Bloodline.GEMISCHT, 0, 40.0F), null, false, true),
                new Draw("latent Substitute", withShinigamiPower(quincy(Bloodline.GEMISCHT, 0, 40.0F)), prefix + "shinigami",
                        false, false),
                new Draw("awakened Human", quincy(Bloodline.GEMISCHT, 1, 40.0F), null, true, false),
                new Draw("awakened Plus", in(SoulState.PLUS, quincy(Bloodline.GEMISCHT, 1, 40.0F)), prefix + "spirit",
                        false, false),
                new Draw("awakened Hollow", in(SoulState.HOLLOW, quincy(Bloodline.GEMISCHT, 1, 40.0F)), prefix + "spirit",
                        false, false),
                new Draw("Shinigami with a Quincy tier", in(SoulState.SHINIGAMI, quincy(Bloodline.GEMISCHT, 1, 40.0F)), null,
                        true, false),
                new Draw("latent Rukongai soul", in(SoulState.RUKONGAI, quincy(Bloodline.GEMISCHT, 0, 40.0F)), prefix + "spirit",
                        false, false));
        for (Draw draw : draws) {
            String refusal = QuincyLogic.drawRefusal(draw.data());
            helper.assertTrue(Objects.equals(refusal, draw.refusal()),
                    "a " + draw.who() + " is refused the Cross with " + refusal + ", not " + draw.refusal());
            helper.assertTrue(QuincyLogic.canUsePowers(draw.data()) == draw.canUse(),
                    "a " + draw.who() + (draw.canUse() ? " cannot" : " can") + " use Quincy powers");
            helper.assertTrue(QuincyLogic.canAwaken(draw.data()) == draw.canAwaken(),
                    "a " + draw.who() + (draw.canAwaken() ? " cannot" : " can") + " awaken");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trainingAwakensAtTheThresholds(GameTestHelper helper) {
        Map<Integer, Integer> thresholds = Map.of(39, 0, 40, 1, 149, 1, 150, 2, 499, 2, 500, 3, 10000, 3);
        thresholds.forEach((training, tier) -> helper.assertTrue(QuincyLogic.tierForTraining(training) == tier,
                training + " training earns tier " + QuincyLogic.tierForTraining(training) + ", not " + tier));

        SoulData data = quincy(Bloodline.GEMISCHT, 0, 40.0F);
        helper.assertTrue(QuincyLogic.addTraining(data, 39) == 0, "39 training awakened the blood");
        helper.assertTrue(QuincyLogic.addTraining(data, 1) == 1, "40 training did not awaken the blood");
        helper.assertTrue(QuincyLogic.tier(data) == 1, "awakening gave tier " + QuincyLogic.tier(data));
        assertNear(helper, data.maxReiryoku(), 50.0, "maximum reiryoku after awakening");
        assertNear(helper, QuincyLogic.maxReishi(data), 55.0, "the reishi pool after awakening");
        assertNear(helper, QuincyLogic.reishi(data), QuincyLogic.maxReishi(data), "reishi after awakening");
        helper.assertTrue(QuincyLogic.addTraining(data, 200) == 1 && QuincyLogic.tier(data) == 2,
                "240 training gave tier " + QuincyLogic.tier(data));
        helper.assertTrue(QuincyLogic.addTraining(data, 1000) == 1 && QuincyLogic.tier(data) == 3,
                "1240 training gave tier " + QuincyLogic.tier(data));
        helper.assertTrue(QuincyLogic.addTraining(data, 5) == 0, "training raised the tier past the third");

        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 40.0F);
        helper.assertTrue(QuincyLogic.addTraining(human, 100) == 0, "training awakened a Human without Quincy blood");
        helper.assertTrue(QuincyLogic.tier(human) == 0, "a Human without Quincy blood has tier " + QuincyLogic.tier(human));
        helper.assertTrue(human.quincy().training() == 100, "training did not add up: " + human.quincy().training());

        SoulData substitute = withShinigamiPower(quincy(Bloodline.GEMISCHT, 0, 40.0F));
        helper.assertTrue(QuincyLogic.addTraining(substitute, 100) == 0, "training awakened a Substitute's Quincy blood");
        helper.succeed();
    }

    /**
     * A latent soul draws the Cross: long draws train the blood, at most once in a while, and the
     * draw that awakens it looses the first arrow. A batch of its own, so no Hollow from another test
     * stirs the blood, but the boxes of earlier batches stay in the world beside this one, so any
     * Hollow they left is sent away first.
     */
    @GameTest(template = "field", batch = "quincy_awakening", timeoutTicks = 80)
    public static void latentDrawsTrainAndAwaken(GameTestHelper helper) {
        SoulData data = quincy(Bloodline.GEMISCHT, 0, 40.0F);
        Player player = archer(helper, data, 8.5, 8.5);
        player.setXRot(-45.0F);
        clearHollowsNear(helper, player);

        // First, while nothing has been counted yet, so only its length can keep it from counting.
        QuincyEvents.release(player, 10);
        helper.assertTrue(data.quincy().training() == 0, "a short draw trained the blood to " + data.quincy().training());
        QuincyEvents.release(player, 25);
        helper.assertTrue(data.quincy().training() == 2, "a long draw trained the blood to " + data.quincy().training());
        QuincyEvents.release(player, 25);
        helper.assertTrue(data.quincy().training() == 2, "a second draw at once trained the blood to " + data.quincy().training());
        helper.assertFalse(QuincyLogic.isAwakened(data), "a few draws awakened the blood");

        data.setQuincy(data.quincy().withTraining(38));
        helper.runAfterDelay(QuincyLogic.AWAKENING_COOLDOWN_TICKS + 1, () -> {
            clearHollowsNear(helper, player);
            QuincyEvents.release(player, 25);
            helper.assertTrue(QuincyLogic.tier(data) == 1, "the draw to 40 training left tier " + QuincyLogic.tier(data));
            helper.assertTrue(data.quincy().training() == 40, "the awakening draw left training " + data.quincy().training());
            assertNear(helper, data.maxReiryoku(), 50.0, "maximum reiryoku after awakening");
            assertNear(helper, QuincyLogic.reishi(data), 55.0, "reishi after awakening");
            List<HeiligPfeil> arrows = helper.getLevel().getEntitiesOfClass(HeiligPfeil.class, player.getBoundingBox().inflate(4.0));
            helper.assertFalse(arrows.isEmpty(), "the draw that awakened the blood loosed no arrow");
            arrows.forEach(Entity::discard);
            helper.succeed();
        });
    }

    // ---- The Heilig Bogen and reishi ----

    @GameTest(template = "empty")
    public static void bowDamageFollowsTheFormula(GameTestHelper helper) {
        SoulData data = quincy(Bloodline.GEMISCHT, 1, 100.0F);
        assertNear(helper, QuincyLogic.fullDamage(data), 11.0, "full damage at tier 1 with 100 reiryoku");
        assertNear(helper, QuincyLogic.arrowDamage(data, 1.0F), 11.0, "a full draw's damage");
        assertNear(helper, QuincyLogic.arrowDamage(data, 0.0F), 2.75, "a tap's damage");
        assertNear(helper, QuincyLogic.arrowDamage(data, 0.5F), 6.875, "a half draw's damage");
        data.setLevel(Stat.REIATSU_CONTROL, 10);
        assertNear(helper, QuincyLogic.fullDamage(data), 12.0, "full damage with Reiatsu Control 10");
        assertNear(helper, QuincyLogic.fullDamage(quincy(Bloodline.GEMISCHT, 3, 10000.0F)), 35.0, "full damage at the cap");

        helper.assertTrue(QuincyLogic.pierceFor(quincy(Bloodline.GEMISCHT, 1, 40.0F), 1.0F) == 0, "a tier 1 shot pierces");
        helper.assertTrue(QuincyLogic.pierceFor(quincy(Bloodline.GEMISCHT, 2, 40.0F), 0.9F) == 1,
                "a near-full tier 2 shot does not pierce once");
        helper.assertTrue(QuincyLogic.pierceFor(quincy(Bloodline.GEMISCHT, 2, 40.0F), 0.89F) == 0,
                "a tier 2 shot short of a near-full draw pierces");
        helper.assertTrue(QuincyLogic.pierceFor(quincy(Bloodline.GEMISCHT, 3, 40.0F), 1.0F) == 2,
                "a full tier 3 shot does not pierce twice");

        SpiderHollow spider = still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 1, 1, 1));
        Cow cow = still(helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 1));
        assertNear(helper, QuincyLogic.damageAgainst(10.0F, spider), 12.5, "10 damage against a Hollow");
        assertNear(helper, QuincyLogic.damageAgainst(10.0F, cow), 10.0, "10 damage against a cow");
        spider.discard();
        cow.discard();

        assertNear(helper, QuincyLogic.shotSpeed(1.0F), 3.2, "a full draw's speed");
        assertNear(helper, QuincyLogic.shotInaccuracy(1.0F), 0.25, "a full draw's inaccuracy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shotsFallBackToReiryoku(GameTestHelper helper) {
        SoulData data = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        assertNear(helper, QuincyLogic.maxReishi(data), 54.0, "the reishi pool at tier 1 with 40 reiryoku");

        QuincyLogic.setReishi(data, 2.0F);
        data.setReiryoku(40.0F);
        assertNear(helper, QuincyLogic.payForShot(data, 1.0F), 1.0, "the power paid for with reiryoku to spare");
        assertNear(helper, QuincyLogic.reishi(data), 0.0, "reishi after reiryoku made up the shot");
        assertNear(helper, data.reiryoku(), 36.0, "reiryoku after making up the shot");

        QuincyLogic.setReishi(data, 0.0F);
        data.setReiryoku(1.0F);
        assertNear(helper, QuincyLogic.payForShot(data, 1.0F), -1.0, "the power paid for with nothing to pay");
        assertNear(helper, data.reiryoku(), 1.0, "reiryoku after a shot that could not be paid for");

        QuincyLogic.setReishi(data, 2.5F);
        data.setReiryoku(0.0F);
        assertNear(helper, QuincyLogic.payForShot(data, 1.0F), 0.5, "the power 2.5 reishi pays for");
        assertNear(helper, QuincyLogic.reishi(data), 0.0, "reishi after a weaker shot");

        QuincyLogic.setReishi(data, 10.0F);
        assertNear(helper, QuincyLogic.payForShot(data, 1.0F), 1.0, "the power 10 reishi pays for");
        assertNear(helper, QuincyLogic.reishi(data), 6.0, "reishi after a full shot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reishiPoolAndGatheringRates(GameTestHelper helper) {
        SoulData data = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        assertNear(helper, QuincyLogic.maxReishi(data), 54.0, "the reishi pool at tier 1 with 40 reiryoku");
        assertNear(helper, QuincyLogic.maxReishi(quincy(Bloodline.GEMISCHT, 3, 200.0F)), 110.0,
                "the reishi pool at tier 3 with 200 reiryoku");
        SoulData latent = quincy(Bloodline.GEMISCHT, 0, 40.0F);
        assertNear(helper, QuincyLogic.maxReishi(latent), 0.0, "a latent soul's reishi pool");

        assertNear(helper, QuincyLogic.gatherPerSecond(data, 1.0, false, false), 0.4, "passive gathering");
        data.setLevel(Stat.REIATSU_CONTROL, 10);
        assertNear(helper, QuincyLogic.gatherPerSecond(data, 1.0, true, false), 3.6, "channelling with Reiatsu Control 10");
        assertNear(helper, QuincyLogic.gatherPerSecond(data, 1.0, true, true), 4.14, "channelling in the full garb");
        assertNear(helper, QuincyLogic.gatherPerSecond(data, 2.0, false, false), 0.8, "passive gathering in dense reishi");
        data.setQuincy(data.quincy().withPoison(30.0F));
        assertNear(helper, QuincyLogic.gatherPerSecond(data, 1.0, false, false), 0.3, "passive gathering with poison 30");
        data.setQuincy(data.quincy().withPoison(60.0F));
        assertNear(helper, QuincyLogic.gatherPerSecond(data, 1.0, false, false), 0.2, "passive gathering with poison 60");
        assertNear(helper, QuincyLogic.gatherPerSecond(latent, 1.0, true, true), 0.0, "a latent soul's gathering");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void densityFollowsTheWorld(GameTestHelper helper) {
        assertNear(helper, QuincyLogic.dimensionDensity(Level.OVERWORLD), 1.0, "the Overworld's density");
        assertNear(helper, QuincyLogic.dimensionDensity(Level.NETHER), 0.6, "the Nether's density");
        assertNear(helper, QuincyLogic.dimensionDensity(Level.END), 0.8, "the End's density");
        assertNear(helper, QuincyLogic.dimensionDensity(ResourceKey.create(Registries.DIMENSION, BleachMod.id("elsewhere"))),
                1.0, "another world's density");

        Map<Double, String> feel = Map.of(0.84, "thin", 0.85, "ordinary", 1.15, "rich", 2.0, "dense");
        feel.forEach((density, name) -> helper.assertTrue(QuincyLogic.densityKey(density).equals("density.bleach." + name),
                "density " + density + " feels " + QuincyLogic.densityKey(density) + ", not " + name));
        helper.succeed();
    }

    // ---- Hirenkyaku and footing ----

    @GameTest(template = "empty")
    public static void hirenkyakuNeedsTheSecondTier(GameTestHelper helper) {
        SoulData first = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        helper.assertFalse(QuincyLogic.knowsHirenkyaku(first), "a tier 1 Quincy knows Hirenkyaku");
        helper.assertTrue(QuincyLogic.movementFor(first, false, 0L) == QuincyLogic.Movement.HIRENKYAKU,
                "a tier 1 Quincy is not told about Hirenkyaku");

        SoulData second = quincy(Bloodline.GEMISCHT, 2, 40.0F);
        helper.assertTrue(QuincyLogic.knowsHirenkyaku(second), "a tier 2 Quincy does not know Hirenkyaku");
        assertNear(helper, QuincyLogic.hirenkyakuDistance(second, -1), 7.0, "a tier 2 Hirenkyaku's distance");
        assertNear(helper, QuincyLogic.hirenkyakuCost(second), 3.0, "a tier 2 Hirenkyaku's cost");
        helper.assertTrue(QuincyLogic.hirenkyakuCooldownTicks(second) == 20,
                "a tier 2 Hirenkyaku's cooldown is " + QuincyLogic.hirenkyakuCooldownTicks(second));

        SoulData third = quincy(Bloodline.GEMISCHT, 3, 40.0F);
        third.setLevel(Stat.REIATSU_CONTROL, 20);
        assertNear(helper, QuincyLogic.hirenkyakuDistance(third, -1), 10.5, "a tier 3 Hirenkyaku's distance");
        assertNear(helper, QuincyLogic.hirenkyakuCost(third), 2.0, "a tier 3 Hirenkyaku's cost");
        helper.assertTrue(QuincyLogic.hirenkyakuCooldownTicks(third) == 12,
                "a tier 3 Hirenkyaku's cooldown is " + QuincyLogic.hirenkyakuCooldownTicks(third));
        assertNear(helper, QuincyLogic.hirenkyakuDistance(third, 0), 7.875, "a slowed tier 3 Hirenkyaku's distance");

        SoulData substitute = withShinigamiPower(quincy(Bloodline.GEMISCHT, 2, 40.0F));
        substitute.setLevel(Stat.HOHO, BleachConfig.get(BleachConfig.SHUNPO_MIN_HOHO_LEVEL));
        helper.assertTrue(QuincyLogic.movementFor(substitute, false, 0L) == QuincyLogic.Movement.SHUNPO,
                "a Substitute Quincy without the Cross does not flash step");
        helper.assertTrue(QuincyLogic.movementFor(substitute, true, 0L) == QuincyLogic.Movement.HIRENKYAKU,
                "a Substitute Quincy holding the Cross does not use Hirenkyaku");
        helper.assertTrue(QuincyLogic.movementFor(ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F), false, 0L)
                        == QuincyLogic.Movement.SHUNPO, "a Shinigami without Hohō is not told about Shunpo");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quincyFootingPaysInReishi(GameTestHelper helper) {
        SoulData second = quincy(Bloodline.GEMISCHT, 2, 40.0F);
        helper.assertTrue(ShinigamiLogic.canUseReishiFooting(second, 0L), "a tier 2 Quincy cannot stand on reishi");
        helper.assertTrue(QuincyLogic.footingOnReishi(second, 0L), "a tier 2 Quincy's footing is not paid in reishi");

        SoulData first = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        helper.assertFalse(ShinigamiLogic.canUseReishiFooting(first, 0L), "a tier 1 Quincy can stand on reishi");
        helper.assertFalse(QuincyLogic.footingOnReishi(first, 0L), "a tier 1 Quincy's footing is paid in reishi");

        SoulData poisoned = quincy(Bloodline.GEMISCHT, 2, 40.0F);
        poisoned.setQuincy(poisoned.quincy().withPoison(50.0F));
        helper.assertFalse(ShinigamiLogic.canUseReishiFooting(poisoned, 0L), "a Quincy clouded by poison can stand on reishi");
        helper.assertFalse(QuincyLogic.footingOnReishi(poisoned, 0L), "a Quincy clouded by poison has reishi footing");

        SoulData shinigami = in(SoulState.SHINIGAMI, quincy(Bloodline.GEMISCHT, 2, 40.0F));
        shinigami.setLevel(Stat.HOHO, BleachConfig.get(BleachConfig.FOOTING_MIN_HOHO_LEVEL));
        helper.assertTrue(ShinigamiLogic.canUseReishiFooting(shinigami, 0L), "a Shinigami Quincy cannot stand on reishi");
        helper.assertFalse(QuincyLogic.footingOnReishi(shinigami, 0L),
                "a Shinigami Quincy with the Hohō for it pays for footing in reishi");

        SoulData substitute = withShinigamiPower(quincy(Bloodline.GEMISCHT, 2, 40.0F));
        helper.assertTrue(QuincyLogic.footingOnReishi(substitute, 0L),
                "a Substitute Quincy without Hohō does not stand on reishi as a Quincy");
        helper.succeed();
    }

    // ---- Blut ----

    @GameTest(template = "empty")
    public static void blutUnlocksByTierAndBlood(GameTestHelper helper) {
        record Unlock(Bloodline bloodline, int tier, boolean vene, boolean arterie) {
        }
        for (Unlock unlock : List.of(new Unlock(Bloodline.ECHT, 1, true, false), new Unlock(Bloodline.ECHT, 2, true, true),
                new Unlock(Bloodline.GEMISCHT, 1, false, false), new Unlock(Bloodline.GEMISCHT, 2, true, false),
                new Unlock(Bloodline.GEMISCHT, 3, true, true))) {
            SoulData data = quincy(unlock.bloodline(), unlock.tier(), 40.0F);
            String who = unlock.bloodline().getSerializedName() + " Quincy of tier " + unlock.tier();
            helper.assertTrue(QuincyLogic.blutUnlocked(data, BlutMode.VENE) == unlock.vene(),
                    "Blut Vene is " + (unlock.vene() ? "locked" : "open") + " to a " + who);
            helper.assertTrue(QuincyLogic.blutUnlocked(data, BlutMode.ARTERIE) == unlock.arterie(),
                    "Blut Arterie is " + (unlock.arterie() ? "locked" : "open") + " to a " + who);
        }

        SoulData echtFirst = quincy(Bloodline.ECHT, 1, 40.0F);
        SoulData echtSecond = quincy(Bloodline.ECHT, 2, 40.0F);
        helper.assertTrue(QuincyLogic.nextBlut(echtSecond, BlutMode.OFF, false) == BlutMode.VENE, "the key does not call Blut Vene");
        helper.assertTrue(QuincyLogic.nextBlut(echtSecond, BlutMode.VENE, false) == BlutMode.ARTERIE,
                "the key does not turn Vene to Arterie");
        helper.assertTrue(QuincyLogic.nextBlut(echtFirst, BlutMode.VENE, false) == BlutMode.OFF,
                "the key does not let Vene go while Arterie is locked");
        helper.assertTrue(QuincyLogic.nextBlut(echtSecond, BlutMode.ARTERIE, false) == BlutMode.VENE,
                "the key does not turn Arterie back to Vene");
        helper.assertTrue(QuincyLogic.nextBlut(echtSecond, BlutMode.VENE, true) == BlutMode.OFF, "sneaking does not let Blut go");

        assertNear(helper, QuincyLogic.veneReduction(echtFirst), 0.2, "Blut Vene at tier 1");
        assertNear(helper, QuincyLogic.veneReduction(quincy(Bloodline.ECHT, 3, 40.0F)), 0.3, "Blut Vene at tier 3");
        assertNear(helper, QuincyLogic.veneReduction(withShinigamiPower(quincy(Bloodline.ECHT, 1, 40.0F))), 0.1,
                "Blut Vene in a soul with a Shinigami's power");
        assertNear(helper, QuincyLogic.arterieBonus(echtSecond), 0.25, "Blut Arterie at tier 2");
        assertNear(helper, QuincyLogic.arterieBonus(quincy(Bloodline.ECHT, 3, 40.0F)), 0.30, "Blut Arterie at tier 3");

        SoulData plus = in(SoulState.PLUS, quincy(Bloodline.ECHT, 1, 40.0F));
        plus.setQuincy(plus.quincy().withBlut(BlutMode.VENE));
        helper.assertTrue(QuincyLogic.activeBlut(plus) == BlutMode.OFF, "Blut works in a Plus");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blutVeneSoftensHits(GameTestHelper helper) {
        SoulData data = quincy(Bloodline.ECHT, 1, 40.0F);
        data.setQuincy(data.quincy().withReishi(50.0F).withBlut(BlutMode.VENE));
        Player player = ZanpakutoTests.mock(helper, data);

        player.hurt(player.damageSources().generic(), 10.0F);
        assertNear(helper, player.getHealth(), 12.0, "health after a 10 damage hit under Blut Vene");
        assertNear(helper, QuincyLogic.reishi(data), 49.0, "reishi after Blut Vene stopped 2 damage");

        // Past the invulnerability frames, so only Vene decides what the second hit does.
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().fellOutOfWorld(), 2.0F);
        assertNear(helper, player.getHealth(), 10.0, "health after falling out of the world under Blut Vene");
        assertNear(helper, QuincyLogic.reishi(data), 49.0, "reishi after a hit Blut Vene cannot stop");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blutArterieStrikesHarder(GameTestHelper helper) {
        Cow cow = still(helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 1));
        SoulData data = quincy(Bloodline.ECHT, 2, 40.0F);
        data.setQuincy(data.quincy().withBlut(BlutMode.ARTERIE));
        Player attacker = ZanpakutoTests.mock(helper, data);

        cow.hurt(cow.damageSources().playerAttack(attacker), 4.0F);
        assertNear(helper, cow.getHealth(), 5.0, "a cow's health after a 4 damage blow under Blut Arterie");
        assertNear(helper, QuincyLogic.reiatsuFactor(attacker), 1.5, "the reiatsu of a Quincy under Blut Arterie");
        assertNear(helper, QuincyLogic.reiatsuFactor(ZanpakutoTests.mock(helper, quincy(Bloodline.ECHT, 2, 40.0F))), 1.0,
                "the reiatsu of a Quincy without Blut");
        cow.discard();
        helper.succeed();
    }

    // ---- Hollow poison ----

    @GameTest(template = "field", timeoutTicks = 40)
    public static void hollowHitsPoisonAQuincy(GameTestHelper helper) {
        SpiderHollow spider = still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 8));
        SoulData plain = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        SoulData vene = quincy(Bloodline.ECHT, 1, 40.0F);
        vene.setQuincy(vene.quincy().withReishi(50.0F).withBlut(BlutMode.VENE));
        SoulData latent = quincy(Bloodline.GEMISCHT, 0, 40.0F);
        boolean onHit = spider.kind().onHit != null;
        Difficulty difficulty = helper.getLevel().getDifficulty();

        Map<String, SoulData> souls = Map.of("plain Quincy", plain, "Quincy under Blut Vene", vene, "latent soul", latent);
        souls.forEach((who, data) -> {
            Player player = ZanpakutoTests.mock(helper, data);
            float health = player.getHealth();
            player.hurt(helper.getLevel().damageSources().mobAttack(spider), 4.0F);
            float lost = health - player.getHealth();
            float poison = data.quincy().poison();
            if (data == latent) {
                helper.assertTrue(poison == 0.0F, "a Hollow's blow poisoned a " + who + " with " + poison);
                return;
            }
            // One poison a point at Normal, half as much on Easy, a quarter more on Hard; half again
            // more for a bite that carries an effect, and half as much under Vene. Both souls are tier 1.
            double factor = switch (difficulty) {
                case PEACEFUL, EASY -> 0.5;
                case NORMAL -> 1.0;
                case HARD -> 1.25;
            };
            double expected = lost * (onHit ? 1.5 : 1.0) * (data == vene ? 0.5 : 1.0) * factor;
            helper.assertTrue(poison > 0.0F, "a Hollow's blow did not poison a " + who);
            assertNear(helper, poison, expected, "the poison a " + who + " took from " + lost + " damage");
        });
        spider.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonStagesAndCures(GameTestHelper helper) {
        Map<Float, Integer> stages = Map.of(24.9F, 0, 25.0F, 1, 50.0F, 2, 75.0F, 3, 100.0F, 4);
        stages.forEach((poison, stage) -> helper.assertTrue(QuincyLogic.poisonStage(poison) == stage,
                "poison " + poison + " is at stage " + QuincyLogic.poisonStage(poison) + ", not " + stage));
        assertNear(helper, QuincyLogic.poisonGatherMultiplier(10.0F), 1.0, "gathering with poison 10");
        assertNear(helper, QuincyLogic.poisonGatherMultiplier(25.0F), 0.75, "gathering with poison 25");
        assertNear(helper, QuincyLogic.poisonGatherMultiplier(50.0F), 0.5, "gathering with poison 50");

        SoulData data = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        data.setQuincy(data.quincy().withPoison(30.0F));
        assertNear(helper, QuincyLogic.poisonRegenMultiplier(data), 0.85, "reiryoku coming back with poison 30");
        helper.assertTrue(QuincyLogic.cure(data, 10.0F), "curing poison 30 changed nothing");
        assertNear(helper, data.quincy().poison(), 20.0, "poison 30 cured by 10");
        data.setQuincy(data.quincy().withPoison(5.0F));
        QuincyLogic.cure(data, 10.0F);
        assertNear(helper, data.quincy().poison(), 0.0, "poison 5 cured by 10");

        SoulData plain = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        assertNear(helper, QuincyLogic.poisonFrom(plain, 4.0F, false, Difficulty.NORMAL), 4.0, "poison from a blow of 4");
        assertNear(helper, QuincyLogic.poisonFrom(plain, 4.0F, true, Difficulty.NORMAL), 6.0, "poison from a bite of 4");
        assertNear(helper, QuincyLogic.poisonFrom(plain, 4.0F, false, Difficulty.EASY), 2.0, "poison from a blow of 4 on Easy");
        assertNear(helper, QuincyLogic.poisonFrom(plain, 4.0F, false, Difficulty.PEACEFUL), 2.0, "poison from a blow of 4 on Peaceful");
        assertNear(helper, QuincyLogic.poisonFrom(plain, 4.0F, false, Difficulty.HARD), 5.0, "poison from a blow of 4 on Hard");
        assertNear(helper, QuincyLogic.poisonFrom(plain, 0.0F, true, Difficulty.HARD), 0.0, "poison from a blow of 0");
        SoulData veins = quincy(Bloodline.ECHT, 1, 40.0F);
        veins.setQuincy(veins.quincy().withBlut(BlutMode.VENE));
        assertNear(helper, QuincyLogic.poisonFrom(veins, 4.0F, true, Difficulty.NORMAL), 3.0, "poison from a bite of 4 under Vene");
        assertNear(helper, QuincyLogic.poisonFrom(quincy(Bloodline.GEMISCHT, 3, 40.0F), 4.0F, false, Difficulty.NORMAL), 3.2,
                "poison from a blow of 4 at tier 3");

        assertNear(helper, QuincyProgress.NONE.withPoison(150.0F).poison(), 100.0, "poison 150 kept as");
        assertNear(helper, QuincyProgress.NONE.withPoison(-3.0F).poison(), 0.0, "poison -3 kept as");
        assertNear(helper, QuincyProgress.NONE.withPoison(Float.NaN).poison(), 0.0, "poison NaN kept as");
        helper.succeed();
    }

    // ---- Death ----

    @GameTest(template = "empty")
    public static void destroyedSoulsComeBackHuman(GameTestHelper helper) {
        SoulData data = in(SoulState.PLUS, quincy(Bloodline.ECHT, 2, 100.0F));
        data.setQuincy(data.quincy().withPoison(100.0F).withReishi(20.0F).withBlut(BlutMode.VENE));
        data.markSoulDestroyed();
        // As it would load after logging out on the death screen.
        SoulData saved = SoulData.CODEC.parse(NbtOps.INSTANCE,
                SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
        helper.assertTrue(saved.isSoulDestroyed(), "saving forgot that the soul was torn apart");

        SoulData next = SoulLogic.afterDeath(saved);
        helper.assertTrue(next.state() == SoulState.HUMAN, "a destroyed soul came back as a " + next.state());
        helper.assertTrue(next.chainMaxTicks() == 0, "a destroyed soul came back with a chain");
        assertNear(helper, next.maxReiryoku(), 90.0, "a destroyed soul's maximum reiryoku");
        assertNear(helper, next.quincy().poison(), 0.0, "a destroyed soul's poison");
        assertNear(helper, next.quincy().reishi(), 0.0, "a destroyed soul's reishi");
        helper.assertTrue(next.quincy().blut() == BlutMode.OFF, "a destroyed soul came back under " + next.quincy().blut());
        helper.assertTrue(next.tier(PowerPath.QUINCY) == 2, "a destroyed soul lost its Quincy tier");
        helper.assertTrue(next.wasSoulReformed(), "a destroyed soul is not marked as reformed");
        helper.assertFalse(next.isSoulDestroyed(), "a reformed soul is still being torn apart");

        // An awakened Quincy Plus whose death costs the last of the chain is torn apart now, not one death later.
        SoulData plus = in(SoulState.PLUS, quincy(Bloodline.GEMISCHT, 1, 100.0F));
        plus.setChainTicks(20);
        SoulData torn = SoulLogic.afterDeath(plus);
        helper.assertTrue(torn.state() == SoulState.HUMAN, "a Quincy Plus out of chain came back as a " + torn.state());
        helper.assertTrue(torn.wasSoulReformed(), "a Quincy Plus out of chain is not marked as reformed");
        assertNear(helper, torn.maxReiryoku(), 90.0, "a Quincy Plus out of chain's maximum reiryoku");
        // Any other Plus keeps its last link for the tick after respawning.
        SoulData ordinary = in(SoulState.PLUS, ShinigamiTests.soul(SoulState.HUMAN, 100.0F));
        ordinary.setChainTicks(20);
        SoulData hollowing = SoulLogic.afterDeath(ordinary);
        helper.assertTrue(hollowing.state() == SoulState.PLUS && hollowing.chainTicks() == 1,
                "a Plus out of chain came back as a " + hollowing.state() + " with " + hollowing.chainTicks() + " ticks of chain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void awakenedQuincyAreRebornAlive(GameTestHelper helper) {
        SoulData data = quincy(Bloodline.GEMISCHT, 1, 40.0F);
        data.setQuincy(data.quincy().withPoison(40.0F).withReishi(30.0F).withBlut(BlutMode.VENE));
        SoulData next = SoulLogic.afterDeath(data);
        helper.assertTrue(next.state() == SoulState.HUMAN, "an awakened Quincy came back as a " + next.state());
        helper.assertTrue(next.chainMaxTicks() == 0, "an awakened Quincy came back with a chain");
        helper.assertTrue(next.wasQuincyReborn(), "an awakened Quincy is not marked as reborn");
        assertNear(helper, next.quincy().poison(), 20.0, "a reborn Quincy's poison");
        assertNear(helper, next.quincy().reishi(), 0.0, "a reborn Quincy's reishi");
        helper.assertTrue(next.quincy().blut() == BlutMode.OFF, "a reborn Quincy came back under " + next.quincy().blut());

        SoulData latent = SoulLogic.afterDeath(quincy(Bloodline.GEMISCHT, 0, 40.0F));
        helper.assertTrue(latent.state() == SoulState.PLUS, "a latent Quincy came back as a " + latent.state());
        SoulData substitute = SoulLogic.afterDeath(withShinigamiPower(quincy(Bloodline.GEMISCHT, 1, 40.0F)));
        helper.assertTrue(substitute.state() == SoulState.SHINIGAMI, "a Substitute Quincy came back as a " + substitute.state());
        helper.succeed();
    }

    // ---- The Shinigami ----

    @GameTest(template = "empty")
    public static void quincyCannotTakeShinigamiPower(GameTestHelper helper) {
        helper.assertFalse(ShinigamiLogic.canReceivePower(quincy(Bloodline.GEMISCHT, 1, 40.0F)),
                "an awakened Quincy can take a Shinigami's power");
        helper.assertTrue(ShinigamiLogic.canReceivePower(quincy(Bloodline.GEMISCHT, 0, 40.0F)),
                "a latent Quincy cannot take a Shinigami's power");
        helper.assertTrue(ShinigamiLogic.canReceivePower(ShinigamiTests.soul(SoulState.HUMAN, 40.0F)),
                "a Human without Quincy blood cannot take a Shinigami's power");
        helper.assertFalse(ShinigamiLogic.canEnroll(in(SoulState.RUKONGAI, quincy(Bloodline.GEMISCHT, 1, 40.0F))),
                "an awakened Quincy can enroll at the Academy");
        helper.assertTrue(ShinigamiLogic.canEnroll(in(SoulState.RUKONGAI, quincy(Bloodline.GEMISCHT, 0, 40.0F))),
                "a latent Quincy cannot enroll at the Academy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void instructorAndWoundedKnowAQuincy(GameTestHelper helper) {
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        WoundedShinigami wounded = helper.spawn(ModEntities.WOUNDED_SHINIGAMI.get(), 1, 1, 1);
        long now = helper.getLevel().getGameTime();
        Map<String, SoulData> souls = Map.of(
                "awakened Quincy", quincy(Bloodline.GEMISCHT, 1, 40.0F),
                "latent Quincy", quincy(Bloodline.GEMISCHT, 0, 40.0F),
                "Substitute Quincy", withShinigamiPower(quincy(Bloodline.GEMISCHT, 1, 40.0F)));
        Map<String, DialogueView[]> expected = Map.of(
                "awakened Quincy", new DialogueView[] {DialogueView.QUINCY, DialogueView.QUINCY},
                "latent Quincy", new DialogueView[] {DialogueView.LIVING, DialogueView.OFFER_POWER},
                "Substitute Quincy", new DialogueView[] {DialogueView.SUBSTITUTE, DialogueView.RESTING});
        souls.forEach((who, soul) -> {
            DialogueView[] views = expected.get(who);
            helper.assertTrue(instructor.viewFor(soul, now) == views[0],
                    "the instructor shows " + instructor.viewFor(soul, now) + " to a " + who);
            helper.assertTrue(wounded.viewFor(soul, now) == views[1],
                    "the wounded Shinigami shows " + wounded.viewFor(soul, now) + " to a " + who);
        });
        instructor.discard();
        wounded.discard();
        helper.succeed();
    }

    // ---- Heilig Pfeil in flight ----

    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void heiligPfeilDealsItsDamage(GameTestHelper helper) {
        Cow cow = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 12));
        Player owner = archer(helper, 8.5, 4.5);
        HeiligPfeil arrow = loose(helper, owner, helper.absoluteVec(new Vec3(8.5, 2.5, 4.5)), SOUTH, 6.0F, 0, 3.0F);
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(cow.getHealth() - 4.0F) < 0.01F, "the cow's health is " + cow.getHealth() + ", not 4");
            helper.assertTrue(arrow.isRemoved(), "the arrow flew on after its one hit");
            cow.discard();
        });
    }

    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void heiligPfeilPiercesOnlyAsFarAsItMay(GameTestHelper helper) {
        Cow piercedFront = still(helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 8));
        Cow piercedBack = still(helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 12));
        Cow stoppedFront = still(helper.spawnWithNoFreeWill(EntityType.COW, 11, 2, 8));
        Cow stoppedBack = still(helper.spawnWithNoFreeWill(EntityType.COW, 11, 2, 12));
        Player owner = archer(helper, 8.5, 3.5);
        HeiligPfeil piercing = loose(helper, owner, helper.absoluteVec(new Vec3(4.5, 2.5, 3.5)), SOUTH, 3.0F, 1, 3.0F);
        HeiligPfeil single = loose(helper, owner, helper.absoluteVec(new Vec3(11.5, 2.5, 3.5)), SOUTH, 3.0F, 0, 3.0F);
        helper.runAfterDelay(20, () -> {
            assertNear(helper, piercedFront.getHealth(), 7.0, "the first cow in the piercing arrow's path");
            assertNear(helper, piercedBack.getHealth(), 7.0, "the cow behind it, which the arrow may pierce to");
            assertNear(helper, stoppedFront.getHealth(), 7.0, "the first cow in the other arrow's path");
            assertNear(helper, stoppedBack.getHealth(), 10.0, "the cow behind it, which the arrow may not reach");
            helper.assertTrue(piercing.isRemoved(), "the piercing arrow flew on past its last pierce");
            helper.assertTrue(single.isRemoved(), "the arrow without pierce flew on");
            for (Cow cow : List.of(piercedFront, piercedBack, stoppedFront, stoppedBack)) {
                cow.discard();
            }
            helper.succeed();
        });
    }

    /**
     * A creature right behind another is not missed: not after the arrow's last pierce, and not after
     * it flew through one that took no harm (one still reeling from a blow).
     */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void heiligPfeilReachesTheNextOneClose(GameTestHelper helper) {
        Cow piercedFront = still(helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 8));
        Cow piercedBack = still(helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 9));
        Cow reeling = still(helper.spawnWithNoFreeWill(EntityType.COW, 11, 2, 8));
        Cow behindReeling = still(helper.spawnWithNoFreeWill(EntityType.COW, 11, 2, 9));
        reeling.hurt(helper.getLevel().damageSources().generic(), 4.0F);
        Player owner = archer(helper, 8.5, 3.5);
        HeiligPfeil piercing = loose(helper, owner, helper.absoluteVec(new Vec3(4.5, 2.5, 3.5)), SOUTH, 3.0F, 1, 3.0F);
        HeiligPfeil single = loose(helper, owner, helper.absoluteVec(new Vec3(11.5, 2.5, 3.5)), SOUTH, 3.0F, 0, 3.0F);
        helper.runAfterDelay(8, () -> {
            assertNear(helper, piercedFront.getHealth(), 7.0, "the first cow in the piercing arrow's path");
            assertNear(helper, piercedBack.getHealth(), 7.0, "the cow a step behind it");
            assertNear(helper, reeling.getHealth(), 6.0, "the reeling cow, which the arrow flies through");
            assertNear(helper, behindReeling.getHealth(), 7.0, "the cow a step behind the reeling one");
            helper.assertTrue(piercing.isRemoved(), "the piercing arrow flew on past its last pierce");
            helper.assertTrue(single.isRemoved(), "the arrow without pierce flew on past the cow it hurt");
            for (Cow cow : List.of(piercedFront, piercedBack, reeling, behindReeling)) {
                cow.discard();
            }
            helper.succeed();
        });
    }

    /**
     * An arrow that flies on past a creature still bursts on the wall behind it, rather than carrying
     * through to whoever stands on the other side: after a pierce, and after flying through a creature
     * that took no harm (one still reeling from a blow).
     */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void heiligPfeilStopsAtTheWallBehindACreature(GameTestHelper helper) {
        Cow pierced = still(helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 10));
        Cow reeling = still(helper.spawnWithNoFreeWill(EntityType.COW, 11, 2, 10));
        Cow hidingFromPierce = still(helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 13));
        Cow hidingFromSingle = still(helper.spawnWithNoFreeWill(EntityType.COW, 11, 2, 13));
        for (int x : new int[] {4, 11}) {
            helper.setBlock(x, 2, 11, Blocks.STONE);
            helper.setBlock(x, 3, 11, Blocks.STONE);
        }
        reeling.hurt(helper.getLevel().damageSources().generic(), 4.0F);
        Player owner = archer(helper, 8.5, 3.5);
        HeiligPfeil piercing = loose(helper, owner, helper.absoluteVec(new Vec3(4.5, 2.5, 3.5)), SOUTH, 3.0F, 1, 3.0F);
        HeiligPfeil single = loose(helper, owner, helper.absoluteVec(new Vec3(11.5, 2.5, 3.5)), SOUTH, 3.0F, 0, 3.0F);
        helper.runAfterDelay(10, () -> {
            assertNear(helper, pierced.getHealth(), 7.0, "the cow the piercing arrow hurt before the wall");
            assertNear(helper, reeling.getHealth(), 6.0, "the reeling cow the other arrow flew through");
            assertNear(helper, hidingFromPierce.getHealth(), 10.0, "the cow behind the wall the piercing arrow met");
            assertNear(helper, hidingFromSingle.getHealth(), 10.0, "the cow behind the wall the other arrow met");
            helper.assertTrue(piercing.isRemoved(), "the piercing arrow did not burst on the wall");
            helper.assertTrue(single.isRemoved(), "the arrow that flew through a creature did not burst on the wall");
            for (Cow cow : List.of(pierced, reeling, hidingFromPierce, hidingFromSingle)) {
                cow.discard();
            }
            helper.succeed();
        });
    }

    /** A Breeze that turns the arrow back, just past the creature it pierced, spares the block behind. */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void breezesTurnHeiligPfeilBack(GameTestHelper helper) {
        Cow open = still(helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 8));
        Cow guarded = still(helper.spawnWithNoFreeWill(EntityType.COW, 11, 2, 8));
        Breeze breeze = still(helper.spawnWithNoFreeWill(EntityType.BREEZE, 11, 2, 9));
        BlockPos openTarget = new BlockPos(4, 2, 10);
        BlockPos guardedTarget = new BlockPos(11, 2, 10);
        helper.setBlock(openTarget, Blocks.TARGET);
        helper.setBlock(guardedTarget, Blocks.TARGET);
        Player owner = archer(helper, 8.5, 3.5);
        // Each meets its cow, then (in the second lane) the Breeze, then the target, all in one step.
        HeiligPfeil unturned = loose(helper, owner, helper.absoluteVec(new Vec3(4.5, 2.5, 6.2)), SOUTH, 3.0F, 1, 4.0F);
        loose(helper, owner, helper.absoluteVec(new Vec3(11.5, 2.5, 6.2)), SOUTH, 3.0F, 1, 4.0F);
        helper.runAfterDelay(6, () -> {
            assertNear(helper, open.getHealth(), 7.0, "the cow in front of the open target");
            assertNear(helper, guarded.getHealth(), 7.0, "the cow in front of the Breeze");
            assertNear(helper, breeze.getHealth(), breeze.getMaxHealth(), "the health of the Breeze");
            helper.assertBlockProperty(openTarget, BlockStateProperties.POWER, power -> power > 0,
                    "the target behind the pierced cow was not struck");
            helper.assertBlockProperty(guardedTarget, BlockStateProperties.POWER, 0);
            helper.assertTrue(unturned.isRemoved(), "the arrow did not burst on the open target");
            for (Mob mob : List.<Mob>of(open, guarded, breeze)) {
                mob.discard();
            }
            helper.succeed();
        });
    }

    /** A raised shield facing the shot stops it, pierce or not, and nothing behind is touched. */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void shieldsStopHeiligPfeil(GameTestHelper helper) {
        Cow guard = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 8));
        Cow behind = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 12));
        guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        // Facing north, towards where the arrow comes from.
        guard.setYRot(180.0F);
        guard.setYHeadRot(180.0F);
        guard.setYBodyRot(180.0F);
        guard.startUsingItem(InteractionHand.MAIN_HAND);
        Player owner = archer(helper, 6.5, 3.5);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(guard.isBlocking(), "the cow is not holding its shield up");
            HeiligPfeil arrow = loose(helper, owner, helper.absoluteVec(new Vec3(8.5, 2.5, 3.5)), SOUTH, 3.0F, 2, 3.0F);
            helper.runAfterDelay(8, () -> {
                assertNear(helper, guard.getHealth(), 10.0, "the health of the cow behind its shield");
                assertNear(helper, behind.getHealth(), 10.0, "the health of the cow behind the shield");
                helper.assertTrue(arrow.isRemoved(), "the arrow flew on past the shield");
                guard.discard();
                behind.discard();
                helper.succeed();
            });
        });
    }

    /** A shield turned away from the shot stops nothing: one still reeling behind it is flown through. */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void shieldsOnlyStopShotsTheyFace(GameTestHelper helper) {
        Cow guard = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 8));
        Cow behind = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 12));
        guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        // Facing south, away from where the arrow comes from.
        guard.setYRot(0.0F);
        guard.setYHeadRot(0.0F);
        guard.setYBodyRot(0.0F);
        guard.startUsingItem(InteractionHand.MAIN_HAND);
        Player owner = archer(helper, 6.5, 3.5);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(guard.isBlocking(), "the cow is not holding its shield up");
            // A harder blow just before, so the arrow does it no harm.
            guard.hurt(guard.damageSources().generic(), 4.0F);
            assertNear(helper, guard.getHealth(), 6.0, "the health of the cow after the first blow");
            HeiligPfeil arrow = loose(helper, owner, helper.absoluteVec(new Vec3(8.5, 2.5, 3.5)), SOUTH, 3.0F, 0, 3.0F);
            helper.runAfterDelay(8, () -> {
                assertNear(helper, guard.getHealth(), 6.0, "the health of the cow with its back to the arrow");
                assertNear(helper, behind.getHealth(), 7.0, "the health of the cow behind it");
                helper.assertTrue(arrow.isRemoved(), "the arrow flew on past the cow behind");
                guard.discard();
                behind.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void heiligPfeilNeverStrikesItsOwner(GameTestHelper helper) {
        Cow ownerCow = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 6));
        Cow target = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 12));
        // Loosed from the middle of its owner, so the arrow starts inside it.
        loose(helper, ownerCow, ownerCow.getBoundingBox().getCenter(), SOUTH, 3.0F, 0, 3.0F);
        helper.runAfterDelay(20, () -> {
            assertNear(helper, ownerCow.getHealth(), 10.0, "the owner's health");
            assertNear(helper, target.getHealth(), 7.0, "the target's health");
            helper.assertTrue(ownerCow.getLastHurtMob() == target, "the owner does not remember what its arrow hit");
            ownerCow.discard();
            target.discard();
            helper.succeed();
        });
    }

    /** A shot from inside a creature lands; one that only starts against a creature's side, aimed away, does not. */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void pointBlankShotsLand(GameTestHelper helper) {
        Cow cow = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 8));
        Cow brushed = still(helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 8));
        Player owner = archer(helper, 6.5, 8.5);
        loose(helper, owner, cow.getBoundingBox().getCenter(), EAST, 3.0F, 0, 3.0F);
        // A fifth of a block north of the other cow, and flying away from it.
        AABB box = brushed.getBoundingBox();
        loose(helper, owner, new Vec3(box.getCenter().x, box.getCenter().y, box.minZ - 0.2), NORTH, 3.0F, 0, 3.0F);
        helper.runAfterDelay(5, () -> {
            assertNear(helper, cow.getHealth(), 7.0, "the health of a cow shot from inside");
            assertNear(helper, brushed.getHealth(), 10.0, "the health of a cow a shot started beside and flew away from");
            cow.discard();
            brushed.discard();
            helper.succeed();
        });
    }

    /**
     * A shot from inside two creatures at once strikes both if it may pierce, and one that flies
     * through the first (still reeling from a blow) strikes the other.
     */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void pointBlankShotsStrikeEveryoneThere(GameTestHelper helper) {
        Cow first = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 4));
        Cow second = still(helper.spawnWithNoFreeWill(EntityType.COW, 8, 2, 4));
        Cow reeling = still(helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 12));
        Cow beside = still(helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 12));
        reeling.hurt(helper.getLevel().damageSources().generic(), 4.0F);
        Player owner = archer(helper, 8.5, 8.5);
        loose(helper, owner, first.getBoundingBox().getCenter(), EAST, 3.0F, 1, 3.0F);
        loose(helper, owner, reeling.getBoundingBox().getCenter(), EAST, 3.0F, 0, 3.0F);
        helper.runAfterDelay(5, () -> {
            assertNear(helper, first.getHealth(), 7.0, "the first of two cows a piercing shot started inside");
            assertNear(helper, second.getHealth(), 7.0, "the second of two cows a piercing shot started inside");
            assertNear(helper, reeling.getHealth(), 6.0, "the reeling cow a shot started inside");
            assertNear(helper, beside.getHealth(), 7.0, "the cow beside the reeling one");
            for (Cow cow : List.of(first, second, reeling, beside)) {
                cow.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void heiligPfeilLeavesNothingBehind(GameTestHelper helper) {
        Player owner = archer(helper, 8.5, 5.5);
        HeiligPfeil down = loose(helper, owner, helper.absoluteVec(new Vec3(8.5, 3.5, 2.5)), DOWN, 3.0F, 0, 1.0F);
        helper.assertTrue(down.pickup == AbstractArrow.Pickup.DISALLOWED, "a Heilig Pfeil can be picked up");
        // Slow enough to stay under the test's barrier ceiling for its whole life.
        HeiligPfeil up = loose(helper, owner, helper.absoluteVec(new Vec3(8.5, 4.0, 8.5)), UP, 3.0F, 0, 0.15F);
        int life = BleachConfig.get(BleachConfig.PFEIL_LIFE_TICKS);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(down.isRemoved(), "an arrow that met the floor is still there");
            helper.assertEntityNotPresent(EntityType.ITEM);
        });
        helper.runAfterDelay(life - 5, () -> helper.assertFalse(up.isRemoved(), "an arrow went before its time was up"));
        helper.runAfterDelay(life + 4, () -> {
            helper.assertTrue(up.isRemoved(), "an arrow flew on past its time");
            helper.succeed();
        });
    }

    /**
     * A Heilig Pfeil that kills a Hollow destroys it rather than purifying it. The kill earns the
     * destroyed soul's training and nothing for the hit, since the Hollow it struck is dead.
     */
    @GameTest(template = "field", batch = "quincy", timeoutTicks = 60)
    public static void heiligPfeilDestroysHollows(GameTestHelper helper) {
        SpiderHollow spider = still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 12));
        SoulData data = quincy(Bloodline.GEMISCHT, 1, 200.0F);
        data.setQuincy(data.quincy().withTraining(40));
        Player owner = archer(helper, data, 8.5, 5.5);
        helper.runAfterDelay(2, () -> {
            spider.setHealth(1.0F);
            loose(helper, owner, helper.absoluteVec(new Vec3(8.5, 2.6, 5.5)), SOUTH, 5.0F, 0, 3.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(spider.isAlive(), "the Heilig Pfeil did not kill the Hollow");
            helper.assertFalse(spider.isPurifying(), "a Heilig Pfeil purified a Hollow");
            helper.assertTrue(data.hunt().slain() == 1 && data.hunt().purified() == 0,
                    "the kill counted as " + data.hunt().slain() + " slain and " + data.hunt().purified() + " purified");
            helper.assertTrue(data.quincy().destroyed() == 1, "the kill counted " + data.quincy().destroyed() + " destroyed souls");
            helper.assertTrue(data.quincy().training() == 50, "destroying a Hollow left training " + data.quincy().training());
            spider.discard();
        });
    }
}
