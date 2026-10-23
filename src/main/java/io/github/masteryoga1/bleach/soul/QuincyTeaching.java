package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a Quincy Elder has taught this soul: whether they have met, how many lessons he has given and
 * on which day the last one was, when he last came by, and Ransōtengai, learned or being tried for.
 * Part of {@link QuincyProgress}, so it is saved with the soul and sent to its owner's client. The
 * rules are in {@link QuincyNpcLogic}. Times are game times in ticks; {@link #NEVER} means never.
 *
 * @param elderMet         whether the soul has talked with an Elder
 * @param lessons          lessons given in all, at most the configured number
 * @param lessonDay        the training day of the last lesson (see {@code QuincyArtsLogic.gloveDay}), or -1
 * @param lastVisitAt      when an Elder last came to the soul, talked to or not
 * @param ransotengaiKnown whether the soul has learned Ransōtengai
 * @param trialUntil       when the Ransōtengai trial the Elder set closes; the trial is open before it
 */
public record QuincyTeaching(boolean elderMet, int lessons, long lessonDay, long lastVisitAt,
                             boolean ransotengaiKnown, long trialUntil) {
    public static final long NEVER = ShinigamiProgress.NEVER;

    public static final QuincyTeaching NONE = new QuincyTeaching(false, 0, -1L, NEVER, false, NEVER);

    // Every field is read leniently, so one bad value falls back to its default on its own.
    public static final Codec<QuincyTeaching> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("elder_met", false).forGetter(QuincyTeaching::elderMet),
            Codec.INT.lenientOptionalFieldOf("lessons", 0).forGetter(QuincyTeaching::lessons),
            Codec.LONG.lenientOptionalFieldOf("lesson_day", -1L).forGetter(QuincyTeaching::lessonDay),
            Codec.LONG.lenientOptionalFieldOf("last_visit_at", NEVER).forGetter(QuincyTeaching::lastVisitAt),
            Codec.BOOL.lenientOptionalFieldOf("ransotengai_known", false).forGetter(QuincyTeaching::ransotengaiKnown),
            Codec.LONG.lenientOptionalFieldOf("trial_until", NEVER).forGetter(QuincyTeaching::trialUntil)
    ).apply(instance, QuincyTeaching::new));

    /** A lesson count is never negative. */
    public QuincyTeaching {
        lessons = Math.max(0, lessons);
    }

    public QuincyTeaching withElderMet(boolean value) {
        return new QuincyTeaching(value, lessons, lessonDay, lastVisitAt, ransotengaiKnown, trialUntil);
    }

    /** One more lesson, given on this training day. */
    public QuincyTeaching withLesson(long day) {
        return new QuincyTeaching(elderMet, lessons + 1, day, lastVisitAt, ransotengaiKnown, trialUntil);
    }

    public QuincyTeaching withLessons(int value) {
        return new QuincyTeaching(elderMet, value, lessonDay, lastVisitAt, ransotengaiKnown, trialUntil);
    }

    public QuincyTeaching withLessonDay(long value) {
        return new QuincyTeaching(elderMet, lessons, value, lastVisitAt, ransotengaiKnown, trialUntil);
    }

    public QuincyTeaching withLastVisitAt(long value) {
        return new QuincyTeaching(elderMet, lessons, lessonDay, value, ransotengaiKnown, trialUntil);
    }

    public QuincyTeaching withRansotengaiKnown(boolean value) {
        return new QuincyTeaching(elderMet, lessons, lessonDay, lastVisitAt, value, trialUntil);
    }

    public QuincyTeaching withTrialUntil(long value) {
        return new QuincyTeaching(elderMet, lessons, lessonDay, lastVisitAt, ransotengaiKnown, value);
    }
}
