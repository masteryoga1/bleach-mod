package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Shikai: releasing and sealing the zanpakutō and what a release costs, Zangetsu's constant
 * release and its Getsuga Tenshō, and Zabimaru's lash. The checks in {@link ShikaiEvents} run on mock
 * players from {@link ZanpakutoTests}. The crescents fly for real on the {@code field} template, a
 * 16 by 16 stone floor they cross at relative y 2.5, owned by a mock that is not in the world.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ShikaiTests {
    private static final float EPSILON = 1.0E-3F;
    /** The crescents in the field tests fly this many ticks: from x 1.5 to just short of the far edge. */
    private static final int CRESCENT_LIFE = 9;

    private ShikaiTests() {
    }

    /**
     * A crescent of the lowest charge with no blade, swung along +X from relative (0.5, 2.5, 8.5), so it
     * flies from (1.5, 2.5, 8.5).
     */
    static GetsugaTensho crescent(GameTestHelper helper, Player owner, float damage) {
        Vec3 root = helper.absoluteVec(new Vec3(0.5, 2.5, 8.5));
        Vec3 along = helper.absoluteVec(new Vec3(1.5, 2.5, 8.5)).subtract(root);
        return GetsugaTensho.launch(helper.getLevel(), owner, root, along, 0.0F, damage, CRESCENT_LIFE, ItemStack.EMPTY,
                ZanpakutoBlade.DEFAULT_COLOUR);
    }

    /** A creature that stands where it is put and does nothing. */
    private static <T extends Mob> T still(T mob) {
        mob.setNoAi(true);
        return mob;
    }

    // ---- Release and seal ----

    @GameTest(template = "empty")
    public static void releaseChecksComeInOrder(GameTestHelper helper) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = SoulLogic.get(player);
        ItemStack own = player.getMainHandItem().copy();
        long now = helper.getLevel().getGameTime();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.NO_BLADE, "an empty hand released something");
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.NAMELESS, "a nameless Asauchi was not nameless");
        player.setItemInHand(InteractionHand.MAIN_HAND,
                ZanpakutoTests.someoneElsesBlade(helper, ZanpakutoSpirits.ZABIMARU));
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.NOT_YOURS, "someone else's blade answered");
        player.setItemInHand(InteractionHand.MAIN_HAND, own);

        ShinigamiProgress progress = data.shinigami();
        data.setShinigami(progress.withPowerLentUntil(now + 1200L));
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.LENT, "a soul whose power is lent released");
        data.setShinigami(progress);
        data.setState(SoulState.HOLLOW);
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.SILENT, "a Hollow released a zanpakutō");
        data.setState(SoulState.SHINIGAMI);
        data.setReiryoku(0.4F);
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.TOO_WEAK, "0.4 reiryoku paid for a release");
        data.setReiryoku(data.maxReiryoku());

        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.RELEASED, "a ready Shinigami could not release");
        player.tickCount += 10;
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.SETTLING, "a release half a second later did not wait");
        player.tickCount += 41;
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.ALREADY, "a released blade released again");

        Player ichigo = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        helper.assertTrue(ShikaiEvents.release(ichigo) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
        ichigo.tickCount += 41;
        helper.assertTrue(ShikaiEvents.release(ichigo) == ShikaiEvents.Result.CONSTANT, "a released Zangetsu released again");

        Player lost = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData lostData = SoulLogic.get(lost);
        ResourceLocation missing = BleachMod.id("missing");
        lostData.setZanpakuto(lostData.zanpakuto().withSpirit(Optional.of(missing)));
        ItemStack blade = lost.getMainHandItem();
        ZanpakutoBlade bound = ZanpakutoItem.blade(blade);
        blade.set(ModComponents.ZANPAKUTO_BLADE.get(), new ZanpakutoBlade(bound.owner(), bound.ownerName(), Optional.of(missing),
                ZanpakutoStage.SEALED, false, bound.colour()));
        helper.assertTrue(ShikaiEvents.release(lost) == ShikaiEvents.Result.SILENT, "a spirit missing from the world answered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sealingRestoresTheSealedForm(GameTestHelper helper) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = SoulLogic.get(player);
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.RELEASED, "Zabimaru would not release");
        ItemStack stack = player.getMainHandItem();
        helper.assertTrue(ZanpakutoTests.model(stack) == 3, "released Zabimaru uses model " + ZanpakutoTests.model(stack));
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double reach = 0.0;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ENTITY_INTERACTION_RANGE) && entry.modifier().id().equals(ZanpakutoItem.REACH_ID)) {
                reach = entry.modifier().amount();
            }
        }
        helper.assertTrue(Math.abs(reach - 3.0) < EPSILON, "released Zabimaru adds " + reach + " reach, not 3");
        helper.assertTrue(data.isShikaiActive(), "the release was not noted");

        helper.assertTrue(ShikaiEvents.seal(player) == ShikaiEvents.Result.SEALED, "Zabimaru would not seal");
        helper.assertTrue(ZanpakutoTests.model(stack) == 2, "sealed Zabimaru uses model " + ZanpakutoTests.model(stack));
        helper.assertTrue(stack.getComponentsPatch().get(DataComponents.ATTRIBUTE_MODIFIERS) == null,
                "sealed Zabimaru kept stats of its own");
        helper.assertFalse(ZanpakutoItem.blade(stack).released(), "the sealed blade is still marked released");
        helper.assertFalse(data.isShikaiActive(), "the soul is still in Shikai after sealing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zangetsuIsReleasedOnceAndForAll(GameTestHelper helper) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(player);
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
        ItemStack stack = player.getMainHandItem();
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        helper.assertTrue(blade.constant() && blade.released(), "released Zangetsu is not a constant release");
        helper.assertTrue(data.zanpakuto().firstReleaseDone(), "the first release was not remembered");
        helper.assertFalse(data.isShikaiActive(), "a constant release counts as a Shikai that drains and seals");
        helper.assertTrue(ShikaiEvents.seal(player) == ShikaiEvents.Result.CANNOT_SEAL, "Zangetsu sealed");

        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(5, stack);
        ZanpakutoEvents.reconcile(player, data);
        ItemStack moved = player.getInventory().getItem(5);
        helper.assertTrue(ZanpakutoItem.blade(moved).released() && ZanpakutoTests.model(moved) == 1,
                "Zangetsu sealed when it left the hand");

        player.getInventory().setItem(5, ZanpakutoTests.asauchi());
        ZanpakutoEvents.reconcile(player, data);
        ItemStack reformed = player.getInventory().getItem(5);
        helper.assertTrue(ZanpakutoItem.isOwnedBy(reformed, player.getUUID()) && ZanpakutoItem.blade(reformed).released()
                        && ZanpakutoTests.model(reformed) == 1,
                "a lost Zangetsu re-formed sealed, or not at all");

        // The spirit changed away and back while this blade lay in a chest: this Zangetsu is yet to be released.
        data.setZanpakuto(data.zanpakuto().withFirstReleaseDone(false));
        ZanpakutoEvents.reconcile(player, data);
        ZanpakutoBlade waiting = ZanpakutoItem.blade(player.getInventory().getItem(5));
        helper.assertTrue(waiting.constant() && !waiting.released(), "a Zangetsu released before its first release stayed out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shikaiDrainShrinksWithReiatsuControl(GameTestHelper helper) {
        ZanpakutoSpirit zabimaru = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F);
        int[] levels = {0, 10, 50};
        float[] drains = {0.15F, 0.12F, 0.06F};
        for (int i = 0; i < levels.length; i++) {
            data.setLevel(Stat.REIATSU_CONTROL, levels[i]);
            float drain = ZanpakutoLogic.shikaiDrainPerTick(data, zabimaru);
            helper.assertTrue(Math.abs(drain - drains[i]) < 1.0E-4F,
                    "with Reiatsu Control " + levels[i] + " Zabimaru drained " + drain + " a tick, not " + drains[i]);
        }
        float zangetsu = ZanpakutoLogic.shikaiDrainPerTick(data, ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU));
        helper.assertTrue(Math.abs(zangetsu) < 1.0E-4F, "Zangetsu drained " + zangetsu + " a tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void releasedShikaiPausesRegeneration(GameTestHelper helper) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = SoulLogic.get(player);
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.RELEASED, "Zabimaru would not release");
        helper.assertFalse(SoulEvents.mayRegenerate(player, data), "reiryoku refills with Zabimaru released");
        helper.assertTrue(ShikaiEvents.seal(player) == ShikaiEvents.Result.SEALED, "Zabimaru would not seal");
        helper.assertTrue(SoulEvents.mayRegenerate(player, data), "reiryoku does not refill after sealing");

        Player ichigo = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        helper.assertTrue(ShikaiEvents.release(ichigo) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
        helper.assertTrue(SoulEvents.mayRegenerate(ichigo, SoulLogic.get(ichigo)), "reiryoku does not refill with Zangetsu out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void constantReleaseDrawsMoreHollows(GameTestHelper helper) {
        double multiplier = BleachConfig.get(BleachConfig.CONSTANT_RELEASE_ARRIVAL_MULTIPLIER);
        helper.assertTrue(Math.abs(multiplier - 1.25) < 1.0E-9, "the default constant_release_arrival_multiplier is " + multiplier);
        SoulData plain = ZanpakutoTests.soul(SoulState.SHINIGAMI, 400.0F);
        plain.setHunt(plain.hunt().withFirstHollowDone(true));
        SoulData constant = ZanpakutoTests.soul(SoulState.SHINIGAMI, 400.0F);
        constant.setHunt(constant.hunt().withFirstHollowDone(true));
        constant.setZanpakuto(ZanpakutoBond.NONE.asNamed(Component.literal("Zangetsu"), true).withFirstReleaseDone(true));
        double base = HollowLogic.arrivalChancePerMinute(plain, false);
        double more = HollowLogic.arrivalChancePerMinute(constant, false);
        helper.assertTrue(base > 0.0, "a Shinigami draws no Hollows at all");
        helper.assertTrue(Math.abs(more - base * 1.25) < 1.0E-9,
                "Zangetsu out for good made the chance " + more + ", not " + base * 1.25);
        helper.succeed();
    }

    // ---- Getsuga Tenshō ----

    @GameTest(template = "empty")
    public static void getsugaNumbersFollowTheConfig(GameTestHelper helper) {
        SoulData named = ZanpakutoTests.soul(SoulState.SHINIGAMI, 400.0F);
        named.setZanpakuto(ZanpakutoBond.NONE.withGetsugaNamed(true));
        SoulData unnamed = ZanpakutoTests.soul(SoulState.SHINIGAMI, 400.0F);
        SoulData strongest = ZanpakutoTests.soul(SoulState.SHINIGAMI, 1.0E6F);
        strongest.setZanpakuto(ZanpakutoBond.NONE.withGetsugaNamed(true));
        assertNear(helper, ZanpakutoLogic.getsugaDamage(named, 1.0F), 28.8, "a named full swing at 400 reiryoku");
        assertNear(helper, ZanpakutoLogic.getsugaDamage(unnamed, 0.0F), 10.8, "an unnamed tap at 400 reiryoku");
        assertNear(helper, ZanpakutoLogic.getsugaDamage(strongest, 1.0F), 72.0, "a full swing at the damage cap");

        assertNear(helper, ZanpakutoLogic.getsugaCost(unnamed, 0.0F), 24.0, "a tap's cost at 400 reiryoku");
        assertNear(helper, ZanpakutoLogic.getsugaCost(unnamed, 1.0F), 64.0, "a full swing's cost at 400 reiryoku");
        assertNear(helper, ZanpakutoLogic.getsugaCost(ZanpakutoTests.soul(SoulState.SHINIGAMI, 20.0F), 0.0F), 3.0,
                "a tap's cost at 20 reiryoku");

        helper.assertTrue(GetsugaTensho.pierceFor(0.0F) == 3 && GetsugaTensho.pierceFor(0.5F) == 4 && GetsugaTensho.pierceFor(1.0F) == 5,
                "a crescent cuts through " + GetsugaTensho.pierceFor(0.0F) + ", " + GetsugaTensho.pierceFor(0.5F) + " and "
                        + GetsugaTensho.pierceFor(1.0F) + " creatures");
        assertNear(helper, GetsugaTensho.halfWidthFor(0.5F), 1.7, "half a half-charged crescent's width");
        helper.assertTrue(GetsugaTensho.lifeTicksFor(40.0) == 25,
                "a 40-block crescent flies " + GetsugaTensho.lifeTicksFor(40.0) + " ticks");

        SoulData budget = ZanpakutoTests.soul(SoulState.SHINIGAMI, 400.0F);
        budget.setReiryoku(40.5F);
        assertNear(helper, ZanpakutoLogic.affordableCharge(budget, 1.0F), 0.4, "the charge 40.5 of 400 reiryoku pays for");
        budget.setReiryoku(2.0F);
        assertNear(helper, ZanpakutoLogic.affordableCharge(budget, 1.0F), -1.0, "the charge 2 reiryoku pays for");
        helper.succeed();
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON, what + " was " + actual + ", not " + expected);
    }

    @GameTest(template = "empty")
    public static void getsugaNeedsZangetsuReleasedFirst(GameTestHelper helper) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        helper.assertTrue(ShikaiEvents.gather(player) == ShikaiEvents.GatherResult.NOT_RELEASED, "sealed Zangetsu gathered");
        Player renji = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        helper.assertTrue(ShikaiEvents.release(renji) == ShikaiEvents.Result.RELEASED, "Zabimaru would not release");
        helper.assertTrue(ShikaiEvents.gather(renji) == ShikaiEvents.GatherResult.NO_TECHNIQUE, "Zabimaru gathered a Getsuga Tenshō");

        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
        helper.assertTrue(ShikaiEvents.gather(player) == ShikaiEvents.GatherResult.GATHERING, "released Zangetsu did not gather");
        helper.assertTrue(ShikaiEvents.gather(player) == ShikaiEvents.GatherResult.ALREADY_GATHERING, "it gathered twice at once");
        GetsugaTensho fired = ShikaiEvents.fire(player);
        helper.assertTrue(fired != null, "letting go loosed no Getsuga Tenshō");
        fired.discard();
        helper.assertTrue(ShikaiEvents.gather(player) == ShikaiEvents.GatherResult.COOLDOWN, "it gathered again at once");

        // The cooldown cannot be reset from here, so the weak swing is another soul's.
        Player weak = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        helper.assertTrue(ShikaiEvents.release(weak) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
        SoulLogic.get(weak).setReiryoku(1.0F);
        helper.assertTrue(ShikaiEvents.gather(weak) == ShikaiEvents.GatherResult.TOO_WEAK, "1 reiryoku gathered a Getsuga Tenshō");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void getsugaIsNamedAfterTenCrescents(GameTestHelper helper) {
        SoulData data = ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F);
        Player player = ZanpakutoTests.mock(helper, data);
        for (int i = 1; i < 10; i++) {
            helper.assertFalse(ShikaiEvents.noteGetsugaFired(player, data, 0.0F), "Zangetsu named the technique after " + i);
        }
        helper.assertTrue(ShikaiEvents.noteGetsugaFired(player, data, 0.0F), "Zangetsu did not name the technique after 10");
        helper.assertTrue(data.zanpakuto().getsugaNamed() && data.zanpakuto().getsugaFired() == 10, "the naming was not remembered");
        helper.assertFalse(ShikaiEvents.noteGetsugaFired(player, data, 0.0F), "Zangetsu named the technique twice");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 60, batch = "getsuga")
    public static void getsugaCutsThroughThreeHollows(GameTestHelper helper) {
        List<SpiderHollow> spiders = new ArrayList<>();
        for (int x : new int[] {4, 7, 10, 13}) {
            spiders.add(still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), x, 2, 8)));
        }
        Player owner = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        GetsugaTensho crescent = crescent(helper, owner, 1000.0F);
        helper.succeedWhen(() -> {
            int purified = SoulLogic.get(owner).hunt().purified();
            helper.assertTrue(purified == 3, "the crescent purified " + purified + " Hollows, not 3");
            helper.assertTrue(spiders.get(3).isAlive(), "the crescent cut a fourth Hollow");
            helper.assertTrue(crescent.isRemoved(), "the crescent is still flying");
            spiders.get(3).discard();
        });
    }

    @GameTest(template = "field", timeoutTicks = 60, batch = "getsuga")
    public static void getsugaSparesPlusesAndShinigami(GameTestHelper helper) {
        PlusSpirit plus = still(helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 5, 2, 8));
        WoundedShinigami wounded = still(helper.spawnWithNoFreeWill(ModEntities.WOUNDED_SHINIGAMI.get(), 8, 2, 8));
        SpiderHollow spider = still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 11, 2, 8));
        // She is wounded from the start, so what matters is that the crescent takes nothing more.
        float plusHealth = plus.getHealth();
        float woundedHealth = wounded.getHealth();
        Player owner = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        // A Plus cut down by anything but a Hollow re-forms at full health, and the last damage taken is
        // forgotten after two seconds, so any hit at all is noted as it happens.
        boolean[] plusCut = {false};
        boolean[] woundedCut = {false};
        helper.onEachTick(() -> {
            plusCut[0] |= plus.getLastDamageSource() != null;
            woundedCut[0] |= wounded.getLastDamageSource() != null;
        });
        crescent(helper, owner, 1000.0F);
        helper.succeedWhen(() -> {
            helper.assertFalse(spider.isAlive(), "the Hollow behind them was not cut");
            helper.assertTrue(plus.isAlive() && plus.getHealth() == plusHealth && !plusCut[0], "the crescent cut a Plus");
            helper.assertTrue(wounded.isAlive() && wounded.getHealth() == woundedHealth && !woundedCut[0],
                    "the crescent cut a Shinigami");
            plus.discard();
            wounded.discard();
        });
    }

    /**
     * A launched crescent that reaches a sinner opens the Gate of Hell for it in place of purifying it, and flies on past
     * the Gate's square to purify the plain Hollow behind; its owner is credited as for a purification for both, once each.
     * HellGateTests takes the Getsuga by its damage type; this is the real crescent in flight.
     */
    @GameTest(template = "field", timeoutTicks = 60, batch = "getsuga")
    public static void getsugaSendsASinnerToTheGate(GameTestHelper helper) {
        SpiderHollow sinner = still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 4, 2, 8));
        sinner.setSinner(true);
        SpiderHollow plain = still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 10, 2, 8));
        plain.setSinner(false);
        Player owner = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        crescent(helper, owner, 1000.0F);
        helper.succeedWhen(() -> {
            helper.assertTrue(sinner.isHellbound() && !sinner.isPurifying(), "the crescent purified the sinner");
            helper.assertTrue(plain.isPurifying() && !plain.isHellbound(), "the crescent sent the plain Hollow to the Gate");
            int purified = SoulLogic.get(owner).hunt().purified();
            helper.assertTrue(purified == 2, "the owner was credited " + purified + " times, not 2");
        });
    }

    @GameTest(template = "field", timeoutTicks = 40, batch = "getsuga")
    public static void getsugaCutsPointBlankButNothingBehind(GameTestHelper helper) {
        // Three swings start at x 3.5 with the wielder facing +X: level along z 6.5, aimed 45 degrees
        // down along z 2.5 and straight down along z 10.5. In each lane one pig is pressed against the
        // front of the swing, short of where the crescent appears, and the other stands just behind
        // it, inside the crescent's first sweep.
        Player owner = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        owner.setYRot(-90.0F);
        String[] swings = {"level", "45 degrees down", "straight down", "straight down from a step"};
        List<Pig> fronts = new ArrayList<>();
        List<Pig> behinds = new ArrayList<>();
        for (double[] lane : new double[][] {{6.5, 2.5, 1.0, 0.0}, {2.5, 3.2, 1.0, -1.0}, {10.5, 3.2, 0.0, -1.0}}) {
            double z = lane[0];
            fronts.add(still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.0, 2.0, z))));
            behinds.add(still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.9, 2.0, z))));
            Vec3 root = helper.absoluteVec(new Vec3(3.5, lane[1], z));
            Vec3 along = helper.absoluteVec(new Vec3(3.5 + lane[2], lane[1] + lane[3], z)).subtract(root);
            GetsugaTensho.launch(helper.getLevel(), owner, root, along, 0.0F, 1000.0F, 4, ItemStack.EMPTY,
                    ZanpakutoBlade.DEFAULT_COLOUR);
        }
        // Straight down along z 10.5 by a wielder standing on a block at x 11: the pig at the foot of
        // the block behind them is lower than their feet, yet still at their back.
        helper.setBlock(11, 2, 10, Blocks.STONE);
        fronts.add(still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(12.5, 2.0, 10.5))));
        behinds.add(still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(10.5, 2.0, 10.5))));
        GetsugaTensho.launch(helper.getLevel(), owner, helper.absoluteVec(new Vec3(11.5, 4.2, 10.5)), new Vec3(0.0, -1.0, 0.0),
                0.0F, 1000.0F, 4, ItemStack.EMPTY, ZanpakutoBlade.DEFAULT_COLOUR);
        // Straight down from high up along z 14.5: a pig on the ground a little to the wielder's back
        // side is far out along the path, not at their back, so it is cut.
        Pig below = still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.6, 2.0, 14.5)));
        GetsugaTensho.launch(helper.getLevel(), owner, helper.absoluteVec(new Vec3(3.5, 11.5, 14.5)), new Vec3(0.0, -1.0, 0.0),
                0.0F, 1000.0F, 8, ItemStack.EMPTY, ZanpakutoBlade.DEFAULT_COLOUR);
        helper.succeedWhen(() -> {
            helper.assertFalse(below.isAlive(), "the straight-down crescent missed the pig far below the wielder");
            for (int i = 0; i < swings.length; i++) {
                String swing = swings[i];
                helper.assertFalse(fronts.get(i).isAlive(), "the crescent swung " + swing + " missed the pig against the front of the swing");
                Pig behind = behinds.get(i);
                helper.assertTrue(behind.isAlive() && behind.getHealth() == behind.getMaxHealth(),
                        "the crescent swung " + swing + " cut the pig behind the swing");
            }
            behinds.forEach(Entity::discard);
        });
    }

    @GameTest(template = "field", timeoutTicks = 60, batch = "getsuga")
    public static void getsugaCarvesSoftBlocksAndStopsAtStone(GameTestHelper helper) {
        helper.setBlock(6, 2, 8, Blocks.DIRT);
        helper.setBlock(8, 2, 8, Blocks.GLASS);
        helper.setBlock(11, 2, 8, Blocks.STONE);
        helper.setBlock(13, 2, 8, Blocks.DIRT);
        Player owner = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        GetsugaTensho crescent = crescent(helper, owner, 10.0F);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, 6, 2, 8);
            helper.assertBlockPresent(Blocks.AIR, 8, 2, 8);
            helper.assertBlockPresent(Blocks.STONE, 11, 2, 8);
            helper.assertBlockPresent(Blocks.DIRT, 13, 2, 8);
            helper.assertTrue(crescent.isRemoved(), "the crescent flew on through the stone");
            for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds())) {
                drop.discard();
            }
        });
    }

    @GameTest(template = "field")
    public static void getsugaRespectsGriefingAndHardness(GameTestHelper helper) {
        double hardness = BleachConfig.get(BleachConfig.GETSUGA_MAX_HARDNESS);
        helper.assertTrue(Math.abs(hardness - 0.8) < 1.0E-9, "the default max_hardness is " + hardness);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        for (Block soft : List.of(Blocks.DIRT, Blocks.GLASS, Blocks.OAK_LEAVES)) {
            helper.assertTrue(GetsugaTensho.cuttable(soft.defaultBlockState(), level, pos, hardness), soft + " is too hard to cut");
        }
        for (Block hard : List.of(Blocks.STONE, Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.CHEST)) {
            helper.assertFalse(GetsugaTensho.cuttable(hard.defaultBlockState(), level, pos, hardness), hard + " can be cut");
        }
        // Soft enough, but it holds a block entity.
        helper.assertFalse(GetsugaTensho.cuttable(Blocks.DAYLIGHT_DETECTOR.defaultBlockState(), level, pos, hardness),
                "a daylight detector can be cut");

        Player builder = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        GetsugaTensho breaker = new GetsugaTensho(ModEntities.GETSUGA_TENSHO.get(), level);
        breaker.setPos(Vec3.atCenterOf(pos));
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        helper.assertFalse(GetsugaTensho.permitted(level, pos, dirt, breaker, builder, false),
                "a crescent broke blocks with block breaking off");
        helper.assertTrue(GetsugaTensho.permitted(level, pos, dirt, breaker, builder, true),
                "a survival player's crescent could not cut dirt");
        helper.assertTrue(GetsugaTensho.permitted(level, pos, dirt, null, builder, true), "a survival player could not cut dirt");
        Player visitor = helper.makeMockPlayer(GameType.ADVENTURE);
        visitor.getAbilities().mayBuild = false;
        helper.assertFalse(GetsugaTensho.permitted(level, pos, dirt, breaker, visitor, true), "a player who may not build cut dirt");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void getsugaWithoutAnOwnerFades(GameTestHelper helper) {
        Vec3 root = helper.absoluteVec(new Vec3(0.5, 1.5, 1.5));
        Vec3 along = helper.absoluteVec(new Vec3(1.5, 1.5, 1.5)).subtract(root);
        GetsugaTensho crescent = GetsugaTensho.launch(helper.getLevel(), null, root, along, 0.0F, 10.0F, CRESCENT_LIFE,
                ItemStack.EMPTY, ZanpakutoBlade.DEFAULT_COLOUR);
        helper.succeedWhen(() -> helper.assertTrue(crescent.isRemoved(), "a crescent with no owner is still flying"));
    }

    // ---- Zabimaru ----

    @GameTest(template = "field")
    public static void zabimaruLashesBeyondArmsLength(GameTestHelper helper) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        helper.assertTrue(ShikaiEvents.release(player) == ShikaiEvents.Result.RELEASED, "Zabimaru would not release");
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 8.5));
        player.setPos(at.x, at.y, at.z);
        ItemStack blade = player.getMainHandItem();

        Pig far = still(helper.spawnWithNoFreeWill(EntityType.PIG, 7, 2, 8));
        helper.assertTrue(ShikaiEvents.lash(player, far, blade), "a blow five blocks away did not lash out");
        List<ZabimaruLash> lashes = helper.getLevel().getEntitiesOfClass(ZabimaruLash.class, far.getBoundingBox().inflate(2.0));
        helper.assertFalse(lashes.isEmpty(), "no lash reached the pig");
        AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        AttributeModifier retract = reach == null ? null : reach.getModifier(ShikaiEvents.RETRACT_ID);
        helper.assertTrue(retract != null && Math.abs(retract.amount() + 3.0) < EPSILON,
                "the drawn-back reach was " + (retract == null ? "never taken" : retract.amount()));

        Pig near = still(helper.spawnWithNoFreeWill(EntityType.PIG, 4, 2, 8));
        helper.assertFalse(ShikaiEvents.lash(player, near, blade), "a blow two blocks away lashed out");
        lashes.forEach(Entity::discard);
        far.discard();
        near.discard();
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 40)
    public static void aTossedOrDroppedBladeSeals(GameTestHelper helper) {
        Player owner = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        ItemStack zabimaru = ZanpakutoTests.asauchi();
        ZanpakutoItem.bind(zabimaru, owner, ZanpakutoSpirits.ZABIMARU.location(),
                ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU), true);
        ItemStack zangetsu = ZanpakutoTests.asauchi();
        ZanpakutoItem.bind(zangetsu, owner, ZanpakutoSpirits.ZANGETSU.location(),
                ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), true);
        ItemEntity tossed = drop(helper, new Vec3(4.5, 2.0, 8.5), zabimaru);
        ItemEntity dropped = drop(helper, new Vec3(10.5, 2.0, 8.5), zangetsu);
        helper.succeedWhen(() -> {
            ItemStack lying = tossed.getItem();
            helper.assertTrue(!ZanpakutoItem.blade(lying).released() && ZanpakutoTests.model(lying) == 2,
                    "Zabimaru on the ground is still released");
            ItemStack constant = dropped.getItem();
            helper.assertTrue(ZanpakutoItem.blade(constant).released() && ZanpakutoTests.model(constant) == 1,
                    "Zangetsu sealed on the ground");
            tossed.discard();
            dropped.discard();
        });
    }

    /** An item lying still at this relative position. */
    private static ItemEntity drop(GameTestHelper helper, Vec3 relative, ItemStack stack) {
        Vec3 at = helper.absoluteVec(relative);
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    @GameTest(template = "empty")
    public static void lendingPowerSealsTheGiversShikai(GameTestHelper helper) {
        Player giver = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData giverData = SoulLogic.get(giver);
        helper.assertTrue(ShikaiEvents.release(giver) == ShikaiEvents.Result.RELEASED, "Zabimaru would not release");
        Player human = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.HUMAN,
                (float) (double) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD)));
        ShinigamiEvents.transfer(giver, human);
        helper.assertFalse(giverData.isShikaiActive(), "the giver is still in Shikai");
        ItemStack blade = giver.getMainHandItem();
        helper.assertTrue(!ZanpakutoItem.blade(blade).released() && ZanpakutoTests.model(blade) == 2,
                "the giver's Zabimaru stayed released");
        helper.succeed();
    }
}
