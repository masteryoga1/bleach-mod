package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import net.minecraft.world.Difficulty;

/**
 * The rules for the two living Quincy who walk the Human World: the old Elder who visits those with
 * Quincy blood to teach them, and the proud young Hunter who turns up where Hollows come through.
 * When each comes, what each says to whom, and how the Hunter shoots. Like {@link QuincyLogic}, these
 * read only their arguments and the config, so game tests can call them directly. {@code now} is the
 * level's game time, and {@code day} a training day as {@link QuincyArtsLogic#gloveDay} counts them.
 * The NPCs themselves are in the entity package; their comings and goings in {@link QuincyNpcEvents}.
 */
public final class QuincyNpcLogic {
    /** Why the Elder comes to a soul, most pressing first; NONE means he does not. */
    public enum Visit {
        NONE,
        /** The soul's power has been sealed a long while: he can bring it back. */
        RESTORE,
        /** A third-tier Quincy who does not know Ransōtengai yet. */
        TEACH,
        /** Today's lesson is still to be given. */
        LESSON
    }

    /** Ticks between visit checks for each soul: one a minute. */
    public static final int VISIT_CHECK_TICKS = 1200;
    /** How long a visit lasts if he is not spoken to: five minutes. */
    public static final int VISIT_LIFETIME = 6000;
    /** At most this long once he has been spoken to, and this once his business is done. */
    public static final int TALKED_LIFETIME = 2400, DONE_LIFETIME = 600;
    /** No two Quincy NPCs of a kind this close; no visit with a Hollow this close. */
    public static final double NPC_SPACING = 128.0, NO_HOLLOW_RADIUS = 32.0;
    /** Where they come down: the Elder close by, the Hunter farther off, where the fight is. */
    public static final int ELDER_SPOT_MIN = 8, ELDER_SPOT_MAX = 16, HUNTER_SPOT_MIN = 16, HUNTER_SPOT_MAX = 24,
            SPOT_TRIES = 12, SPOT_MAX_RISE = 12;

    private static final long TICKS_PER_MINUTE = 1200L;

    private QuincyNpcLogic() {
    }

    // ---- Souls ----

    /** Whether Quincy blood runs in this soul, latent or awakened, with the question settled. */
    public static boolean hasQuincyBlood(SoulData d) {
        return d.quincy().decided() && QuincyLogic.bloodline(d) != Bloodline.NONE;
    }

    /** A living Human or a Shinigami, a hybrid included: not a Plus, a Hollow or an Arrancar. */
    public static boolean livingOrShinigami(SoulData d) {
        return d.state() == SoulState.HUMAN || d.state() == SoulState.SHINIGAMI;
    }

    /**
     * Gives a Quincy NPC its soul: a living Human of this blood, its training enough for this tier
     * and the tier itself, and this much reiryoku, full. Its arrows and the blows against it then go
     * through the reiatsu difference like anyone's.
     */
    public static void initSoul(SoulData soul, Bloodline bloodline, int tier, float reiryoku) {
        SoulLogic.initialize(soul);
        SoulLogic.changeState(soul, SoulState.HUMAN);
        soul.setQuincy(soul.quincy().withBloodline(bloodline).withDecided(true)
                .withTraining(QuincyLogic.trainingForTier(tier)));
        soul.setTier(PowerPath.QUINCY, tier);
        soul.setMaxReiryoku(reiryoku);
        soul.setReiryoku(reiryoku);
    }

    // ---- The Elder ----

    /**
     * Why the Elder would come to this soul now: to restore a power sealed for {@code restoreAfter}
     * ticks or more, to teach Ransōtengai to a third-tier Quincy who can use the power, or to give the
     * day's lesson. A sealed soul gets no lessons. Whether the soul may be visited at all is
     * {@link QuincyNpcEvents#elderMayVisit}'s question.
     */
    public static Visit visitReason(SoulData d, long now, long day, int maxLessons, long restoreAfter) {
        if (QuincyArtsLogic.isSealed(d)) {
            long since = QuincyArtsLogic.arts(d).stageSince();
            return now >= since && now - since >= restoreAfter ? Visit.RESTORE : Visit.NONE;
        }
        if (canBeTaught(d)) {
            return Visit.TEACH;
        }
        return lessonAvailable(d, day, maxLessons) ? Visit.LESSON : Visit.NONE;
    }

    /** As {@link #visitReason(SoulData, long, long, int, long)}, with the configured lessons and wait. */
    public static Visit visitReason(SoulData d, long now, long day) {
        return visitReason(d, now, day, BleachConfig.get(BleachConfig.ELDER_MAX_LESSONS),
                BleachConfig.get(BleachConfig.ELDER_RESTORE_AFTER_TICKS));
    }

    /**
     * Whether the Elder's wait since his last visit is over. A soul he never visited waits from the
     * world's first tick, so a new world sees no Elder in its first minutes; a visit stamped in the
     * future (a soul brought from another world) does not hold him back.
     */
    public static boolean visitDue(QuincyTeaching t, long now, long cooldownTicks) {
        long last = t.lastVisitAt();
        if (last == QuincyTeaching.NEVER) {
            return now >= cooldownTicks;
        }
        return now < last || now - last >= cooldownTicks;
    }

