package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.FieldTrial;
import io.github.masteryoga1.bleach.soul.FieldTrialEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Academy's field trial: the practice Hollows, the hunt and its pass, each failure and the day's wait, the
 * saved record and {@code /bleach trial}. The death, change of world and logout they post run every listener on the
 * joined players, harmlessly.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class FieldTrialTests {
    private static final String KEY = "bleach:field_trial";
    private static final String TAG = "field_trial_command";
    /** How far about the student the ground must tick for practice Hollows 10 to 20 blocks off. */
    private static final double REACH = 24.0;

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
    @GameTest(template = "empty", batch = "field_trial")
    public static void practiceHollowsGiveNothingAndFade(GameTestHelper helper) {
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
        helper.succeed();
    }

    // ---- The hunt and the pass ----

    /**
     * The hunt's own practice Hollows come to a student two at a time, 10 to 20 blocks off and set on it; five slain
     * pass, told: the bar comes down, the rest fade, and the student stays where it stands.
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
                                    && DutyTests.said(student, "message.bleach.field_trial.passed").size() == 1,
                            "five slain left the trial " + trial + ", its bar or a practice Hollow");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- Failures and the day's wait ----

    /**
     * A trial fails as its time runs out, as its student dies or leaves the Human World, each marked on the lesson's day
     * and told, its practice Hollows fading; one whose hunt could place no practice Hollow in its first 30 seconds ends
     * with nothing marked, told why.
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
        try {
            FieldTrialEvents.step(late, now + FieldTrialEvents.TRIAL_TICKS - 1);
            helper.assertTrue(FieldTrialEvents.trial(late).running(), "the trial ran out a tick early");
            FieldTrialEvents.step(late, now + FieldTrialEvents.TRIAL_TICKS);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(dying, dying.damageSources().generic()));
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(leaving, Level.OVERWORLD, Level.NETHER));
            assertFailed(helper, late, "message.bleach.field_trial.failed.time");
            assertFailed(helper, dying, "message.bleach.field_trial.failed.died");
            assertFailed(helper, leaving, "message.bleach.field_trial.failed.left");
            helper.assertTrue(hollows.stream().allMatch(Entity::isRemoved), "a failed trial's practice Hollow stands on");
            // No step before: one would place a practice Hollow on the open ground about.
            FieldTrialEvents.step(stranded, now + FieldTrialEvents.GROUND_TICKS);
            helper.assertTrue(FieldTrialEvents.trial(stranded).equals(FieldTrial.NONE)
                            && FieldTrialEvents.bar(stranded) == null
                            && DutyTests.said(stranded, "message.bleach.field_trial.no_ground").size() == 1,
                    "a hunt with no ground left " + FieldTrialEvents.trial(stranded) + ", or was not told why");
        } finally {
            List.of(late, dying, leaving, stranded).forEach(FieldTrialEvents::forget);
            TravelTests.discard(hollows.toArray(new Entity[0]));
        }
        helper.succeed();
    }

    // ---- The saved record, and a trial cut short ----

    /**
     * A save from before the trial holds no record and reads as none, bad fields as their defaults, and a record
     * round-trips; a logout fails the trial there and then, saved so. A trial saved under way by a stop or a crash fails,
     * cut short and told, in its student's first second back.
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
        FieldTrial passed = FieldTrial.NONE.won();
        helper.assertTrue(FieldTrial.CODEC.parse(NbtOps.INSTANCE, FieldTrial.CODEC.encodeStart(NbtOps.INSTANCE, passed)
                .getOrThrow()).result().equals(Optional.of(passed)), "a passed record did not read back");

        ServerPlayer leaver = student(helper, new Vec3(2.5, 1.0, 1.5));
        ServerPlayer back = student(helper, new Vec3(6.5, 1.0, 1.5));
        try {
            helper.assertFalse(leaver.saveWithoutId(new CompoundTag()).getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                    .contains(KEY), "a student who never took the trial saves a record");
            SpiderHollow hollow = begun(helper, leaver);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(leaver));
            assertFailed(helper, leaver, null);
            helper.assertTrue(hollow.isRemoved(), "a practice Hollow outlived its student's logout");
            Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
            loaded.load(leaver.saveWithoutId(new CompoundTag()));
            helper.assertTrue(loaded.getData(SoulAttachments.FIELD_TRIAL).equals(FieldTrialEvents.trial(leaver)),
                    "the failed record did not load");
            long now = HollowPlayerTests.now(helper);
            back.setData(SoulAttachments.FIELD_TRIAL, FieldTrial.NONE.begun(now));
            FieldTrialEvents.step(back, now);
            assertFailed(helper, back, "message.bleach.field_trial.failed.cut_short");
        } finally {
            FieldTrialEvents.forget(leaver);
            FieldTrialEvents.forget(back);
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
}
