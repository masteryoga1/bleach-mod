package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.VastoLorde;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for a Vasto Lorde's kit: its Cero gathered at the brow, its rush along the ground and its presence. Each test asks
 * only about its own bodies, by object, and discards them, pass or fail. A body with its wits about it gathers, rushes and
 * weighs on others by itself, and its presence reaches past the box, so each test with one has a batch of its own.
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
}
