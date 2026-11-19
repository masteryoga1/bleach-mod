package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Academy's field trial, a hunt in the Human World that the instructor offers at the stat bar, sending the student
 * out by his Senkaimon from Soul Society: practice Hollows come for it alone, two at a time, and five slain within five
 * minutes pass it, though now and then a real Hollow breaks in. Time running out, death, leaving the Human World or
 * logging out fails it, the lesson's day marked; every end fades the practice Hollows, and one the instructor sent out is
 * owed gates home to the Academy until it leaves the Human World. Graduation asks for the pass unless the server says not.
 */
public final class FieldTrialEvents {
    /** Practice Hollows a student must slay, the time it has, and how many come for it at once. The mod's numbers. */
    public static final int NEEDED = 5;
    public static final int TRIAL_TICKS = 5 * 60 * 20;
    public static final int AT_ONCE = 2;
    /** A hunt that has placed no practice Hollow this long after it began ends unmarked: no open ground about. */
    public static final int GROUND_TICKS = 30 * 20;
    /** How far from the student practice Hollows appear, in how many tries, and how far above or below it. */
    private static final double SPOT_MIN = 10.0;
    private static final double SPOT_MAX = 20.0;
    private static final int SPOT_TRIES = 12;
    private static final int SPOT_RISE = 8;
    /** How far a practice Hollow may stray from its student before it fades. */
    static final double STRAY = 48.0;
    private static final String BEGUN_KEY = "message.bleach.field_trial.begun";
    private static final String PASSED_KEY = "message.bleach.field_trial.passed";
    private static final String TIME_KEY = "message.bleach.field_trial.failed.time";
    private static final String DIED_KEY = "message.bleach.field_trial.failed.died";
    private static final String LEFT_KEY = "message.bleach.field_trial.failed.left";
    private static final String CUT_SHORT_KEY = "message.bleach.field_trial.failed.cut_short";
    private static final String NO_GROUND_KEY = "message.bleach.field_trial.no_ground";
    private static final String BAR_KEY = "bossbar.bleach.field_trial";
    private static final String SET_OUT_KEY = "message.bleach.field_trial.set_out";
    private static final String NOT_YOURS_KEY = "message.bleach.field_trial.not_yours";
    private static final String GATE_HOME_KEY = "message.bleach.field_trial.gate_home";
    private static final String LAST_GATE_KEY = "message.bleach.field_trial.gate_home.last";
    private static final String ARRIVED_KEY = "message.bleach.konso.arrived";
    private static final String BREAK_IN_KEY = "message.bleach.field_trial.break_in";
    /** A real Hollow that breaks in comes this long into the trial, and up to this much later: one to three minutes. */
    private static final int BREAK_IN_EARLIEST = 60 * 20;
    private static final int BREAK_IN_WINDOW = 120 * 20;

    /**
     * A trial's hunt while its student is here: when it began, its bar, how many it has slain, whether any came, and when
     * a real Hollow breaks in ({@link ShinigamiProgress#NEVER}: none, or it has come).
     */
    private static final class Hunt {
        final long start;
        final ServerBossEvent bar;
        int slain;
        boolean placed;
        long breakInAt;

        Hunt(long start, ServerBossEvent bar, long breakInAt) {
            this.start = start;
            this.bar = bar;
            this.breakInAt = breakInAt;
        }
    }

    /** Each student's hunt under way, by UUID. Server thread only; never saved, and a stop clears it. */
    private static final Map<UUID, Hunt> HUNTS = new HashMap<>();

