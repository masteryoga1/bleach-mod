package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.FieldTrial;
import io.github.masteryoga1.bleach.soul.FieldTrialEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Academy's field trial: the practice Hollows, the hunt and its pass, each failure, the day's wait and the
 * gates home each end owes, the saved record, {@code /bleach trial}, the instructor's offer and his gates there and home,
 * graduation's need of the pass, and a real Hollow breaking in. The death, change of world and logout they post run every
 * listener on the joined players, harmlessly.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class FieldTrialTests {
    private static final String KEY = "bleach:field_trial";
    private static final String TAG = "field_trial_command";
    /** How far about the middle of the practice Hollows' test its ground must tick entities before any is placed. */
    private static final double PRACTICE_GROUND = 6.0;
    /** How far about the student the ground must tick for practice Hollows 10 to 20 blocks off. */
    private static final double REACH = 24.0;
    /** The same for a Garganta 12 to 24 blocks off. */
    private static final double BREAK_IN_REACH = 28.0;
    private static final long DAY = 24_000L;
    private static final String GATE_HOME = "message.bleach.field_trial.gate_home";

    private FieldTrialTests() {
    }

    /**
     * A joined student holding a nameless Asauchi at this spot in the box. Not invulnerable, as no Hollow hunts such a
     * player (mc Player.java:902): Resistance V takes every bite's harm, and nothing moves it back.
     */
    private static ServerPlayer student(GameTestHelper helper, Vec3 at) {
        ServerPlayer student = TestPlayers.join(helper, GameType.SURVIVAL, at);
        student.setData(SoulAttachments.SOUL, ShinigamiTests.student(60.0F));
        student.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        student.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4,
                false, false));
        student.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        return student;
    }

    /** A Spider Hollow that does nothing, made a practice Hollow of this student's trial under way. */
    private static SpiderHollow practice(GameTestHelper helper, ServerPlayer student, int x, int z) {
        SpiderHollow hollow = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), x, z);
        hollow.makePractice(student.getUUID(), FieldTrialEvents.trial(student).startedAt());
        return hollow;
    }

    /** Begins this student's trial now, with one still practice Hollow of it standing in the box before it. */
    private static SpiderHollow begun(GameTestHelper helper, ServerPlayer student) {
        FieldTrialEvents.begin(student, HollowPlayerTests.now(helper));
        return practice(helper, student, Mth.floor(helper.relativeVec(student.position()).x), 5);
    }

    /** This student's practice Hollows within 64 blocks, alive. */
    private static List<Hollow> practiceOf(ServerPlayer student) {
        return student.serverLevel().getEntitiesOfClass(Hollow.class, student.getBoundingBox().inflate(64.0),
                hollow -> hollow.isAlive() && student.getUUID().equals(hollow.trialOf()));
    }

    /** One killing blow of this player's, past the target's frames and the swing's wait; whether it landed. */
    private static boolean slay(Player player, Hollow hollow) {
        hollow.invulnerableTime = 0;
        player.tickCount += 10;
        return hollow.hurt(player.damageSources().playerAttack(player), 1000.0F);
    }

    /** The lesson's day, by which a failure is marked. */
    private static long today(GameTestHelper helper) {
        return KidoLogic.lessonDay(helper.getLevel().getGameTime(), helper.getLevel().getDayTime());
    }

    /** A student's soul at every exam's mark. */
    private static SoulData atTheBar() {
        SoulData data = ShinigamiTests.student(60.0F);
        ShinigamiLogic.graduationRequirements().forEach((art, level) -> data.setLevel(art, level));
        return data;
    }

    /** Gives this soul a passed field trial, as every test that graduates a student must. */
    static void passed(Player player) {
        player.setData(SoulAttachments.FIELD_TRIAL, FieldTrial.NONE.won());
    }

    /** Marks this student sent out by the instructor's gate, owed every gate home. */
    private static void sentOut(ServerPlayer student) {
        student.setData(SoulAttachments.FIELD_TRIAL, FieldTrialEvents.trial(student).sentOut());
    }

    /** This student's trial gates within 8 blocks of it. */
    private static List<Senkaimon> gatesFor(ServerPlayer student) {
        return student.level().getEntitiesOfClass(Senkaimon.class, student.getBoundingBox().inflate(8.0),
                gate -> student.getUUID().equals(gate.trialFor()));
    }

    /** How many trial gates stand for this student, each then taken away, so that none crowds the next. */
    private static int gatesTaken(ServerPlayer student) {
        List<Senkaimon> gates = gatesFor(student);
        gates.forEach(Entity::discard);
        return gates.size();
    }

    /** Whether an instructor in Soul Society offers this soul its trial, this many days on. */
    private static boolean offered(GameTestHelper helper, Player student, SoulData data, int daysOn) {
        ServerLevel level = helper.getLevel();
        return FieldTrialEvents.offered(student, data, BleachWorlds.SOUL_SOCIETY, level.getGameTime() + daysOn * DAY,
                level.getDayTime() + daysOn * DAY);
    }

    /** The Garganta torn for this student, standing. */
    private static List<GargantaRift> torn(ServerPlayer student, AABB around) {
        return student.serverLevel().getEntitiesOfClass(GargantaRift.class, around,
                rift -> student.getUUID().equals(rift.drawer()));
    }

    /** The real Hollows a Garganta brought for this student, alive. */
    private static List<Hollow> drawnTo(ServerPlayer student, AABB around) {
        return student.serverLevel().getEntitiesOfClass(Hollow.class, around,
                hollow -> hollow.isAlive() && !hollow.isPractice() && student.getUUID().equals(hollow.drawer()));
    }

    /**
     * Steps this student's trial at this time with mob spawning on, only within this one call as game rules are the
     * server's, and no instructor's shelter, so that none an earlier test left near holds a Garganta off.
     */
    private static void stepSpawning(ServerLevel level, ServerPlayer student, long now) {
        GameRules.BooleanValue spawning = level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean was = spawning.get();
        try {
            spawning.set(true, level.getServer());
            KidoTests.withConfig(BleachConfig.ACADEMY_SAFE_RADIUS, 0, () -> FieldTrialEvents.step(student, now));
        } finally {
            spawning.set(was, level.getServer());
        }
    }

    /** Asserts this student's trial failed today, its hunt and bar gone, none due till a new day; told this line once. */
    private static void assertFailed(GameTestHelper helper, ServerPlayer student, @Nullable String key) {
        FieldTrial trial = FieldTrialEvents.trial(student);
        long today = today(helper);
        int told = key == null ? 1 : DutyTests.said(student, key).size();
        helper.assertTrue(!trial.running() && !trial.passed() && trial.failedDay() == today && !trial.dueOn(today)
                        && trial.dueOn(today + 1) && FieldTrialEvents.bar(student) == null && told == 1,
                "a failed trial left " + trial + " on day " + today + ", told " + key + " " + told + " times");
    }

    // ---- Practice Hollows ----

    /**
     * A practice Hollow its student slays counts toward the five and gives nothing a slain Hollow gives, though the blow
     * trains; it hunts and answers its own student alone, takes no portal, never despawns and is never saved. Two
     * students hold two trials, each its own count and bar, and the rest fade, discarded, as each ends.
     */
    @GameTest(template = "empty", batch = "field_trial", timeoutTicks = 400)
    public static void practiceHollowsGiveNothingAndFade(GameTestHelper helper) {
        // A Hollow placed before its chunk is shown sits in a hidden section (mc PersistentEntitySectionManager.java:48),
        // which getEntitiesOfClass skips (mc EntitySectionStorage.java:57), so the trial's end could not find it.
        ServerLevel level = helper.getLevel();
        Vec3 middle = helper.absoluteVec(new Vec3(4.5, 2.0, 3.5));
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, HollowBaitTests.allTicking(level, middle, PRACTICE_GROUND),
                        "the trial's ground does not tick entities"))
                .thenExecute(() -> practiceFades(helper))
                .thenSucceed();
    }

    /** The body of {@link #practiceHollowsGiveNothingAndFade}, once its ground ticks entities. */
    private static void practiceFades(GameTestHelper helper) {
        ServerPlayer student = student(helper, new Vec3(2.5, 1.0, 1.5));
        ServerPlayer other = student(helper, new Vec3(6.5, 1.0, 1.5));
        SpiderHollow first = begun(helper, student);
        SpiderHollow theirs = begun(helper, other);
        SpiderHollow second = practice(helper, student, 4, 5);
        SpiderHollow real = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 4, 3);
        boolean peaceful = HollowPlayerTests.peaceful(helper);
        try {
            SoulData data = SoulLogic.get(student);
            helper.assertTrue(slay(student, real) && data.hunt().purified() == 1, "the real Hollow was not purified");
            float grown = data.maxReiryoku();
            int xp = data.xp(Stat.ZANJUTSU);
            int experience = student.totalExperience;
            int score = student.getScore();
            helper.assertTrue(slay(student, first) && first.isDeadOrDying() && !first.isPurifying(),
                    "the student's blow did not slay its own practice Hollow, or purified it");
            int trained = HollowLogic.hitXp(true, first.wasMaskStruckAt(helper.getLevel().getGameTime()));
            helper.assertTrue(data.hunt().purified() == 1 && data.hunt().slain() == 0 && data.maxReiryoku() == grown
                            && student.totalExperience == experience && student.getScore() == score,
                    "a practice Hollow counted, grew reiryoku to " + data.maxReiryoku() + ", or gave experience or score");
            helper.assertTrue(data.xp(Stat.ZANJUTSU) == xp + trained,
                    "the blow on a practice Hollow trained " + (data.xp(Stat.ZANJUTSU) - xp) + ", not " + trained);
            helper.assertTrue(second.markFragmentRolled(), "a practice Hollow's mask still had its fragment's roll");
            second.setTarget(other);
            helper.assertTrue(HollowLogic.isPreyFor(second, student) && !HollowLogic.isPreyFor(second, other)
                            && (peaceful || second.canAttack(student)) && !second.canAttack(other)
                            && second.getTarget() != other,
                    "a practice Hollow would hunt another student, or not its own, or was set on another");
            helper.assertFalse(slay(other, second) || second.isDeadOrDying() || QuincyNpc.isQuarry(second),
                    "another's blow landed on a practice Hollow, or a Quincy would hunt it");
            helper.assertTrue(DevourLogic.protectedPrey(other, second) && !Negacion.mayHold(second)
                            && !SpiritProjectiles.mayStrike(other, second) && !KidoLogic.mayBind(other, second)
                            && SpiritProjectiles.mayStrike(student, second) && KidoLogic.mayBind(student, second),
                    "another soul's arts could strike or bind a practice Hollow, it could be devoured or lifted, or its own"
                            + " student's arts could not reach it");
            helper.assertFalse(second.saveAsPassenger(new CompoundTag()) || second.canUsePortal(false)
                    || second.removeWhenFarAway(200.0), "a practice Hollow would be saved, take a portal or despawn");
            helper.assertTrue(slay(other, theirs) && FieldTrialEvents.slain(student) == 1
                    && FieldTrialEvents.slain(other) == 1, "the two trials counted " + FieldTrialEvents.slain(student)
                    + " and " + FieldTrialEvents.slain(other));
            // getPlayers() is a Set (mc ServerBossEvent.java:16-17, :130), never equal to a List.
            helper.assertTrue(FieldTrialEvents.bar(student).getPlayers().equals(Set.of(student))
                    && FieldTrialEvents.bar(other).getPlayers().equals(Set.of(other)), "the two share a bar");
        } finally {
            FieldTrialEvents.forget(student);
            FieldTrialEvents.forget(other);
            TravelTests.discard(real, first, theirs);
        }
        Entity.RemovalReason faded = second.getRemovalReason();
        TravelTests.discard(second);
        helper.assertTrue(faded == Entity.RemovalReason.DISCARDED && FieldTrialEvents.bar(student) == null,
                "the practice Hollow was " + faded + " as its trial ended, not discarded");
    }

    // ---- The hunt and the pass ----

    /**
     * The hunt's own practice Hollows come to a student two at a time, 10 to 20 blocks off and set on it; five slain
     * pass, told: the bar comes down, the rest fade, and the student stays where it stands, owed no gate home.
     */
    @GameTest(template = "field", batch = "field_trial_hunt", skyAccess = true, timeoutTicks = 600)
    public static void fiveSlainPass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer student = student(helper, new Vec3(8.5, 2.0, 8.5));
        Vec3 start = student.position();
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, start, REACH);
        boolean peaceful = HollowPlayerTests.peaceful(helper);
        Runnable clear = () -> {
            FieldTrialEvents.forget(student);
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            forced.clear();
        };
        helper.runAtTickTime(590, () -> {
            int slain = FieldTrialEvents.slain(student);
            clear.run();
            helper.fail("the trial was not passed in time; " + slain + " practice Hollows were slain");
        });
        GameTestSequence sequence = helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(HollowBaitTests.allTicking(level, start, REACH), "no ticking ground"))
                .thenExecute(() -> FieldTrialEvents.begin(student, level.getGameTime()));
        for (int kill = 0; kill < FieldTrialEvents.NEEDED; kill++) {
            int owed = Math.min(FieldTrialEvents.AT_ONCE, FieldTrialEvents.NEEDED - kill);
            sequence.thenWaitUntil(() -> helper.assertTrue(practiceOf(student).size() == owed,
                            practiceOf(student).size() + " practice Hollows stand, not " + owed))
                    .thenExecute(HollowBaitTests.orClear(() -> {
                        for (Hollow hollow : practiceOf(student)) {
                            double away = Math.sqrt(hollow.distanceToSqr(student.getX(), hollow.getY(), student.getZ()));
                            helper.assertTrue(away <= 20.71 && (peaceful || hollow.getTarget() == student),
                                    "a practice Hollow stands " + away + " blocks off, set on " + hollow.getTarget());
                        }
                        helper.assertTrue(slay(student, practiceOf(student).get(0)), "the student's blow did not land");
                    }, clear));
        }
        sequence.thenExecute(HollowBaitTests.orClear(() -> {
                    FieldTrial trial = FieldTrialEvents.trial(student);
                    helper.assertTrue(trial.passed() && !trial.running() && FieldTrialEvents.bar(student) == null
                                    && practiceOf(student).isEmpty() && student.level() == level
                                    && gatesFor(student).isEmpty()
                                    && DutyTests.said(student, "message.bleach.field_trial.passed").size() == 1,
                            "five slain left the trial " + trial + ", its bar, a practice Hollow or a gate");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- Failures and the day's wait ----

    /**
     * A trial fails as its time runs out, as its student dies or leaves the Human World, each marked on the lesson's day
     * and told, its practice Hollows fading; one whose hunt could place no practice Hollow in its first 30 seconds ends
     * with nothing marked, told why. Sent out by the instructor, a student timed out or without ground gets its gate home
     * at once, and the next as each goes, the third the last; the dead get theirs alive again, and one who left none.
     */
    @GameTest(template = "empty", batch = "field_trial")
    public static void timeDeathAndLeavingFail(GameTestHelper helper) {
        ServerPlayer late = student(helper, new Vec3(1.5, 1.0, 1.5));
        ServerPlayer dying = student(helper, new Vec3(3.5, 1.0, 1.5));
        ServerPlayer leaving = student(helper, new Vec3(5.5, 1.0, 1.5));
        ServerPlayer stranded = student(helper, new Vec3(7.5, 1.0, 1.5));
        long now = HollowPlayerTests.now(helper);
        List<Hollow> hollows = new ArrayList<>(List.of(begun(helper, late), begun(helper, dying), begun(helper, leaving)));
        FieldTrialEvents.begin(stranded, now);
        List<ServerPlayer> all = List.of(late, dying, leaving, stranded);
        all.forEach(FieldTrialTests::sentOut);
        try {
            FieldTrialEvents.step(late, now + FieldTrialEvents.TRIAL_TICKS - 1);
            helper.assertTrue(FieldTrialEvents.trial(late).running(), "the trial ran out a tick early");
            FieldTrialEvents.step(late, now + FieldTrialEvents.TRIAL_TICKS);
            helper.assertTrue(gatesTaken(late) == 1, "a student out of time was not opened its gate home at once");
            for (int gate = 2; gate <= FieldTrial.GATES_HOME + 1; gate++) {
                FieldTrialEvents.step(late, now);
                helper.assertTrue(gatesTaken(late) == (gate <= FieldTrial.GATES_HOME ? 1 : 0), "gate home " + gate
                        + " of " + FieldTrial.GATES_HOME + " did not open, or one past them did");
            }
            helper.assertTrue(FieldTrialEvents.trial(late).gatesHome() == 0
                            && TravelTests.lines(late, GATE_HOME) == FieldTrial.GATES_HOME - 1
                            && TravelTests.lines(late, GATE_HOME + ".last") == 1,
                    "the gates home left " + FieldTrialEvents.trial(late) + ", told " + TravelTests.said(late));
            // At no health, as a player is when its death is posted, so not alive (mc LivingEntity.java:1548-1550).
            dying.setHealth(0.0F);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(dying, dying.damageSources().generic()));
            helper.assertTrue(gatesTaken(dying) == 0 && FieldTrialEvents.trial(dying).gatesHome() == FieldTrial.GATES_HOME,
                    "a dead student was opened a gate home, or let go of it");
            dying.setHealth(dying.getMaxHealth());
            FieldTrialEvents.step(dying, now);
            helper.assertTrue(gatesTaken(dying) == 1, "a student alive again was not opened its gate home");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(leaving, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(gatesTaken(leaving) == 0 && FieldTrialEvents.trial(leaving).gatesHome() == 0,
                    "a student who left the Human World was opened a gate home, or is owed one");
            assertFailed(helper, late, "message.bleach.field_trial.failed.time");
            assertFailed(helper, dying, "message.bleach.field_trial.failed.died");
            assertFailed(helper, leaving, "message.bleach.field_trial.failed.left");
            helper.assertTrue(hollows.stream().allMatch(Entity::isRemoved), "a failed trial's practice Hollow stands on");
            // No step before: one would place a practice Hollow on the open ground about.
            FieldTrialEvents.step(stranded, now + FieldTrialEvents.GROUND_TICKS);
            helper.assertTrue(FieldTrialEvents.trial(stranded).cameHome().equals(FieldTrial.NONE)
                            && FieldTrialEvents.bar(stranded) == null && gatesTaken(stranded) == 1
                            && DutyTests.said(stranded, "message.bleach.field_trial.no_ground").size() == 1,
                    "a hunt with no ground left " + FieldTrialEvents.trial(stranded) + ", a gate home, or was not told why");
        } finally {
            dying.setHealth(dying.getMaxHealth());
            all.forEach(student -> {
                gatesTaken(student);
                FieldTrialEvents.forget(student);
            });
            TravelTests.discard(hollows.toArray(new Entity[0]));
        }
        helper.succeed();
    }

    // ---- The saved record, and a trial cut short ----

    /**
     * A save from before the trial holds no record and reads as none, bad fields as their defaults, one from before the
     * gates home as owing none, and a record round-trips; a logout fails the trial there and then, saved so, opening no
     * gate home and keeping them owed. A trial saved under way by a stop or a crash fails, cut short and told, in its
     * student's first second back, and opens the gate home owed.
     */
    @GameTest(template = "empty", batch = "field_trial")
    public static void theRecordSavesAndAnAbsenceFails(GameTestHelper helper) {
        helper.assertTrue(FieldTrial.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).result()
                .equals(Optional.of(FieldTrial.NONE)), "an empty record does not read as none");
        CompoundTag bad = new CompoundTag();
        bad.put("passed", StringTag.valueOf("yes"));
        bad.put("failed_day", StringTag.valueOf("monday"));
        helper.assertTrue(FieldTrial.CODEC.parse(NbtOps.INSTANCE, bad).result().equals(Optional.of(FieldTrial.NONE)),
                "bad fields do not read as their defaults");
        CompoundTag old = new CompoundTag();
        old.putBoolean("passed", true);
        old.putLong("failed_day", 3L);
        old.putLong("started_at", 40L);
        helper.assertTrue(FieldTrial.CODEC.parse(NbtOps.INSTANCE, old).result()
                .equals(Optional.of(new FieldTrial(true, 3L, 40L, 0))), "a record from before the gates home owes one");
        for (FieldTrial record : List.of(FieldTrial.NONE.won(), FieldTrial.NONE.lost(3L).sentOut())) {
            helper.assertTrue(FieldTrial.CODEC.parse(NbtOps.INSTANCE, FieldTrial.CODEC.encodeStart(NbtOps.INSTANCE, record)
                    .getOrThrow()).result().equals(Optional.of(record)), "the record " + record + " did not read back");
        }

        ServerPlayer leaver = student(helper, new Vec3(2.5, 1.0, 1.5));
        ServerPlayer back = student(helper, new Vec3(6.5, 1.0, 1.5));
        try {
            helper.assertFalse(leaver.saveWithoutId(new CompoundTag()).getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                    .contains(KEY), "a student who never took the trial saves a record");
            SpiderHollow hollow = begun(helper, leaver);
            sentOut(leaver);
            // A logout marks the player gone before its event (mc ServerGamePacketListenerImpl.java:1168-1169).
            leaver.disconnect();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(leaver));
            assertFailed(helper, leaver, null);
            helper.assertTrue(hollow.isRemoved(), "a practice Hollow outlived its student's logout");
            helper.assertTrue(gatesTaken(leaver) == 0 && FieldTrialEvents.trial(leaver).gatesHome() == FieldTrial.GATES_HOME,
                    "a student logging out was opened a gate home, or let go of it");
            Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
            loaded.load(leaver.saveWithoutId(new CompoundTag()));
            helper.assertTrue(loaded.getData(SoulAttachments.FIELD_TRIAL).equals(FieldTrialEvents.trial(leaver)),
                    "the failed record did not load");
            long now = HollowPlayerTests.now(helper);
            back.setData(SoulAttachments.FIELD_TRIAL, FieldTrial.NONE.sentOut().begun(now));
            FieldTrialEvents.step(back, now);
            assertFailed(helper, back, "message.bleach.field_trial.failed.cut_short");
            helper.assertTrue(gatesTaken(back) == 1, "a trial cut short opened no gate home");
        } finally {
            gatesTaken(back);
            FieldTrialEvents.forget(leaver);
            FieldTrialEvents.forget(back);
            // Off the server for good, as its logout would have taken it.
            leaver.connection.disconnect(Component.literal("Logged out"));
        }
        helper.succeed();
    }

    // ---- The operator's command ----

    /**
     * {@code /bleach trial} begins a student's trial where it stands, though below the exams' mark and failed today;
     * it refuses, saying why, a soul not enrolled, a student on the death screen, one out of the Human World and one
     * already on its trial.
     */
    @GameTest(template = "empty", batch = "field_trial")
    public static void theCommandBeginsATrialInTheHumanWorld(GameTestHelper helper) {
        ServerLevel soul = TravelTests.holdCourt(helper);
        long now = HollowPlayerTests.now(helper);
        ServerPlayer fresh = student(helper, new Vec3(1.5, 1.0, 1.5));
        fresh.setData(SoulAttachments.FIELD_TRIAL, FieldTrial.NONE.lost(today(helper)));
        ServerPlayer unenrolled = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(3.5, 1.0, 1.5));
        unenrolled.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.RUKONGAI, 60.0F));
        ServerPlayer away = student(helper, new Vec3(5.5, 1.0, 1.5));
        ServerPlayer busy = student(helper, new Vec3(7.5, 1.0, 1.5));
        ServerPlayer fallen = student(helper, new Vec3(1.5, 1.0, 3.5));
        List<ServerPlayer> all = List.of(fresh, unenrolled, away, busy, fallen);
        try {
            FieldTrialEvents.begin(busy, now - 100);
            away.teleportTo(soul, TravelLogic.COURT.x, TravelLogic.COURT.y, TravelLogic.COURT.z, 0.0F, 0.0F);
            all.forEach(player -> player.addTag(TAG));
            // On the death screen till this tick's end, which @a still selects (mc EntitySelectorParser.java:208-252).
            fallen.setHealth(0.0F);
            List<Component> said = GateTests.run(helper.getLevel(), "bleach trial @a[tag=" + TAG + "]");
            for (String key : List.of("commands.bleach.trial.not_student", "commands.bleach.trial.dead",
                    "commands.bleach.trial.not_human_world", "commands.bleach.trial.running")) {
                helper.assertTrue(said.stream().filter(line -> GateTests.says(line, key, -1)).count() == 1,
                        "the command did not say " + key + " once: " + said.stream().map(Component::getString).toList());
            }
            helper.assertTrue(said.size() == 5 && said.stream().anyMatch(line -> line.getContents()
                            instanceof TranslatableContents words && words.getKey().equals("commands.bleach.trial.success")
                            && Integer.valueOf(1).equals(words.getArgs()[0])),
                    "the command said " + said.stream().map(Component::getString).toList());
            FieldTrial begun = FieldTrialEvents.trial(fresh);
            helper.assertTrue(begun.startedAt() == now && begun.failedDay() == -1L && FieldTrialEvents.bar(fresh) != null
                            && DutyTests.said(fresh, "message.bleach.field_trial.begun").size() == 1,
                    "the command left the student's trial " + begun);
            helper.assertTrue(FieldTrialEvents.trial(busy).startedAt() == now - 100
                            && FieldTrialEvents.trial(unenrolled).equals(FieldTrial.NONE)
                            && FieldTrialEvents.trial(away).equals(FieldTrial.NONE)
                            && FieldTrialEvents.trial(fallen).equals(FieldTrial.NONE),
                    "the command began a trial it refused");
        } finally {
            fallen.setHealth(fallen.getMaxHealth());
            all.forEach(FieldTrialEvents::forget);
        }
        helper.succeed();
    }

    // ---- The offer ----

    /**
     * An instructor in Soul Society or the Human World offers the trial to a student at every exam's mark with one due,
     * and to no one else: not one a mark short, a Shinigami or a soul not enrolled, nor one with a trial under way, passed
     * or failed that day, nor where the server asks for none. In the Human World it begins where the student stands.
     */
    @GameTest(template = "empty", batch = "field_trial")
    public static void theTrialIsOfferedOnlyAtTheBar(GameTestHelper helper) {
        ServerPlayer bar = student(helper, new Vec3(2.5, 1.0, 1.5));
        SoulData data = atTheBar();
        bar.setData(SoulAttachments.SOUL, data);
        long now = HollowPlayerTests.now(helper);
        long dayTime = helper.getLevel().getDayTime();
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        try {
            helper.assertTrue(offered(helper, bar, data, 0)
                            && FieldTrialEvents.offered(bar, data, Level.OVERWORLD, now, dayTime)
                            && !FieldTrialEvents.offered(bar, data, Level.NETHER, now, dayTime),
                    "the trial was offered in the wrong worlds");
            KidoTests.withConfig(BleachConfig.FIELD_TRIAL, false, () -> helper.assertFalse(offered(helper, bar, data, 0),
                    "the trial was offered with field_trial false"));
            SoulData shy = atTheBar();
            shy.setLevel(Stat.HOHO, ShinigamiLogic.graduationRequirements().get(Stat.HOHO) - 1);
            SoulData shinigami = ShinigamiTests.soul(SoulState.SHINIGAMI, 60.0F);
            SoulData unenrolled = ShinigamiTests.soul(SoulState.RUKONGAI, 60.0F);
            for (SoulData other : List.of(shinigami, unenrolled)) {
                ShinigamiLogic.graduationRequirements().forEach((art, level) -> other.setLevel(art, level));
            }
            helper.assertFalse(offered(helper, bar, shy, 0) || offered(helper, bar, shinigami, 0)
                    || offered(helper, bar, unenrolled, 0), "the trial was offered a mark short, to a Shinigami or to a"
                    + " soul not enrolled");
            long today = today(helper);
            for (FieldTrial trial : List.of(FieldTrial.NONE.lost(today), FieldTrial.NONE.begun(now), FieldTrial.NONE.won())) {
                bar.setData(SoulAttachments.FIELD_TRIAL, trial);
                helper.assertFalse(offered(helper, bar, data, 0), "the trial was offered with " + trial);
            }
            bar.setData(SoulAttachments.FIELD_TRIAL, FieldTrial.NONE.lost(today));
            helper.assertTrue(offered(helper, bar, data, 1), "a trial failed today was not offered a day on");
            bar.removeData(SoulAttachments.FIELD_TRIAL);
            DialogueView view = instructor.viewFor(bar, data, now);
            helper.assertTrue(view == DialogueView.STUDENT_TRIAL && instructor.viewFor(data, now) == DialogueView.STUDENT,
                    "an instructor in the Human World showed the student " + view);
            instructor.perform(bar, NpcAction.FIELD_TRIAL);
            FieldTrial begun = FieldTrialEvents.trial(bar);
            helper.assertTrue(begun.startedAt() == now && begun.gatesHome() == 0 && gatesFor(bar).isEmpty()
                            && DutyTests.said(bar, "message.bleach.field_trial.begun").size() == 1,
                    "the instructor in the Human World left the trial " + begun + ", or opened a gate");
        } finally {
            FieldTrialEvents.forget(bar);
            // Persistent, and the test grid is never cleared.
            instructor.discard();
        }
        helper.succeed();
    }

    // ---- The instructor's gates ----

    /**
     * An instructor in Soul Society sends a student at the bar out: his gate, beyond it from him and the only one (a
     * second is crowded), takes it alone to where a gate home would set it down, its trial begun as it comes out; a
     * Shinigami in the gate stays, told it is not its. Passed, the student is owed its gates home, the first of which
     * opens before it and takes it to the Academy's landing, facing the hall, owed none.
     */
    @GameTest(template = "empty", batch = "field_trial_gate", timeoutTicks = 800)
    public static void theInstructorsGatesTakeTheStudentOutAndHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel soul = TravelTests.holdCourt(helper);
        SiteTests.holdAcademy(helper);
        // The return point the gate out sets the student down at, on stone.
        helper.setBlock(1, 0, 0, Blocks.STONE);
        BlockPos back = helper.absolutePos(new BlockPos(1, 1, 0));
        ServerPlayer student = student(helper, new Vec3(1.5, 1.0, 1.5));
        student.setData(SoulAttachments.SOUL, atTheBar());
        student.setData(SoulAttachments.TRAVEL, student.getData(SoulAttachments.TRAVEL)
                .withHumanWorldReturn(GlobalPos.of(Level.OVERWORLD, back)));
        ServerPlayer shinigami = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(0.5, 1.0, 1.5));
        shinigami.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F));
        Vec3 court = TravelLogic.COURT;
        List<Entity> spawned = new ArrayList<>();
        Runnable clear = () -> {
            FieldTrialEvents.forget(student);
            TravelTests.discard(spawned.toArray(new Entity[0]));
            helper.setBlock(1, 0, 0, Blocks.AIR);
        };
        helper.runAtTickTime(790, () -> {
            clear.run();
            helper.fail("the student is at " + TravelTests.where(student) + " with " + FieldTrialEvents.trial(student)
                    + "; the action bar said " + TravelTests.said(student));
        });
        helper.startSequence()
                .thenWaitUntil(() -> AcademyTests.assertAcademyStands(helper, soul))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    AcademyInstructor instructor = ModEntities.ACADEMY_INSTRUCTOR.get().create(soul);
                    instructor.moveTo(court.x - 3.0, court.y, court.z, 0.0F, 0.0F);
                    soul.addFreshEntity(instructor);
                    spawned.add(instructor);
                    student.teleportTo(soul, court.x, court.y, court.z, 0.0F, 0.0F);
                    shinigami.teleportTo(soul, court.x + 3.0, court.y, court.z, 0.0F, 0.0F);
                    DialogueView view = instructor.viewFor(student, SoulLogic.get(student), soul.getGameTime());
                    helper.assertTrue(view == DialogueView.STUDENT_TRIAL, "the instructor showed the student " + view);
                    instructor.perform(student, NpcAction.FIELD_TRIAL);
                    instructor.perform(student, NpcAction.FIELD_TRIAL);
                    List<Senkaimon> out = gatesFor(student);
                    spawned.addAll(out);
                    // Facing from him to the student, so it stands beyond the student, never on or behind him.
                    helper.assertTrue(out.size() == 1 && out.get(0).leadsTo() == Level.OVERWORLD
                                    && out.get(0).facing() == Direction.EAST
                                    && out.get(0).distanceTo(instructor) > student.distanceTo(instructor),
                            "the instructor opened " + out.size() + " gates for the student, the first " + out.stream()
                                    .map(gate -> gate.facing() + " at " + gate.position()).findFirst().orElse("none"));
                    helper.assertTrue(TravelTests.lines(student, "message.bleach.field_trial.set_out") == 1
                                    && TravelTests.lines(student, "message.bleach.senkaimon.crowded") == 1,
                            "the student was told " + TravelTests.said(student));
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(!student.isChangingDimension() && !shinigami.isChangingDimension()
                        && student.getPortalCooldown() == 0, "the two are still coming into Soul Society"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    Senkaimon gate = gatesFor(student).get(0);
                    Direction across = gate.facing().getClockWise();
                    double dx = across.getStepX() * 0.75;
                    double dz = across.getStepZ() * 0.75;
                    student.teleportTo(soul, gate.getX() - dx, gate.getY(), gate.getZ() - dz, 0.0F, 0.0F);
                    shinigami.teleportTo(soul, gate.getX() + dx, gate.getY(), gate.getZ() + dz, 0.0F, 0.0F);
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(student.level() == level, "the student is at "
                        + TravelTests.where(student)))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    FieldTrial trial = FieldTrialEvents.trial(student);
                    helper.assertTrue(student.position().distanceTo(Vec3.atBottomCenterOf(back)) < 0.01,
                            "the gate out set the student down at " + student.position() + ", not at " + back);
                    helper.assertTrue(trial.running() && trial.gatesHome() == FieldTrial.GATES_HOME
                                    && level.getGameTime() - trial.startedAt() <= 1 && FieldTrialEvents.bar(student) != null
                                    && DutyTests.said(student, "message.bleach.field_trial.begun").size() == 1,
                            "coming out left the trial " + trial);
                    helper.assertTrue(shinigami.level() == soul
                                    && TravelTests.lines(shinigami, "message.bleach.field_trial.not_yours") >= 1,
                            "the Shinigami is at " + TravelTests.where(shinigami) + ", told " + TravelTests.said(shinigami));
                    for (int x = 0; x < FieldTrialEvents.NEEDED; x++) {
                        helper.assertTrue(slay(student, practice(helper, student, x, 5)), "the student's blow did not land");
                    }
                    List<Senkaimon> home = gatesFor(student);
                    spawned.addAll(home);
                    FieldTrial passed = FieldTrialEvents.trial(student);
                    helper.assertTrue(passed.passed() && !passed.running() && passed.gatesHome() == FieldTrial.GATES_HOME - 1
                                    && home.size() == 1 && home.get(0).leadsTo() == BleachWorlds.SOUL_SOCIETY
                                    && TravelTests.lines(student, GATE_HOME) == 1,
                            "the pass left " + passed + " and " + home.size() + " gates home");
                    // Its trial no longer due, the instructor's gate out takes it nowhere.
                    helper.assertTrue(TravelEvents.destination((Senkaimon) spawned.get(1), student) == null,
                            "the gate out would take the passed student out again");
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(!student.isChangingDimension() && student.getPortalCooldown() == 0,
                        "the student is still coming out"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    Senkaimon home = gatesFor(student).get(0);
                    student.teleportTo(level, home.getX(), home.getY(), home.getZ(), 0.0F, 0.0F);
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(student.level() == soul, "the student is at "
                        + TravelTests.where(student)))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    FieldTrial trial = FieldTrialEvents.trial(student);
                    helper.assertTrue(student.position().distanceTo(AcademyLocator.LANDING) < 0.01
                                    && Math.abs(Mth.wrapDegrees(student.getYRot() - AcademyLocator.LANDING_YAW)) < 0.01,
                            "the gate home set the student down at " + student.position() + " facing " + student.getYRot());
                    helper.assertTrue(trial.passed() && trial.gatesHome() == 0
                                    && DutyTests.said(student, "message.bleach.konso.arrived").size() == 1,
                            "coming home left " + trial);
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- Graduation ----

    /**
     * Graduation asks for the pass: a student at every exam's mark with none stays a student, unless the server asks for
     * none. The pass outlives death, and with it the student graduates and its record goes; a Shinigami is untouched.
     */
    @GameTest(template = "empty", batch = "field_trial")
    public static void graduationNeedsThePass(GameTestHelper helper) {
        Player student = helper.makeMockPlayer(GameType.SURVIVAL);
        student.setData(SoulAttachments.SOUL, atTheBar());
        helper.assertFalse(ShinigamiEvents.graduate(student), "a student with no pass graduated");
        helper.assertTrue(ShinigamiLogic.isStudent(SoulLogic.get(student)), "a student with no pass is a student no more");
        Player free = helper.makeMockPlayer(GameType.SURVIVAL);
        free.setData(SoulAttachments.SOUL, atTheBar());
        KidoTests.withConfig(BleachConfig.FIELD_TRIAL, false, () -> helper.assertTrue(ShinigamiEvents.graduate(free)
                && SoulLogic.get(free).state() == SoulState.SHINIGAMI, "with field_trial false a student did not graduate"));
        passed(student);
        Player reborn = helper.makeMockPlayer(GameType.SURVIVAL);
        reborn.setData(SoulAttachments.SOUL, atTheBar());
        // As a respawn copies a player's attachments (NeoForge's AttachmentInternals.onPlayerClone).
        reborn.copyAttachmentsFrom(student, true);
        helper.assertTrue(FieldTrialEvents.trial(reborn).passed(), "the pass did not outlive death");
        helper.assertTrue(ShinigamiEvents.graduate(reborn) && SoulLogic.get(reborn).state() == SoulState.SHINIGAMI
                && !reborn.hasData(SoulAttachments.FIELD_TRIAL), "the pass did not graduate the student, or stayed");
        Player elder = helper.makeMockPlayer(GameType.SURVIVAL);
        elder.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, 60.0F));
        helper.assertFalse(ShinigamiEvents.graduate(elder) || SoulLogic.get(elder).state() != SoulState.SHINIGAMI
                || SoulLogic.get(elder).maxReiryoku() != 60.0F, "a Shinigami graduated again, or was changed");
        helper.succeed();
    }

    // ---- The break-in ----

    /**
     * At chance 1 a real Hollow is due one to three minutes into a trial; at its time, stepped until open ground takes its
     * tear, a Garganta opens for the student, told once, and a Hollow it drew comes through, real: slain, it counts as
     * purified and not toward the five. On Peaceful none opens, and it stays due.
     */
    @GameTest(template = "field", batch = "field_trial_break_in", skyAccess = true, timeoutTicks = 400)
    public static void aRealHollowBreaksIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer student = student(helper, new Vec3(8.5, 2.0, 8.5));
        Vec3 start = student.position();
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, start, BREAK_IN_REACH);
        AABB around = new AABB(start, start).inflate(BREAK_IN_REACH + 8.0);
        boolean peaceful = HollowPlayerTests.peaceful(helper);
        long[] due = {ShinigamiProgress.NEVER};
        // Every rift and Hollow about, practice and real, the hand-placed one among them.
        Runnable clear = () -> {
            FieldTrialEvents.forget(student);
            level.getEntitiesOfClass(GargantaRift.class, around).forEach(Entity::discard);
            level.getEntitiesOfClass(Hollow.class, around).forEach(Entity::discard);
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            forced.clear();
        };
        helper.runAtTickTime(390, () -> {
            // Says how far it got: never rolled, still due, or no longer due, with what stands about.
            long left = FieldTrialEvents.breakInAt(student);
            String stage = due[0] == ShinigamiProgress.NEVER ? "never rolled"
                    : "due at " + due[0] + (left == due[0] ? ", still due" : ", no longer due") + " at game time "
                            + level.getGameTime() + ", with " + torn(student, around).size() + " Garganta and "
                            + drawnTo(student, around).size() + " real Hollows standing";
            clear.run();
            helper.fail("no real Hollow came for the student in time; its break-in was " + stage);
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(HollowBaitTests.allTicking(level, start, BREAK_IN_REACH),
                        "no ticking ground"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    long now = level.getGameTime();
                    KidoTests.withConfig(BleachConfig.FIELD_TRIAL_BREAK_IN_CHANCE, 1.0,
                            () -> FieldTrialEvents.begin(student, now));
                    due[0] = FieldTrialEvents.breakInAt(student);
                    helper.assertTrue(due[0] >= now + 1200 && due[0] < now + 3600,
                            "a sure break-in is due at " + due[0] + ", begun at " + now);
                    // One standing, so a step at the due time does not end the hunt for want of ground.
                    practice(helper, student, 8, 12);
                }, clear))
                // Each tick until open ground 12 to 24 blocks off takes the tear; on Peaceful once, which opens none.
                .thenWaitUntil(() -> {
                    if (torn(student, around).isEmpty()) {
                        HollowBaitTests.orClear(() -> stepSpawning(level, student, due[0]), clear).run();
                    }
                    helper.assertTrue(peaceful || !torn(student, around).isEmpty(), "no Garganta opened at its time");
                })
                .thenExecute(HollowBaitTests.orClear(() -> {
                    long after = FieldTrialEvents.breakInAt(student);
                    int told = DutyTests.said(student, "message.bleach.field_trial.break_in").size();
                    helper.assertTrue(peaceful ? torn(student, around).isEmpty() && after == due[0] && told == 0
                                    : after == ShinigamiProgress.NEVER && told == 1,
                            "at its time the break-in left it due at " + after + ", told " + told + " times");
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(peaceful || !drawnTo(student, around).isEmpty(),
                        "no real Hollow came through for the student"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    if (!peaceful) {
                        SoulData data = SoulLogic.get(student);
                        int purified = data.hunt().purified();
                        helper.assertTrue(slay(student, drawnTo(student, around).get(0))
                                        && data.hunt().purified() == purified + 1 && FieldTrialEvents.slain(student) == 0,
                                "the real Hollow slain counted " + (data.hunt().purified() - purified)
                                        + " purified, and the trial " + FieldTrialEvents.slain(student) + " slain");
                    }
                    clear.run();
                }, clear))
                .thenSucceed();
    }
}
