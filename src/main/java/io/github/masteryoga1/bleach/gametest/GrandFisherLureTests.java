package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.item.DenreishinkiItem;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritEvents;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Grand Fisher's lure: the one it grows and the one it takes, what reads her as a Hollow (nothing), the stalk
 * that draws her back and tears, and her fading with no death. Bodies stand with no will of their own, so only the
 * lure's own rules move her; each test asks only about its own bodies and lures, by object.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrandFisherLureTests {
    private static final float EPSILON = 1.0E-3F;
    /** Souls who see spirits, and one who does not. */
    private static final float AWARE = 40.0F;
    private static final float UNAWARE = 10.0F;
    /** How near its reach the stalk's pull must leave her: a move checked against collisions is not exact. */
    private static final double PULLED = 0.05;
    /** How soon a test's cut lure fades, in place of the config's twenty seconds. */
    private static final int FADE_TICKS = 5;

    private GrandFisherLureTests() {
    }

    /** The lures in the test's box whose body is this one. */
    private static List<GrandFisherLure> luresOf(GameTestHelper helper, GrandFisher body) {
        return helper.getLevel().getEntitiesOfClass(GrandFisherLure.class, helper.getBounds(), lure -> lure.body() == body);
    }

    /** How far the top of her head is from her body's brow. */
    private static double stalk(GrandFisher body, GrandFisherLure lure) {
        return lure.stalkEnd(1.0F).distanceTo(body.brow(1.0F));
    }

    /**
     * A body grows one lure on its first tick, linked both ways, within reach, with the config's health, and never a
     * second, nor does a copy of it moved to another world; a body that takes a lure laid first grows none. She is a
     * spirit and no Hollow: a mundane mob cannot see her, an unaware soul's blow passes through her, no Hollow hunts her,
     * a Quincy never aims at her, and the pager by her side points past her at her body. Slain, she is its lure no more.
     */
    @GameTest(template = "field", timeoutTicks = 60)
    public static void aBodyGrowsOneLureAndNoReaderTakesHerForAHollow(GameTestHelper helper) {
        GrandFisher body = helper.spawnWithNoFreeWill(ModEntities.GRAND_FISHER.get(), 2, 2, 8);
        // Further from where the pager is read than the body, so the pager finds the body.
        GrandFisher taker = helper.spawnWithNoFreeWill(ModEntities.GRAND_FISHER.get(), 2, 2, 1);
        GrandFisherLure laid = GrandFisherLure.grow(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 2.0, 4.5)), 0.0F);
        helper.assertTrue(laid != null, "the world refused a lure laid alone");
        taker.takeLure(laid);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 8, 2, 14);
        GrandFisher[] moved = new GrandFisher[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, body.lure() != null, "the body grew no lure"))
                .thenExecute(() -> {
                    List<GrandFisherLure> lures = luresOf(helper, body);
                    helper.assertTrue(lures.size() == 1 && body.lure() == lures.get(0),
                            "the body grew " + lures.size() + " lures, or does not hold the one it grew");
                    GrandFisherLure lure = lures.get(0);
                    double reach = BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_REACH);
                    double health = BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_HEALTH);
                    helper.assertTrue(!lure.isSevered() && stalk(body, lure) <= reach,
                            "the grown lure is cut off, or " + stalk(body, lure) + " blocks from the brow");
                    helper.assertTrue(Math.abs(lure.getMaxHealth() - health) < EPSILON
                                    && lure.getHealth() == lure.getMaxHealth(),
                            "the lure has " + lure.getHealth() + " of " + lure.getMaxHealth() + " health");
                    helper.assertTrue(luresOf(helper, taker).equals(List.of(laid)) && taker.lure() == laid,
                            "a body that took a lure laid first grew its own, or does not hold the one it took");

                    helper.assertTrue(SoulLogic.isSpirit(lure) && !lure.getType().is(BleachTags.HOLLOWS),
                            "the lure is no spirit, or is a Hollow");
                    helper.assertTrue(SpiritEvents.blindToSpirit(zombie, lure), "a zombie can see the lure");
                    helper.assertTrue(SpiritEvents.passesThrough(HollowTests.soulPlayer(helper, SoulState.HUMAN, UNAWARE),
                            lure), "an unaware soul's blow lands on the lure");
                    helper.assertFalse(HollowLogic.isPrey(lure) || QuincyNpc.isQuarry(lure),
                            "the lure is a Hollow's prey or a Quincy's quarry");
                    // A block east of the reader, with her body ten blocks west: a pager that took her for a Hollow would
                    // point east, five blocks off.
                    lure.moveTo(helper.absoluteVec(new Vec3(13.5, 2.0, 8.5)));
                    Player reader = HollowTests.shinigami(helper, AWARE);
                    reader.moveTo(helper.absoluteVec(new Vec3(12.5, 2.0, 8.5)));
                    Component reading = DenreishinkiItem.reading(reader);
                    Object[] args = HollowTests.translatable(helper, reading, "message.bleach.denreishinki.hollow").getArgs();
                    helper.assertTrue(args[0] instanceof Component way && way.getContents() instanceof TranslatableContents
                                    compass && compass.getKey().equals("message.bleach.direction.west")
                                    && String.valueOf(args[1]).equals("10"),
                            "the pager by the lure points " + args[0] + ", " + args[1] + " blocks off, not west to the body");
                    zombie.discard();

                    // Moved to another world by command, a body comes there as a copy of itself, as this one is made.
                    moved[0] = ModEntities.GRAND_FISHER.get().create(helper.getLevel());
                    moved[0].restoreFrom(body);
                    moved[0].setUUID(UUID.randomUUID());
                    moved[0].moveTo(helper.absoluteVec(new Vec3(12.5, 2.0, 2.5)));
                    moved[0].removeFreeWill();
                    helper.getLevel().addFreshEntity(moved[0]);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    GrandFisherLure lure = body.lure();
                    helper.assertTrue(luresOf(helper, body).equals(List.of(lure)), "the body grew a second lure");
                    helper.assertTrue(moved[0].tickCount > 0 && luresOf(helper, moved[0]).isEmpty()
                            && moved[0].lure() == null, "a body moved to another world grew a second lure there");
                    moved[0].discard();
                    lure.kill();
                    helper.assertTrue(!lure.isRemoved() && body.lure() == null, "the body still holds its slain lure");
                })
                .thenSucceed();
    }

    /**
     * The stalk: a lure set past its reach from the brow is drawn back to it on its next tick, still on her stalk; one set
     * past half as far again tears it. A lure whose body is gone is cut off.
     */
    @GameTest(template = "field", timeoutTicks = 60)
    public static void theStalkDrawsHerBackAndTearsFarPastItsReach(GameTestHelper helper) {
        GrandFisher body = helper.spawnWithNoFreeWill(ModEntities.GRAND_FISHER.get(), 1, 2, 1);
        GrandFisher lost = helper.spawnWithNoFreeWill(ModEntities.GRAND_FISHER.get(), 12, 2, 8);
        double reach = BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_REACH);
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, body.lure() != null && lost.lure() != null,
                        "a body grew no lure"))
                .thenExecute(() -> {
                    // Thirteen blocks east of the brow, which faces south from (1.5, 6.1, 2.5): past the reach, short of
                    // a tear.
                    body.lure().moveTo(helper.absoluteVec(new Vec3(14.5, 2.0, 2.5)));
                    double set = stalk(body, body.lure());
                    helper.assertTrue(set > reach && set < reach * GrandFisherLure.TEAR, "the lure was set " + set + " off");
                    lost.discard();
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    GrandFisherLure lure = body.lure();
                    // Drawn back to the reach itself: no nearer, as a pull that overshot would leave her.
                    helper.assertTrue(!lure.isSevered() && Math.abs(stalk(body, lure) - reach) <= PULLED,
                            "past its reach the lure was left " + stalk(body, lure) + " off, or her stalk tore");
                    helper.assertTrue(lost.lure() != null && lost.lure().isSevered(),
                            "a lure whose body is gone is on its stalk");
                    lure.moveTo(helper.absoluteVec(new Vec3(14.5, 2.0, 15.5)));
                    double set = stalk(body, lure);
                    helper.assertTrue(set > reach * GrandFisherLure.TEAR, "the far lure was set only " + set + " off");
                })
                .thenIdle(1)
                .thenExecute(() -> helper.assertTrue(body.lure().isSevered(), "a stalk pulled far past its reach held"))
                .thenSucceed();
    }

    /** A cut lure lies until her time is up, then goes, discarded, with no death; her body, whole, holds none after. */
    @GameTest(template = "field", timeoutTicks = 60)
    public static void aCutLureFadesWithNoDeath(GameTestHelper helper) {
        GrandFisher body = helper.spawnWithNoFreeWill(ModEntities.GRAND_FISHER.get(), 8, 2, 4);
        GrandFisherLure[] lure = new GrandFisherLure[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, body.lure() != null, "the body grew no lure"))
                .thenExecute(() -> {
                    lure[0] = body.lure();
                    lure[0].sever(FADE_TICKS);
                })
                .thenIdle(FADE_TICKS - 1)
                .thenExecute(() -> helper.assertTrue(lure[0].isAlive() && lure[0].isSevered(),
                        "the cut lure went before her time"))
                .thenWaitUntil(() -> helper.assertTrue(lure[0].isRemoved(), "the cut lure has not faded"))
                .thenExecute(() -> {
                    helper.assertTrue(lure[0].getRemovalReason() == Entity.RemovalReason.DISCARDED && lure[0].deathTime == 0,
                            "the lure faded by " + lure[0].getRemovalReason() + " after " + lure[0].deathTime
                                    + " ticks of death");
                    helper.assertTrue(body.isAlive() && body.lure() == null, "the body died with her, or still holds her");
                })
                .thenSucceed();
    }
}
