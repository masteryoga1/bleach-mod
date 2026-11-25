package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.HellGate;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.ShriekerLeech;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Gate of Hell: which deaths open it, the body it holds and takes on its own clock, what a death gives
 * (once, at the death tick) when the Gate takes the Hollow, and the hold ending cleanly when the Gate or the body goes
 * first. Each test asks only about its own Hollows, its own Gates and its own souls, by object; each Gate stands inside
 * its own test's box.
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
            helper.assertTrue(SoulLogic.get(killer).hunt().purified() == 1 && done(killer, "purified"),
                    "the killer was not credited as for a purification");
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
     * plain Hollow cut down by an Asauchi dissolves as before; a sinner slain by an iron sword or a Quincy's Heilig Pfeil
     * falls as before, with no Gate. Up to 100 ticks for the ground, one tick after the blows, then they are let go.
     */
    @GameTest(template = "field", timeoutTicks = 200)
    public static void onlyASinnersPurifyingBlowOpensTheGate(GameTestHelper helper) {
        onceTicking(helper, () -> {
            SpiderHollow getsuga = sinner(helper, 3.0, 8.0);
            SpiderHollow plain = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), new Vec3(7.0, 2.0, 8.0)));
            SpiderHollow sword = sinner(helper, 10.0, 8.0);
            SpiderHollow arrow = sinner(helper, 13.0, 8.0);
            Player quincy = QuincyTests.archer(helper, QuincyTests.quincy(Bloodline.ECHT, 1, REIRYOKU), 13.0, 3.0);
            Player cutter = shinigami(helper, 7.0, 3.0, ModItems.ASAUCHI.get());
            Player swordsman = shinigami(helper, 10.0, 3.0, Items.IRON_SWORD);
            getsuga.hurt(blow(helper, GetsugaTensho.DAMAGE_TYPE, shinigami(helper, 3.0, 3.0, Items.AIR)), KILLING_BLOW);
            plain.hurt(helper.getLevel().damageSources().playerAttack(cutter), KILLING_BLOW);
            sword.hurt(helper.getLevel().damageSources().playerAttack(swordsman), KILLING_BLOW);
            arrow.hurt(blow(helper, HeiligPfeil.DAMAGE_TYPE, quincy), KILLING_BLOW);
            List<SpiderHollow> fallen = List.of(sword, arrow);
            helper.assertTrue(getsuga.isHellbound() && !getsuga.isPurifying(), "the Getsuga Tenshō did not open the Gate");
            helper.assertTrue(plain.isPurifying() && !plain.isHellbound(), "the plain Hollow did not dissolve as before");
            helper.assertTrue(fallen.stream().allMatch(spider -> spider.isDeadOrDying() && !spider.isHellbound()
                    && !spider.isPurifying()), "a sword's or an arrow's sinner was held or dissolved");
            HellGate gate = gateOf(helper, getsuga);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(HellGate.class, helper.getBounds().inflate(4.0),
                    other -> fallen.contains(other.body())).isEmpty(), "a fallen sinner has a Gate");
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(fallen.stream().allMatch(spider -> spider.deathTime >= 1) && getsuga.deathTime == 0,
                        "a fallen sinner did not tip over as before, or the Gate's did");
                gate.discard();
                List.of(getsuga, plain, sword, arrow).forEach(Entity::discard);
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
}