    /** Whether the Elder has a lesson for this soul today: not sealed, not taught today, not taught them all. */
    public static boolean lessonAvailable(SoulData d, long day, int maxLessons) {
        QuincyTeaching teaching = d.quincy().teaching();
        return !QuincyArtsLogic.isSealed(d) && teaching.lessons() < maxLessons && teaching.lessonDay() != day;
    }

    /** A third-tier Quincy who can use the power and has not learned Ransōtengai. */
    private static boolean canBeTaught(SoulData d) {
        return QuincyLogic.tier(d) >= QuincyLogic.MAX_TIER && QuincyLogic.canUsePowers(d)
                && !d.quincy().teaching().ransotengaiKnown();
    }

    /**
     * What the Elder says to this soul, in order: nothing to a Hollow, and nothing to one without the
     * blood; restoring a sealed power; a greeting the first time; the open trial; the teaching of
     * Ransōtengai; the day's lesson; otherwise that it is enough for today. The trial is read from the
     * record directly (it is open while {@code trialUntil} is ahead), as {@code QuincyRansotengai} does.
     */
    public static DialogueView elderView(SoulData d, long now, long day, int maxLessons) {
        if (d.state() == SoulState.HOLLOW || d.state() == SoulState.ARRANCAR) {
            return DialogueView.ELDER_HOLLOW;
        }
        if (!hasQuincyBlood(d)) {
            return DialogueView.ELDER_STRANGER;
        }
        if (QuincyArtsLogic.isSealed(d)) {
            return DialogueView.ELDER_RESTORE;
        }
        QuincyTeaching teaching = d.quincy().teaching();
        if (!teaching.elderMet()) {
            return DialogueView.ELDER_GREETING;
        }
        if (teaching.trialUntil() > now) {
            return DialogueView.ELDER_TRIAL;
        }
        if (canBeTaught(d)) {
            return DialogueView.ELDER_TEACH;
        }
        return lessonAvailable(d, day, maxLessons) ? DialogueView.ELDER_LESSON : DialogueView.ELDER_IDLE;
    }

    // ---- The Hunter ----

    /**
     * What the Hunter says to this soul, in order: a warning to a Hollow; to the soul he is in a
     * contest with, to get on with it; to anyone once his one contest of the visit is done, that they
     * are done; a challenge to an awakened Quincy (sealed or a hybrid alike) or to one with a
     * Shinigami's power; and to anyone else, the unaware too, to go home.
     */
    public static DialogueView hunterView(SoulData d, boolean contestWithVisitor, boolean contestOffered) {
        if (d.state() == SoulState.HOLLOW || d.state() == SoulState.ARRANCAR) {
            return DialogueView.HUNTER_HOLLOW;
        }
        if (contestWithVisitor) {
            return DialogueView.HUNTER_CONTEST;
        }
        if (contestOffered) {
            return DialogueView.HUNTER_RESTING;
        }
        if (QuincyLogic.isAwakened(d)) {
            return DialogueView.HUNTER_RIVALRY;
        }
        return ShinigamiLogic.hasShinigamiPower(d) ? DialogueView.HUNTER_CHALLENGE : DialogueView.HUNTER_PLAIN;
    }

    /**
     * Whether the Hunter follows a Garganta that opened for this soul. Never within
     * {@code cooldownTicks} of his last coming (never coming counts as long ago); surely the first
     * time, once the soul has purified or slain {@code guaranteeKills} Hollows; otherwise when
     * {@code roll} (from 0 to 1) falls under {@code chance}.
     */
    public static boolean hunterArrives(SoulData d, long now, double roll, double chance, int guaranteeKills,
                                        long cooldownTicks) {
        HuntRecord hunt = d.hunt();
        BaitRecord bait = hunt.bait();
        long last = bait.lastHunterAt();
        if (last != BaitRecord.NEVER && now >= last && now - last < cooldownTicks) {
            return false;
        }
        if (!bait.hunterMet() && (long) hunt.purified() + hunt.slain() >= guaranteeKills) {
            return true;
        }
        return roll < chance;
    }

    /**
     * As {@link #hunterArrives(SoulData, long, double, double, int, long)}, with the configured chance,
     * guarantee and wait.
     */
    public static boolean hunterArrives(SoulData d, long now, double roll) {
        return hunterArrives(d, now, roll, BleachConfig.get(BleachConfig.HUNTER_CHANCE),
                BleachConfig.get(BleachConfig.HUNTER_GUARANTEE_KILLS),
                BleachConfig.get(BleachConfig.HUNTER_COOLDOWN_MINUTES) * TICKS_PER_MINUTE);
    }

    /** Whether this arrival could bring the Hunter: a natural one, and not the first Hollow. */
    public static boolean hunterMayCome(HollowKind kind, boolean byCommand) {
        return !byCommand && kind != HollowKind.FISHBONE_D;
    }

    /** Ticks the Hunter waits between shots after the draw: quicker the harder the world. Peaceful counts as Easy. */
    public static int shotInterval(Difficulty difficulty) {
        return switch (difficulty) {
            case NORMAL -> 30;
            case HARD -> 20;
            default -> 40;
        };
    }

    /** How far the Hunter's shots stray, in the arrow's inaccuracy units: truer the harder the world. */
    public static float hunterInaccuracy(Difficulty difficulty) {
        return switch (difficulty) {
            case NORMAL -> 1.2F;
            case HARD -> 0.6F;
            default -> 2.0F;
        };
    }
}
