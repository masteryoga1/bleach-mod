package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.HellGate;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.ShriekerLeech;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BaitSite;
import io.github.masteryoga1.bleach.soul.BaitSites;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.PlusEvents;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Gate of Hell: which deaths open it, the body it holds and takes on its own clock, what a death gives
 * (once, at the death tick) when the Gate takes the Hollow, the hold ending cleanly when the Gate or the body goes
 * first, the push of anyone else in its way, and the share of sinners among arrivals. Each test asks only about its own
 * Hollows, its own Gates and its own souls, by object; each Gate stands inside its own test's box, so it pushes no one
 * from another test.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HellGateTests {
    /** Reiryoku enough to see spirits and to cut a Hollow down, with nothing taken off for the difference. */
    private static final float REIRYOKU = 100.0F;
    private static final float KILLING_BLOW = 1000.0F;
    /** Room for a held body's position: it is set, not moved, so it stays exactly where it was. */
    private static final double HELD = 1.0E-3;
    /** Far enough past its end that a Gate's clock jumps over the taking in one tick. */
    private static final int LATE_TICKS = 200;

    private HellGateTests() {
    }

    // ---- Helpers ----

    /** A Spider Hollow standing still at this spot on the floor, a sinner in life. */
    private static SpiderHollow sinner(GameTestHelper helper, double x, double z) {
        SpiderHollow spider = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), new Vec3(x, 2.0, z)));
        spider.setSinner(true);
        return spider;
    }

    /** A mock Shinigami on the floor at this spot, holding this. */
    private static Player shinigami(GameTestHelper helper, double x, double z, Item held) {
        Player player = QuincyTests.archer(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, REIRYOKU), x, z);
        HollowTests.arm(player, held);
        return player;
    }

    /** A blow of this damage type from this creature. */
    private static DamageSource blow(GameTestHelper helper, ResourceKey<DamageType> type, Entity from) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type), from);
    }

    /** The one Gate that holds this body; fails unless there is exactly one. */
    static HellGate gateOf(GameTestHelper helper, Hollow body) {
        List<HellGate> gates = helper.getLevel().getEntitiesOfClass(HellGate.class, helper.getBounds().inflate(4.0),
                gate -> gate.body() == body);
        helper.assertTrue(gates.size() == 1, gates.size() + " Gates hold the body");
        return gates.get(0);
    }

    /** Runs this once the test's ground ticks, so the Gate's clock (the world's) and its body's ticks agree. */
    private static void onceTicking(GameTestHelper helper, Runnable then) {
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, ShriekerLeechTests.groundTicks(helper),
                        "the test's ground has not begun to tick"))
                .thenExecute(then);
    }

    /** Whether this player has earned the advancement {@code bleach:<name>}. */
    static boolean done(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.server.getAdvancements().get(BleachMod.id(name));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    // ---- The held death and the Gate's clock ----

    /**
     * A Shrieker, a sinner as every one is, its leeches loosed, cut down by a joined Shinigami's own Asauchi: held where
     * the blow left it, neither dissolving nor tipping over, before a Gate that rose behind it facing the killer; its
     * leeches gone with its life; credited at once as a purification; drawn into the doorway, where the Gate stands, by
     * the slam; taken (removed as killed) when the doors have slammed, at the Gate's {@link HellGate#TAKEN_AT}; no soul
     * rises; the Gate is gone at its end. Up to 100 ticks for the ground, then the blow; the body gone 90 after it, the
     * Gate 120: 80 ticks of room.
     */
    @GameTest(template = "field", timeoutTicks = 300)
    public static void aSinnerCutDownByAZanpakutoIsHeldAndTakenByTheGate(GameTestHelper helper) {
        onceTicking(helper, () -> {
            ServerPlayer killer = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 2.5));
            killer.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, REIRYOKU));
            HollowTests.arm(killer, ModItems.ASAUCHI.get());
            Shrieker sinner = QuincyTests.still(helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(8.5, 2.0, 7.5)));
            sinner.setTarget(killer);
            sinner.looseLeeches();
            List<ShriekerLeech> leeches = List.copyOf(sinner.leeches());
            helper.assertFalse(leeches.isEmpty(), "the Shrieker loosed no leeches");
            Vec3 heldAt = sinner.position();
            sinner.hurt(helper.getLevel().damageSources().playerAttack(killer), KILLING_BLOW);
            helper.assertTrue(sinner.isHellbound() && !sinner.isPurifying() && sinner.deathTime == 0,
                    "the Asauchi's sinner is not held for the Gate, or dissolves");
            HellGate gate = gateOf(helper, sinner);
            Vec3 away = heldAt.subtract(killer.position()).multiply(1.0, 0.0, 1.0).normalize();
            helper.assertTrue(gate.away().dot(away) > 0.99 && gate.position().subtract(heldAt).dot(away) > 2.0,
                    "the Gate does not face the killer from behind its sinner");
            helper.assertTrue(SoulLogic.get(killer).hunt().purified() == 1 && done(killer, "purified")
                    && done(killer, "hell_gate"), "the killer was not credited as for a purification, or for the Gate");
            double[] drift = {0.0};
            int[] tipped = {0};
            long[] takenAt = {-1L};
            boolean[] drawn = {false};
            boolean[] echo = {false};
            helper.succeedWhen(() -> {
                if (!sinner.isRemoved()) {
                    if (gate.age() < HellGate.DRAW_AT) {
                        drift[0] = Math.max(drift[0], sinner.position().distanceTo(heldAt));
                    } else if (gate.age() >= HellGate.SLAM_AT) {
                        drawn[0] |= Math.hypot(sinner.getX() - gate.getX(), sinner.getZ() - gate.getZ()) <= HELD;
                    }
                    tipped[0] = Math.max(tipped[0], sinner.deathTime);
                } else if (takenAt[0] < 0L) {
                    takenAt[0] = gate.age();
                }
                echo[0] |= !helper.getLevel().getEntitiesOfClass(PlusSpirit.class, AABB.ofSize(heldAt, 8.0, 8.0, 8.0),
                        PlusSpirit::isDeparting).isEmpty();
                helper.assertTrue(gate.isRemoved(), "the Gate still stands at " + gate.age());
                helper.assertTrue(sinner.getRemovalReason() == Entity.RemovalReason.KILLED
                                && takenAt[0] >= HellGate.TAKEN_AT && takenAt[0] <= HellGate.TAKEN_AT + 1,
                        "the sinner went " + sinner.getRemovalReason() + " at the Gate's " + takenAt[0]);
                helper.assertTrue(drift[0] <= HELD && tipped[0] == 0, "the held sinner moved " + drift[0]
                        + " before it was drawn in, or tipped over (death time " + tipped[0] + ")");
                helper.assertTrue(drawn[0], "the sinner was not drawn into the Gate's doorway before it was taken");
                helper.assertTrue(leeches.stream().allMatch(Entity::isRemoved), "a leech of the taken Shrieker is left");
                helper.assertFalse(echo[0], "a soul rose from the sinner the Gate took");
                helper.assertTrue(SoulLogic.get(killer).hunt().purified() == 1, "the killer was credited again");
            });
        });
    }

    /**
     * Only a sinner's purifying blow opens the Gate, chosen in place of the dissolve: a Getsuga Tenshō opens it; a
     * plain Hollow cut down by an Asauchi dissolves as before; a sinner slain by an iron sword, a Quincy's Heilig Pfeil,
     * a Hadō or a Hollow's bite falls as before, with no Gate. Up to 100 ticks for the ground, one tick after the blows,
     * then they are let go.
     */
    @GameTest(template = "field", timeoutTicks = 200)
    public static void onlyASinnersPurifyingBlowOpensTheGate(GameTestHelper helper) {
        onceTicking(helper, () -> {
            SpiderHollow getsuga = sinner(helper, 3.0, 8.0);
            SpiderHollow plain = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), new Vec3(7.0, 2.0, 8.0)));
            SpiderHollow sword = sinner(helper, 10.0, 8.0);
            SpiderHollow arrow = sinner(helper, 13.0, 8.0);
            SpiderHollow kido = sinner(helper, 8.5, 12.0);
            SpiderHollow bitten = sinner(helper, 11.5, 12.0);
            SpiderHollow biter = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), new Vec3(14.5, 2.0, 12.0)));
            Player quincy = QuincyTests.archer(helper, QuincyTests.quincy(Bloodline.ECHT, 1, REIRYOKU), 13.0, 3.0);
            Player cutter = shinigami(helper, 7.0, 3.0, ModItems.ASAUCHI.get());
            Player swordsman = shinigami(helper, 10.0, 3.0, Items.IRON_SWORD);
            getsuga.hurt(blow(helper, GetsugaTensho.DAMAGE_TYPE, shinigami(helper, 3.0, 3.0, Items.AIR)), KILLING_BLOW);
            plain.hurt(helper.getLevel().damageSources().playerAttack(cutter), KILLING_BLOW);
            sword.hurt(helper.getLevel().damageSources().playerAttack(swordsman), KILLING_BLOW);
            arrow.hurt(blow(helper, HeiligPfeil.DAMAGE_TYPE, quincy), KILLING_BLOW);
            kido.hurt(blow(helper, KidoEvents.HADO, shinigami(helper, 8.5, 14.5, Items.AIR)), KILLING_BLOW);
            bitten.hurt(helper.getLevel().damageSources().mobAttack(biter), KILLING_BLOW);
            List<SpiderHollow> fallen = List.of(sword, arrow, kido, bitten);
            helper.assertTrue(getsuga.isHellbound() && !getsuga.isPurifying(), "the Getsuga Tenshō did not open the Gate");
            helper.assertTrue(plain.isPurifying() && !plain.isHellbound(), "the plain Hollow did not dissolve as before");
            helper.assertTrue(fallen.stream().allMatch(spider -> spider.isDeadOrDying() && !spider.isHellbound()
                    && !spider.isPurifying()), "a sword's, an arrow's, a Hadō's or a bite's sinner was held or dissolved");
            HellGate gate = gateOf(helper, getsuga);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(HellGate.class, helper.getBounds().inflate(4.0),
                    other -> fallen.contains(other.body())).isEmpty(), "a fallen sinner has a Gate");
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(fallen.stream().allMatch(spider -> spider.deathTime >= 1) && getsuga.deathTime == 0,
                        "a fallen sinner did not tip over as before, or the Gate's did");
                gate.discard();
                List.of(getsuga, plain, sword, arrow, kido, bitten, biter).forEach(Entity::discard);
                helper.succeed();
            });
        });
    }

    /**
     * The hold ends cleanly however it ends. A Gate unloaded at once leaves its body held as it was, which goes by itself
     * at {@link HellGate#HOLD_LIMIT}, and is never written meanwhile; a body unloaded ten ticks in slams its Gate shut at
     * once, which crumbles to its end. A Gate whose clock jumps past its end in one tick still takes its body, as killed,
     * on that tick, and is gone. A Gate raised over a Hollow that lives lets it go on its first tick and slams shut. Each
     * kill is credited once. Up to 100 ticks for the ground, then the blows; the slammed Gate is gone about 46 after
     * them, the first body about 110: 90 ticks of room.
     */
    @GameTest(template = "field", timeoutTicks = 300)
    public static void theHoldEndsCleanlyIfTheGateOrItsBodyGoesFirst(GameTestHelper helper) {
        onceTicking(helper, () -> {
            Player killer = shinigami(helper, 8.0, 2.5, ModItems.ASAUCHI.get());
            // Each Gate rises 3.05 behind its body, away from the killer, inside the box.
            SpiderHollow orphan = sinner(helper, 5.0, 6.5);
            SpiderHollow lost = sinner(helper, 11.0, 6.5);
            SpiderHollow late = sinner(helper, 8.0, 9.0);
            SpiderHollow living = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), new Vec3(13.5, 2.0, 10.5)));
            Vec3 heldAt = orphan.position();
            DamageSource cut = helper.getLevel().damageSources().playerAttack(killer);
            orphan.hurt(cut, KILLING_BLOW);
            lost.hurt(cut, KILLING_BLOW);
            late.hurt(cut, KILLING_BLOW);
            HellGate first = gateOf(helper, orphan);
            HellGate second = gateOf(helper, lost);
            HellGate jumped = gateOf(helper, late);
            helper.assertTrue(HellGate.open(helper.getLevel(), living, killer), "no Gate rose over the living Hollow");
            HellGate freed = gateOf(helper, living);
            first.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertFalse(orphan.saveAsPassenger(new CompoundTag()), "a hellbound body would be saved");
            jumped.ageForTest(LATE_TICKS);
            helper.startSequence()
                    .thenExecuteAfter(1, () -> {
                        helper.assertTrue(late.getRemovalReason() == Entity.RemovalReason.KILLED
                                        && jumped.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                                "a Gate whose clock jumped left " + late.getRemovalReason() + " and "
                                        + jumped.getRemovalReason());
                        helper.assertTrue(living.isAlive() && !living.isHellbound() && freed.body() == null
                                && freed.age() >= HellGate.SLAM_AT, "a Gate held a Hollow that lives, at " + freed.age());
                    })
                    .thenExecuteAfter(9, () -> lost.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK))
                    .thenExecuteAfter(1, () -> helper.assertTrue(second.body() == null && second.age() >= HellGate.SLAM_AT,
                            "a Gate whose body went still waits, at " + second.age()))
                    .thenWaitUntil(() -> helper.assertTrue(second.isRemoved(), "the slammed Gate never crumbled away"))
                    .thenWaitUntil(() -> helper.assertTrue(orphan.getRemovalReason() == Entity.RemovalReason.KILLED,
                            "the body whose Gate went is " + orphan.getRemovalReason()))
                    .thenExecute(() -> {
                        helper.assertTrue(orphan.position().distanceTo(heldAt) <= HELD && orphan.deathTime == 0,
                                "the body whose Gate went moved or tipped over");
                        helper.assertTrue(SoulLogic.get(killer).hunt().purified() == 3,
                                "three Gate kills counted " + SoulLogic.get(killer).hunt().purified());
                        living.discard();
                    })
                    .thenSucceed();
        });
    }

    // ---- The bait, the push and the share ----

    /**
     * A sinner its owner cuts down at a contest's bait counts at its death, once: one kill to the owner, none to the
     * rival, one purified; and the Gate's taking, its clock run past its end in one tick, counts nothing more. Driven by
     * the death itself, as {@link HollowBaitTests#contestCountsWhoKilled} is, as the test server never ticks a bait site.
     */
    @GameTest(template = "field")
    public static void aSinnerTakenAtABaitCountsOnceInItsTallyAndItsKillersCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuincyHunter hunter = QuincyTests.still(helper.spawn(ModEntities.QUINCY_HUNTER.get(), 14, 2, 2));
        Player owner = shinigami(helper, 8.5, 2.5, ModItems.ASAUCHI.get());
        SpiderHollow sinner = sinner(helper, 8.5, 7.5);
        List<Entity> made = new ArrayList<>(List.of(hunter, sinner));
        BaitSites sites = BaitSites.get(level.getServer());
        BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
        BaitSite site = BaitSite.create(owner.getUUID(), centre, true, hunter.getUUID(), 0);
        sites.add(site);
        try {
            sinner.hurt(level.damageSources().playerAttack(owner), KILLING_BLOW);
            HellGate gate = gateOf(helper, sinner);
            made.add(gate);
            Runnable once = () -> helper.assertTrue(site.ownerKills() == 1 && site.rivalKills() == 0
                    && SoulLogic.get(owner).hunt().purified() == 1, "the Gate's sinner counted " + site.ownerKills() + " to "
                    + site.rivalKills() + " at the bait, and " + SoulLogic.get(owner).hunt().purified() + " purified");
            once.run();
            gate.ageForTest(LATE_TICKS);
            gate.tick();
            helper.assertTrue(sinner.getRemovalReason() == Entity.RemovalReason.KILLED && gate.isRemoved(),
                    "the Gate run past its end left its sinner " + sinner.getRemovalReason());
            once.run();
        } finally {
            QuincyArtsTests.discardAll(made);
            QuincyArtsTests.clearItems(helper);
            QuincyArtsTests.discardAll(level.getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()));
            sites.remove(site.id());
        }
        helper.succeed();
    }

    /**
     * A Hollow standing where the Gate rises, free to move but with no will to, is pushed out of the Gate by its nearest
     * side and stays in the world, never taken, while the Gate keeps its sinner; a pig standing in it, which no spirit
     * touches, is not pushed. The Gate's middle is 3.05 behind the sinner, at z 9.55, so the Hollow at 9.5 starts 2
     * blocks deep in it. Up to 100 ticks for the ground, then the blow; out within a few ticks: 130 ticks of room.
     */
    @GameTest(template = "field", timeoutTicks = 240)
    public static void aBystanderInTheGatesWayIsPushedOutAndNeverTaken(GameTestHelper helper) {
        onceTicking(helper, () -> {
            Player killer = shinigami(helper, 8.5, 2.5, ModItems.ASAUCHI.get());
            SpiderHollow sinner = sinner(helper, 8.5, 6.5);
            SpiderHollow bystander = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), new Vec3(8.5, 2.0, 9.5));
            Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(10.5, 2.0, 10.5));
            Vec3 pigAt = pig.position();
            sinner.hurt(helper.getLevel().damageSources().playerAttack(killer), KILLING_BLOW);
            HellGate gate = gateOf(helper, sinner);
            helper.assertTrue(gate.outOf(bystander) != null && gate.outOf(pig) != null, "they did not stand in the Gate");
            helper.succeedWhen(() -> {
                helper.assertTrue(gate.outOf(bystander) == null, "the bystander is still in the Gate");
                helper.assertTrue(bystander.isAlive() && gate.body() == sinner,
                        "the bystander was hurt or taken, or the Gate let its sinner go");
                helper.assertTrue(pig.position().distanceTo(pigAt) < 0.1, "the Gate pushed a pig");
                List.of(sinner, bystander, pig, gate).forEach(Entity::discard);
            });
        });
    }

    /**
     * The share of sinners, on arrivals finalised as spawns are: at 0 none is a sinner; at 1 every lesser one is, but
     * never a Menos, Fishbone D nor Grand Fisher; the mark is kept through a save and a load; a practice Hollow never is
     * one; and a Hollow a Plus turns into rolls the share as any arrival does. The Shrieker, always one, is left to its
     * own tests.
     */
    @GameTest(template = "field")
    public static void theShareMarksArrivalsButNeverAMenosFishboneDOrAPracticeHollow(GameTestHelper helper) {
        List<HollowKind> kinds = List.of(HollowKind.SPIDER, HollowKind.HEXAPODUS, HollowKind.HOUND, HollowKind.SERPENT,
                HollowKind.FISHBONE_D, HollowKind.GILLIAN, HollowKind.ADJUCHAS, HollowKind.GRAND_FISHER);
        List<HollowKind> lesser = List.of(HollowKind.SPIDER, HollowKind.HEXAPODUS, HollowKind.HOUND, HollowKind.SERPENT);
        KidoTests.withConfig(BleachConfig.SINNER_SHARE, 0.0, () -> kinds.forEach(kind -> helper.assertFalse(
                arrival(helper, kind).isSinner(), "a share of 0 made a " + kind + " arrival a sinner")));
        KidoTests.withConfig(BleachConfig.SINNER_SHARE, 1.0, () -> {
            for (HollowKind kind : kinds) {
                helper.assertTrue(arrival(helper, kind).isSinner() == lesser.contains(kind),
                        "a share of 1 marked a " + kind + " wrongly");
            }
            Hollow arrived = arrival(helper, HollowKind.SPIDER);
            Hollow loaded = ModEntities.SPIDER_HOLLOW.get().create(helper.getLevel());
            loaded.load(arrived.saveWithoutId(new CompoundTag()));
            helper.assertTrue(loaded.isSinner(), "a sinner lost its mark through a save and a load");
            arrived.makePractice(UUID.randomUUID(), 0L);
            helper.assertFalse(arrived.isSinner(), "a practice Hollow is a sinner");
            PlusSpirit plus = helper.spawn(ModEntities.PLUS.get(), new Vec3(8.5, 2.0, 8.5));
            List<Hollow> turned = new ArrayList<>();
            try {
                helper.assertTrue(PlusEvents.hollowfy(helper.getLevel(), plus), "the Plus did not turn");
                turned.addAll(helper.getLevel().getEntitiesOfClass(Hollow.class, helper.getBounds(), Hollow::fromPlus));
                helper.assertTrue(turned.size() == 1 && turned.get(0).isSinner(), "the Hollow a Plus turned into is none");
            } finally {
                plus.discard();
                QuincyArtsTests.discardAll(turned);
            }
        });
        helper.succeed();
    }

    /** A Hollow of this kind made as an arrival is, finalised as a spawn in the test's box, never put in the world. */
    private static Hollow arrival(GameTestHelper helper, HollowKind kind) {
        ServerLevel level = helper.getLevel();
        Hollow hollow = ModEntities.hollowType(kind).create(level);
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        hollow.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        EventHooks.finalizeMobSpawn(hollow, level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        return hollow;
    }
}
