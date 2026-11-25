package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.ShriekerLeech;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for a Shrieker's leeches: loosed a few at a time, the rest held back, clinging at a back each tick without riding,
 * felled by one blow from a soul who sees them, falling apart as what holds them goes, bursting together at the ring and
 * breaking no block, knocked by a blow in flight, one sent at its Shrieker's head hurting him badly, and a blow to his
 * mask breaking off the ring. Each asks only about its own Shriekers, its own leeches and its own players, by object.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ShriekerLeechTests {
    /**
     * The reiatsu of the souls whose blows the tests strike with: aware, and more than a third of the Shrieker's (150), so
     * nothing is taken off what reaches him for the difference.
     */
    private static final float STRIKER = 100.0F;
    /**
     * A joined player cannot be hurt for its first 60 ticks of its own (ServerPlayer's spawn invulnerability), so the
     * tests wait on its tick count, not the test's clock.
     */
    private static final int SPAWN_SAFE_TICKS = 61;
    /** Health enough for the live fight's prey to outlast a ring and the Shrieker's swoops. */
    private static final double LASTING = 100.0;
    private static final float SAME = 1.0E-3F;

    private ShriekerLeechTests() {
    }

    /** A blow from this soul, as its hand lands it. */
    private static DamageSource blowFrom(GameTestHelper helper, Player soul) {
        return helper.getLevel().damageSources().playerAttack(soul);
    }

    /** A mock Human soul of this reiryoku with its eyes at this point (absolute), looking at that one. */
    private static Player soulAt(GameTestHelper helper, float reiryoku, Vec3 eyes, Vec3 looking) {
        Player soul = HollowTests.soulPlayer(helper, SoulState.HUMAN, reiryoku);
        soul.moveTo(eyes.x, eyes.y - soul.getEyeHeight(), eyes.z);
        soul.lookAt(EntityAnchorArgument.Anchor.EYES, looking);
        return soul;
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

    /** Whether every chunk the test stands in ticks its entities: one at each corner of its box. */
    static boolean groundTicks(GameTestHelper helper) {
        AABB box = helper.getBounds();
        ServerLevel level = helper.getLevel();
        return level.isPositionEntityTicking(BlockPos.containing(box.minX, box.minY, box.minZ))
                && level.isPositionEntityTicking(BlockPos.containing(box.maxX - 1.0, box.minY, box.minZ))
                && level.isPositionEntityTicking(BlockPos.containing(box.minX, box.minY, box.maxZ - 1.0))
                && level.isPositionEntityTicking(BlockPos.containing(box.maxX - 1.0, box.minY, box.maxZ - 1.0));
    }

    /**
     * A Shrieker with its wits, set on an aware soul once the soul can be hurt, looses as many leeches as it looses at once
     * and holds the rest back; every one flies to the soul and clings at its back without riding it. An unaware soul's
     * blow is refused, and an aware soul's makes a clinging leech fall apart. It rings, the rest burst on the soul as the
     * ring ends, and once they are spent and its wait is over it looses the next. The soul holds its ground through it all.
     */
    @GameTest(template = "field", timeoutTicks = 600)
    public static void aShriekerLoosesLeechesThatClingAndBurstAsItRings(GameTestHelper helper) {
        int brood = BleachConfig.get(BleachConfig.SHRIEKER_LEECHES);
        int atOnce = Math.min(brood, BleachConfig.get(BleachConfig.SHRIEKER_LEECHES_AT_ONCE));
        int second = Math.min(atOnce, brood - atOnce);
        ServerPlayer prey = ShriekerTests.aware(helper, new Vec3(8.5, 2.0, 8.5));
        prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(LASTING);
        prey.setHealth(prey.getMaxHealth());
        prey.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        Shrieker[] shrieker = new Shrieker[1];
        long[] spentAt = new long[1];
        List<ShriekerLeech> loosed = new ArrayList<>();
        helper.startSequence()
                // The bursts must land, so the Shrieker comes once the soul's spawn protection is over, by the soul's
                // own ticks (a stalled server ticks the test's clock but not the soul).
                .thenWaitUntil(() -> GateTests.patiently(helper, prey.tickCount > SPAWN_SAFE_TICKS,
                        "the prey is still new to the world"))
                .thenExecute(() -> {
                    shrieker[0] = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(3.5, 2.0, 8.5));
                    shrieker[0].setTarget(prey);
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, !shrieker[0].leeches().isEmpty(),
                        "the Shrieker has loosed no leeches"))
                .thenExecute(() -> {
                    loosed.addAll(shrieker[0].leeches());
                    helper.assertTrue(loosed.size() == atOnce && shrieker[0].leechesHeld() == brood - atOnce,
                            "the Shrieker loosed " + loosed.size() + " and holds " + shrieker[0].leechesHeld()
                                    + " back, not " + atOnce + " and " + (brood - atOnce));
                })
                .thenWaitUntil(() -> helper.assertTrue(loosed.stream().allMatch(leech -> !leech.isRemoved()
                        && atBack(leech, prey)), "not every leech clings at its prey's back"))
                .thenExecute(() -> {
                    ShriekerLeech struck = loosed.get(0);
                    Player unaware = HollowTests.soulPlayer(helper, SoulState.HUMAN, 0.0F);
                    helper.assertFalse(struck.hurt(blowFrom(helper, unaware), 1.0F) || struck.isRemoved(),
                            "an unaware soul's blow struck a leech");
                    Player aware = HollowTests.soulPlayer(helper, SoulState.HUMAN, STRIKER);
                    helper.assertTrue(struck.hurt(blowFrom(helper, aware), 1.0F) && struck.isRemoved(),
                            "an aware soul's blow left a leech clinging");
                })
                .thenWaitUntil(() -> helper.assertTrue(shrieker[0].isRinging(), "the Shrieker has not rung"))
                .thenWaitUntil(() -> {
                    DamageSource last = prey.getLastDamageSource();
                    helper.assertTrue(loosed.stream().allMatch(Entity::isRemoved) && last != null
                                    && last.is(ShriekerLeech.BURST) && last.getEntity() == shrieker[0]
                                    && last.getDirectEntity() instanceof ShriekerLeech,
                            "the leeches have not burst on their prey, last hurt by " + last);
                    spentAt[0] = helper.getLevel().getGameTime();
                })
                .thenExecute(() -> helper.assertFalse(shrieker[0].isRinging(), "the ring outlasted its bursts"))
                .thenWaitUntil(() -> helper.assertTrue(shrieker[0].leeches().size() == second
                                && shrieker[0].leechesHeld() == brood - atOnce - second,
                        "the Shrieker has loosed no second volley: " + shrieker[0].leeches().size() + " out"))
                .thenExecute(() -> {
                    // A few ticks' grace: the last burst may have come a tick or two before this wait saw it.
                    long waited = helper.getLevel().getGameTime() - spentAt[0];
                    helper.assertTrue(waited >= Shrieker.LOOSE_COOLDOWN_TICKS - 5,
                            "the second volley came " + waited + " ticks after the first was spent");
                    helper.assertTrue(prey.isAlive(), "the prey did not outlast the fight");
                    shrieker[0].discard();
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

    /**
     * At the ring, two leeches on one soul and one on another burst together, each with its whole damage, so the first
     * soul loses twice what the second does. A soul just past a burst's reach of the first soul is unhurt, and a Hollow
     * within the second soul's burst is unhurt, as a Shrieker spares its kind; the Shrieker itself, far off, is unhurt. The
     * dirt against the first soul's back still stands: a pin, as nothing in a burst touches blocks.
     */
    @GameTest(template = "field", timeoutTicks = 200)
    public static void theRingBurstsEveryClingingLeechAndBreaksNoBlock(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(4, 2, 7);
        helper.setBlock(dirt, Blocks.DIRT);
        ServerPlayer twice = ShriekerTests.aware(helper, new Vec3(4.5, 2.0, 8.5));
        ServerPlayer once = ShriekerTests.aware(helper, new Vec3(12.5, 2.0, 8.5));
        // A burst on the first soul's back is measured from within its body, at most 4.8 across: 2.7 from this body (7.5).
        ServerPlayer between = ShriekerTests.aware(helper, new Vec3(7.8, 2.0, 8.5));
        // At the second soul's back: its near side 0.9 from where that soul's burst is measured, well within the reach.
        SpiderHollow kin = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), new Vec3(12.5, 2.0, 6.5));
        Shrieker shrieker = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), new Vec3(8.5, 7.0, 14.5));
        List<ShriekerLeech> leeches = new ArrayList<>();
        float[] whole = new float[1];
        helper.startSequence()
                // The bursts must land past the souls' spawn protection, by their own ticks, and every leech must tick
                // from its cling.
                .thenWaitUntil(() -> GateTests.patiently(helper, Stream.of(twice, once, between)
                                .allMatch(soul -> soul.tickCount > SPAWN_SAFE_TICKS) && shrieker.tickCount > 0
                                && groundTicks(helper), "the souls are still new to the world, or the ground is still"))
                .thenExecute(() -> {
                    for (ServerPlayer soul : List.of(twice, once, between)) {
                        // Facing south, so each back is to the north, towards the dirt for the first.
                        soul.setYRot(0.0F);
                        soul.setYHeadRot(0.0F);
                        soul.setYBodyRot(0.0F);
                    }
                    shrieker.setTarget(twice);
                    helper.assertTrue(shrieker.looseLeeches() >= 3, "the Shrieker loosed too few leeches for the test");
                    leeches.addAll(shrieker.leeches());
                    leeches.get(0).clingTo(twice);
                    leeches.get(1).clingTo(twice);
                    leeches.get(2).clingTo(once);
                    leeches.stream().skip(3).forEach(Entity::discard);
                    whole[0] = shrieker.getHealth();
                    helper.assertTrue(shrieker.startRing() && shrieker.isRinging(), "the Shrieker would not ring");
                })
                .thenWaitUntil(() -> helper.assertTrue(leeches.stream().allMatch(Entity::isRemoved),
                        "the leeches have not burst"))
                .thenExecute(() -> {
                    float lostTwice = twice.getMaxHealth() - twice.getHealth();
                    float lostOnce = once.getMaxHealth() - once.getHealth();
                    helper.assertTrue(lostOnce > 0.0F && Math.abs(lostTwice - 2.0F * lostOnce) < SAME,
                            "two bursts took " + lostTwice + " and one " + lostOnce);
                    DamageSource last = twice.getLastDamageSource();
                    helper.assertTrue(last != null && last.is(ShriekerLeech.BURST) && last.getEntity() == shrieker
                                    && last.getDirectEntity() instanceof ShriekerLeech,
                            "the soul was last hurt by " + last + ", not its Shrieker's leech");
                    helper.assertTrue(between.getHealth() == between.getMaxHealth(),
                            "a soul past the bursts' reach lost " + (between.getMaxHealth() - between.getHealth()));
                    helper.assertTrue(kin.getHealth() == kin.getMaxHealth(),
                            "a Hollow within a burst lost " + (kin.getMaxHealth() - kin.getHealth()));
                    helper.assertTrue(shrieker.getHealth() == whole[0] && !shrieker.isRinging(),
                            "the Shrieker was hurt by its own leeches, or rings on");
                    helper.assertTrue(helper.getBlockState(dirt).is(Blocks.DIRT), "a burst broke the dirt behind a soul");
                    shrieker.discard();
                    kin.discard();
                })
                .thenSucceed();
    }

    /**
     * Leeches in flight before a still Shrieker: an unaware soul's blow is refused by one; an aware soul's, facing away
     * from him, knocks it off that way, never to cling, and it falls apart at the wall in time without harming him. A blow
     * facing his head sends one there, a second blow on it is refused, and it bursts by his head: it hurts him badly, its
     * own damage and never the mask's double, credited to the soul that sent it, and cracks his mask.
     */
    @GameTest(template = "field", timeoutTicks = 200)
    public static void aStruckLeechFliesOffOrBurstsAtItsShriekersHead(GameTestHelper helper) {
        Shrieker shrieker = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), new Vec3(8.5, 5.0, 3.5));
        // Its prey, never reached: its leeches are struck as they leave it.
        Player mark = HollowTests.soulPlayer(helper, SoulState.HUMAN, STRIKER);
        ShriekerLeech[] away = new ShriekerLeech[1];
        ShriekerLeech[] sent = new ShriekerLeech[1];
        Player[] sender = new Player[1];
        float[] whole = new float[1];
        long[] knockedAt = new long[1];
        long[] awayGoneAt = {-1L};
        helper.startSequence()
                // No player holds this ground: wait until it ticks, so each leech flies from the tick it is struck.
                .thenWaitUntil(() -> GateTests.patiently(helper, shrieker.tickCount > 0 && groundTicks(helper),
                        "the test's ground has not begun to tick"))
                .thenExecute(() -> {
                    ShriekerTests.faceSouth(shrieker);
                    shrieker.setTarget(mark);
                    helper.assertTrue(shrieker.looseLeeches() >= 2, "the Shrieker loosed too few leeches for the test");
                    List<ShriekerLeech> loosed = shrieker.leeches();
                    away[0] = loosed.get(0);
                    sent[0] = loosed.get(1);
                    loosed.stream().skip(2).forEach(Entity::discard);
                    Vec3 mask = HollowMask.maskCenter(shrieker);
                    away[0].setPos(mask.add(-2.0, 0.0, 6.0));
                    sent[0].setPos(mask.add(2.0, 0.0, 6.0));
                    whole[0] = shrieker.getHealth();
                    Vec3 awayAt = away[0].getBoundingBox().getCenter();
                    Vec3 west = awayAt.add(-1.0, 0.0, 0.0);
                    helper.assertFalse(away[0].hurt(blowFrom(helper, soulAt(helper, 0.0F, awayAt, west)), 1.0F)
                            || away[0].isKnocked(), "an unaware soul's blow struck a leech");
                    helper.assertTrue(away[0].hurt(blowFrom(helper, soulAt(helper, STRIKER, awayAt, west)), 1.0F)
                                    && away[0].isKnocked() && away[0].getDeltaMovement().normalize().x < -0.99,
                            "an aware soul's blow facing west did not knock a leech west");
                    sender[0] = soulAt(helper, STRIKER, sent[0].getBoundingBox().getCenter(), mask);
                    helper.assertTrue(sent[0].hurt(blowFrom(helper, sender[0]), 1.0F), "a blow at the head missed");
                    helper.assertFalse(sent[0].hurt(blowFrom(helper, sender[0]), 1.0F) || !sent[0].isKnocked()
                            || sent[0].isRemoved(), "a second blow on a knocked leech was not refused");
                    knockedAt[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> {
                    if (awayGoneAt[0] < 0 && away[0].isRemoved()) {
                        awayGoneAt[0] = helper.getLevel().getGameTime();
                    }
                    helper.assertTrue(awayGoneAt[0] >= 0 && sent[0].isRemoved(), "a struck leech is still about");
                })
                .thenExecute(() -> {
                    float lost = whole[0] - shrieker.getHealth();
                    double head = BleachConfig.get(BleachConfig.SHRIEKER_HEAD_DAMAGE);
                    // Its armour (4) takes a few percent of so big a blow; the mask's multiplier would double it.
                    helper.assertTrue(lost >= head * 0.9 && lost <= head + SAME,
                            "the leeches did " + lost + " to the Shrieker, not one burst at its head of " + head);
                    helper.assertTrue(shrieker.getLastHurtByMob() == sender[0] && shrieker.isMaskCracked(),
                            "the burst at the head was not credited to its sender, or left the mask whole");
                    long flew = awayGoneAt[0] - knockedAt[0];
                    helper.assertTrue(away[0].bearer() == null && flew <= ShriekerLeech.KNOCKED_TICKS + 2,
                            "the leech knocked away clung, or flew " + flew + " ticks");
                    shrieker.discard();
                })
                .thenSucceed();
    }

    /**
     * Its mask already cracked, a Shrieker rings over a soul its leeches cling to, and a blow to its mask, no fresh crack,
     * breaks the ring off: past the ring's time no leech has burst and the soul is unhurt. It may not ring again until its
     * wait is over, and then may.
     */
    @GameTest(template = "field", timeoutTicks = 200)
    public static void aBlowToTheMaskBreaksOffTheRing(GameTestHelper helper) {
        ServerPlayer bearer = ShriekerTests.aware(helper, new Vec3(8.5, 2.0, 2.5));
        Shrieker shrieker = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), new Vec3(8.5, 6.0, 10.5));
        List<ShriekerLeech> leeches = new ArrayList<>();
        long[] brokenAt = new long[1];
        helper.startSequence()
                // An unbroken ring's bursts would land past the soul's spawn protection, by its own ticks, and the
                // Shrieker must tick.
                .thenWaitUntil(() -> GateTests.patiently(helper, bearer.tickCount > SPAWN_SAFE_TICKS
                                && shrieker.tickCount > 0 && groundTicks(helper),
                        "the soul is still new to the world, or the ground is still"))
                .thenExecute(() -> {
                    ShriekerTests.faceSouth(shrieker);
                    shrieker.setTarget(bearer);
                    helper.assertTrue(shrieker.looseLeeches() > 0, "the Shrieker loosed no leeches");
                    leeches.addAll(shrieker.leeches());
                    leeches.forEach(leech -> leech.clingTo(bearer));
                    // Cracked already, so the blow that breaks the ring is no fresh crack.
                    shrieker.onMaskStruck(helper.getLevel().getGameTime());
                    helper.assertTrue(shrieker.startRing(), "the Shrieker would not ring");
                })
                .thenExecuteAfter(5, () -> {
                    Vec3 mask = HollowMask.maskCenter(shrieker);
                    shrieker.hurt(blowFrom(helper, soulAt(helper, STRIKER, mask.add(0.0, 0.0, 2.0), mask)), 4.0F);
                    brokenAt[0] = helper.getLevel().getGameTime();
                    helper.assertTrue(shrieker.wasMaskStruckAt(brokenAt[0]) && !shrieker.isRinging(),
                            "a blow to the mask did not break off the ring");
                })
                .thenExecuteAfter(Shrieker.RING_TICKS, () -> {
                    helper.assertTrue(leeches.stream().allMatch(leech -> !leech.isRemoved() && leech.bearer() == bearer),
                            "a leech burst or fell away after a broken ring");
                    helper.assertTrue(bearer.getHealth() == bearer.getMaxHealth(), "a broken ring hurt its soul");
                    helper.assertFalse(shrieker.startRing(), "the Shrieker rang again before its wait was over");
                })
                .thenExecuteAfter(Shrieker.RING_RETRY_TICKS - Shrieker.RING_TICKS, () -> {
                    helper.assertTrue(shrieker.startRing(), "the Shrieker could not ring again after its wait");
                    shrieker.discard();
                })
                .thenSucceed();
    }
}
