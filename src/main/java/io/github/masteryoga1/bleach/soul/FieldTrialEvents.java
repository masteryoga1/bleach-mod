package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.registry.ModEntities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Academy's field trial, a hunt in the Human World: practice Hollows come for the student alone, two at a time, and
 * five slain within five minutes pass it. Time running out, death, leaving the Human World or logging out fails it, the
 * lesson's day marked; every end fades the practice Hollows, and the student stays where it stands.
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

    /** A trial's hunt while its student is here: when it began, its bar, how many it has slain, whether any came. */
    private static final class Hunt {
        final long start;
        final ServerBossEvent bar;
        int slain;
        boolean placed;

        Hunt(long start, ServerBossEvent bar) {
            this.start = start;
            this.bar = bar;
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

    /** Whether this soul's trial is under way: no Garganta tears near it, and no Shinigami on duty comes for it. */
    public static boolean running(Player player) {
        return trial(player).running();
    }

    // ---- The hunt ----

    /** The trial begins where the student stands, as {@code /bleach trial} begins it: its record, its bar and the line. */
    public static void begin(ServerPlayer student, long now) {
        endHunt(student);
        student.setData(SoulAttachments.FIELD_TRIAL, trial(student).begun(now));
        ServerBossEvent bar = new ServerBossEvent(barName(0), BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(student);
        HUNTS.put(student.getUUID(), new Hunt(now, bar));
        student.sendSystemMessage(Component.translatable(BEGUN_KEY, NEEDED, TRIAL_TICKS / 1200)
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Once a second for each player, from its tick; public for tests. A trial under way ends quietly for a soul no longer
     * a student; fails, cut short, with no hunt left of it (the server stopped or crashed), or with its time up; ends
     * unmarked if no practice Hollow could come in its first 30 seconds; and otherwise keeps them coming.
     */
    public static void step(ServerPlayer student, long now) {
        FieldTrial trial = trial(student);
        if (!trial.running()) {
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

    /** The trial ends as {@code after} records it, and its hunt with it; the student is told this line (none for null). */
    private static void end(ServerPlayer student, FieldTrial after, @Nullable String key) {
        student.setData(SoulAttachments.FIELD_TRIAL, after);
        endHunt(student);
        if (key != null) {
            student.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
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

    /** Takes the trial off this soul, under way or not, record and all: {@code /bleach reset}, and tests. */
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

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer student && event.getFrom() == Level.OVERWORLD && running(student)) {
            fail(student, LEFT_KEY);
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
