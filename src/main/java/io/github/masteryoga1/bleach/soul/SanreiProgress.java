package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Quincy's week in the Sanrei Glove: whether it was handed down, whether it is on the hand now, how
 * many separate days of training with it have counted, which day the soul is on and how far today's
 * training has come, and whether the glove is mastered. Part of {@link QuincyArts}; the rules are in
 * {@code QuincyArtsLogic}.
 *
 * @param given    whether the glove has been handed down, so it is handed down only once
 * @param worn     whether the glove is on the soul's hand. A worn glove is part of the soul, not an
 *                 item, so it is never dropped
 * @param days     days of training that have counted, towards mastering it
 * @param day      the day {@code today} belongs to, counted in whole Minecraft days
 * @param today    training points earned on that day
 * @param mastered whether the glove no longer fights its wearer. Kept as a record once it is torn off
 */
public record SanreiProgress(boolean given, boolean worn, int days, long day, int today, boolean mastered) {
    public static final SanreiProgress NONE = new SanreiProgress(false, false, 0, 0L, 0, false);

    // Every field is read leniently, so one bad value falls back to its default on its own.
    public static final Codec<SanreiProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("given", false).forGetter(SanreiProgress::given),
            Codec.BOOL.lenientOptionalFieldOf("worn", false).forGetter(SanreiProgress::worn),
            Codec.INT.lenientOptionalFieldOf("days", 0).forGetter(SanreiProgress::days),
            Codec.LONG.lenientOptionalFieldOf("day", 0L).forGetter(SanreiProgress::day),
            Codec.INT.lenientOptionalFieldOf("today", 0).forGetter(SanreiProgress::today),
            Codec.BOOL.lenientOptionalFieldOf("mastered", false).forGetter(SanreiProgress::mastered)
    ).apply(instance, SanreiProgress::new));

    /** Counts are never negative. */
    public SanreiProgress {
        days = Math.max(0, days);
        today = Math.max(0, today);
    }

    public SanreiProgress withGiven(boolean value) {
        return new SanreiProgress(value, worn, days, day, today, mastered);
    }

    public SanreiProgress withWorn(boolean value) {
        return new SanreiProgress(given, value, days, day, today, mastered);
    }

    public SanreiProgress withDays(int value) {
        return new SanreiProgress(given, worn, value, day, today, mastered);
    }

    public SanreiProgress withDay(long value) {
        return new SanreiProgress(given, worn, days, value, today, mastered);
    }

    public SanreiProgress withToday(int value) {
        return new SanreiProgress(given, worn, days, day, value, mastered);
    }

    public SanreiProgress withMastered(boolean value) {
        return new SanreiProgress(given, worn, days, day, today, value);
    }
}
