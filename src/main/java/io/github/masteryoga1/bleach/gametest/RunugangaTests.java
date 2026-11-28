package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.Runuganga;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.HuecoMundoSpawns;
import io.github.masteryoga1.bleach.soul.RunugangaComing;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Runuganga: his body (T1), his fight (T2), his slump and re-forming (T3), his sinking away (T4), whom he
 * rises for and how often a look is made (T5), where and when, and never by a game test's server (T6), a bottle's splash
 * soaking him (T7), and his soaked sand kept from re-forming and a soaked body's death (T8). Every body is discarded as
 * its test ends; he stands past a field's top, so his tests in the field run in batches of their own, an awake body's
 * alone.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class RunugangaTests {
    private static final float EPSILON = 1.0E-3F;
    /**
     * T6's centre, held 3 about: x -240 to -129 and z -48 to 63, clear of Las Noches' wall and towers. Creatures tick
     * in x -208 to -161 and z -16 to 31: his sand, and past r 200 the open dunes.
     */
    private static final ChunkPos WHERE = new ChunkPos(-12, 0);
    /**
     * T7 and T8's batch, whose still bodies' sides lie 0.5 inside the 16-wide field: a bottle's splash reaches 4 past its
     * own width of 0.25, so 1.6 past the box, and with 5 blocks between columns and 6 between rows (StructureGridSpawner)
     * a neighbour's nearest body starts 5.5 past it.
     */
    private static final String WATER = "runuganga_water";

    private RunugangaTests() {
    }

    /** A still body of his standing at this spot of the field, his goals kept but never run, discarded as the test ends. */
    private static Runuganga still(GameTestHelper helper, double x, double z) {
        return kept(helper, QuincyTests.still(helper.spawn(ModEntities.RUNUGANGA.get(), new Vec3(x, 2.0, z))));
    }

    /**
     * He, or a bottle thrown at him, is discarded as the test ends, passed or failed: a hold of no chunks keeps it. One
     * added mid-test is safe, as a test's listeners run only as it passes or fails.
     */
    private static <T extends Entity> T kept(GameTestHelper helper, T made) {
        LasNochesHallsTests.Hold.hold(helper, helper.getLevel(), List.of(), 0, List.of()).placed().add(made);
        return made;
    }

    /** A bottle of water of this kind, thrown by this soul or by none, falling from this spot of the field. */
    private static ThrownPotion splash(GameTestHelper helper, @Nullable Player owner, Item kind, double x, double y,
                                       double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        ThrownPotion bottle = new ThrownPotion(helper.getLevel(), at.x, at.y, at.z);
        bottle.setItem(PotionContents.createItemStack(kind, Potions.WATER));
        bottle.setOwner(owner);
        bottle.setDeltaMovement(0.0, -0.5, 0.0);
        helper.getLevel().addFreshEntity(bottle);
        return kept(helper, bottle);
    }

    /** A joined soul in this state and mode, as strong as he is, here in the field: it ticks, and leaves at the end. */
    private static ServerPlayer joined(GameTestHelper helper, SoulState state, GameType mode, double x, double z) {
        ServerPlayer soul = TestPlayers.join(helper, mode, new Vec3(x, 2.0, z));
        soul.setData(SoulAttachments.SOUL, ShinigamiTests.soul(state, HollowKind.RUNUGANGA.reiatsu));
        return soul;
    }

    /** An awake body of his, not kept for good as the helper's spawn keeps one, at this spot of the field. */
    private static Runuganga awake(GameTestHelper helper, double x, double z) {
        Runuganga giant = ModEntities.RUNUGANGA.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
        giant.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(giant);
        return kept(helper, giant);
    }

    /** A mock soul in this state, by this mode's rules, at this spot of the field, in no world: aware unless Human. */
    private static Player soul(GameTestHelper helper, SoulState state, GameType mode, double x, double z) {
        Player soul = helper.makeMockPlayer(mode);
        soul.setData(SoulAttachments.SOUL, ShinigamiTests.soul(state, state == SoulState.HUMAN ? 0.0F : 100.0F));
        Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
        soul.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return soul;
    }

    /** A survival mock soul in this state standing here in Hueco Mundo: aware unless Human. */
    private static Player inHueco(ServerLevel hueco, SoulState state, double x, double y, double z) {
        return HuecoMundoTests.mockIn(hueco, new Vec3(x, y, z),
                ShinigamiTests.soul(state, state == SoulState.HUMAN ? 0.0F : 100.0F));
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
     * Three blows past his floor leave him on it, standing; next he slumps into sand that nothing aims at or harms
     * while he is dry, his bar hidden. After reform_seconds he rises with reform_share of his health and his bar,
     * unhurt till he stands three seconds on. {@code /kill} ends him rising or as sand, and a body killed before its
     * slump stays dead, never sand.
     */
    @GameTest(template = "field", skyAccess = true, batch = "runuganga", timeoutTicks = 300)
    public static void atHisFloorHeSlumpsAndReformsAndOnlyKillEndsHim(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.RUNUGANGA_REFORM_SECONDS.getDefault() == 5
                && BleachConfig.RUNUGANGA_REFORM_SHARE.getDefault() == 0.5, "his re-forming's config defaults changed");
        BossLedger ledger = BossLedger.get(helper.getLevel().getServer());
        ledger.reset(RunugangaComing.ID);
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
                        ledger.reset(RunugangaComing.ID);
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

    /**
     * Awake with no foe or intruder on his ground, he sinks away after ten seconds: discarded, never dead, so the ledger
     * keeps no wait, but he rests a minute; a body named or kept for good stays. A death keeps him away return_days and
     * sets no rest. While one is about, or a tear still carries one, a summon is refused; one with no player names none.
     */
    @GameTest(template = "field", skyAccess = true, batch = "runuganga_lonely", timeoutTicks = 400)
    public static void aloneHeSinksAwayAndOnlyADeathKeepsHimAway(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.RUNUGANGA_RETURN_DAYS.getDefault() == 3, "his return_days default changed");
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BossLedger ledger = BossLedger.get(server);
        ledger.reset(RunugangaComing.ID);
        RunugangaComing.rest(server, 0);
        // A tear not yet open drops nothing, and this one is gone before the world ticks.
        int before = RunugangaComing.BOSS.present(server);
        GargantaRift tear = GargantaRift.arrival(level, helper.absoluteVec(new Vec3(6.5, 8.0, 6.5)),
                level.getGameTime() + 100, List.of(HollowKind.RUNUGANGA), null, 1.0F);
        try {
            helper.assertTrue(level.addFreshEntity(tear) && RunugangaComing.BOSS.present(server) == before + 1
                    && RunugangaComing.BOSS.summonRefusal(server) != null,
                    "a tear carrying him was not counted, or let a summon by");
        } finally {
            tear.discard();
        }
        Runuganga lonely = awake(helper, 10.0, 10.0), named = awake(helper, 10.0, 3.0), dying = still(helper, 3.0, 3.0);
        named.setCustomName(Component.literal("Named"));
        Runuganga keptForGood = kept(helper, helper.spawn(ModEntities.RUNUGANGA.get(), new Vec3(3.0, 2.0, 10.0)));
        Component nobody = RunugangaComing.BOSS.summon(server, null);
        helper.assertTrue(RunugangaComing.BOSS.summonRefusal(server) != null
                        && GateTests.says(nobody, "commands.bleach.boss.summon.no_player", -1),
                "a summon was not refused while he was about, or with no player found one");
        helper.startSequence()
                .thenExecuteAfter(Runuganga.LONELY_TICKS - 40, () -> helper.assertTrue(lonely.standing()
                        && !lonely.isRemoved(), "he sank away before ten seconds alone"))
                .thenWaitUntil(() -> GateTests.patiently(helper, lonely.isRemoved(), "alone, he never sank away"))
                .thenExecute(() -> {
                    try {
                        long now = BossLedger.now(server);
                        helper.assertTrue(lonely.getRemovalReason() == Entity.RemovalReason.DISCARDED
                                        && !lonely.isDeadOrDying() && ledger.mayCome(RunugangaComing.ID, now)
                                        && RunugangaComing.resting(server),
                                "he went otherwise than sunk away, the ledger keeps him away, or he does not rest");
                        helper.assertTrue(named.standing() && !named.isRemoved() && keptForGood.standing()
                                && !keptForGood.isRemoved(), "a body named or kept for good sank away");
                        RunugangaComing.rest(server, 0);
                        dying.kill();
                        long wait = ledger.waitLeft(RunugangaComing.ID, now);
                        helper.assertTrue(wait == 3 * BossLedger.TICKS_PER_DAY && !RunugangaComing.resting(server),
                                "a death kept him away " + wait + " ticks, or set his rest");
                    } finally {
                        ledger.reset(RunugangaComing.ID);
                        RunugangaComing.rest(server, 0);
                    }
                })
                .thenSucceed();
    }

    /**
     * A Shinigami on his sand draws him; no Arrancar, Hollow, unaware or creative soul does, nor one off his sand or
     * under it. A landing in Hueco Mundo is noted, never elsewhere, and a second within its grace keeps the first; for
     * landing_grace_seconds the soul neither draws him nor is fought. His row is the ledger's.
     */
    @GameTest(template = "empty")
    public static void anIntruderOnHisSandDrawsHimOnceItsGraceIsOver(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.RUNUGANGA_LANDING_GRACE_SECONDS.getDefault() == 30, "his grace's default changed");
        ServerLevel hueco = LasNochesTests.hueco(helper);
        Player shinigami = inHueco(hueco, SoulState.SHINIGAMI, 150.5, 97.0, 0.5);
        helper.assertTrue(RunugangaComing.draws(shinigami), "a Shinigami on his sand did not draw him");
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, 100.0F));
        creative.moveTo(150.5, 97.0, 0.5);
        Map.of("an Arrancar", inHueco(hueco, SoulState.ARRANCAR, 150.5, 97.0, 0.5),
                        "a Hollow", inHueco(hueco, SoulState.HOLLOW, 150.5, 97.0, 0.5),
                        "an unaware soul", inHueco(hueco, SoulState.HUMAN, 150.5, 97.0, 0.5), "a soul in creative", creative,
                        "a soul inside the wall", inHueco(hueco, SoulState.SHINIGAMI, 123.5, 97.0, 0.5),
                        "a soul on the dunes", inHueco(hueco, SoulState.SHINIGAMI, 200.5, 97.0, 0.5),
                        "a soul in the Forest of Menos", inHueco(hueco, SoulState.SHINIGAMI, 150.5, 79.0, 0.5))
                .forEach((which, soul) -> helper.assertFalse(RunugangaComing.draws(soul), which + " drew him"));

        long now = hueco.getGameTime();
        Player landed = inHueco(hueco, SoulState.SHINIGAMI, 150.5, 97.0, 0.5);
        Player elsewhere = helper.makeMockPlayer(GameType.SURVIVAL);
        RunugangaComing.landed(landed);
        RunugangaComing.landed(elsewhere);
        helper.assertTrue(landed.getData(SoulAttachments.HUECO_LANDING) == now
                && !elsewhere.hasData(SoulAttachments.HUECO_LANDING), "a landing was noted out of Hueco Mundo, or not in it");
        helper.assertTrue(!RunugangaComing.draws(landed) && !Runuganga.intrudes(landed)
                        && RunugangaComing.inLandingGrace(landed, now + 599)
                        && !RunugangaComing.inLandingGrace(landed, now + 600)
                        && !RunugangaComing.inLandingGrace(landed, now - 1)
                        && !RunugangaComing.inLandingGrace(shinigami, now),
                "the grace is not the 30 seconds after a landing");
        KidoTests.withConfig(BleachConfig.RUNUGANGA_LANDING_GRACE_SECONDS, 0, () -> helper.assertTrue(
                RunugangaComing.draws(landed), "with no grace a soul just landed did not draw him"));
        landed.setData(SoulAttachments.HUECO_LANDING, now - 100);
        RunugangaComing.landed(landed);
        long first = landed.getData(SoulAttachments.HUECO_LANDING);
        landed.setData(SoulAttachments.HUECO_LANDING, now - 600);
        RunugangaComing.landed(landed);
        helper.assertTrue(first == now - 100 && landed.getData(SoulAttachments.HUECO_LANDING) == now,
                "a landing within the grace lengthened it, or one after it was not noted");

        int every = RunugangaComing.LOOK_TICKS;
        helper.assertTrue(every == 100 && RunugangaComing.lookNow(0) && RunugangaComing.lookNow(every)
                        && !RunugangaComing.lookNow(every - 1) && !RunugangaComing.lookNow(every + 1)
                        && !RunugangaComing.looksBy(hueco.getServer()),
                "a look for him is not made every five seconds, or a game test's server makes one");
        helper.assertTrue(BossLedger.boss(RunugangaComing.ID) == RunugangaComing.BOSS
                        && RunugangaComing.BOSS.returnDays() == BleachConfig.get(BleachConfig.RUNUGANGA_RETURN_DAYS),
                "his row is not the ledger's");
        helper.succeed();
    }

    /**
     * He rises only on his sand, never for an Arrancar alone: 8 to 16 across from the intruder, facing it, rising, in a
     * ticking chunk. No second comes while he is about, nor while the ledger keeps him away or he rests, on Peaceful or
     * with mob spawning off. Looks from the ticking chunks' edge bring him only into them, and none loads a chunk. His
     * ground there ends at the wall and under the sand.
     */
    @GameTest(template = "empty", batch = "runuganga_coming", timeoutTicks = 900)
    public static void heRisesOnHisSandOnlyAndOneAtATime(GameTestHelper helper) {
        ServerLevel hueco = LasNochesTests.hueco(helper);
        MinecraftServer server = hueco.getServer();
        BossLedger ledger = BossLedger.get(server);
        LasNochesHallsTests.Hold hold = VastoLordeComingTests.held(helper, hueco, WHERE, 3);
        helper.startSequence()
                .thenWaitUntil(() -> VastoLordeComingTests.ready(helper, hueco, WHERE, 3))
                .thenExecute(() -> VastoLordeComingTests.open(hueco, 0, () -> {
                    try {
                        ledger.reset(RunugangaComing.ID);
                        RunugangaComing.rest(server, 0);
                        risesWhereAndWhen(helper, hueco, hold);
                    } finally {
                        ledger.reset(RunugangaComing.ID);
                        RunugangaComing.rest(server, 0);
                    }
                }))
                .thenSucceed();
    }

    /** T6 once its world is open, his ledger row clear and his rest over. */
    private static void risesWhereAndWhen(GameTestHelper helper, ServerLevel hueco, LasNochesHallsTests.Hold hold) {
        MinecraftServer server = hueco.getServer();
        BossLedger ledger = BossLedger.get(server);
        BlockPos spot = VastoLordeComingTests.sand(helper, hueco, -170, 0, top -> true);
        // The last column of his sand, r 199.5, beside open dune at r 200.5 that the sand alone refuses.
        BlockPos edge = VastoLordeComingTests.sand(helper, hueco, -200, 0, top -> HuecoMundoSpawns
                .onHollowGround(hueco, top.west()) && hueco.getBlockState(top.west()).isAir());
        helper.assertTrue(RunugangaComing.fits(hueco, spot) && !RunugangaComing.fits(hueco, spot.above(3))
                        && !RunugangaComing.fits(hueco, spot.below()) && RunugangaComing.fits(hueco, edge)
                        && !RunugangaComing.fits(hueco, edge.west()),
                "he would not rise on his sand at " + spot.toShortString() + " and " + edge.toShortString()
                        + ", or would off it, in the air or in the ground");
        double x = spot.getX() + 0.5, y = spot.getY(), z = spot.getZ() + 0.5;
        Player soul = inHueco(hueco, SoulState.SHINIGAMI, x, y, z);
        Player arrancar = inHueco(hueco, SoulState.ARRANCAR, x, y, z);
        helper.assertTrue(noneRose(hold, hueco, List.of(arrancar)), "he rose for an Arrancar");
        Runuganga made = rolled(hold, hueco, List.of(arrancar, soul));
        helper.assertTrue(made != null, "he never rose for an intruder on his sand at " + spot.toShortString());
        double across = Math.hypot(made.getX() - soul.getX(), made.getZ() - soul.getZ());
        helper.assertTrue(across > RunugangaComing.NEAREST - 1.0 && across < RunugangaComing.FURTHEST + 1.0
                        && Runuganga.onTheSand(made.getBlockX(), made.getBlockZ())
                        && hueco.isPositionEntityTicking(made.blockPosition())
                        && made.getPose() == Pose.EMERGING && RunugangaComing.BOSS.present(server) == 1
                        && made.getLookAngle().dot(soul.position().subtract(made.position()).normalize()) > 0.9,
                "he rose " + across + " across at " + made.position() + ", or otherwise than rising at the soul");
        helper.assertTrue(noneRose(hold, hueco, List.of(soul)), "a second rose while he was about");
        made.discard();
        // A look on a tick one falls on, for a soul a roll has just brought him to: a game test's server makes none.
        Runuganga looked = kept(hold, RunugangaComing.look(server, hueco, List.of(soul), 0));
        TravelTests.discard(looked);
        helper.assertTrue(looked == null, "a game test's server looked for him");
        ledger.markAway(RunugangaComing.ID, BossLedger.now(server), 1, List.of());
        boolean away = noneRose(hold, hueco, List.of(soul));
        ledger.reset(RunugangaComing.ID);
        RunugangaComing.rest(server, RunugangaComing.REST_TICKS);
        boolean resting = noneRose(hold, hueco, List.of(soul));
        RunugangaComing.rest(server, 0);
        // Both put back by open, should a look throw.
        GameRules.BooleanValue spawning = hueco.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        server.setDifficulty(Difficulty.PEACEFUL, true);
        boolean peaceful = noneRose(hold, hueco, List.of(soul));
        server.setDifficulty(Difficulty.NORMAL, true);
        spawning.set(false, server);
        boolean idle = noneRose(hold, hueco, List.of(soul));
        spawning.set(true, server);
        helper.assertTrue(away && resting && peaceful && idle, "he rose while the ledger kept him away (" + !away
                + "), while he rested (" + !resting + "), on Peaceful (" + !peaceful + ") or with mob spawning off ("
                + !idle + ")");

        // A soul 4 inside the ticking chunks' east edge, and one 4 inside the held chunks' south edge, by the chunk past.
        Player inside = inHueco(hueco, SoulState.SHINIGAMI, -164.5, y, 8.5);
        Player outside = inHueco(hueco, SoulState.SHINIGAMI, -184.5, y, 60.5);
        ChunkPos beyond = new ChunkPos(WHERE.x, WHERE.z + 4);
        helper.assertFalse(hueco.hasChunk(beyond.x, beyond.z), "the chunk past those held was loaded already");
        int rose = 0;
        for (int i = 0; i < 24; i++) {
            Runuganga there = kept(hold, RunugangaComing.comeNear(hueco, hueco.getRandom(), i % 2 == 0 ? inside : outside));
            helper.assertTrue(there == null || hueco.isPositionEntityTicking(there.blockPosition()),
                    "a look brought him where creatures do not tick");
            rose += there == null ? 0 : 1;
            TravelTests.discard(there);
        }
        helper.assertTrue(rose >= 3 && !hueco.hasChunk(beyond.x, beyond.z), rose
                + " of 12 looks at the ticking edge brought him, or a look loaded the chunk past those held");

        Runuganga placed = QuincyTests.still(ModEntities.RUNUGANGA.get().create(hueco));
        placed.moveTo(-162.5, y, 0.5);
        hold.add(placed);
        helper.assertTrue(!placed.onGround(new Vec3(-123.5, y, 0.5)) && placed.onGround(new Vec3(-125.5, y, 0.5))
                        && !placed.onGround(new Vec3(-125.5, HuecoMundoPlan.CAVERN_CEILING_Y - 0.5, 0.5)),
                "his ground in Hueco Mundo reaches inside the wall or under the sand, or not to the wall");
    }

    /**
     * A bottle of water a soul who sees him throws bursts on his head and soaks him: his bar blue, no floor, a blow's harm
     * twice over. An unaware soul's goes through him, and a blow leaves him as dry as it found him; a lingering one
     * bursting on the sand 2.5 blocks off his side soaks him. The soak runs soak_seconds from the splash, then he is dry,
     * white and floored again.
     */
    @GameTest(template = "field", skyAccess = true, batch = WATER, timeoutTicks = 500)
    public static void waterSplashedOnHimSoaksHimUnlessAnUnawareSoulThrewIt(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.RUNUGANGA_SOAK_SECONDS.getDefault() == 10
                && BleachConfig.RUNUGANGA_SOAKED_HARM.getDefault() == 2.0, "his soak's config defaults changed");
        Runuganga wet = still(helper, 2.5, 8.0), dry = still(helper, 13.5, 8.0);
        Player aware = soul(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 2.5, 1.5);
        Player unaware = soul(helper, SoulState.HUMAN, GameType.SURVIVAL, 13.5, 1.5);
        ThrownPotion[] bottles = new ThrownPotion[2];
        long[] burst = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, wet.tickCount > 0 && dry.tickCount > 0,
                        "his bodies have not taken their first steps"))
                .thenExecute(() -> {
                    // A foot above each head, which is 14 up: both burst on it in the same tick, the second or third.
                    bottles[0] = splash(helper, aware, Items.SPLASH_POTION, 2.5, 15.0, 8.0);
                    bottles[1] = splash(helper, unaware, Items.SPLASH_POTION, 13.5, 15.0, 8.0);
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, bottles[0].isRemoved() && bottles[1].isRemoved(),
                        "a bottle never burst on his head"))
                .thenExecute(() -> {
                    burst[0] = helper.getTick();
                    helper.assertTrue(wet.soaked() && wet.floor() == 0.0F
                                    && wet.bossBar().event().getColor() == BossEvent.BossBarColor.BLUE,
                            "a bottle of water burst on his head left him dry, with his floor or his white bar");
                    helper.assertTrue(!dry.soaked() && dry.floor() == Runuganga.FLOOR
                                    && dry.bossBar().event().getColor() == BossEvent.BossBarColor.WHITE,
                            "an unaware soul's bottle soaked him");
                    DamageSources damage = helper.getLevel().damageSources();
                    wet.hurt(damage.generic(), 10.0F);
                    dry.hurt(damage.generic(), 10.0F);
                    float wetLost = wet.getMaxHealth() - wet.getHealth(), dryLost = dry.getMaxHealth() - dry.getHealth();
                    helper.assertTrue(near(wetLost, 20.0) && near(dryLost, 10.0),
                            "a blow of 10 took " + wetLost + " soaked and " + dryLost + " dry");
                    helper.assertTrue(!dry.soaked() && dry.floor() == Runuganga.FLOOR
                                    && dry.bossBar().event().getColor() == BossEvent.BossBarColor.WHITE,
                            "a dry blow soaked him");
                    splash(helper, null, Items.LINGERING_POTION, 13.5, 3.0, 3.5);
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, dry.soaked(),
                        "a lingering bottle bursting by him never soaked him"))
                .thenWaitUntil(() -> GateTests.patiently(helper, !wet.soaked(), "his soak never ran out"))
                .thenExecute(() -> {
                    // He is soaked in the bottle's tick and counts down in his own, so he dries 200 ticks on.
                    long waited = helper.getTick() - burst[0];
                    helper.assertTrue(waited >= 198 && waited <= 202 && wet.floor() == Runuganga.FLOOR
                                    && wet.bossBar().event().getColor() == BossEvent.BossBarColor.WHITE,
                            "he dried " + waited + " ticks after the splash, or without his floor or his white bar");
                })
                .thenSucceed();
    }

    /**
     * Soaked, a blow ends him outright where he stands, never slumping, and a blow of no one's earns no one anything.
     * Brought to his floor dry by one soul, he slumps; another's bottle of water falls through his sand, bursts on the
     * ground and soaks it, his head out of it and his bar shown, and past reform_seconds it has not re-formed. That soul's
     * Asauchi on his head purifies him, as it would any Hollow: it alone earns the advancement and is the ledger's victor,
     * and he crumbles from where he lay. His ledger row is reset after each death.
     */
    @GameTest(template = "field", skyAccess = true, batch = WATER, timeoutTicks = 400)
    public static void soakedABlowEndsHimAndEarnsItsDealerTheAdvancement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BossLedger ledger = BossLedger.get(server);
        ledger.reset(RunugangaComing.ID);
        ServerPlayer striker = joined(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 8.0, 2.5);
        ServerPlayer finisher = joined(helper, SoulState.SHINIGAMI, GameType.SURVIVAL, 8.0, 13.5);
        HollowTests.arm(finisher, ModItems.ASAUCHI.get());
        Runuganga standing = still(helper, 2.5, 8.0), mound = still(helper, 13.5, 8.0);
        String fallen = Milestones.RUNUGANGA;
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, standing.tickCount > 0 && mound.tickCount > 0
                        && striker.tickCount > 0 && finisher.tickCount > 0, "his bodies or the souls have not ticked"))
                .thenExecute(() -> {
                    DamageSources damage = helper.getLevel().damageSources();
                    try {
                        standing.soak();
                        standing.hurt(damage.generic(), 1_000.0F);
                        helper.assertTrue(standing.isDeadOrDying() && standing.getPose() == Pose.DYING,
                                "soaked, a blow left him " + standing.getPose() + " with " + standing.getHealth());
                        helper.assertFalse(DangaiTests.earned(striker, fallen) || DangaiTests.earned(finisher, fallen),
                                "a death no soul dealt earned the advancement");
                    } finally {
                        ledger.reset(RunugangaComing.ID);
                    }
                    for (int i = 0; i < 3; i++) {
                        mound.invulnerableTime = 0;
                        mound.hurt(damage.playerAttack(striker), 1_000.0F);
                    }
                    helper.assertTrue(near(mound.getHealth(), Runuganga.FLOOR), "dry, three blows took him past his floor");
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, mound.getPose() == Pose.DIGGING
                        && mound.sunk(0.0F) >= 0.99F, "he never slumped and sank into his sand"))
                .thenExecute(() -> {
                    helper.assertFalse(mound.isPickable(), "dry, his sand can be aimed at");
                    splash(helper, finisher, Items.SPLASH_POTION, 13.5, 8.0, 8.0);
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, mound.soaked(),
                        "a bottle that fell through his sand never soaked it"))
                .thenExecute(() -> helper.assertTrue(near(mound.sunk(0.0F), Runuganga.WET_SUNK) && mound.head().isPickable()
                                && mound.floor() == 0.0F && !mound.bossBar().hidden(),
                        "soaked, his head stayed under the sand, he kept a floor or his bar stayed hidden"))
                // Soaked some 30 ticks after he slumped: 80 more pass his 100 ticks of reform_seconds.
                .thenExecuteAfter(80, () -> helper.assertTrue(mound.getPose() == Pose.DIGGING && mound.soaked(),
                        "soaked, his sand re-formed past reform_seconds"))
                .thenExecute(() -> {
                    try {
                        int purified = SoulLogic.get(finisher).hunt().purified();
                        DamageSource blow = helper.getLevel().damageSources().playerAttack(finisher);
                        mound.invulnerableTime = 0;
                        helper.assertTrue(mound.head().hurt(blow, 10.0F) && mound.isDeadOrDying(),
                                "soaked, a blow on his head did not end him");
                        helper.assertTrue(mound.isPurifying() && SoulLogic.get(finisher).hunt().purified() == purified + 1,
                                "the Asauchi did not purify him, or its soul's count did not grow");
                        helper.assertTrue(DangaiTests.earned(finisher, fallen) && !DangaiTests.earned(striker, fallen),
                                "the advancement went to the soul that struck him dry, or not to the one that ended him");
                        long now = BossLedger.now(server);
                        List<UUID> victors = List.copyOf(ledger.beatenBy(RunugangaComing.ID));
                        helper.assertTrue(ledger.waitLeft(RunugangaComing.ID, now) == 3 * BossLedger.TICKS_PER_DAY
                                        && victors.equals(List.of(finisher.getUUID())),
                                "the ledger did not keep him away 3 days, beaten by the soul that ended him: " + victors);
                    } finally {
                        ledger.reset(RunugangaComing.ID);
                        // His deaths drop nothing; should one ever, none of it is left lying in the field.
                        QuincyArtsTests.clearItems(helper);
                    }
                })
                .thenExecuteAfter(1, () -> helper.assertTrue(mound.sunk(0.0F) > Runuganga.WET_SUNK
                        && mound.sunk(0.0F) < 1.0F, "purified, he crumbled from " + mound.sunk(0.0F) + ", not where he lay"))
                .thenSucceed();
    }

    /** A roll for these souls, tried three times in case every column of a look missed; what rose, kept. */
    @Nullable
    private static Runuganga rolled(LasNochesHallsTests.Hold hold, ServerLevel hueco, List<Player> souls) {
        Runuganga made = null;
        for (int i = 0; made == null && i < 3; i++) {
            made = kept(hold, RunugangaComing.roll(hueco, hueco.getRandom(), souls));
        }
        return made;
    }

    /** Whether such a roll brought none; one that rose is discarded at once, so it keeps away none of the next. */
    private static boolean noneRose(LasNochesHallsTests.Hold hold, ServerLevel hueco, List<Player> souls) {
        Runuganga made = rolled(hold, hueco, souls);
        TravelTests.discard(made);
        return made == null;
    }

    /** What rose, kept for the hold to discard as the test ends, pass or fail. */
    @Nullable
    private static Runuganga kept(LasNochesHallsTests.Hold hold, @Nullable Runuganga made) {
        if (made != null) {
            hold.placed().add(made);
        }
        return made;
    }
}
