package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A soul's drill at the Academy's training dummies: the day ({@link KidoLogic#lessonDay}) its blows last counted, the
 * points they gave that day, and the quarters of a point carried to the next blow. Saved and kept through death but
 * never sent, every field lenient, so a save from before the dummies reads as {@link #NONE}.
 */
public record DummyDrill(long day, int today, int quarters) {
    public static final DummyDrill NONE = new DummyDrill(-1L, 0, 0);
    public static final Codec<DummyDrill> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.lenientOptionalFieldOf("day", -1L).forGetter(DummyDrill::day),
            Codec.INT.lenientOptionalFieldOf("today", 0).forGetter(DummyDrill::today),
            Codec.INT.lenientOptionalFieldOf("quarters", 0).forGetter(DummyDrill::quarters))
            .apply(instance, DummyDrill::new));

    /** No count below zero, and never a whole point carried. */
    public DummyDrill {
        today = Math.max(0, today);
        quarters = Math.floorMod(quarters, 4);
    }

    /** The points blows gave on this day: none yet if the record is from an earlier one. */
    public int todayOn(long on) {
        return day == on ? today : 0;
    }

    /**
     * After a blow worth {@code add} quarters of a point on this day: each whole four is a point, up to {@code perDay}
     * points that day, and the rest of a four is carried, so no quarter is lost or counted twice.
     */
    public DummyDrill blow(int add, int perDay, long on) {
        int total = quarters + Math.max(0, add);
        int counted = todayOn(on);
        return new DummyDrill(on, counted + Math.min(total / 4, Math.max(0, perDay - counted)), total % 4);
    }
}
