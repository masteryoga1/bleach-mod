package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A soul's Hollow Bait and its dealings with the young Quincy Hunter: baits crushed, whether the
 * Hunter has come to it and when he last did, the contests won and lost against him, and the baits
 * that ended with the sky cracking. Part of {@link HuntRecord}, so it is saved with the soul and sent
 * to its owner's client. The rules are in {@link BaitLogic} and {@link QuincyNpcLogic}.
 *
 * @param crushed      Hollow Baits crushed, by hand or by command
 * @param hunterMet    whether the Hunter has turned up for this soul
 * @param lastHunterAt when the Hunter last came, or {@link #NEVER}
 * @param won          contests this soul won against him
 * @param lost         contests this soul lost to him
 * @param finales      baits of this soul's that drew enough Hollows to crack the sky
 */
public record BaitRecord(int crushed, boolean hunterMet, long lastHunterAt, int won, int lost, int finales) {
    public static final long NEVER = ShinigamiProgress.NEVER;

    public static final BaitRecord NONE = new BaitRecord(0, false, NEVER, 0, 0, 0);

    // Every field is read leniently, so one bad value falls back to its default on its own.
    public static final Codec<BaitRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("crushed", 0).forGetter(BaitRecord::crushed),
            Codec.BOOL.lenientOptionalFieldOf("hunter_met", false).forGetter(BaitRecord::hunterMet),
            Codec.LONG.lenientOptionalFieldOf("last_hunter_at", NEVER).forGetter(BaitRecord::lastHunterAt),
            Codec.INT.lenientOptionalFieldOf("won", 0).forGetter(BaitRecord::won),
            Codec.INT.lenientOptionalFieldOf("lost", 0).forGetter(BaitRecord::lost),
            Codec.INT.lenientOptionalFieldOf("finales", 0).forGetter(BaitRecord::finales)
    ).apply(instance, BaitRecord::new));

    /** No count is ever negative. */
    public BaitRecord {
        crushed = Math.max(0, crushed);
        won = Math.max(0, won);
        lost = Math.max(0, lost);
        finales = Math.max(0, finales);
    }

    public BaitRecord withCrushed(int value) {
        return new BaitRecord(value, hunterMet, lastHunterAt, won, lost, finales);
    }

    public BaitRecord withHunterMet(boolean value) {
        return new BaitRecord(crushed, value, lastHunterAt, won, lost, finales);
    }

    public BaitRecord withLastHunterAt(long value) {
        return new BaitRecord(crushed, hunterMet, value, won, lost, finales);
    }

    public BaitRecord withWon(int value) {
        return new BaitRecord(crushed, hunterMet, lastHunterAt, value, lost, finales);
    }

    public BaitRecord withLost(int value) {
        return new BaitRecord(crushed, hunterMet, lastHunterAt, won, value, finales);
    }

    public BaitRecord withFinales(int value) {
        return new BaitRecord(crushed, hunterMet, lastHunterAt, won, lost, value);
    }
}
