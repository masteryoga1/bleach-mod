package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A soul's dealings with Hollows and Pluses: when it first became aware, whether it has met its
 * first Hollow, when the last Garganta opened for it, and how many Hollows it has purified or slain
 * and Pluses it has sent on. Saved with the soul and sent to its owner's client. Times are game times
 * in ticks; {@link #NEVER} means it never happened.
 *
 * @param awareSincePlayTicks the soul's play time, in ticks, when it was first seen to be aware (-1 until then)
 * @param firstHollowDone     whether the first Hollow (Fishbone D) has come and gone for good
 * @param lastArrivalAt       when a Garganta last opened for this soul, or the first Hollow last left
 * @param purified            Hollows this soul has purified with a zanpakutō
 * @param slain               Hollows this soul has destroyed without purifying them
 * @param sentOn              Pluses, players or not, this soul has sent on with Konsō
 * @param maskHinted          whether this soul has been told to aim for the mask
 */
public record HuntRecord(int awareSincePlayTicks, boolean firstHollowDone, long lastArrivalAt,
                         int purified, int slain, int sentOn, boolean maskHinted) {
    public static final long NEVER = ShinigamiProgress.NEVER;

    public static final HuntRecord NONE = new HuntRecord(-1, false, NEVER, 0, 0, 0, false);

    // Every field is read leniently, so one bad value falls back to its default on its own.
    public static final Codec<HuntRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("aware_since_play_ticks", -1).forGetter(HuntRecord::awareSincePlayTicks),
            Codec.BOOL.lenientOptionalFieldOf("first_hollow_done", false).forGetter(HuntRecord::firstHollowDone),
            Codec.LONG.lenientOptionalFieldOf("last_arrival_at", NEVER).forGetter(HuntRecord::lastArrivalAt),
            Codec.INT.lenientOptionalFieldOf("purified", 0).forGetter(HuntRecord::purified),
            Codec.INT.lenientOptionalFieldOf("slain", 0).forGetter(HuntRecord::slain),
            Codec.INT.lenientOptionalFieldOf("sent_on", 0).forGetter(HuntRecord::sentOn),
            Codec.BOOL.lenientOptionalFieldOf("mask_hinted", false).forGetter(HuntRecord::maskHinted)
    ).apply(instance, HuntRecord::new));

    public HuntRecord withAwareSincePlayTicks(int value) {
        return new HuntRecord(value, firstHollowDone, lastArrivalAt, purified, slain, sentOn, maskHinted);
    }

    public HuntRecord withFirstHollowDone(boolean value) {
        return new HuntRecord(awareSincePlayTicks, value, lastArrivalAt, purified, slain, sentOn, maskHinted);
    }

    public HuntRecord withLastArrivalAt(long value) {
        return new HuntRecord(awareSincePlayTicks, firstHollowDone, value, purified, slain, sentOn, maskHinted);
    }

    public HuntRecord withPurified(int value) {
        return new HuntRecord(awareSincePlayTicks, firstHollowDone, lastArrivalAt, value, slain, sentOn, maskHinted);
    }

    public HuntRecord withSlain(int value) {
        return new HuntRecord(awareSincePlayTicks, firstHollowDone, lastArrivalAt, purified, value, sentOn, maskHinted);
    }

    public HuntRecord withSentOn(int value) {
        return new HuntRecord(awareSincePlayTicks, firstHollowDone, lastArrivalAt, purified, slain, value, maskHinted);
    }

    public HuntRecord withMaskHinted(boolean value) {
        return new HuntRecord(awareSincePlayTicks, firstHollowDone, lastArrivalAt, purified, slain, sentOn, value);
    }
}
