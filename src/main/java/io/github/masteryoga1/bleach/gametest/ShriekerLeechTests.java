package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.ShriekerLeech;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for a Shrieker's leeches: loosed a few at a time, the rest held back, clinging at a back each tick without riding,
 * felled by one blow from a soul who sees them, and falling apart as what holds them goes. Each asks only about its own
 * Shriekers, its own leeches and its own players, by object.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ShriekerLeechTests {
    /** A soul aware of spirits, as the Shrieker's own tests take one. */
    private static final float AWARE = 40.0F;

    private ShriekerLeechTests() {
    }

    /** A blow from this soul, as its hand lands it. */
    private static DamageSource blowFrom(GameTestHelper helper, Player soul) {
        return helper.getLevel().damageSources().playerAttack(soul);
    }

    /**
     * Whether the leech is still there and clings at the bearer's back, by the bearer's id every client is sent: behind
     * its body's facing and within a block of it, riding nothing and ridden by nothing.
     */
    private static boolean atBack(ShriekerLeech leech, LivingEntity bearer) {
        Vec3 off = leech.position().subtract(bearer.position());
        Vec3 ahead = Vec3.directionFromRotation(0.0F, bearer.yBodyRot);
        return !leech.isRemoved() && leech.bearer() == bearer && leech.getVehicle() == null
                && bearer.getPassengers().isEmpty()
                && off.x * ahead.x + off.z * ahead.z < 0.0 && off.horizontalDistance() < 1.0;
    }

    /**
     * A Shrieker with its wits, set on an aware soul, looses as many leeches as it looses at once and holds the rest back;
     * every one flies to the soul and clings at its back without riding it. An unaware soul's blow is refused, and an
     * aware soul's makes a clinging leech fall apart.
     */
    @GameTest(template = "field", timeoutTicks = 300)
    public static void aShriekerLoosesLeechesThatClingToItsPrey(GameTestHelper helper) {
        int brood = BleachConfig.get(BleachConfig.SHRIEKER_LEECHES);
        int atOnce = Math.min(brood, BleachConfig.get(BleachConfig.SHRIEKER_LEECHES_AT_ONCE));
        ServerPlayer prey = ShriekerTests.aware(helper, new Vec3(8.5, 2.0, 8.5));
        Shrieker shrieker = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(3.5, 2.0, 8.5));
        shrieker.setTarget(prey);
        List<ShriekerLeech> loosed = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, !shrieker.leeches().isEmpty(),
                        "the Shrieker has loosed no leeches"))
                .thenExecute(() -> {
                    loosed.addAll(shrieker.leeches());
                    helper.assertTrue(loosed.size() == atOnce && shrieker.leechesHeld() == brood - atOnce,
                            "the Shrieker loosed " + loosed.size() + " and holds " + shrieker.leechesHeld() + " back, not "
                                    + atOnce + " and " + (brood - atOnce));
                })
                .thenWaitUntil(() -> helper.assertTrue(loosed.stream().allMatch(leech -> !leech.isRemoved()
                        && atBack(leech, prey)), "not every leech clings at its prey's back"))
                .thenExecute(() -> {
                    ShriekerLeech struck = loosed.get(0);
                    Player unaware = HollowTests.soulPlayer(helper, SoulState.HUMAN, 0.0F);
                    helper.assertFalse(struck.hurt(blowFrom(helper, unaware), 1.0F) || struck.isRemoved(),
                            "an unaware soul's blow struck a leech");
                    Player aware = HollowTests.soulPlayer(helper, SoulState.HUMAN, AWARE);
                    helper.assertTrue(struck.hurt(blowFrom(helper, aware), 1.0F) && struck.isRemoved(),
                            "an aware soul's blow left a leech clinging");
                    shrieker.discard();
                })
                .thenSucceed();
    }

    /**
     * Leeches set at four souls' backs are kept there each tick, riding none, and one follows its soul as it moves; one
     * falls apart when its soul dies, one when its soul goes to another world, one when its soul logs out, and every
     * leech of a Shrieker that dies, clinging or still in flight.
     */
    @GameTest(template = "field", timeoutTicks = 300)
    public static void aLeechFallsApartWhenWhatHoldsItGoes(GameTestHelper helper) {
        ServerPlayer dies = ShriekerTests.aware(helper, new Vec3(2.5, 2.0, 2.5));
        ServerPlayer leaves = ShriekerTests.aware(helper, new Vec3(6.5, 2.0, 2.5));
        ServerPlayer quits = ShriekerTests.aware(helper, new Vec3(10.5, 2.0, 2.5));
        ServerPlayer stays = ShriekerTests.aware(helper, new Vec3(13.5, 2.0, 6.5));
        Shrieker shrieker = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), new Vec3(6.5, 6.0, 12.5));
        Shrieker other = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), new Vec3(12.5, 6.0, 12.5));
        List<Entity> all = List.of(dies, leaves, quits, stays, shrieker, other);
        List<ShriekerLeech> held = new ArrayList<>();
        List<ShriekerLeech> others = new ArrayList<>();
        Vec3[] moved = new Vec3[1];
        helper.startSequence()
                // Every leech here must tick from the first: wait until the ground under each of them does.
                .thenWaitUntil(() -> GateTests.patiently(helper, all.stream().allMatch(entity
                                -> helper.getLevel().isPositionEntityTicking(entity.blockPosition())),
                        "the test's ground has not begun to tick"))
                .thenExecute(() -> {
                    shrieker.setTarget(dies);
                    other.setTarget(stays);
                    helper.assertTrue(shrieker.looseLeeches() >= 3 && other.looseLeeches() >= 1,
                            "a Shrieker loosed too few leeches for the test");
                    held.addAll(shrieker.leeches());
                    others.addAll(other.leeches());
                    held.get(0).clingTo(dies);
                    held.get(1).clingTo(leaves);
                    held.get(2).clingTo(quits);
                    others.get(0).clingTo(stays);
                })
                .thenExecuteAfter(1, () -> {
                    List<ServerPlayer> bearers = List.of(dies, leaves, quits);
                    for (int i = 0; i < bearers.size(); i++) {
                        helper.assertTrue(atBack(held.get(i), bearers.get(i)), "a leech is not at its soul's back");
                    }
                    helper.assertTrue(atBack(others.get(0), stays), "the other Shrieker's leech is not at its back");
                    moved[0] = stays.position().add(-3.0, 0.0, 0.0);
                    stays.teleportTo(helper.getLevel(), moved[0].x, moved[0].y, moved[0].z, 0.0F, 0.0F);
                    // On the death screen till its health comes back, as a soul killed is.
                    dies.setHealth(0.0F);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(held.get(0).isRemoved(), "a leech clings to a dead soul");
                    helper.assertTrue(stays.position().distanceTo(moved[0]) < 0.01 && atBack(others.get(0), stays),
                            "a leech did not follow its soul: it is at " + others.get(0).position());
                    dies.setHealth(dies.getMaxHealth());
                    dies.deathTime = 0;
                    ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
                    helper.assertTrue(nether != null, "the test server has no Nether");
                    leaves.teleportTo(nether, 0.5, 130.0, 0.5, 0.0F, 0.0F);
                    quits.connection.disconnect(Component.literal("Logged out"));
                })
                .thenWaitUntil(() -> helper.assertTrue(quits.isRemoved() && held.get(1).isRemoved()
                        && held.get(2).isRemoved(), "a leech clings to a soul gone to another world or logged out"))
                .thenExecute(() -> other.kill())
                .thenWaitUntil(() -> helper.assertTrue(others.stream().allMatch(Entity::isRemoved),
                        "a leech of a dead Shrieker is still about"))
                .thenExecute(() -> {
                    shrieker.discard();
                    other.discard();
                })
                .thenSucceed();
    }
}
