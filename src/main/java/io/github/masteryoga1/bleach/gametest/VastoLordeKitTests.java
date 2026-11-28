package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.VastoLorde;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.DevourEvents;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.MenosEvolution;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for a Vasto Lorde's kit and its fall: its Cero gathered at the brow, its rush along the ground, its presence, its
 * hide and its fast healing, what devouring one counts for, and whom bringing one down earns the advancement. Each test
 * asks only about its own bodies and players, by object, and discards the bodies, pass or fail. A body with its wits about
 * it gathers, rushes and weighs on others by itself, and its presence reaches past the box, so each test with one has a
 * batch of its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class VastoLordeKitTests {
    private static final float EAST = -90.0F;
    private static final double EPSILON = 0.01;
    /** How long a Vasto Lorde's Cero gathers, as a Vasto Lorde player's does: three quarters of a second. */
    private static final int GATHER_TICKS = 15;
    /** Its wait after its first step and after a broken gather, and how long a test sees no gather in either. */
    private static final int CERO_WAIT_TICKS = 40;
    private static final int QUIET_TICKS = 30;
    private static final int GIVE_UP_TICK = 190;
    /** Its wait after its first step before a first rush, and how many steps past it a hunter without sight is watched. */
    private static final int DASH_WAIT_TICKS = 10;
    private static final int UNSEEN_TICKS = 15;
    /** The reiatsu of the soul a body hunts: enough to see spirits, and far too little to frighten it. */
    private static final float AWARE = 40.0F;
    /** A fifth of a Vasto Lorde's reiatsu: a Hollow its presence weighs on at weight 2 and frightens, but never withers. */
    private static final float STRONG = 500.0F;
    /** Under an eighth of it: a Menos its presence weighs on hardest, as on a small Hollow, so only its kind spares it. */
    private static final float WEAK_MENOS = 200.0F;
    /** Two fifths of it: a soul whose blows the reiatsu difference leaves whole, so its hide holds against them in full. */
    private static final float UNCUT = 1_000.0F;
    /** A soul so much weaker that the reiatsu difference cuts its blows by more than the hide would. */
    private static final float WEAK = 300.0F;
    /** A blow the armour turns more of than the hide would, and one the hide turns more of. */
    private static final float LIGHT_BLOW = 6.0F;
    private static final float HEAVY_BLOW = 30.0F;
    /** A Hollow player's reiatsu: more than a third of a Vasto Lorde's, as a devourer of one needs. */
    private static final float DEVOURER = 900.0F;
    private static final float FED = 60.0F;
    private static final float FATAL = 1_000.0F;
    /** The healing's and the fall's tests give up here, within their timeouts of 120 and 60. */
    private static final int HEAL_GIVE_UP_TICK = 110;
    private static final int FALL_GIVE_UP_TICK = 50;

    private VastoLordeKitTests() {
    }

    // ---- Helpers ----

    /** Stone 3 by 3 at this x, across the z 8 line, so a Cero fired east along it ends in the box with its burst. */
    private static void backstop(GameTestHelper helper, int x) {
        for (int y = 2; y <= 4; y++) {
            for (int z = 7; z <= 9; z++) {
                helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
    }

    /** Discards these creatures and every Cero left in the test's box. */
    private static void discard(GameTestHelper helper, Entity... entities) {
        for (Entity entity : entities) {
            entity.discard();
        }
        helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds()).forEach(Entity::discard);
    }

    /** A Vasto Lorde here with its wits about it, so it steps and may rush, but with no goal to move it or pick its prey. */
    private static VastoLorde awake(GameTestHelper helper, int x, int y, int z) {
        VastoLorde lorde = helper.spawnWithNoFreeWill(ModEntities.VASTO_LORDE.get(), x, y, z);
        lorde.targetSelector.removeAllGoals(goal -> true);
        return lorde;
    }

    /** A mock Shinigami who sees spirits, standing at this spot as a hunter's prey; never in the world, so never left. */
    private static LivingEntity prey(GameTestHelper helper, double x, double z) {
        return HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, AWARE, new Vec3(x, 2.0, z));
    }

    /** A mock Shinigami of this reiatsu behind a body at (8, 8) facing east, looking at its feet, so no blow finds a mask. */
    private static Player striker(GameTestHelper helper, float reiatsu) {
        Player striker = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, reiatsu,
                new Vec3(6.5, 2.0, 8.5));
        striker.setXRot(90.0F);
        return striker;
    }

    /** What a blow of this much lands as on a body made whole and open to blows again. */
    private static float landed(VastoLorde lorde, DamageSource source, float dealt) {
        lorde.setHealth(lorde.getMaxHealth());
        lorde.invulnerableTime = 0;
        lorde.hurt(source, dealt);
        return lorde.getMaxHealth() - lorde.getHealth();
    }

    /** What its armour alone leaves of a blow of this much. */
    private static float armoured(VastoLorde lorde, DamageSource source, float dealt) {
        return CombatRules.getDamageAfterAbsorb(lorde, dealt, source, lorde.getArmorValue(),
                (float) lorde.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    }

    /** A mock Hollow player of this form here, fed, its road begun at that form. */
    private static Player devourer(GameTestHelper helper, HollowTier form, Vec3 at) {
        Player player = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, DEVOURER, at);
        SoulData data = SoulLogic.get(player);
        data.setTier(PowerPath.HOLLOW, form.tier());
        data.setHollow(data.hollow().withEvolution(data.hollow().evolution().startedAt(form.tier())));
        HollowBody.setSatiety(data, FED);
        return player;
    }

    // ---- Its Cero ----

    /**
     * Its Cero: a Vasto Lorde has the goal, and gathers at its brow only at prey it may strike at least four blocks off
     * across the ground and in sight. The Cero is a Vasto Lorde player's with the mob's own damage and an aim that holds
     * for the last quarter second; it is still gathering at 12 ticks and has struck its prey by 18, and then it waits out
     * its cooldown before another, at other prey just as fit. Still (NoAI) once it is seen to have the goal, so no goal
     * starts a Cero the test does not. Stone behind its prey ends the beam, and its burst, in the box.
     */
    @GameTest(template = "field", timeoutTicks = 60)
    public static void itsCeroGathersAtTheBrowAndStrikes(GameTestHelper helper) {
        backstop(helper, 13);
        VastoLorde lorde = helper.spawn(ModEntities.VASTO_LORDE.get(), new Vec3(3.5, 2.0, 8.5));
        Husk prey = MenosTests.husk(helper, 11, 8);
        Husk near = MenosTests.husk(helper, 5, 5);
        Runnable clear = () -> discard(helper, lorde, prey, near);
        HollowBaitTests.orClear(() -> {
            helper.assertTrue(lorde.goalSelector.getAvailableGoals().stream()
                    .anyMatch(goal -> goal.getGoal() instanceof VastoLorde.CeroGoal), "a Vasto Lorde has no Cero goal");
            QuincyTests.still(lorde);
            MenosTests.face(lorde, EAST);
            // Its brow: a little above the middle of its mask, and just before it.
            Vec3 brow = helper.relativeVec(lorde.ceroMuzzle(1.0F));
            helper.assertTrue(brow.distanceTo(new Vec3(4.3, 3.9, 8.5)) < EPSILON, "a Vasto Lorde's Cero gathers at " + brow);
            // Each husk is set as the prey and as what last hurt it, so the Vasto Lorde may strike it.
            lorde.setTarget(near);
            lorde.setLastHurtByMob(near);
            helper.assertTrue(lorde.startCero() == null, "a Vasto Lorde gathered a Cero at prey 3.6 blocks off");
            lorde.setTarget(prey);
            lorde.setLastHurtByMob(prey);
            // Stone between their eyes.
            helper.setBlock(7, 3, 8, Blocks.STONE);
            helper.assertTrue(lorde.startCero() == null, "a Vasto Lorde gathered a Cero at prey behind stone");
            helper.setBlock(7, 3, 8, Blocks.AIR);
            CeroBeam gathering = lorde.startCero();
            helper.assertTrue(gathering != null && gathering.isCharging() && lorde.chargingBeam() == gathering,
                    "a Vasto Lorde gathered no Cero at prey 8 blocks off in sight");
            CeroBeam.Spec spec = lorde.ceroSpec();
            float damage = (float) (BleachConfig.get(BleachConfig.VASTO_LORDE_CERO_DAMAGE)
                    * BleachConfig.get(BleachConfig.HOLLOW_DAMAGE_MULTIPLIER));
            helper.assertTrue(Math.abs(spec.damage() - damage) < EPSILON && spec.chargeTicks() == GATHER_TICKS
                            && spec.aimLockTicks() == VastoLorde.CERO_AIM_LOCK_TICKS
                            && spec.range() == BleachConfig.get(BleachConfig.CERO_RANGE)
                            && spec.colour() == CeroBeam.MENOS_RED
                            && spec.breaksBlocks() == BleachConfig.get(BleachConfig.CERO_BREAKS_BLOCKS),
                    "a Vasto Lorde's Cero is " + spec);
        }, clear).run();
        CeroBeam beam = lorde.chargingBeam();
        helper.startSequence()
                .thenExecuteAfter(12, HollowBaitTests.orClear(() -> helper.assertTrue(beam.isCharging() && !beam.isFired(),
                        "the Cero was no longer gathering after 12 ticks"), clear))
                .thenExecuteAfter(6, HollowBaitTests.orClear(() -> {
                    CeroBeam.Shot shot = beam.lastShot();
                    helper.assertTrue(beam.isFired() && shot != null && shot.struck().contains(prey),
                            "after 18 ticks the Cero struck " + (shot != null ? shot.struck() : "nothing"));
                    Vec3 end = helper.relativeVec(shot.to());
                    helper.assertTrue(shot.hitBlock() && end.x < 14.0, "the Cero ended at " + end + ", not the stone");
                    helper.assertTrue(!prey.isAlive() || prey.getHealth() < prey.getMaxHealth(),
                            "the Cero left its prey whole");
                    helper.assertTrue(lorde.chargingBeam() == null, "a Vasto Lorde still holds its fired Cero");
                    helper.moveTo(near, 11.5F, 2.0F, 4.5F);
                    lorde.setLastHurtByMob(near);
                    helper.assertFalse(lorde.mayCero(near), "a Vasto Lorde may gather another Cero at once");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    /**
     * Its Cero's goal, the only goal it keeps, first of them, uninterruptable, standing and facing. With its wits about it
     * and prey six blocks off in sight (too near to rush), it gathers nothing in the two seconds after its first step,
     * though its goals ask before that step starts the wait; then it gathers. A fresh crack in its mask breaks the
     * gather, and it waits as long again; the next Cero strikes the prey. A batch of its own, as it acts by itself.
     */
    @GameTest(template = "field", batch = "vasto_lorde_cero", timeoutTicks = 200)
    public static void itsCeroGoalGathersWaitsAndBreaksOnACrack(GameTestHelper helper) {
        backstop(helper, 11);
        VastoLorde lorde = helper.spawn(ModEntities.VASTO_LORDE.get(), new Vec3(3.5, 2.0, 8.5));
        Husk prey = MenosTests.husk(helper, 9, 8);
        Runnable clear = () -> discard(helper, lorde, prey);
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("a Vasto Lorde with only its Cero's goal never took its first step, gathered, or fired again");
        });
        long[] firstStep = new long[1];
        CeroBeam[] gathering = new CeroBeam[1];
        HollowBaitTests.orClear(() -> {
            List<WrappedGoal> cero = lorde.goalSelector.getAvailableGoals().stream()
                    .filter(goal -> goal.getGoal() instanceof VastoLorde.CeroGoal).toList();
            helper.assertTrue(cero.size() == 1 && cero.get(0).getPriority() == 1 && !cero.get(0).isInterruptable()
                            && cero.get(0).getFlags().equals(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)),
                    "a Vasto Lorde's Cero goal is not one, first, uninterruptable, and moving and looking");
            lorde.removeAllGoals(goal -> !(goal instanceof VastoLorde.CeroGoal));
            lorde.targetSelector.removeAllGoals(goal -> true);
            lorde.setTarget(prey);
            lorde.setLastHurtByMob(prey);
        }, clear).run();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    GateTests.patiently(helper, lorde.tickCount > 0, "the Vasto Lorde has not taken its first step");
                    firstStep[0] = helper.getLevel().getGameTime();
                })
                .thenExecuteFor(QUIET_TICKS, HollowBaitTests.orClear(() -> helper.assertTrue(lorde.chargingBeam() == null,
                        "a Vasto Lorde gathered a Cero in the wait after its first step"), clear))
                .thenWaitUntil(() -> {
                    GateTests.patiently(helper, lorde.chargingBeam() != null, "the Vasto Lorde has not gathered a Cero");
                    gathering[0] = lorde.chargingBeam();
                })
                // A tick into the gather, which waits out the 40 ticks after the first step (a tick of room given).
                .thenIdle(1)
                .thenExecute(HollowBaitTests.orClear(() -> {
                    long now = helper.getLevel().getGameTime();
                    helper.assertTrue(now - firstStep[0] >= CERO_WAIT_TICKS && gathering[0].isCharging(),
                            "a Vasto Lorde gathered " + (now - firstStep[0] - 1) + " ticks after its first step, or stopped");
                    lorde.onMaskStruck(now);
                    helper.assertTrue(lorde.chargingBeam() == null && gathering[0].isRemoved() && !gathering[0].isFired(),
                            "a fresh crack in a Vasto Lorde's mask did not break its Cero");
                    // What last hurt it is forgotten 100 steps on: set again, so only the broken gather's wait holds it.
                    lorde.setLastHurtByMob(prey);
                }, clear))
                .thenExecuteFor(QUIET_TICKS, HollowBaitTests.orClear(() -> helper.assertTrue(lorde.chargingBeam() == null,
                        "a Vasto Lorde gathered a Cero in the wait after its broken one"), clear))
                .thenWaitUntil(() -> {
                    GateTests.patiently(helper, lorde.chargingBeam() != null, "the Vasto Lorde has not gathered again");
                    gathering[0] = lorde.chargingBeam();
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, !gathering[0].isCharging(),
                        "the Vasto Lorde's second Cero is still gathering"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    CeroBeam.Shot shot = gathering[0].lastShot();
                    helper.assertTrue(gathering[0].isFired() && shot != null && shot.struck().contains(prey)
                                    && shot.hitBlock() && (!prey.isAlive() || prey.getHealth() < prey.getMaxHealth()),
                            "the second Cero struck " + (shot != null ? shot.struck() : "nothing") + ", or left it whole");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- Its rush ----

    /**
     * Its rush along the ground: a body on stone two blocks high will not rush off its edge and stays put; one rushing at
     * a wall two blocks high stops short of it, clear of it, and must then wait out its cooldown; one on open ground goes
     * the whole way, up a one-block step and down again. A body with its wits about it rushes by itself, once its first
     * wait is over, at prey 10 blocks off, to stand two blocks short of it; one with a stone column between it and its prey
     * as far off stays where it stood. A batch of its own, as its hunters act by themselves.
     */
    @GameTest(template = "field", batch = "vasto_lorde_dash", timeoutTicks = 200)
    public static void itRushesButNeverIntoAWallOrOffALedge(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            for (int x = 1; x <= 4 && y <= 3; x++) {
                helper.setBlock(x, y, 2, Blocks.STONE);
            }
            helper.setBlock(6, y, 10, Blocks.STONE);
        }
        helper.setBlock(6, 2, 7, Blocks.STONE);
        helper.setBlock(6, 3, 7, Blocks.STONE);
        // The open row's step: the rush's box, 0.7 wide, lifts a block onto it at its ninth quarter-block step (its front
        // at x 5.1) and settles back to the floor at its sixteenth (its back at x 6.15), so a grounded sweep lands at
        // x 8.5, where one that never lifts stops at x 4.5.
        helper.setBlock(5, 2, 12, Blocks.STONE);
        VastoLorde ledge = awake(helper, 2, 4, 2);
        VastoLorde walled = awake(helper, 2, 2, 7);
        VastoLorde open = awake(helper, 2, 2, 12);
        VastoLorde hunter = awake(helper, 2, 2, 14);
        VastoLorde unseeing = awake(helper, 2, 2, 10);
        hunter.setTarget(prey(helper, 12.5, 14.5));
        unseeing.setTarget(prey(helper, 12.5, 10.5));
        Vec3 stood = unseeing.position();
        Runnable clear = () -> discard(helper, ledge, walled, open, hunter, unseeing);
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("a hunting Vasto Lorde never stepped or rushed, or the one behind stone never stepped on");
        });
        Vec3 east = new Vec3(1.0, 0.0, 0.0);
        HollowBaitTests.orClear(() -> {
            Vec3 before = ledge.position();
            helper.assertFalse(ledge.dash(east, 6.0), "a Vasto Lorde rushed off a ledge");
            helper.assertTrue(ledge.position().equals(before), "a refused rush moved a Vasto Lorde to " + ledge.position());
            helper.assertTrue(walled.dash(east, 8.0), "a Vasto Lorde would not rush towards a wall");
            Vec3 stop = helper.relativeVec(walled.position());
            helper.assertTrue(stop.x + walled.getBbWidth() / 2.0 <= 6.0 + 1.0E-6 && stop.x > 3.5
                    && helper.getLevel().noCollision(walled), "a Vasto Lorde rushing at a wall stopped at " + stop);
            helper.assertFalse(walled.dash(east.reverse(), 3.0), "a Vasto Lorde rushed again without waiting");
            helper.assertTrue(open.dash(east, 6.0), "a Vasto Lorde would not rush across open ground");
            Vec3 landed = helper.relativeVec(open.position());
            helper.assertTrue(landed.distanceTo(new Vec3(8.5, 2.0, 12.5)) < EPSILON,
                    "a Vasto Lorde rushing 6 blocks across open ground and over a step landed at " + landed);
        }, clear).run();
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, hunter.tickCount > 0 && unseeing.tickCount > 0,
                        "the hunting Vasto Lorde have not taken their first steps"))
                .thenWaitUntil(() -> GateTests.patiently(helper,
                        Math.abs(helper.relativeVec(hunter.position()).x - 10.5) < EPSILON,
                        "the hunting Vasto Lorde is at " + helper.relativeVec(hunter.position())))
                .thenWaitUntil(() -> GateTests.patiently(helper, unseeing.tickCount > DASH_WAIT_TICKS + UNSEEN_TICKS,
                        "the Vasto Lorde behind stone has not stepped on"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    helper.assertTrue(unseeing.position().distanceTo(stood) < EPSILON,
                            "a Vasto Lorde rushed at prey behind stone, to " + helper.relativeVec(unseeing.position()));
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- Its presence ----

    /**
     * Its presence, beaten by hand from a still body: within 12 blocks it weighs on every creature it dwarfs, frightening
     * a strong Hollow of a fifth its reiatsu, and withers by presence_harm only the small Hollow and the Plus it dwarfs
     * most, sparing the strong Hollow, a named one, an Adjuchas it dwarfs as much and the player; one 12.7 blocks off it
     * neither weighs on nor frightens. With presence_radius 0 it reaches no one, with presence_harm 0 it only weighs, and
     * in Peaceful it withers no one. A still body never beats by itself: one with its wits about it beside the far Hollow
     * withers that one a second after it comes, while the near Hollow stays whole. A batch of its own, as that body's
     * presence reaches past the box.
     */
    @GameTest(template = "field", batch = "vasto_lorde_presence", timeoutTicks = 200)
    public static void itsPresenceWithersOnlyTheSmallAndWeak(GameTestHelper helper) {
        ServerPlayer player = ShriekerTests.aware(helper, new Vec3(4.5, 2.0, 1.5));
        VastoLorde lorde = HollowPlayerTests.placed(helper, ModEntities.VASTO_LORDE.get(), 2, 2);
        SpiderHollow small = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 2, 6);
        SpiderHollow strong = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 6, 2);
        SpiderHollow named = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 5, 5);
        Adjuchas adjuchas = HollowPlayerTests.placed(helper, ModEntities.ADJUCHAS.get(), 7, 8);
        PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 6, 6);
        SpiderHollow far = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 11, 11);
        List<Entity> bodies = new ArrayList<>(List.of(lorde, small, strong, named, adjuchas, plus, far));
        Runnable clear = () -> QuincyArtsTests.discardAll(bodies);
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("the Vasto Lorde with its wits about it never stepped, or never withered the far Hollow");
        });
        strong.soul().setMaxReiryoku(STRONG);
        adjuchas.soul().setMaxReiryoku(WEAK_MENOS);
        named.setCustomName(Component.literal("Named"));
        float harm = (float) BleachConfig.get(BleachConfig.VASTO_LORDE_PRESENCE_HARM);
        VastoLorde[] awake = new VastoLorde[1];
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(HollowBaitTests.orClear(() -> {
                    int withered = lorde.pulsePresence();
                    helper.assertTrue(withered == 2,
                            "a presence withered " + withered + ", not the small Hollow and the Plus");
                    QuincyTests.assertNear(helper, small.getHealth(), small.getMaxHealth() - harm,
                            "the small Hollow's health");
                    helper.assertTrue(plus.getHealth() < plus.getMaxHealth(), "a presence left the Plus whole");
                    for (LivingEntity spared : List.<LivingEntity>of(strong, named, adjuchas, far)) {
                        helper.assertTrue(spared.getHealth() == spared.getMaxHealth(), "a presence withered " + spared);
                    }
                    helper.assertTrue(named.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                            "a presence did not weigh on a named Hollow");
                    helper.assertTrue(adjuchas.hasEffect(MobEffects.WEAKNESS),
                            "a presence did not weigh hardest on a weak Adjuchas");
                    MobEffectInstance weighed = strong.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    helper.assertTrue(weighed != null && weighed.getAmplifier() == 1,
                            "a presence weighed on a strong Hollow with " + weighed);
                    long now = helper.getLevel().getGameTime();
                    helper.assertTrue(strong.getData(SoulAttachments.FRIGHT).until() > now,
                            "a presence did not frighten a strong Hollow it weighs on");
                    helper.assertFalse(far.hasData(SoulAttachments.FRIGHT), "a presence frightened a Hollow 12.7 blocks off");
                    MobEffectInstance slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    helper.assertTrue(slow != null && slow.getAmplifier() == 2 && player.hasEffect(MobEffects.WEAKNESS)
                            && player.getHealth() == player.getMaxHealth(), "a presence weighed on a player with " + slow
                            + ", leaving them at " + player.getHealth());
                    helper.assertFalse(far.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a presence reached 12.7 blocks");
                    // Every beat from here withers no one: the small Hollow whole and unweighed, and the last blows'
                    // invulnerability let go, so a wither would land.
                    small.setHealth(small.getMaxHealth());
                    small.removeAllEffects();
                    small.invulnerableTime = 0;
                    plus.invulnerableTime = 0;
                    KidoTests.withConfig(BleachConfig.VASTO_LORDE_PRESENCE_RADIUS, 0.0, () -> helper.assertTrue(
                            lorde.pulsePresence() == 0 && small.getHealth() == small.getMaxHealth()
                                    && !small.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                            "with presence_radius 0 a presence still reached the small Hollow"));
                    KidoTests.withConfig(BleachConfig.VASTO_LORDE_PRESENCE_HARM, 0.0, () -> helper.assertTrue(
                            lorde.pulsePresence() == 0 && small.getHealth() == small.getMaxHealth()
                                    && small.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                            "with presence_harm 0 a presence withered the small Hollow, or did not weigh on it"));
                    // Peaceful within this one call, so no tick sees it.
                    MinecraftServer server = helper.getLevel().getServer();
                    Difficulty difficulty = server.getWorldData().getDifficulty();
                    server.setDifficulty(Difficulty.PEACEFUL, true);
                    int peaceful;
                    try {
                        peaceful = lorde.pulsePresence();
                    } finally {
                        server.setDifficulty(difficulty, true);
                    }
                    helper.assertTrue(peaceful == 0 && small.getHealth() == small.getMaxHealth(),
                            "in Peaceful a presence withered " + peaceful);
                    QuincyArtsTests.discardAll(List.<Entity>of(strong, named, adjuchas, plus));
                    awake[0] = awake(helper, 13, 2, 13);
                    bodies.add(awake[0]);
                }, clear))
                .thenWaitUntil(() -> GateTests.patiently(helper, awake[0].tickCount > 0,
                        "the Vasto Lorde with its wits about it has not taken its first step"))
                .thenWaitUntil(() -> GateTests.patiently(helper, far.getHealth() < far.getMaxHealth(),
                        "the presence of a Vasto Lorde with its wits about it has not withered the Hollow beside it"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    helper.assertTrue(small.getHealth() == small.getMaxHealth(),
                            "a still Vasto Lorde's presence beat by itself");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- Its hide ----

    /**
     * Its hide, as an Arrancar's Hierro: at the default pierce_ratio of 2 it turns hide_share of a blow from a soul of two
     * fifths or all of its reiatsu, half that from one of one and a half times it and none from one of twice it, and at
     * a pierce_ratio of 3 half from one of twice it; none of a blow armour lets through, of one on another, or of its own.
     * From the soul of two fifths a real blow lands as its armour or its hide leaves it, whichever takes more, never both:
     * 6 as the armour leaves it, 30 as the hide does, and 30 as half with hide_share 0.5. From a soul of 300 the reiatsu
     * difference cuts more than the hide would, so its blows land as the cut and the armour leave them, the hide never on
     * top. Still, and struck from behind by souls looking at its feet, so no blow finds its mask.
     */
    @GameTest(template = "field")
    public static void itsHideWeighsAgainstItsArmour(GameTestHelper helper) {
        List<Entity> bodies = new ArrayList<>();
        try {
            VastoLorde lorde = HollowPlayerTests.placed(helper, ModEntities.VASTO_LORDE.get(), 8, 8);
            bodies.add(lorde);
            Husk husk = MenosTests.husk(helper, 12, 12);
            bodies.add(husk);
            MenosTests.face(lorde, EAST);
            lorde.soul();
            float vic = SoulLogic.reiatsu(lorde);
            DamageSources damage = helper.getLevel().damageSources();
            Player striker = striker(helper, UNCUT);
            DamageSource blow = damage.playerAttack(striker);
            float kept = 1.0F - (float) BleachConfig.get(BleachConfig.VASTO_LORDE_HIDE_SHARE);
            QuincyTests.assertNear(helper, VastoLorde.hide(lorde, blow, vic * 0.4F, vic), kept,
                    "its hide against a weaker soul");
            QuincyTests.assertNear(helper, VastoLorde.hide(lorde, blow, vic, vic), kept, "its hide against an equal");
            QuincyTests.assertNear(helper, VastoLorde.hide(lorde, blow, vic * 1.5F, vic), (1.0F + kept) / 2.0F,
                    "its hide against a soul of one and a half times its reiatsu");
            QuincyTests.assertNear(helper, VastoLorde.hide(lorde, blow, vic * 2.0F, vic), 1.0, "its hide against twice it");
            KidoTests.withConfig(BleachConfig.HIERRO_PIERCE_RATIO, 3.0, () -> QuincyTests.assertNear(helper,
                    VastoLorde.hide(lorde, blow, vic * 2.0F, vic), (1.0F + kept) / 2.0F,
                    "its hide against twice it at a pierce_ratio of 3"));
            QuincyTests.assertNear(helper, VastoLorde.hide(husk, blow, vic * 0.4F, vic), 1.0, "a husk's hide");
            QuincyTests.assertNear(helper, VastoLorde.hide(lorde, damage.indirectMagic(striker, striker), vic * 0.4F, vic),
                    1.0, "its hide against magic");
            QuincyTests.assertNear(helper, VastoLorde.hide(lorde, damage.mobAttack(lorde), vic * 0.4F, vic), 1.0,
                    "its hide against its own blow");
            // No reiatsu cut from the soul of two fifths: the armour takes more of the light blow, the hide of the heavy.
            float light = armoured(lorde, blow, LIGHT_BLOW);
            float heavy = armoured(lorde, blow, HEAVY_BLOW);
            helper.assertTrue(SoulLogic.reiatsuDamageMultiplier(UNCUT, vic) == 1.0F && light < LIGHT_BLOW * kept
                    && heavy > HEAVY_BLOW * kept, "the armour leaves " + light + " and " + heavy + " of blows of 6 and 30");
            QuincyTests.assertNear(helper, landed(lorde, blow, LIGHT_BLOW), light, "a blow of 6 on a Vasto Lorde");
            QuincyTests.assertNear(helper, landed(lorde, blow, HEAVY_BLOW), HEAVY_BLOW * kept, "a blow of 30");
            KidoTests.withConfig(BleachConfig.VASTO_LORDE_HIDE_SHARE, 0.5, () -> QuincyTests.assertNear(helper,
                    landed(lorde, blow, HEAVY_BLOW), HEAVY_BLOW * 0.5F, "a blow of 30 with hide_share 0.5"));
            // The soul of 300: the reiatsu difference cuts more than the hide, which then adds nothing to it.
            DamageSource weak = damage.playerAttack(striker(helper, WEAK));
            float gap = SoulLogic.reiatsuDamageMultiplier(WEAK, vic);
            helper.assertTrue(gap < VastoLorde.hide(lorde, weak, WEAK, vic), "the reiatsu difference keeps " + gap);
            for (float dealt : new float[] {LIGHT_BLOW, HEAVY_BLOW}) {
                QuincyTests.assertNear(helper, landed(lorde, weak, dealt), armoured(lorde, weak, dealt * gap),
                        "a blow of " + dealt + " from a soul of 300");
            }
        } finally {
            QuincyArtsTests.discardAll(bodies);
        }
        helper.succeed();
    }

    // ---- Its healing ----

    /**
     * Its healing, on top of every Hollow's: none for regen_delay_seconds after a blow, then regen_per_second, half of it
     * each half second; none while a fresh crack in its mask holds healing back, none with the key at 0, and never past
     * its health. Still (NoAI), as its healing runs in its tick; it waits for its first tick, so its config bases are set.
     */
    @GameTest(template = "field", timeoutTicks = 120)
    public static void itHealsFastOnlyUnstruck(GameTestHelper helper) {
        VastoLorde lorde = HollowPlayerTests.placed(helper, ModEntities.VASTO_LORDE.get(), 8, 8);
        Runnable clear = () -> discard(helper, lorde);
        helper.runAtTickTime(HEAL_GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("the Vasto Lorde never took its first tick");
        });
        float[] struck = new float[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, lorde.tickCount > 0,
                        "the Vasto Lorde has not taken its first tick"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    lorde.setHealth(lorde.getMaxHealth() / 2.0F);
                    helper.assertTrue(lorde.hurt(helper.getLevel().damageSources().generic(), 1.0F),
                            "a blow did not land on a Vasto Lorde");
                    struck[0] = lorde.getHealth();
                }, clear))
                .thenExecuteAfter(55, HollowBaitTests.orClear(() -> helper.assertTrue(lorde.getHealth() == struck[0],
                        "a Vasto Lorde healed to " + lorde.getHealth() + " within 55 ticks of a blow"), clear))
                .thenExecuteAfter(20, HollowBaitTests.orClear(() -> {
                    // One or two half-second heals since its wait of 60 ended, and none yet of every Hollow's at 100.
                    float healed = lorde.getHealth() - struck[0];
                    helper.assertTrue(healed >= 2.0F - EPSILON && healed <= 4.0F + EPSILON,
                            "a Vasto Lorde healed " + healed + " in the 15 ticks after its wait");
                    long now = helper.getLevel().getGameTime();
                    long pause = BleachConfig.get(BleachConfig.MASK_REGEN_PAUSE_SECONDS) * 20L;
                    QuincyTests.assertNear(helper, lorde.regenerate(now), 2.0, "a half second's healing");
                    lorde.onMaskStruck(now);
                    QuincyTests.assertNear(helper, lorde.regenerate(now + pause - 1), 0.0,
                            "the healing just before a fresh crack's pause ends");
                    QuincyTests.assertNear(helper, lorde.regenerate(now + pause), 2.0, "the healing as the pause ends");
                    KidoTests.withConfig(BleachConfig.VASTO_LORDE_REGEN_PER_SECOND, 0.0, () -> QuincyTests.assertNear(helper,
                            lorde.regenerate(now + pause), 0.0, "the healing with regen_per_second at 0"));
                    lorde.setHealth(lorde.getMaxHealth() - 0.5F);
                    QuincyTests.assertNear(helper, lorde.regenerate(now + pause), 0.5, "the healing half a point short");
                    helper.assertTrue(lorde.getHealth() == lorde.getMaxHealth(), "a Vasto Lorde healed past its health");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- Its fall ----

    /**
     * A Vasto Lorde mob devoured counts as devour_count Hollows toward a Gillian's or an Adjuchas's next form, and never as
     * a Menos. A Gillian it leaves one Hollow short of an Adjuchas, its Menos met, is still a Gillian and would rise with
     * one Hollow more; an Adjuchas it would lift to a Vasto Lorde while the server is full is held there, still an
     * Adjuchas. Mock players, none of whom reaches a new form, so the server's record of its Vasto Lordes is never written.
     */
    @GameTest(template = "field")
    public static void aVastoLordeDevouredCountsAsManyHollows(GameTestHelper helper) {
        List<Entity> bodies = new ArrayList<>();
        try {
            VastoLorde lorde = HollowPlayerTests.placed(helper, ModEntities.VASTO_LORDE.get(), 8, 12);
            bodies.add(lorde);
            int worth = BleachConfig.get(BleachConfig.VASTO_LORDE_DEVOUR_COUNT);
            long now = helper.getLevel().getGameTime();
            Vec3 spot = new Vec3(8.5, 2.0, 10.5);
            Player gillian = devourer(helper, HollowTier.GILLIAN, spot);
            SoulData data = SoulLogic.get(gillian);
            int hollows = BleachConfig.get(BleachConfig.ADJUCHAS_HOLLOWS) - worth - 1;
            int menos = BleachConfig.get(BleachConfig.ADJUCHAS_MENOS);
            data.setHollow(data.hollow().withEvolution(data.hollow().evolution().withTierHollows(hollows)
                    .withTierMenos(menos)));
            DevourLogic.Meal meal = DevourLogic.meal(gillian, lorde);
            helper.assertTrue(meal.kind() == HollowKind.VASTO_LORDE && meal.hollowLike(),
                    "a Vasto Lorde mob is the meal " + meal);
            MenosEvolution.afterMeal(gillian, data, meal, now);
            HollowProgress.Evolution after = data.hollow().evolution();
            helper.assertTrue(HollowTier.of(data) == HollowTier.GILLIAN && after.tierHollows() == hollows + worth
                            && after.tierMenos() == menos && MenosEvolution.nextTier(data) == null,
                    "a Gillian that devoured a Vasto Lorde is a " + HollowTier.of(data) + " with the record " + after);
            data.setHollow(data.hollow().withEvolution(after.withTierHollows(hollows + worth + 1)));
            helper.assertTrue(MenosEvolution.nextTier(data) == HollowTier.ADJUCHAS,
                    "a Gillian one Hollow further on would not rise");

            Player full = devourer(helper, HollowTier.ADJUCHAS, spot);
            SoulData fullData = SoulLogic.get(full);
            int toVasto = BleachConfig.get(BleachConfig.VASTO_HOLLOWS) - 1;
            int adjuchas = BleachConfig.get(BleachConfig.VASTO_ADJUCHAS);
            fullData.setHollow(fullData.hollow().withEvolution(fullData.hollow().evolution().withTierHollows(toVasto)
                    .withTierMenos(adjuchas)));
            KidoTests.withConfig(BleachConfig.VASTO_DESTINED_SHARE, 1.0, () -> KidoTests.withConfig(BleachConfig.VASTO_MAX, 0,
                    () -> MenosEvolution.afterMeal(full, fullData, DevourLogic.meal(full, lorde), now)));
            after = fullData.hollow().evolution();
            helper.assertTrue(HollowTier.of(fullData) == HollowTier.ADJUCHAS
                            && after.stall() == HollowProgress.Evolution.STALL_FULL
                            && after.tierHollows() == toVasto + worth && after.tierMenos() == adjuchas,
                    "an Adjuchas that devoured a Vasto Lorde with the server full is a " + HollowTier.of(fullData)
                            + " with the record " + after);
        } finally {
            QuincyArtsTests.discardAll(bodies);
        }
        helper.succeed();
    }

    /**
     * Its fall: a death no player dealt earns no one the advancement, though a Shinigami struck it first and has the kill
     * credit; that Shinigami's Asauchi purifies another, which counts and earns it; a Hollow's jaws devour a third and earn
     * it, the meal counted as devour_count Hollows on its road and once among the Hollows of its life. Still bodies a blow
     * from death and joined players, as an advancement is a server player's; it waits until the first body's bar shows.
     */
    @GameTest(template = "field", timeoutTicks = 60)
    public static void bringingOneDownEarnsTheAdvancement(GameTestHelper helper) {
        ServerPlayer purifier = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(4.5, 2.0, 3.5));
        purifier.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, AWARE));
        HollowTests.arm(purifier, ModItems.ASAUCHI.get());
        ServerPlayer jaws = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(12.5, 2.0, 3.5));
        jaws.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, DEVOURER));
        VastoLorde unclaimed = HollowPlayerTests.placed(helper, ModEntities.VASTO_LORDE.get(), 8, 8);
        VastoLorde purified = HollowPlayerTests.placed(helper, ModEntities.VASTO_LORDE.get(), 4, 8);
        VastoLorde devoured = HollowPlayerTests.placed(helper, ModEntities.VASTO_LORDE.get(), 12, 8);
        Runnable clear = () -> {
            discard(helper, unclaimed, purified, devoured);
            QuincyArtsTests.clearItems(helper);
        };
        helper.runAtTickTime(FALL_GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("the Shinigami was never shown a Vasto Lorde's bar");
        });
        String fallen = Milestones.VASTO_LORDE_FALLEN;
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, ShriekerTests.holds(unclaimed.bossBar(), purifier),
                        "the Shinigami was not shown the Vasto Lorde's bar"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    DamageSources damage = helper.getLevel().damageSources();
                    unclaimed.hurt(damage.playerAttack(purifier), 1.0F);
                    helper.assertTrue(unclaimed.isAlive() && unclaimed.getKillCredit() == purifier,
                            "the Shinigami's first blow left the kill credit with " + unclaimed.getKillCredit());
                    unclaimed.setHealth(1.0F);
                    unclaimed.invulnerableTime = 0;
                    unclaimed.hurt(damage.generic(), FATAL);
                    helper.assertTrue(unclaimed.isDeadOrDying(), "a fatal blow left a Vasto Lorde alive");
                    helper.assertFalse(DangaiTests.earned(purifier, fallen) || DangaiTests.earned(jaws, fallen),
                            "a death no player dealt brought a Vasto Lorde down");
                    purified.setHealth(1.0F);
                    purified.hurt(damage.playerAttack(purifier), FATAL);
                    helper.assertTrue(purified.isPurifying() && SoulLogic.get(purifier).hunt().purified() == 1,
                            "the Asauchi did not purify a Vasto Lorde, or the purifier's count did not grow");
                    helper.assertTrue(DangaiTests.earned(purifier, fallen),
                            "its purifier has not brought a Vasto Lorde down");
                    devoured.setHealth(1.0F);
                    helper.assertTrue(DevourEvents.complete(jaws, devoured, helper.getLevel().getGameTime())
                            && DangaiTests.earned(jaws, fallen), "its devourer has not brought a Vasto Lorde down");
                    HollowProgress meals = SoulLogic.get(jaws).hollow();
                    int worth = BleachConfig.get(BleachConfig.VASTO_LORDE_DEVOUR_COUNT);
                    helper.assertTrue(meals.evolution().tierHollows() == worth && meals.totalHollows() == 1,
                            "a Hollow that devoured a Vasto Lorde has the record " + meals.evolution() + " and "
                                    + meals.totalHollows() + " Hollows in its life");
                    clear.run();
                }, clear))
                .thenSucceed();
    }
}
