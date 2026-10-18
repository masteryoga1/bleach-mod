package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.GintoSpells;
import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SprengerShape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Gintō: filling an empty tube, choosing a spell, and the four spells themselves.
 * Gritz binds what is looked at, Wolke breaks falls, Heizen strikes a pillar, and Sprenger blasts the
 * inside of a pentagon of Seele Schneider. Casts come from mock players from {@link ZanpakutoTests},
 * which are not in the world but are still found by the casts they raise. The world tests use the
 * {@code field} template, a 16 by 16 stone floor with creatures at relative y 2, and run in their own
 * batch, apart from the other Quincy tests.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GintoTests {
    /** Room for the float steps of a health bar. */
    private static final double HEALTH_EPSILON = 1.0E-3;
    /**
     * Five floor blocks (relative x, z) whose blades stand in a pentagon round about (8.5, 8.3): the
     * Sprenger formation of these tests. The block at the middle, (8, 8), is inside it.
     */
    private static final int[][] PENTAGON = {{8, 5}, {11, 7}, {10, 10}, {6, 10}, {5, 7}};

    private GintoTests() {
    }

    // ---- Helpers ----

    /** A stack of filled Gintō in the player's main hand, as a cast takes them. */
    private static ItemStack tubes(Player player, int count) {
        ItemStack stack = new ItemStack(ModItems.GINTO.get(), count);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    /** The same soul with this spell chosen for its Gintō. */
    private static SoulData choosing(SoulData data, GintoSpell spell) {
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withGinto(spell));
        return data;
    }

    private static boolean onCooldown(Player player) {
        return player.getCooldowns().isOnCooldown(ModItems.GINTO.get());
    }

    /** Plants the owner's blades on the pentagon's floor blocks, in order, and returns them. */
    private static List<SeeleSchneiderPfeil> plantPentagon(GameTestHelper helper, Player owner) {
        List<SeeleSchneiderPfeil> blades = new ArrayList<>();
        for (int[] block : PENTAGON) {
            SeeleSchneiderPfeil blade = SeeleSchneiderPfeil.plant(helper.getLevel(), owner,
                    helper.absolutePos(new BlockPos(block[0], 1, block[1])));
            helper.assertTrue(blade != null, "no blade could be planted at " + block[0] + ", " + block[1]);
            blades.add(blade);
        }
        return blades;
    }

    /** Every Gintō cast still standing in the test's box. */
    private static void clearCasts(GameTestHelper helper) {
        QuincyArtsTests.discardAll(helper.getLevel().getEntitiesOfClass(GintoCast.class, helper.getBounds()));
    }

    private static boolean unhurt(LivingEntity entity) {
        return entity.isAlive() && entity.getHealth() == entity.getMaxHealth();
    }

    private static boolean hurtOrDead(LivingEntity entity) {
        return !entity.isAlive() || entity.getHealth() < entity.getMaxHealth();
    }

    /** The corners of a pentagon round the origin, at these distances, angles (in degrees) and heights. */
    private static List<Vec3> pentagon(double[] radii, double[] degrees, double[] heights) {
        List<Vec3> corners = new ArrayList<>();
        for (int i = 0; i < radii.length; i++) {
            double angle = Math.toRadians(degrees[i]);
            corners.add(new Vec3(radii[i] * Math.cos(angle), heights[i], radii[i] * Math.sin(angle)));
        }
        return corners;
    }

    /** The same, with every corner at y 0. */
    private static List<Vec3> pentagon(double[] radii, double[] degrees) {
        return pentagon(radii, degrees, new double[radii.length]);
    }

    // ---- Filling and choosing ----

    @GameTest(template = "empty")
    public static void fillingNeedsTierAndReishi(GameTestHelper helper) {
        helper.assertTrue(Objects.equals(QuincyArtsLogic.fillRefusal(QuincyTests.quincy(Bloodline.ECHT, 1, 200.0F)),
                "message.bleach.ginto.fill_tier"), "a first-tier Quincy was let fill a Gintō");
        SoulData data = QuincyTests.quincy(Bloodline.ECHT, 2, 200.0F);
        QuincyLogic.setReishi(data, 5.0F);
        helper.assertTrue(Objects.equals(QuincyArtsLogic.fillRefusal(data), "message.bleach.ginto.fill_reishi"),
                "a Quincy with 5 reishi was let fill a Gintō");

        QuincyLogic.setReishi(data, 20.0F);
        float before = QuincyLogic.reishi(data);
        Player player = ZanpakutoTests.mock(helper, data);
        ItemStack empty = new ItemStack(ModItems.EMPTY_GINTO.get(), 2);
        ItemStack left = empty.getItem().finishUsingItem(empty, helper.getLevel(), player);
        helper.assertTrue(left.is(ModItems.EMPTY_GINTO.get()) && left.getCount() == 1,
                "filling one of two empty tubes left " + left);
        int filled = QuincyArtsTests.carried(player, ModItems.GINTO.get());
        helper.assertTrue(filled == 1, "filling a tube gave " + filled + " filled Gintō");
        QuincyTests.assertNear(helper, QuincyLogic.reishi(data), before - BleachConfig.get(BleachConfig.GINTO_FILL_COST),
                "the reishi left after filling a tube");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spellChoiceCycles(GameTestHelper helper) {
        SoulData data = QuincyArtsTests.thirdTier();
        Player player = ZanpakutoTests.mock(helper, data);
        List<GintoSpell> known = QuincyArtsLogic.knownSpells(data);
        helper.assertTrue(known.equals(List.of(GintoSpell.GRITZ, GintoSpell.WOLKE, GintoSpell.HEIZEN)),
                "an unrestored third tier knows " + known);
        helper.assertTrue(QuincyArtsLogic.selectedSpell(data) == GintoSpell.GRITZ,
                "a fresh soul has " + QuincyArtsLogic.selectedSpell(data) + " chosen");
        for (GintoSpell next : List.of(GintoSpell.WOLKE, GintoSpell.HEIZEN, GintoSpell.GRITZ)) {
            QuincyArtsEvents.cycleSpell(player, data);
            helper.assertTrue(QuincyArtsLogic.selectedSpell(data) == next,
                    "choosing the next spell gave " + QuincyArtsLogic.selectedSpell(data) + ", not " + next);
        }
        choosing(data, GintoSpell.SPRENGER);
        helper.assertTrue(QuincyArtsLogic.selectedSpell(data) == GintoSpell.GRITZ,
                "a spell the soul does not know is cast as " + QuincyArtsLogic.selectedSpell(data));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gritzTicksScale(GameTestHelper helper) {
        int[][] cases = {
                {200, 200, 0, 100},
                {100, 400, 0, 25},
                {400, 100, 0, 100},
                {200, 200, 1, 25}};
        for (int[] c : cases) {
            int ticks = QuincyArtsLogic.gritzTicks(c[0], c[1], c[2] == 1);
            helper.assertTrue(ticks == c[3], "Gritz from reiatsu " + c[0] + " on " + c[1] + (c[2] == 1 ? " (a boss)" : "")
                    + " binds for " + ticks + " ticks, not " + c[3]);
        }
        helper.succeed();
    }

    // ---- The spells ----

    @GameTest(template = "field", batch = "ginto", timeoutTicks = 40)
    public static void gritzBinds(GameTestHelper helper) {
        Pig pig = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 8, 2, 6));
        SoulData data = choosing(QuincyTests.quincy(Bloodline.ECHT, 2, 200.0F), GintoSpell.GRITZ);
        Player player = QuincyTests.archer(helper, data, 8.5, 2.5);
        QuincyArtsTests.face(player, pig.getBoundingBox().getCenter());
        ItemStack stack = tubes(player, 1);
        helper.assertTrue(GintoSpells.cast(player, data, stack), "Gritz was not cast at the pig in view");
        helper.assertTrue(stack.isEmpty(), "Gritz left " + stack.getCount() + " tubes");
        helper.assertTrue(onCooldown(player), "Gritz started no cooldown");
        helper.assertTrue(pig.hasEffect(ModEffects.GRITZ), "Gritz did not bind the pig");
        double speed = pig.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed == 0.0, "the bound pig's speed is " + speed);
        double swim = pig.getAttributeValue(NeoForgeMod.SWIM_SPEED);
        helper.assertTrue(swim == 0.0, "the bound pig's swimming speed is " + swim);
        // A jump that leaves the ground anyway, as one on Jump Boost does, goes nowhere.
        pig.setDeltaMovement(0.2, 0.52, 0.2);
        NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(pig));
        helper.assertTrue(pig.getDeltaMovement().equals(Vec3.ZERO), "the bound pig jumped off at " + pig.getDeltaMovement());
        // Nor does a bound player swim, whose lift towards where they look comes after the effect's tick.
        player.setSwimming(true);
        ModEffects.GRITZ.get().applyEffectTick(player, 0);
        helper.assertFalse(player.isSwimming(), "Gritz left a player swimming");
        player.startFallFlying();
        ModEffects.GRITZ.get().applyEffectTick(player, 0);
        helper.assertFalse(player.isFallFlying(), "Gritz left a player gliding");
        pig.discard();
        clearCasts(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedCastKeepsTubes(GameTestHelper helper) {
        SoulData data = choosing(QuincyTests.quincy(Bloodline.ECHT, 2, 200.0F), GintoSpell.GRITZ);
        Player player = ZanpakutoTests.mock(helper, data);
        // Straight up, where nothing stands to be bound. The look reads last tick's pitch, so both are set.
        player.setXRot(-90.0F);
        player.xRotO = -90.0F;
        ItemStack stack = tubes(player, 3);
        helper.assertFalse(GintoSpells.cast(player, data, stack), "Gritz was cast with nothing in view");
        helper.assertTrue(stack.getCount() == 3, "a failed cast left " + stack.getCount() + " of 3 tubes");
        helper.assertFalse(onCooldown(player), "a failed cast started the cooldown");
        helper.succeed();
    }

    /** Heizen strikes everything standing in its pillar, five blocks across, and nothing outside it. */
    @GameTest(template = "field", batch = "ginto", timeoutTicks = 60)
    public static void heizenHitsItsPillarOnly(GameTestHelper helper) {
        IronGolem target = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, 8, 2, 8));
        IronGolem inside = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(9.5, 2.0, 8.5)));
        IronGolem outside = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(12.5, 2.0, 8.5)));
        SoulData data = choosing(QuincyArtsTests.thirdTier(), GintoSpell.HEIZEN);
        Player player = QuincyTests.archer(helper, data, 2.5, 8.5);
        QuincyArtsTests.face(player, target.getBoundingBox().getCenter());
        ItemStack stack = tubes(player, 4);
        helper.assertTrue(GintoSpells.cast(player, data, stack), "Heizen was not cast at the golem in view");
        helper.assertTrue(stack.isEmpty(), "Heizen left " + stack.getCount() + " of its 4 tubes");
        float expected = GintoCast.heizenDamage(QuincyLogic.fullDamage(data), false);

        helper.assertTrue(GintoCast.heizenDamage(10.0F, true) == 30.0F,
                "Heizen of strength 10 deals a Hollow " + GintoCast.heizenDamage(10.0F, true));
        helper.assertTrue(GintoCast.heizenDamage(50.0F, true) == 60.0F,
                "Heizen of strength 50 deals a Hollow " + GintoCast.heizenDamage(50.0F, true) + ", past its cap");
        // The pillar strikes 10 ticks after it rises.
        helper.runAtTickTime(15, () -> {
            for (IronGolem golem : List.of(target, inside)) {
                float lost = golem.getMaxHealth() - golem.getHealth();
                helper.assertTrue(Math.abs(lost - expected) < HEALTH_EPSILON,
                        "a golem in the pillar lost " + lost + ", not " + expected);
            }
            helper.assertTrue(unhurt(outside), "Heizen struck a golem outside its pillar, leaving " + outside.getHealth());
            QuincyArtsTests.discardAll(List.of(target, inside, outside));
            clearCasts(helper);
            helper.succeed();
        });
    }

    /** A Wolke on the floor takes the fall out of what drops onto it; a drop beside it still hurts. */
    @GameTest(template = "field", batch = "ginto", timeoutTicks = 100)
    public static void wolkeCushionsFall(GameTestHelper helper) {
        SoulData data = choosing(QuincyTests.quincy(Bloodline.ECHT, 2, 200.0F), GintoSpell.WOLKE);
        Player player = QuincyTests.archer(helper, data, 8.5, 8.5);
        helper.assertTrue(GintoSpells.cast(player, data, tubes(player, 1)), "Wolke was not cast");
        // Nine blocks up, as high as the box's roof allows: a fall that hurts a pig.
        Pig cushioned = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(8.5, 11.0, 8.5));
        Pig beside = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(14.5, 11.0, 2.5));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(cushioned.onGround(), "the pig over the Wolke never landed");
            helper.assertTrue(unhurt(cushioned), "the pig that fell onto the Wolke was hurt, leaving " + cushioned.getHealth());
            helper.assertTrue(hurtOrDead(beside), "the pig that fell beside the Wolke was not hurt");
            QuincyArtsTests.discardAll(List.of(cushioned, beside));
            clearCasts(helper);
            helper.succeed();
        });
    }

    /** The reishi went into the tube when it was filled, so a sealed Quincy still casts, though it fills none. */
    @GameTest(template = "field", batch = "ginto", timeoutTicks = 40)
    public static void gintoWorksWhileSealed(GameTestHelper helper) {
        SoulData data = choosing(QuincyArtsTests.sealed(QuincyArtsTests.thirdTier(), 0L), GintoSpell.WOLKE);
        Player player = QuincyTests.archer(helper, data, 8.5, 8.5);
        helper.assertTrue(GintoSpells.cast(player, data, tubes(player, 1)), "a sealed Quincy could not cast Wolke");
        helper.assertTrue(QuincyArtsLogic.fillRefusal(data) != null, "a sealed Quincy may fill a Gintō");
        clearCasts(helper);
        helper.succeed();
    }

    // ---- Sprenger ----

    @GameTest(template = "empty")
    public static void sprengerShapeAcceptsAndRejects(GameTestHelper helper) {
        double[] five = {90.0, 162.0, 234.0, 306.0, 378.0};
        SprengerShape regular = SprengerShape.of(pentagon(new double[] {3.0, 3.0, 3.0, 3.0, 3.0}, five));
        helper.assertTrue(regular != null, "a regular pentagon 3 blocks out is not a formation");
        helper.assertTrue(regular.contains(Vec3.ZERO), "the middle of a regular pentagon is not inside it");
        for (Vec3 away : List.of(new Vec3(4.0, 0.0, 0.0), new Vec3(0.0, 0.0, 4.0), new Vec3(0.0, 0.0, -4.0))) {
            helper.assertFalse(regular.contains(away), away + ", 4 blocks out, is inside a pentagon 3 blocks out");
        }

        List<Vec3> regularCorners = pentagon(new double[] {3.0, 3.0, 3.0, 3.0, 3.0}, five);
        List<Vec3> dented = List.of(new Vec3(-2.5, 0.0, -3.5), new Vec3(2.0, 0.0, -3.0), new Vec3(0.0, 0.0, 3.5),
                new Vec3(3.0, 0.0, 4.0), new Vec3(-4.0, 0.0, 3.0));
        Map<String, List<Vec3>> bad = new LinkedHashMap<>();
        bad.put("one corner pulled in to 0.8", pentagon(new double[] {3.0, 3.0, 3.0, 3.0, 0.8}, five));
        // Within every limit of distance and angle, so only the convexity rule can turn it down.
        bad.put("one corner dented in", dented);
        bad.put("four corners", regularCorners.subList(0, 4));
        bad.put("corners 12 blocks out", pentagon(new double[] {12.0, 12.0, 12.0, 12.0, 12.0}, five));
        bad.put("corners 2 and 5 blocks out", pentagon(new double[] {2.0, 5.0, 2.0, 5.0, 5.0}, five));
        bad.put("a corner 4 blocks higher", pentagon(new double[] {3.0, 3.0, 3.0, 3.0, 3.0}, five,
                new double[] {0.0, 0.0, 0.0, 0.0, 4.0}));
        bad.put("gaps of 20 and 124 degrees", pentagon(new double[] {3.0, 3.0, 3.0, 3.0, 3.0},
                new double[] {0.0, 20.0, 144.0, 216.0, 288.0}));
        bad.forEach((what, corners) -> helper.assertTrue(SprengerShape.of(corners) == null,
                "a pentagon with " + what + " was taken as a formation"));
        helper.succeed();
    }

    /** The fifth blade to stand in the pentagon binds what is inside with Gritz, and the five make a formation. */
    @GameTest(template = "field", batch = "ginto", timeoutTicks = 80)
    public static void plantedBladesBindAndArm(GameTestHelper helper) {
        Pig pig = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 8, 2, 8));
        Player player = QuincyTests.archer(helper, QuincyArtsTests.restored(QuincyArtsTests.thirdTier()), 8.5, 2.5);
        List<SeeleSchneiderPfeil> blades = plantPentagon(helper, player);
        helper.assertTrue(pig.hasEffect(ModEffects.GRITZ), "the fifth blade did not bind the pig inside the pentagon");
        int found = GintoSpells.formation(helper.getLevel(), player.getUUID(), pig.position()).size();
        helper.assertTrue(found == 5, "the planted pentagon is a formation of " + found + " blades, not 5");

        // A corner lifted and planted again straight away does not bind once more.
        pig.removeEffect(ModEffects.GRITZ);
        blades.get(0).discard();
        SeeleSchneiderPfeil again = SeeleSchneiderPfeil.plant(helper.getLevel(), player,
                helper.absolutePos(new BlockPos(PENTAGON[0][0], 1, PENTAGON[0][1])));
        helper.assertTrue(again != null, "the lifted corner could not be planted again");
        helper.assertFalse(pig.hasEffect(ModEffects.GRITZ), "planting a corner again bound the pig once more");
        blades.set(0, again);
        QuincyArtsTests.discardAll(blades);
        pig.discard();
        helper.succeed();
    }

    /** A fired blade that completes the pentagon binds what is inside, judged where it came to rest. */
    @GameTest(template = "field", batch = "ginto", timeoutTicks = 60)
    public static void firedFifthBladeBinds(GameTestHelper helper) {
        Pig pig = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 8, 2, 8));
        Player player = QuincyTests.archer(helper, QuincyArtsTests.restored(QuincyArtsTests.thirdTier()), 8.5, 2.5);
        List<SeeleSchneiderPfeil> blades = new ArrayList<>();
        for (int i = 0; i < PENTAGON.length - 1; i++) {
            blades.add(Objects.requireNonNull(SeeleSchneiderPfeil.plant(helper.getLevel(), player,
                    helper.absolutePos(new BlockPos(PENTAGON[i][0], 1, PENTAGON[i][1]))), "a corner could not be planted"));
        }
        helper.assertFalse(pig.hasEffect(ModEffects.GRITZ), "four blades bound the pig");
        // From above the middle, at a full draw's speed, so it sticks on its first tick.
        int[] last = PENTAGON[PENTAGON.length - 1];
        Vec3 from = helper.absoluteVec(new Vec3(8.5, 4.0, 8.3));
        Vec3 to = helper.absoluteVec(new Vec3(last[0] + 0.5, 2.0, last[1] + 0.5));
        blades.add(SeeleSchneiderPfeil.launch(helper.getLevel(), player, from, to.subtract(from).normalize(), 1.0F, 0, 3.8F));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(blades.get(blades.size() - 1).planted(), "the fired fifth blade did not stick");
            helper.assertTrue(pig.hasEffect(ModEffects.GRITZ), "the fired fifth blade did not bind the pig inside the pentagon");
            QuincyArtsTests.discardAll(blades);
            pig.discard();
            helper.succeed();
        });
    }

    /** Sprenger blasts what is inside the pentagon, uses its blades up, and leaves the floor whole. */
    @GameTest(template = "field", batch = "ginto", timeoutTicks = 80)
    public static void sprengerBlastsInsideOnly(GameTestHelper helper) {
        Pig inside = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 8, 2, 8));
        Pig outside = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 8, 2, 14));
        SoulData data = choosing(QuincyArtsTests.restored(QuincyArtsTests.thirdTier()), GintoSpell.SPRENGER);
        Player player = QuincyTests.archer(helper, data, 8.5, 2.5);
        List<SeeleSchneiderPfeil> blades = plantPentagon(helper, player);
        ItemStack stack = tubes(player, 1);
        helper.assertTrue(GintoSpells.cast(player, data, stack), "Sprenger was not cast with five blades standing");
        helper.assertTrue(stack.isEmpty(), "Sprenger left " + stack.getCount() + " tubes");
        // Held until the blast: none can be taken back, and none counts for another Sprenger.
        helper.assertTrue(blades.stream().allMatch(SeeleSchneiderPfeil::armed), "Sprenger did not hold its blades");
        helper.assertTrue(GintoSpells.formation(helper.getLevel(), player.getUUID(), inside.position()).isEmpty(),
                "the blades a Sprenger holds still make a formation");
        // The blast comes 10 ticks after the cast.
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(hurtOrDead(inside), "Sprenger did not strike the pig inside the pentagon");
            helper.assertTrue(unhurt(outside), "Sprenger struck the pig outside the pentagon, leaving " + outside.getHealth());
            for (SeeleSchneiderPfeil blade : blades) {
                helper.assertTrue(blade.isRemoved(), "a blade of the formation outlasted the blast");
            }
            for (int[] block : PENTAGON) {
                helper.assertBlockPresent(Blocks.STONE, block[0], 1, block[1]);
            }
            QuincyArtsTests.discardAll(List.of(inside, outside));
            QuincyArtsTests.clearItems(helper);
            QuincyArtsTests.discardAll(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()));
            clearCasts(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sprengerNeedsFormation(GameTestHelper helper) {
        SoulData data = choosing(QuincyArtsTests.restored(QuincyArtsTests.thirdTier()), GintoSpell.SPRENGER);
        Player player = ZanpakutoTests.mock(helper, data);
        ItemStack stack = tubes(player, 2);
        helper.assertFalse(GintoSpells.cast(player, data, stack), "Sprenger was cast with no blades standing");
        helper.assertTrue(stack.getCount() == 2, "a failed Sprenger left " + stack.getCount() + " of 2 tubes");
        helper.assertFalse(onCooldown(player), "a failed Sprenger started the cooldown");
        helper.succeed();
    }
}
