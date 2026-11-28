package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.Runuganga;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Runuganga: his body (T1), his fight (T2), and his slump and re-forming (T3). Every body is discarded as its
 * test ends; he stands past a field's top, so his tests run in batches of their own, an awake body's alone.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class RunugangaTests {
    private static final float EPSILON = 1.0E-3F;
    /** His row in the boss ledger, once he has one, which notes a death by {@code /kill}. */
    private static final String LEDGER_ROW = "runuganga";

    private RunugangaTests() {
    }

    /** A still body of his standing at this spot of the field, his goals kept but never run, discarded as the test ends. */
    private static Runuganga still(GameTestHelper helper, double x, double z) {
        return kept(helper, QuincyTests.still(helper.spawn(ModEntities.RUNUGANGA.get(), new Vec3(x, 2.0, z))));
    }

    /** He is discarded as the test ends, passed or failed: a hold of no chunks keeps him for that. */
    private static Runuganga kept(GameTestHelper helper, Runuganga giant) {
        LasNochesHallsTests.Hold.hold(helper, helper.getLevel(), List.of(), 0, List.of()).placed().add(giant);
        return giant;
    }

    /** A mock soul in this state, by this mode's rules, at this spot of the field, in no world: aware unless Human. */
    private static Player soul(GameTestHelper helper, SoulState state, GameType mode, double x, double z) {
        Player soul = helper.makeMockPlayer(mode);
        soul.setData(SoulAttachments.SOUL, ShinigamiTests.soul(state, state == SoulState.HUMAN ? 0.0F : 100.0F));
        Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
        soul.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return soul;
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    /**
     * A Hollow and a spirit 4 by 12, never saved, summoned by command, alone through a tear and never by chance or a
     * sinner, not frightened by released reiatsu, burnt, pushed, portalled or despawned, with the config's 300 health over
     * a stale base and his kind's reiatsu. His body part stands 4 to 9 blocks up, his head 9 to 12 with his mask in it. A
     * lookup at his head finds him alone by the head part, as a plain one there may miss his feet's entity section; a blow
     * on the head part reaching him is what fails if the parts stop forwarding.
     */
    @GameTest(template = "field", skyAccess = true, batch = "runuganga", timeoutTicks = 40)
    public static void heIsAGiantInTwoParts(GameTestHelper helper) {
        HollowKind kind = HollowKind.RUNUGANGA;
        EntityType<Runuganga> type = ModEntities.RUNUGANGA.get();
        EntityDimensions size = type.getDimensions();
        helper.assertTrue(ModEntities.hollowType(kind) == type && type.is(SoulLogic.SPIRITS) && type.is(BleachTags.HOLLOWS)
                        && type.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER) && !type.canSerialize() && type.canSummon()
                        && near(size.width(), Runuganga.WIDTH) && near(size.height(), Runuganga.HEIGHT),
                "bleach:runuganga is registered wrongly, or is " + size);
        helper.assertTrue(kind.arrivalWeight == 0 && !HollowLogic.arrivalPool(10_000.0F).contains(kind) && !kind.isMenos()
                        && !kind.isVasto() && kind.comesByTear()
                        && HollowLogic.groupSize(kind, 10_000.0F, Integer.MAX_VALUE, 0.0) == 1,
                "Runuganga comes by chance, by no tear or not alone, or is a Menos");
        KidoTests.withConfig(BleachConfig.SINNER_SHARE, 1.0,
                () -> helper.assertFalse(Hollow.rollsSinner(kind, 0.0), "Runuganga may be a sinner"));
        helper.assertTrue(BleachConfig.RUNUGANGA_HEALTH.getDefault() == 300.0, "his health's default changed");
        Runuganga giant = still(helper, 8.0, 8.0);
        helper.assertFalse(ShriekerTests.frightens(giant), "released reiatsu frightens him");
        helper.assertTrue(type.fireImmune() && !giant.isPushable() && !giant.canUsePortal(false)
                && !giant.removeWhenFarAway(1_000.0), "he burns, is pushed, takes a portal or despawns far from players");
        // Stale strength, as a summon with data would leave: his first tick puts the config's in.
        giant.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
        // His parts are placed in his AI step, which runs for a NoAI mob too.
        helper.runAfterDelay(1, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(near(giant.getMaxHealth(), 300.0) && near(giant.getHealth(), 300.0)
                            && near(SoulLogic.get(giant).maxReiryoku(), kind.reiatsu),
                    "he has " + giant.getHealth() + " of " + giant.getMaxHealth() + " health, or the wrong reiatsu");
            AABB body = giant.getParts()[Runuganga.PART_BODY].getBoundingBox(), head = giant.head().getBoundingBox();
            double feet = giant.getY();
            helper.assertTrue(near(body.minY, feet + 4.0) && near(body.maxY, feet + 9.0) && near(body.getXsize(), 4.0)
                            && near(head.minY, feet + 9.0) && near(head.maxY, feet + 12.0) && near(head.getXsize(), 3.0)
                            && head.inflate(0.01).contains(HollowMask.maskCenter(giant)),
                    "his parts stand at " + body + " and " + head + ", or his mask is off his head");
            List<LivingEntity> found = SpiritProjectiles.creaturesIn(level, AABB.ofSize(head.getCenter(), 0.5, 0.5, 0.5),
                    entity -> true);
            helper.assertTrue(found.equals(List.of(giant)), "the creatures at his head were " + found);
            helper.assertTrue(giant.head().hurt(level.damageSources().generic(), 10.0F)
                    && giant.getHealth() < giant.getMaxHealth(), "a blow on his head did not reach him");
            helper.succeed();
        });
    }

    /**
     * He fights an intruder on his ground and whoever struck him, no Hollow, Arrancar, unaware, creative or far soul: his
     * ground reaches 48 across, so a soul 46 off is on it and one 49 off is not, and his sand is r 124 to 200. In reach, an
     * intruder sees his forearms rise and a second later takes his blow.
     */
    @GameTest(template = "field", skyAccess = true, batch = "runuganga_fight", timeoutTicks = 200)
    public static void heFightsIntrudersAndStrikesAfterAWindUp(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.RUNUGANGA_DAMAGE.getDefault() == 12.0, "his blow's default changed");
        helper.assertTrue(Runuganga.onTheSand(124, 0) && !Runuganga.onTheSand(123, 0) && Runuganga.onTheSand(0, -200)
                && Runuganga.onTheSand(199, 0) && !Runuganga.onTheSand(200, 0), "his sand is not r 124 to 200");
        Runuganga idle = still(helper, 3.0, 3.0);
        Player shinigami = soul(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 6.0, 3.0);
        Player hollow = soul(helper, SoulState.HOLLOW, GameType.SURVIVAL, 6.0, 3.0);
        helper.assertTrue(Runuganga.intrudes(shinigami) && idle.canAttack(shinigami)
                        && idle.canAttack(soul(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 3.0, 49.0)),
                "he would not fight an intruder beside him or 46 blocks off");
        // Every Hollow spares these four by its own rules too, so whether each intrudes is asked as well.
        Map.of("a Hollow", hollow, "an Arrancar", soul(helper, SoulState.ARRANCAR, GameType.SURVIVAL, 6.0, 3.0),
                        "an unaware soul", soul(helper, SoulState.HUMAN, GameType.SURVIVAL, 6.0, 3.0),
                        "a soul in creative mode", soul(helper, SoulState.SHINIGAMI, GameType.CREATIVE, 6.0, 3.0))
                .forEach((which, soul) -> helper.assertFalse(Runuganga.intrudes(soul) || idle.canAttack(soul),
                        "he would fight " + which));
        helper.assertFalse(idle.canAttack(soul(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 3.0, 52.0)),
                "he would fight a soul past his ground");
        idle.setLastHurtByMob(hollow);
        helper.assertTrue(idle.canAttack(hollow), "he would not strike back at a Hollow that struck him");

        Runuganga giant = kept(helper, helper.spawn(ModEntities.RUNUGANGA.get(), new Vec3(10.0, 2.0, 10.0)));
        Player intruder = soul(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 14.0, 10.0);
        giant.setTarget(intruder);
        long[] raised = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, giant.armsUp(), "he never raised his forearms"))
                .thenExecute(() -> {
                    raised[0] = helper.getTick();
                    helper.assertTrue(near(intruder.getHealth(), intruder.getMaxHealth()), "he struck before his wind-up");
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, intruder.getHealth() < intruder.getMaxHealth(),
                        "his blow never fell"))
                .thenExecute(() -> {
                    // His goal counts the wind-up down once in each of his ticks, and these steps run after the world's
                    // in the same server tick, so the blow is seen 20 ticks after the raise: two of room below, four above.
                    long waited = helper.getTick() - raised[0];
                    helper.assertTrue(waited >= 18 && waited <= 24 && !giant.armsUp(),
                            "his blow fell " + waited + " ticks after his forearms rose, or they stayed up");
                })
                .thenSucceed();
    }

    /**
     * Three blows past his floor leave him on it, standing; next he slumps into sand that nothing aims at or harms, his bar
     * hidden. After reform_seconds he rises with reform_share of his health and his bar, unhurt till he stands three
     * seconds on. {@code /kill} ends him rising or as sand, and a body killed before its slump stays dead, never sand.
     */
    @GameTest(template = "field", skyAccess = true, batch = "runuganga", timeoutTicks = 300)
    public static void atHisFloorHeSlumpsAndReformsAndOnlyKillEndsHim(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.RUNUGANGA_REFORM_SECONDS.getDefault() == 5
                && BleachConfig.RUNUGANGA_REFORM_SHARE.getDefault() == 0.5, "his re-forming's config defaults changed");
        BossLedger ledger = BossLedger.get(helper.getLevel().getServer());
        ledger.reset(LEDGER_ROW);
        Runuganga giant = still(helper, 8.0, 8.0), rising = still(helper, 2.5, 8.0), sand = still(helper, 13.5, 8.0);
        Player soul = soul(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 8.0, 14.0);
        // As strong as he is, so no reiatsu cut applies: the blows of 1,000 test his floor alone.
        SoulLogic.get(soul).setMaxReiryoku(HollowKind.RUNUGANGA.reiatsu);
        long[] slumped = new long[1], emerged = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, giant.tickCount > 0 && rising.tickCount > 0
                        && sand.tickCount > 0, "his bodies have not taken their first steps"))
                .thenExecute(() -> {
                    DamageSources damage = helper.getLevel().damageSources();
                    try {
                        for (int i = 0; i < 3; i++) {
                            giant.invulnerableTime = 0;
                            giant.hurt(damage.playerAttack(soul), 1_000.0F);
                        }
                        helper.assertTrue(giant.isAlive() && near(giant.getHealth(), Runuganga.FLOOR) && giant.standing(),
                                "three blows past his floor left him at " + giant.getHealth() + ", " + giant.getPose());
                        // A blow of no one's leaves a slump for its next tick, which /kill comes before, crediting none.
                        sand.hurt(damage.generic(), 1_000.0F);
                        rising.rise();
                        sand.slump();
                        rising.kill();
                        sand.kill();
                        helper.assertTrue(rising.isDeadOrDying() && sand.isDeadOrDying(),
                                "/kill left him alive rising or as sand");
                    } finally {
                        ledger.reset(LEDGER_ROW);
                    }
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, giant.getPose() == Pose.DIGGING, "he never slumped"))
                .thenExecute(() -> {
                    slumped[0] = helper.getTick();
                    giant.invulnerableTime = 0;
                    DamageSource blow = helper.getLevel().damageSources().playerAttack(soul);
                    helper.assertTrue(!giant.isPickable() && !giant.head().isPickable() && !giant.head().hurt(blow, 10.0F)
                                    && giant.bossBar().hidden() && sand.getPose() == Pose.DYING,
                            "his sand can be aimed at or struck, his bar shows, or a dying body slumped");
                })
                .thenExecuteAfter(Runuganga.SINK_TICKS, () -> helper.assertTrue(giant.sunk(0.0F) >= 0.99F,
                        "he never sank into his sand"))
                .thenWaitUntil(() -> GateTests.patiently(helper, giant.getPose() == Pose.EMERGING, "he never rose again"))
                .thenExecute(() -> {
                    emerged[0] = helper.getTick();
                    long waited = emerged[0] - slumped[0];
                    helper.assertTrue(waited >= 99 && waited <= 105 && near(giant.getHealth(), giant.getMaxHealth() * 0.5)
                                    && !giant.bossBar().hidden() && !giant.isPickable(), "he rose after " + waited
                                    + " ticks with " + giant.getHealth() + " health, or his bar hidden, or to be aimed at");
                    giant.invulnerableTime = 0;
                    DamageSource blow = helper.getLevel().damageSources().playerAttack(soul);
                    helper.assertTrue(!giant.hurt(blow, 10.0F) && !giant.head().hurt(blow, 10.0F)
                                    && near(giant.getHealth(), giant.getMaxHealth() * 0.5),
                            "a blow on him or his head landed as he rose");
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, giant.standing() && giant.isPickable()
                        && giant.sunk(0.0F) == 0.0F, "he never stood again whole"))
                .thenExecute(() -> {
                    // His pose changes in his own tick and these steps run after the world's in the same server tick, so he
                    // is seen slumping, rising and standing on the ticks he does, 100 and then 60 apart: a tick or more of
                    // room either way.
                    long rose = helper.getTick() - emerged[0];
                    helper.assertTrue(rose >= 58 && rose <= 62, "he stood " + rose + " ticks after he began to rise");
                })
                .thenSucceed();
    }
}
