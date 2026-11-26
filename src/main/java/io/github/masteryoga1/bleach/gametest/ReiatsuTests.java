package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.soul.Fright;
import io.github.masteryoga1.bleach.soul.ReiatsuEvents;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritEvents;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests for reiatsu difference, the pressure aura, spiritual awareness and the advancements. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ReiatsuTests {
    private static final float EPSILON = 1.0E-3F;

    private ReiatsuTests() {
    }

    @GameTest(template = "empty")
    public static void reiatsuDifferenceSoftensWeakHits(GameTestHelper helper) {
        double ratio = BleachConfig.get(BleachConfig.REIATSU_DIFFERENCE_RATIO);
        float minimum = (float) BleachConfig.get(BleachConfig.REIATSU_DIFFERENCE_MIN_DAMAGE);
        helper.assertTrue(SoulLogic.reiatsuDamageMultiplier(10.0F, 10.0F) == 1.0F, "equals should hit fully");
        helper.assertTrue(SoulLogic.reiatsuDamageMultiplier(10.0F, (float) (10.0 * ratio)) == 1.0F,
                "a gap up to the ratio should still hit fully");
        float halved = SoulLogic.reiatsuDamageMultiplier(10.0F, (float) (20.0 * ratio));
        helper.assertTrue(Math.abs(halved - 0.5F) < EPSILON, "twice the ratio should halve the hit, got " + halved);
        float floor = SoulLogic.reiatsuDamageMultiplier(1.0F, 1_000_000.0F);
        helper.assertTrue(Math.abs(floor - minimum) < EPSILON, "a huge gap should stop at the minimum, got " + floor);
        helper.assertTrue(SoulLogic.reiatsuDamageMultiplier(1_000.0F, 10.0F) == 1.0F, "the strong should hit the weak fully");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pressureWeighsDownTheWeak(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
        float pigReiatsu = SoulLogic.reiatsu(pig);
        double minRatio = BleachConfig.get(BleachConfig.PRESSURE_MIN_RATIO);

        helper.assertTrue(SoulLogic.applyPressure(pig, (float) (pigReiatsu * minRatio * 0.9)) == 0,
                "a pig resisted less than it should");
        helper.assertFalse(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "weak pressure still slowed the pig");

        helper.assertTrue(SoulLogic.applyPressure(pig, (float) (pigReiatsu * minRatio)) == 1, "pressure at the ratio is level 1");
        MobEffectInstance slow = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null && slow.getAmplifier() == 0, "level 1 should be Slowness I");
        helper.assertFalse(pig.hasEffect(MobEffects.WEAKNESS), "level 1 should not weaken");

        helper.assertTrue(SoulLogic.applyPressure(pig, (float) (pigReiatsu * minRatio * 4)) == 3, "four times over is level 3");
        slow = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null && slow.getAmplifier() == 2, "level 3 should be Slowness III");
        helper.assertTrue(pig.hasEffect(MobEffects.DIG_SLOWDOWN), "no Mining Fatigue under pressure");
        helper.assertTrue(pig.hasEffect(MobEffects.WEAKNESS), "level 3 should weaken");
        helper.assertTrue(slow.showIcon() && !slow.isVisible(), "pressure should show an icon but no particles");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frightenedCreaturesRun(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        helper.assertTrue(zombie.goalSelector.getAvailableGoals().stream()
                        .filter(goal -> goal.getGoal() instanceof ReiatsuFleeGoal).count() == 1,
                "a zombie should get exactly one reiatsu flee goal");
        helper.assertFalse(ReiatsuFleeGoal.isFrightened(zombie), "a new zombie is already frightened");
        long now = helper.getLevel().getGameTime();
        zombie.setData(SoulAttachments.FRIGHT, new Fright(now + 30, zombie.position().add(1.0, 0.0, 0.0)));
        helper.assertTrue(ReiatsuFleeGoal.isFrightened(zombie), "the fright did not take");
        zombie.setData(SoulAttachments.FRIGHT, new Fright(now - 1, zombie.position()));
        helper.assertFalse(ReiatsuFleeGoal.isFrightened(zombie), "the fright did not wear off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frightenedCreaturesDropTheirGrudge(GameTestHelper helper) {
        Player releaser = soulPlayer(helper, SoulState.HUMAN, 400.0F);
        long until = helper.getLevel().getGameTime() + 30;

        Hoglin hoglin = helper.spawnWithNoFreeWill(EntityType.HOGLIN, 1, 1, 1);
        hoglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, releaser);
        ReiatsuEvents.frighten(hoglin, releaser, until);
        helper.assertFalse(hoglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                "a hoglin kept attacking whoever frightened it");
        helper.assertTrue(ReiatsuFleeGoal.isFrightened(hoglin), "the hoglin was not frightened");

        WalkTarget flight = new WalkTarget(hoglin.position().add(8.0, 0.0, 0.0), 1.4F, 0);
        hoglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, releaser);
        ReiatsuFleeGoal.steerBrain(hoglin.getBrain(), flight);
        helper.assertFalse(hoglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                "a running hoglin still had a target");
        helper.assertTrue(hoglin.getBrain().isMemoryValue(MemoryModuleType.WALK_TARGET, flight),
                "the hoglin's brain was not told to run");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 2);
        ReiatsuFleeGoal.steerBrain(zombie.getBrain(), flight);
        helper.assertFalse(zombie.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "steering touched a creature moved by goals");

        Creeper hissing = helper.spawnWithNoFreeWill(EntityType.CREEPER, 2, 1, 1);
        hissing.setSwellDir(1);
        ReiatsuEvents.frighten(hissing, releaser, until);
        Creeper lit = helper.spawnWithNoFreeWill(EntityType.CREEPER, 2, 1, 2);
        lit.ignite();
        lit.setSwellDir(1);
        ReiatsuEvents.frighten(lit, releaser, until);
        boolean defused = hissing.getSwellDir() < 0;
        boolean stillLit = lit.getSwellDir() > 0;
        // Gone before the lit one can blow up beside the other tests.
        hissing.discard();
        lit.discard();
        helper.assertTrue(defused, "a frightened creeper kept its fuse lit");
        helper.assertTrue(stillLit, "a creeper lit with flint and steel was put out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseNeedsReiryokuAndGrowsTheAura(GameTestHelper helper) {
        SoulData data = new SoulData();
        SoulLogic.initialize(data);
        helper.assertTrue(SoulLogic.canRelease(data), "a full, aware soul could not release");
        data.setReiryoku(data.maxReiryoku() * SoulLogic.RELEASE_MIN_FRACTION * 0.5F);
        helper.assertFalse(SoulLogic.canRelease(data), "released with almost no reiryoku");

        float small = SoulLogic.pressureRadius(data);
        data.setMaxReiryoku(400.0F);
        float large = SoulLogic.pressureRadius(data);
        helper.assertTrue(large > small, "more reiryoku should reach further");
        data.setMaxReiryoku(1_000_000.0F);
        helper.assertTrue(SoulLogic.pressureRadius(data) <= BleachConfig.get(BleachConfig.PRESSURE_MAX_RADIUS) + EPSILON,
                "the aura went past its maximum radius");

        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
        data.setMaxReiryoku(100.0F);
        data.setReiryoku(1.0F);
        helper.assertFalse(SoulLogic.drain(pig, data, 0.75F), "draining the last of it should run dry");
        helper.assertTrue(data.reiryoku() == 0.0F, "reiryoku left after running dry: " + data.reiryoku());
        helper.assertTrue(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "running dry should exhaust");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unawareHandsPassThroughSpirits(GameTestHelper helper) {
        Player unaware = soulPlayer(helper, SoulState.HUMAN, 10.0F);
        Player aware = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        Player spirit = soulPlayer(helper, SoulState.PLUS, 40.0F);
        Player human = soulPlayer(helper, SoulState.HUMAN, 40.0F);

        helper.assertTrue(SpiritEvents.passesThrough(unaware, spirit), "the unaware touched a spirit");
        helper.assertFalse(SpiritEvents.passesThrough(aware, spirit), "the aware could not touch a spirit");
        helper.assertFalse(SpiritEvents.passesThrough(unaware, human), "the unaware could not touch the living");
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, soul(SoulState.HUMAN, 10.0F));
        helper.assertFalse(SpiritEvents.passesThrough(creative, spirit), "creative mode should touch anything");

        helper.assertFalse(spirit.addEffect(new MobEffectInstance(MobEffects.POISON, 100), unaware),
                "an unaware player's potion reached a spirit");
        helper.assertFalse(spirit.hasEffect(MobEffects.POISON), "the spirit was poisoned anyway");
        helper.assertTrue(spirit.addEffect(new MobEffectInstance(MobEffects.POISON, 100), aware),
                "an aware player's potion did not reach the spirit");

        float health = spirit.getHealth();
        spirit.hurt(unaware.damageSources().playerAttack(unaware), 5.0F);
        helper.assertTrue(spirit.getHealth() == health, "an unaware player's hit landed on a spirit");
        spirit.hurt(aware.damageSources().playerAttack(aware), 5.0F);
        helper.assertTrue(spirit.getHealth() < health, "an aware player's hit did not land");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void instantPotionsSkipOnlySpirits(GameTestHelper helper) {
        Player unaware = soulPlayer(helper, SoulState.HUMAN, 10.0F);
        Player aware = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        Player spirit = soulPlayer(helper, SoulState.PLUS, 40.0F);
        helper.assertTrue(SpiritEvents.potionPassesThrough(unaware, spirit), "an unaware player's potion reached a spirit");
        helper.assertFalse(SpiritEvents.potionPassesThrough(aware, spirit), "an aware player's potion missed a spirit");
        helper.assertFalse(SpiritEvents.potionPassesThrough(null, spirit), "a potion nobody threw missed a spirit");
        helper.assertFalse(SpiritEvents.potionPassesThrough(spirit, spirit), "a spirit's own potion missed it");

        // The same cloud still heals the living, so the spirit check does not block healing outright.
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
        pig.setHealth(2.0F);
        AreaEffectCloud cloud = new AreaEffectCloud(helper.getLevel(), pig.getX(), pig.getY(), pig.getZ());
        cloud.setOwner(unaware);
        cloud.setPotionContents(new PotionContents(Potions.HEALING));
        cloud.setRadius(2.0F);
        cloud.setWaitTime(0);
        cloud.setDuration(60);
        helper.getLevel().addFreshEntity(cloud);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() > 2.0F, "an unaware player's healing cloud did not heal a pig");
            cloud.discard();
        });
    }

    private static SoulData soul(SoulState state, float maxReiryoku) {
        SoulData data = new SoulData();
        SoulLogic.initialize(data);
        SoulLogic.changeState(data, state);
        data.setMaxReiryoku(maxReiryoku);
        data.setReiryoku(maxReiryoku);
        return data;
    }

    private static Player soulPlayer(GameTestHelper helper, SoulState state, float maxReiryoku) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SoulAttachments.SOUL, soul(state, maxReiryoku));
        return player;
    }

    @GameTest(template = "empty")
    public static void appearanceSurvivesTheNetwork(GameTestHelper helper) {
        SoulData data = soul(SoulState.PLUS, 40.0F);
        data.setChainTicks(data.chainMaxTicks() * 42 / 100);
        data.setReiatsuReleased(true);
        SoulAppearance look = SoulAppearance.of(data);
        helper.assertTrue(Math.abs(look.chainFraction() - 0.5F) < EPSILON,
                "chain should round up to a tenth, got " + look.chainFraction());

        ByteBuf buffer = Unpooled.buffer();
        SoulAppearance.STREAM_CODEC.encode(buffer, look);
        SoulAppearance read = SoulAppearance.STREAM_CODEC.decode(buffer);
        helper.assertTrue(read.equals(look), "sent " + look + " but read " + read);

        SoulAppearance vene = new SoulAppearance(SoulState.HUMAN, 0.0F, false, 0, BlutMode.VENE);
        ByteBuf veneBuffer = Unpooled.buffer();
        SoulAppearance.STREAM_CODEC.encode(veneBuffer, vene);
        SoulAppearance veneRead = SoulAppearance.STREAM_CODEC.decode(veneBuffer);
        helper.assertTrue(veneRead.equals(vene), "sent " + vene + " but read " + veneRead);

        data.setChainTicks(1);
        helper.assertTrue(SoulAppearance.of(data).chainFraction() > 0.0F, "a chain with links left should still show");
        helper.assertTrue(SoulAppearance.of(soul(SoulState.HOLLOW, 40.0F)).chainFraction() == 0.0F, "a Hollow shows a chain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void advancementsLoad(GameTestHelper helper) {
        for (String name : List.of("root", "trained", "rising_reiatsu", "crushing_pressure", "plus", "encroachment", "hollow",
                "passed_on", "enrolled", "graduated", "konso", "substitute", "shunpo", "reishi_footing", "shihakusho",
                "garganta", "first_hollow", "mask_strike", "purified", "fishbone_d", "soul_burial", "jinzen",
                "asauchi_bond", "zanpakuto_name", "shikai", "getsuga_tensho", "quincy", "heilig_pfeil", "hirenkyaku",
                "blut_vene", "blut_arterie", "quincy_garb", "hollow_poison", "last_quincy", "devoured", "cannibal",
                "souls_within", "cleansed", "senkaimon", "dangai", "hueco_mundo", "bankai")) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id(name)) != null,
                    "advancement bleach:" + name + " did not load");
        }
        helper.succeed();
    }
}
