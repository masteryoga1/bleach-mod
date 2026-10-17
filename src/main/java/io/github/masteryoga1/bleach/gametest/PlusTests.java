package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Plus spirits: their chain and a Hollow's bite on it, turning Hollow when it runs out,
 * Konsō, the Pluses villagers leave, fleeing Hollows and pulling themselves back together after a
 * lethal blow. Mock players come from {@link HollowTests}. The {@code field} template is a 16 by 16
 * stone floor creatures stand on at relative y 2.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class PlusTests {
    private static final float EPSILON = 1.0E-3F;
    private static final int TICKS_PER_MINUTE = 1200;

    private PlusTests() {
    }

    /** How long a Plus spirit's chain is, in ticks: 24000 with the defaults. */
    private static int fullChain() {
        return BleachConfig.get(BleachConfig.PLUS_CHAIN_MINUTES) * TICKS_PER_MINUTE;
    }

    /** The Plus spirits within {@code range} blocks of an absolute position. */
    private static List<PlusSpirit> plusesNear(GameTestHelper helper, Vec3 at, double range) {
        return helper.getLevel().getEntitiesOfClass(PlusSpirit.class, AABB.ofSize(at, range * 2.0, range * 2.0, range * 2.0),
                plus -> plus.position().distanceTo(at) <= range);
    }

    @GameTest(template = "empty")
    public static void plusesStartWithAChain(GameTestHelper helper) {
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 1, 1, 1);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(SoulLogic.hasSoul(plus), "a Plus has no soul after its first tick");
            SoulData soul = SoulLogic.get(plus);
            int full = fullChain();
            helper.assertTrue(soul.state() == SoulState.PLUS, "a Plus's soul is a " + soul.state());
            helper.assertTrue(soul.chainMaxTicks() == full, "a Plus's chain is " + soul.chainMaxTicks() + " ticks, not " + full);
            helper.assertTrue(soul.chainTicks() >= full - 2, "a new Plus's chain is already down to " + soul.chainTicks());
            helper.assertTrue(Math.abs(soul.maxReiryoku() - 12.0F) < EPSILON, "a Plus has reiryoku " + soul.maxReiryoku());
            helper.succeed();
        });
    }

    @GameTest(template = "field")
    public static void aPlusWhoseChainRunsOutBecomesAHollow(GameTestHelper helper) {
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 8, 2, 8);
        plus.soul().setChainTicks(2);
        Vec3 where = plus.position();
        helper.succeedWhen(() -> {
            helper.assertTrue(plus.isRemoved(), "the Plus is still there");
            List<Hollow> hollows = helper.getLevel().getEntitiesOfClass(Hollow.class, AABB.ofSize(where, 4.0, 4.0, 4.0),
                    hollow -> hollow.position().distanceTo(where) <= 2.0 && hollow.fromPlus());
            helper.assertFalse(hollows.isEmpty(), "no Hollow took the Plus's place");
            Hollow hollow = hollows.get(0);
            helper.assertTrue(hollow.isMaskForming(), "the new Hollow's mask is not forming");
            hollow.discard();
        });
    }

    @GameTest(template = "field")
    public static void hollowBitesShortenTheChain(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 3, 2, 3);
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 6, 2, 3);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 9, 2, 3);
        DamageSources damage = helper.getLevel().damageSources();
        int cost = BleachConfig.get(BleachConfig.HOLLOW_BITE_CHAIN_COST_SECONDS) * 20;
        SoulData soul = plus.soul();
        int full = soul.chainTicks();

        // 30 seconds a bite with the defaults, but never below the last minute: 24000 to 23400, 1500 to 1200, 600 stays.
        helper.assertTrue(plus.hurt(damage.mobAttack(spider), 1.0F), "the bite did not land");
        helper.assertTrue(soul.chainTicks() == full - cost, "a bite left " + soul.chainTicks() + " of " + full);
        plus.invulnerableTime = 0;
        soul.setChainTicks(1500);
        plus.hurt(damage.mobAttack(spider), 1.0F);
        int floored = Math.max(PlusSpirit.BITE_FLOOR_TICKS, 1500 - cost);
        helper.assertTrue(soul.chainTicks() == floored, "a bite took a chain of 1500 to " + soul.chainTicks() + ", not " + floored);
        plus.invulnerableTime = 0;
        soul.setChainTicks(600);
        plus.hurt(damage.mobAttack(spider), 1.0F);
        helper.assertTrue(soul.chainTicks() == 600, "a bite shortened a chain already in its last minute, to " + soul.chainTicks());

        Player player = HollowTests.soulPlayer(helper, SoulState.PLUS, 40.0F);
        SoulData playerSoul = SoulLogic.get(player);
        playerSoul.startChain(fullChain());
        player.hurt(damage.mobAttack(spider), 1.0F);
        helper.assertTrue(playerSoul.chainTicks() == fullChain() - cost,
                "a bite left a Plus player " + playerSoul.chainTicks() + " of " + fullChain());

        plus.invulnerableTime = 0;
        soul.setChainTicks(full);
        float health = plus.getHealth();
        plus.hurt(damage.mobAttack(zombie), 1.0F);
        helper.assertTrue(soul.chainTicks() == full && plus.getHealth() == health, "a zombie's blow landed on a Plus");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 60)
    public static void konsoSendsAPlusMobOn(GameTestHelper helper) {
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 8, 2, 8);

        Player human = HollowTests.soulPlayer(helper, SoulState.HUMAN, 40.0F);
        HollowTests.arm(human, ModItems.ASAUCHI.get());
        human.setShiftKeyDown(true);
        plus.interact(human, InteractionHand.MAIN_HAND);
        helper.assertFalse(plus.isDeparting(), "a Human without Shinigami power sent a Plus on");

        Player shinigami = HollowTests.shinigami(helper, 100.0F);
        HollowTests.arm(shinigami, ModItems.ASAUCHI.get());
        shinigami.setShiftKeyDown(true);
        helper.assertTrue(plus.interact(shinigami, InteractionHand.MAIN_HAND).consumesAction(), "Konsō on a Plus did nothing");
        helper.assertTrue(plus.isDeparting(), "Konsō did not send the Plus on");
        helper.assertFalse(plus.shouldBeSaved(), "a Plus passing on would be saved with the world");
        SoulData data = SoulLogic.get(shinigami);
        float grown = (float) (100.0 + BleachConfig.get(BleachConfig.KONSO_GROWTH));
        helper.assertTrue(Math.abs(data.maxReiryoku() - grown) < EPSILON,
                "Konsō grew max reiryoku to " + data.maxReiryoku() + ", not " + grown);
        helper.assertTrue(data.hunt().sentOn() == 1, "Konsō counted " + data.hunt().sentOn() + " souls sent on");
        helper.assertTrue(data.shinigami().konsoRewards() == 1, "Konsō counted " + data.shinigami().konsoRewards() + " rewards");
        helper.succeedWhen(() -> helper.assertTrue(plus.isRemoved(), "the Plus has not passed on"));
    }

    @GameTest(template = "field")
    public static void deadVillagersLeavePluses(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 8, 2, 8);
        villager.setCustomName(Component.literal("Bob"));
        // Not what a Plus would be by default, so a Plus that only looks like one cannot pass.
        villager.setVillagerData(new VillagerData(VillagerType.SNOW, VillagerProfession.FARMER, 2));
        Vec3 where = villager.position();
        villager.kill();
        helper.succeedWhen(() -> {
            List<PlusSpirit> pluses = plusesNear(helper, where, 3.0);
            helper.assertTrue(pluses.size() == 1, "the villager left " + pluses.size() + " Pluses");
            PlusSpirit plus = pluses.get(0);
            helper.assertTrue(plus.isPersistenceRequired(), "a villager's Plus may despawn");
            Component name = plus.getCustomName();
            helper.assertTrue(name != null && name.getString().equals("Bob"), "the villager's Plus is called " + name);
            VillagerData look = plus.getVillagerData();
            helper.assertTrue(look.getType() == VillagerType.SNOW && look.getProfession() == VillagerProfession.FARMER
                    && look.getLevel() == 2, "the villager's Plus looks like " + look.getType() + " "
                    + look.getProfession() + " " + look.getLevel());
            helper.assertTrue(plus.hasRestriction(), "the villager's Plus is not kept near its home");
            plus.discard();
        });
    }

    @GameTest(template = "field")
    public static void convertedVillagersLeaveNone(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 8, 2, 8);
        Vec3 where = villager.position();
        // What a zombie's killing blow does: the villager dies and is turned in the same tick.
        villager.setHealth(0.0F);
        villager.die(helper.getLevel().damageSources().generic());
        ZombieVillager zombie = villager.convertTo(EntityType.ZOMBIE_VILLAGER, false);
        helper.assertTrue(zombie != null, "the villager was not turned");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(plusesNear(helper, where, 3.0).isEmpty(), "a villager turned into a zombie left a Plus");
            helper.assertTrue(zombie.isAlive(), "the zombie villager is gone");
            zombie.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "field", batch = "plus_cap")
    public static void villagerPlusesAreCapped(GameTestHelper helper) {
        int cap = BleachConfig.get(BleachConfig.VILLAGER_PLUS_CAP);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 8, 2, 8);
        Vec3 where = villager.position();
        // The cap of Pluses (8 by default) in a ring two blocks around the villager.
        for (int i = 0; i < cap; i++) {
            double angle = 2.0 * Math.PI * i / cap;
            helper.spawnWithNoFreeWill(ModEntities.PLUS.get(),
                    new Vec3(8.5 + 2.0 * Math.cos(angle), 2.0, 8.5 + 2.0 * Math.sin(angle)));
        }
        villager.kill();
        helper.runAfterDelay(5, () -> {
            List<PlusSpirit> pluses = plusesNear(helper, where, 16.0);
            helper.assertTrue(pluses.size() == cap, "with " + cap + " Pluses near, a villager's death left " + pluses.size());
            pluses.forEach(Entity::discard);
            helper.succeed();
        });
    }

    @GameTest(template = "field")
    public static void plusesFleeHollows(GameTestHelper helper) {
        PlusSpirit plus = helper.spawn(ModEntities.PLUS.get(), new Vec3(8.5, 2.0, 8.5));
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), new Vec3(8.5, 2.0, 5.5));
        long flee = plus.goalSelector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof ReiatsuFleeGoal).count();
        helper.assertTrue(flee == 1, "a Plus has " + flee + " reiatsu flee goals, not 1");
        double start = plus.distanceTo(spider);
        // With no Hollow player about, the only thing a Plus can be running from is the Hollow.
        helper.succeedWhen(() -> {
            helper.assertTrue(plus.goalSelector.getAvailableGoals().stream()
                            .anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof AvoidEntityGoal),
                    "a Plus is not running from a Hollow");
            helper.assertTrue(plus.distanceTo(spider) > start + 1.0, "a Plus running from a Hollow got no further from it");
            plus.discard();
            spider.discard();
        });
    }

    @GameTest(template = "empty")
    public static void chainHoldsWhereHollowsCannotForm(GameTestHelper helper) {
        helper.assertFalse(HollowLogic.mayHollowfy(Difficulty.PEACEFUL), "a Plus may turn Hollow on Peaceful");
        for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
            helper.assertTrue(HollowLogic.mayHollowfy(difficulty), "a Plus may not turn Hollow on " + difficulty);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ambientPlusChances(GameTestHelper helper) {
        // 0.1 a minute with the defaults, and three times that in a village.
        double chance = BleachConfig.get(BleachConfig.PLUS_CHANCE_PER_MINUTE);
        double village = Math.min(1.0, chance * BleachConfig.get(BleachConfig.PLUS_VILLAGE_MULTIPLIER));
        helper.assertTrue(Math.abs(HollowLogic.plusChancePerCheck(false) - Math.min(1.0, chance)) < 1.0E-6,
                "the chance away from villages is " + HollowLogic.plusChancePerCheck(false));
        helper.assertTrue(Math.abs(HollowLogic.plusChancePerCheck(true) - village) < 1.0E-6,
                "the chance in a village is " + HollowLogic.plusChancePerCheck(true) + ", not " + village);
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 60)
    public static void aLethalBlowMakesAPlusReform(GameTestHelper helper) {
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 8, 2, 8);
        SoulData soul = plus.soul();
        int before = soul.chainTicks();
        Player attacker = HollowTests.soulPlayer(helper, SoulState.HUMAN, 40.0F);

        plus.hurt(helper.getLevel().damageSources().playerAttack(attacker), 1000.0F);
        // Five minutes of chain with the defaults: 24000 to 18000.
        int expected = Math.max(1, before - BleachConfig.get(BleachConfig.PLUS_DEATH_CHAIN_COST_SECONDS) * 20);
        helper.assertTrue(plus.isAlive(), "a lethal blow killed the Plus outright");
        helper.assertTrue(Math.abs(plus.getHealth() - 15.0F) < EPSILON, "the Plus pulled itself together with " + plus.getHealth() + " health");
        helper.assertTrue(soul.chainTicks() == expected, "pulling together left " + soul.chainTicks() + " of the chain, not " + expected);

        plus.kill();
        helper.succeedWhen(() -> helper.assertTrue(plus.isRemoved(), "/kill did not end the Plus"));
    }
}
