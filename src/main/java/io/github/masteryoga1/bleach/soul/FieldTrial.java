package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A student's field trial at the Academy: whether it has passed one, the day ({@link KidoLogic#lessonDay}) it last failed
 * one, and the game time the trial under way began ({@link ShinigamiProgress#NEVER} while none is). Saved and kept
 * through death but never sent, every field lenient, so a save from before the trial reads as {@link #NONE}.
 */
public record FieldTrial(boolean passed, long failedDay, long startedAt) {
    public static final FieldTrial NONE = new FieldTrial(false, -1L, ShinigamiProgress.NEVER);
    public static final Codec<FieldTrial> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("passed", false).forGetter(FieldTrial::passed),
            Codec.LONG.lenientOptionalFieldOf("failed_day", -1L).forGetter(FieldTrial::failedDay),
            Codec.LONG.lenientOptionalFieldOf("started_at", ShinigamiProgress.NEVER).forGetter(FieldTrial::startedAt))
            .apply(instance, FieldTrial::new));

    /** Whether a trial is under way. */
    public boolean running() {
        return startedAt != ShinigamiProgress.NEVER;
    }

    /** Whether a trial is due on this day: none passed or under way, and none failed that day, so a failure waits a day. */
    public boolean dueOn(long day) {
        return !passed && !running() && failedDay != day;
    }

    /** This record, a trial begun now, which forgets any failure before it. */
    public FieldTrial begun(long now) {
        return new FieldTrial(passed, -1L, now);
    }

    /** This record, the trial passed. */
    public FieldTrial won() {
        return new FieldTrial(true, failedDay, ShinigamiProgress.NEVER);
    }

    /** This record, the trial failed on this day. */
    public FieldTrial lost(long day) {
        return new FieldTrial(passed, day, ShinigamiProgress.NEVER);
    }

    /** This record, the trial over with nothing marked, neither passed nor failed. */
    public FieldTrial stopped() {
        return new FieldTrial(passed, failedDay, ShinigamiProgress.NEVER);
    }
}
