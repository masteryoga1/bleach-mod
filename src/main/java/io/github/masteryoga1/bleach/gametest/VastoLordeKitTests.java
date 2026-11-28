package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.VastoLorde;
import io.github.masteryoga1.bleach.registry.ModEntities;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for a Vasto Lorde's Cero, gathered at the brow. Each test asks only about its own bodies, by object, and discards
 * them, pass or fail. A body with its wits about it gathers by itself, so its goal's test has a batch of its own.
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
    private static final int CERO_GIVE_UP_TICK = 190;

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
        helper.runAtTickTime(CERO_GIVE_UP_TICK, () -> {
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
}