    private FieldTrialEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(FieldTrialEvents::onPlayerTick);
        // Last, so a death another listener called off does not fail the trial.
        bus.addListener(EventPriority.LOWEST, FieldTrialEvents::onDeath);
        bus.addListener(FieldTrialEvents::onChangedDimension);
        bus.addListener(FieldTrialEvents::onLoggedOut);
        bus.addListener(FieldTrialEvents::onServerStopped);
    }

    public static FieldTrial trial(Player player) {
        return player.getData(SoulAttachments.FIELD_TRIAL);
    }

    /** Whether this soul's trial is under way: no Garganta tears near it but its break-in, and no Shinigami on duty comes. */
    public static boolean running(Player player) {
        return trial(player).running();
    }

    // ---- The offer and graduation ----

    /** Whether graduation asks for a passed field trial: {@code [academy] field_trial}. */
    public static boolean required() {
        return BleachConfig.get(BleachConfig.FIELD_TRIAL);
    }

    /** Whether this student may graduate as far as the trial goes: it passed one, or none is asked for. */
    public static boolean mayGraduate(Player student) {
        return !required() || trial(student).passed();
    }

    /**
     * Whether an instructor in this world offers this soul its field trial now: the server asks for one, he stands in Soul
     * Society or the Human World, the soul is a student at every exam's mark, and a trial is due on the lesson's day.
     */
    public static boolean offered(Player student, SoulData data, ResourceKey<Level> world, long now, long dayTime) {
        return required() && (world == BleachWorlds.SOUL_SOCIETY || world == Level.OVERWORLD)
                && ShinigamiLogic.canGraduate(data) && trial(student).dueOn(KidoLogic.lessonDay(now, dayTime));
    }

    /**
     * The instructor sends a student out: from Soul Society by a Senkaimon he turns beyond it, which takes it alone to the
     * Human World, its trial begun as it comes out, refused and told why where the gate would meet Sekkiseki or stand too
     * close to another; in the Human World its trial begins where it stands, with no gate there or home.
     */
    public static void setOut(ShinigamiNpc instructor, Player student) {
        if (!(student instanceof ServerPlayer passer) || passer.level() != instructor.level()) {
            return;
        }
        ServerLevel level = passer.serverLevel();
        if (level.dimension() == Level.OVERWORLD) {
            begin(passer, level.getGameTime());
            return;
        }
        TravelLogic.Refusal refusal = barred(passer, instructor);
        if (refusal != null) {
            passer.displayClientMessage(Component.translatable(refusal.messageKey()).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Senkaimon gate = Senkaimon.open(level, passer, instructor, level.getGameTime());
        if (gate != null) {
            gate.forTrial(passer.getUUID());
            passer.displayClientMessage(Component.translatable(SET_OUT_KEY).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Why no gate turned by this one (null: by none) may open before this soul now: Sekkiseki, or a gate too close. */
    @Nullable
    private static TravelLogic.Refusal barred(Entity soul, @Nullable Entity turner) {
        if (SpiritClip.meets(soul.level(), Senkaimon.boxFor(soul, turner))) {
            return TravelLogic.Refusal.SEKKISEKI;
        }
        return Senkaimon.crowded(soul, turner) ? TravelLogic.Refusal.CROWDED : null;
    }

    // ---- The gates ----

    /**
     * Where a trial's gate takes this soul ({@link TravelEvents#destination} hands every such gate here), or null, told
     * why: only its student passes, out (only with a trial due) to where a Shinigami's gate home would set it down, or
     * home to the Academy's landing. Never by the Dangai: the instructor's gate is his guidance.
     */
    @Nullable
    static DimensionTransition destination(Senkaimon gate, ServerPlayer passer) {
        if (!passer.getUUID().equals(gate.trialFor())) {
            passer.displayClientMessage(Component.translatable(NOT_YOURS_KEY).withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        boolean out = gate.leadsTo() == Level.OVERWORLD;
        // Due, as the offer asks, so a student back home while the gate stands, passed or failed today, is not sent again.
        Level from = passer.level();
        if (out && (!trial(passer).dueOn(KidoLogic.lessonDay(from.getGameTime(), from.getDayTime()))
                || !ShinigamiLogic.isStudent(SoulLogic.get(passer)))) {
            return null;
        }
        ServerLevel target = gate.leadsTo() == null ? null : BleachWorlds.level(passer.server, gate.leadsTo());
        Vec3 spot = target == null ? null
                : out ? TravelEvents.homeward(passer, target) : AcademyLocator.landing(passer, target);
        if (spot == null) {
            passer.displayClientMessage(Component.translatable(TravelEvents.NO_LANDING_KEY)
                    .withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        GlobalPos left = GlobalPos.of(passer.level().dimension(), passer.blockPosition());
        float yaw = out ? passer.getYRot() : AcademyLocator.LANDING_YAW;
        return new DimensionTransition(target, spot, Vec3.ZERO, yaw, 0.0F, entity -> {
            TravelEvents.arrived(entity, left);
            if (entity instanceof ServerPlayer student) {
                cameThrough(student, out);
            }
        });
    }

    /** Through a trial's gate: out, the trial begins there and every gate home is owed; home, none is, and it is told. */
    private static void cameThrough(ServerPlayer student, boolean out) {
        if (out) {
            begin(student, student.level().getGameTime());
            student.setData(SoulAttachments.FIELD_TRIAL, trial(student).sentOut());
        } else {
            student.setData(SoulAttachments.FIELD_TRIAL, trial(student).cameHome());
            student.sendSystemMessage(Component.translatable(ARRIVED_KEY)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * A student owed gates home is owed none once out of the Human World or no student; one standing in it, alive and
     * here, gets the next before it, unless one of its own stands within {@link #STRAY} or the spot is barred.
     */
    private static void tendGateHome(ServerPlayer student, long now) {
        if (student.level().dimension() != Level.OVERWORLD || !ShinigamiLogic.isStudent(SoulLogic.get(student))) {
            student.setData(SoulAttachments.FIELD_TRIAL, trial(student).cameHome());
            return;
        }
        // The dead wait to respawn; one logging out is leaving, and is owed it when it returns.
        if (!student.isAlive() || student.isSpectator() || student.hasDisconnected() || !gatesFor(student).isEmpty()
                || barred(student, null) != null) {
            return;
        }
        Senkaimon gate = Senkaimon.open(student.serverLevel(), student, null, now);
        if (gate != null) {
            gate.forTrial(student.getUUID());
            FieldTrial after = trial(student).gateHomeOpened();
            student.setData(SoulAttachments.FIELD_TRIAL, after);
            student.displayClientMessage(Component.translatable(after.gatesHome() > 0 ? GATE_HOME_KEY : LAST_GATE_KEY)
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** This student's trial gates within {@link #STRAY}: the instructor's, or one home. */
    private static List<Senkaimon> gatesFor(ServerPlayer student) {
        UUID id = student.getUUID();
        return student.serverLevel().getEntitiesOfClass(Senkaimon.class, student.getBoundingBox().inflate(STRAY),
                gate -> id.equals(gate.trialFor()));
    }

    // ---- The hunt ----

    /**
     * The trial begins where the student stands, as {@code /bleach trial} and the instructor begin it: its record, its bar,
     * the line, and the roll for a real Hollow breaking in.
     */
    public static void begin(ServerPlayer student, long now) {
        endHunt(student);
        student.setData(SoulAttachments.FIELD_TRIAL, trial(student).begun(now));
        ServerBossEvent bar = new ServerBossEvent(barName(0), BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(student);
        long breakInAt = student.getRandom().nextDouble() < BleachConfig.get(BleachConfig.FIELD_TRIAL_BREAK_IN_CHANCE)
                ? now + BREAK_IN_EARLIEST + student.getRandom().nextInt(BREAK_IN_WINDOW) : ShinigamiProgress.NEVER;
        HUNTS.put(student.getUUID(), new Hunt(now, bar, breakInAt));
        student.sendSystemMessage(Component.translatable(BEGUN_KEY, NEEDED, TRIAL_TICKS / 1200)
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Once a second for each player, from its tick; public for tests. A trial under way ends quietly for a soul no longer
     * a student; fails, cut short, with no hunt left of it (the server stopped or crashed), or with its time up; ends
     * unmarked if no practice Hollow could come in its first 30 seconds; and otherwise keeps them coming, and lets a real
     * Hollow in at its time; with none under way, a gate home owed is tended.
     */
    public static void step(ServerPlayer student, long now) {
        FieldTrial trial = trial(student);
        if (!trial.running()) {
            if (trial.gatesHome() > 0) {
                tendGateHome(student, now);
            }
            return;
        }
        Hunt hunt = HUNTS.get(student.getUUID());
        if (!ShinigamiLogic.isStudent(SoulLogic.get(student))) {
            end(student, trial.stopped(), null);
        } else if (hunt == null) {
            fail(student, CUT_SHORT_KEY);
        } else if (now - hunt.start >= TRIAL_TICKS) {
            fail(student, TIME_KEY);
        } else if (!hunt.placed && now - hunt.start >= GROUND_TICKS && practiceNear(student, hunt).isEmpty()) {
            end(student, trial.stopped(), NO_GROUND_KEY);
        } else {
            if (practiceNear(student, hunt).size() < Math.min(AT_ONCE, NEEDED - hunt.slain)) {
                spawnPractice(student, hunt);
            }
            hunt.bar.setProgress(Mth.clamp(1.0F - (float) (now - hunt.start) / TRIAL_TICKS, 0.0F, 1.0F));
            breakIn(student, hunt, now);
        }
    }

    /**
     * One practice Hollow of a kind the student's reiatsu draws, on open ground 10 to 20 blocks off (or, under a roof,
     * on a floor near its height), facing it and set on it.
     */
    private static void spawnPractice(ServerPlayer student, Hunt hunt) {
        ServerLevel level = student.serverLevel();
        HollowKind kind = HollowLogic.pickArrival(SoulLogic.get(student).maxReiryoku(), false, level.getRandom());
        EntityType<? extends Hollow> type = ModEntities.hollowType(kind);
        Vec3 at = SpawnSpots.openGround(level, student, type, kind.scaleMax, SPOT_MIN, SPOT_MAX, SPOT_TRIES, SPOT_RISE);
        if (at == null) {
            at = SpawnSpots.floorNear(level, student, type, SPOT_MIN, SPOT_MAX, SPOT_TRIES, SPOT_RISE);
        }
        Hollow hollow = at == null ? null : type.create(level);
        if (hollow == null) {
            return;
        }
        float yaw = (float) (Mth.atan2(student.getZ() - at.z, student.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
        hollow.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        hollow.setYHeadRot(yaw);
        hollow.setYBodyRot(yaw);
        EventHooks.finalizeMobSpawn(hollow, level, level.getCurrentDifficultyAt(BlockPos.containing(at)),
                MobSpawnType.EVENT, null);
        hollow.makePractice(student.getUUID(), hunt.start);
        if (level.addFreshEntity(hollow)) {
            hunt.placed = true;
            hollow.setTarget(student);
            SpiritSenses.particles(level, ParticleTypes.WHITE_ASH, at.x, at.y + hollow.getBbHeight() * 0.5, at.z, 12,
                    hollow.getBbWidth() * 0.4, hollow.getBbHeight() * 0.4, hollow.getBbWidth() * 0.4, 0.02);
        }
    }

    /**
     * Once its time has come, a real Hollow the student's reiatsu draws tears in through a Garganta, told, as an operator's
     * would (no cap, cooldown or Quincy Hunter after); held off, and tried each second, where none may open or beside an
     * Academy instructor.
     */
    private static void breakIn(ServerPlayer student, Hunt hunt, long now) {
        ServerLevel level = student.serverLevel();
        if (hunt.breakInAt == ShinigamiProgress.NEVER || now < hunt.breakInAt || !student.gameMode.isSurvival()
                || !HollowLogic.worldAllowsArrivals(level.getDifficulty(),
                        level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING), level.dimension() == Level.OVERWORLD)
                || GargantaEvents.shelteredByInstructor(student)) {
            return;
        }
        // Opened now, whatever time the step was given (a test's), so the tear drops its Hollow on time.
        if (GargantaEvents.openArrival(student, SoulLogic.get(student), null, level.getGameTime(), true)) {
            hunt.breakInAt = ShinigamiProgress.NEVER;
            student.sendSystemMessage(Component.translatable(BREAK_IN_KEY)
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        }
    }

    /** When a real Hollow breaks into this student's trial under way; NEVER for none, or with no hunt. For tests. */
    public static long breakInAt(Player student) {
        Hunt hunt = HUNTS.get(student.getUUID());
        return hunt == null ? ShinigamiProgress.NEVER : hunt.breakInAt;
    }

    /** This student's practice Hollows within {@link #STRAY}, alive, of this hunt or (null) of any. */
    private static List<Hollow> practiceNear(ServerPlayer student, @Nullable Hunt hunt) {
        UUID id = student.getUUID();
        return student.serverLevel().getEntitiesOfClass(Hollow.class, student.getBoundingBox().inflate(STRAY),
                hollow -> hollow.isAlive() && id.equals(hollow.trialOf())
                        && (hunt == null || hollow.trialStart() == hunt.start));
    }

    /**
     * Whether a practice Hollow is still wanted, asked by the Hollow itself: its trial's hunt runs, and its student is
     * alive in its world within {@link #STRAY}. One that is not fades.
     */
    public static boolean keeps(Hollow hollow) {
        ServerPlayer student = studentOf(hollow);
        Hunt hunt = student == null ? null : HUNTS.get(student.getUUID());
        return hunt != null && hunt.start == hollow.trialStart() && student.isAlive()
                && student.level() == hollow.level() && hollow.distanceToSqr(student) <= STRAY * STRAY;
    }

    /** The student whose trial this practice Hollow belongs to, if it is on the server; null for a real Hollow. */
    @Nullable
    public static ServerPlayer studentOf(Hollow hollow) {
        UUID id = hollow.trialOf();
        return id == null || hollow.getServer() == null ? null : hollow.getServer().getPlayerList().getPlayer(id);
    }

    /**
     * A practice Hollow died, which gives nothing a slain Hollow would (HollowEvents leaves it here): one slain by its
     * own student in its own trial counts, and the fifth passes.
     */
    public static void onPracticeDeath(Hollow hollow, DamageSource source) {
        Hunt hunt = hollow.trialOf() == null ? null : HUNTS.get(hollow.trialOf());
        if (hunt == null || hunt.start != hollow.trialStart() || !(source.getEntity() instanceof ServerPlayer student)
                || !student.getUUID().equals(hollow.trialOf())) {
            return;
        }
        hunt.slain++;
        hunt.bar.setName(barName(hunt.slain));
        if (hunt.slain >= NEEDED) {
            end(student, trial(student).won(), null);
            student.sendSystemMessage(Component.translatable(PASSED_KEY).withStyle(ChatFormatting.GOLD));
            student.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** How many practice Hollows this student has slain in its trial under way; -1 with no hunt. For tests. */
    public static int slain(Player student) {
        Hunt hunt = HUNTS.get(student.getUUID());
        return hunt == null ? -1 : hunt.slain;
    }

    /** This student's trial bar, or null with no hunt. For tests. */
    @Nullable
    public static ServerBossEvent bar(Player student) {
        Hunt hunt = HUNTS.get(student.getUUID());
        return hunt == null ? null : hunt.bar;
    }

    private static Component barName(int slain) {
        return Component.translatable(BAR_KEY, slain, NEEDED);
    }

    // ---- The ends ----

    /** The trial fails, told by this line (none for null): the lesson's day is marked, so the next waits for a new one. */
    private static void fail(ServerPlayer student, @Nullable String key) {
        Level level = student.level();
        end(student, trial(student).lost(KidoLogic.lessonDay(level.getGameTime(), level.getDayTime())), key);
    }

    /**
     * The trial ends as {@code after} records it, and its hunt with it; the student is told this line (none for null), and
     * a gate home it is owed opens at once.
     */
    private static void end(ServerPlayer student, FieldTrial after, @Nullable String key) {
        student.setData(SoulAttachments.FIELD_TRIAL, after);
        endHunt(student);
        if (key != null) {
            student.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (after.gatesHome() > 0) {
            tendGateHome(student, student.level().getGameTime());
        }
    }

    /**
     * The hunt ends, the record untouched: the bar comes down and the student's practice Hollows about it fade (any
     * further off fade as they next look for their trial).
     */
    private static void endHunt(ServerPlayer student) {
        Hunt hunt = HUNTS.remove(student.getUUID());
        if (hunt != null) {
            hunt.bar.removeAllPlayers();
        }
        practiceNear(student, null).forEach(hollow -> hollow.dissolve(student.serverLevel()));
    }

    /** Takes the trial off this soul, under way or not, record and all: {@code /bleach reset}, graduation, and tests. */
    public static void forget(Player player) {
        if (player instanceof ServerPlayer student) {
            endHunt(student);
        }
        player.removeData(SoulAttachments.FIELD_TRIAL);
    }

    // ---- Listeners ----

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            step(player, player.level().getGameTime());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer student && running(student)) {
            fail(student, DIED_KEY);
        }
    }

    /** Out of the Human World, a student is owed no gate home, and its trial under way fails. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer student && event.getFrom() == Level.OVERWORLD) {
            if (trial(student).gatesHome() > 0) {
                student.setData(SoulAttachments.FIELD_TRIAL, trial(student).cameHome());
            }
            if (running(student)) {
                fail(student, LEFT_KEY);
            }
        }
    }

    /**
     * A logout fails the trial there and then, before the student's save; no one is left to tell. One as the server stops
     * or crashes ends only the hunt, so the trial, saved under way, fails, cut short and told, in its first second back.
     */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer student && running(student)) {
            if (student.server.isStopped()) {
                endHunt(student);
            } else {
                fail(student, null);
            }
        }
    }

    /**
     * Once the server has stopped or crashed, saving its players first, every hunt left comes down, so none outlives its
     * world in a game that opens another; each trial saved under way fails, cut short, in its student's first second back.
     */
    private static void onServerStopped(ServerStoppedEvent event) {
        HUNTS.values().forEach(hunt -> hunt.bar.removeAllPlayers());
        HUNTS.clear();
    }
}
