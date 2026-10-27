package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * A soul's life as a Hollow: its soul hunger, the souls it has devoured and what they were, whether it
 * sees in the dark, when it last fed, whether the blow that killed it purified it, how far it has come
 * as a Menos Grande, and its road out of the mask as an Arrancar. The last of {@link SoulData}'s sixteen
 * saved fields, so every later Hollow field (mutations, the Arrancar's arts) goes in here or in a record
 * nested in it, as {@link Evolution} and {@link Arrancar} do.
 * Saved with the soul and sent to its owner's client. The rules are in {@link HollowPlayerLogic}, and
 * what they do to the body in {@link HollowBody}. Times are game times in ticks; {@link #NEVER} means
 * it never happened.
 *
 * @param satiety         soul hunger, from 0 (starving) to {@link #MAX_SATIETY}, or {@link #UNSET} before
 *                        the hunger starts (every soul that is not a Hollow, and a Hollow before its first tick)
 * @param souls           souls devoured and held within, which a death thins out
 * @param pluses          Plus spirits and Plus players devoured
 * @param hollows         Hollow mobs devoured, by kind
 * @param players         players devoured
 * @param sight           whether the soul wants its Hollow sight (night vision) while a Hollow
 * @param lastMealAt      when it last devoured a soul
 * @param purifiedOnDeath whether the blow that killed it purified it, from that death until the respawn it
 *                        comes back as a soul for, so logging out on the death screen does not lose it
 * @param evolution       its road through the Menos forms (see {@link MenosEvolution})
 * @param arrancar        its road out of the mask: the tries at tearing it, the blade and the release (see
 *                        {@link ArrancarEvents}), kept whatever the soul becomes
 */
public record HollowProgress(float satiety, int souls, int pluses, Map<HollowKind, Integer> hollows, int players,
                             boolean sight, long lastMealAt, boolean purifiedOnDeath, Evolution evolution,
                             Arrancar arrancar) {
    public static final float UNSET = -1.0F;
    public static final float MAX_SATIETY = 100.0F;
    public static final long NEVER = Long.MIN_VALUE / 2;

    public static final HollowProgress NONE = new HollowProgress(UNSET, 0, 0, Map.of(), 0, true, NEVER, false);

    // Read leniently, as the soul's own maps are: a kind this version does not know is dropped.
    private static final Codec<Map<HollowKind, Integer>> KIND_MAP =
            SoulData.enumKeyedMap(HollowKind.class, HollowKind.values());

    // Every field is read leniently, so one bad value falls back to its default on its own, and a save
    // from before Hollow players has no "hollow" at all and reads as NONE. Ten of the builder's sixteen fields.
    public static final Codec<HollowProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.lenientOptionalFieldOf("satiety", UNSET).forGetter(HollowProgress::satiety),
            Codec.INT.lenientOptionalFieldOf("souls", 0).forGetter(HollowProgress::souls),
            Codec.INT.lenientOptionalFieldOf("pluses", 0).forGetter(HollowProgress::pluses),
            KIND_MAP.lenientOptionalFieldOf("hollows", Map.of()).forGetter(HollowProgress::hollows),
            Codec.INT.lenientOptionalFieldOf("players", 0).forGetter(HollowProgress::players),
            Codec.BOOL.lenientOptionalFieldOf("sight", true).forGetter(HollowProgress::sight),
            Codec.LONG.lenientOptionalFieldOf("last_meal_at", NEVER).forGetter(HollowProgress::lastMealAt),
            Codec.BOOL.lenientOptionalFieldOf("purified_on_death", false).forGetter(HollowProgress::purifiedOnDeath),
            Evolution.CODEC.lenientOptionalFieldOf("evolution", Evolution.NONE).forGetter(HollowProgress::evolution),
            Arrancar.CODEC.lenientOptionalFieldOf("arrancar", Arrancar.NONE).forGetter(HollowProgress::arrancar)
    ).apply(instance, HollowProgress::new));

    /** The record on its own, for a payload that needs it; the soul itself travels whole (see SyncSoulPayload). */
    public static final StreamCodec<ByteBuf, HollowProgress> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /**
     * Keeps every value in range. Satiety that is not a number or is below zero means the hunger has not
     * started, and is exactly {@link #UNSET}, so equal records compare equal (a record compares -0.0 and
     * 0.0 as different); otherwise it is at most {@link #MAX_SATIETY}. Counts are never negative, and the
     * kinds keep only real counts. A missing evolution or Arrancar record is none yet.
     */
    public HollowProgress {
        if (Float.isNaN(satiety) || satiety < 0.0F) {
            satiety = UNSET;
        } else {
            satiety = satiety == 0.0F ? 0.0F : Math.min(satiety, MAX_SATIETY);
        }
        souls = Math.max(0, souls);
        pluses = Math.max(0, pluses);
        players = Math.max(0, players);
        hollows = cleanKinds(hollows);
        evolution = evolution == null ? Evolution.NONE : evolution;
        arrancar = arrancar == null ? Arrancar.NONE : arrancar;
    }

    /** A soul with no road as a Menos yet, as every save from before the Menos reads. */
    public HollowProgress(float satiety, int souls, int pluses, Map<HollowKind, Integer> hollows, int players,
                          boolean sight, long lastMealAt, boolean purifiedOnDeath) {
        this(satiety, souls, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath, Evolution.NONE);
    }

    /** A soul that has never torn at its mask, as every save from before the Arrancar reads. */
    public HollowProgress(float satiety, int souls, int pluses, Map<HollowKind, Integer> hollows, int players,
                          boolean sight, long lastMealAt, boolean purifiedOnDeath, Evolution evolution) {
        this(satiety, souls, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath, evolution, Arrancar.NONE);
    }

    /** An unmodifiable copy without empty keys or counts of zero or less, in kind order. */
    private static Map<HollowKind, Integer> cleanKinds(Map<HollowKind, Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        EnumMap<HollowKind, Integer> clean = new EnumMap<>(HollowKind.class);
        raw.forEach((kind, count) -> {
            if (kind != null && count != null && count > 0) {
                clean.put(kind, count);
            }
        });
        return clean.isEmpty() ? Map.of() : Collections.unmodifiableMap(clean);
    }

    /** Whether the soul hunger has started: false for every soul that is not a Hollow. */
    public boolean hungerStarted() {
        return satiety >= 0.0F;
    }

    /** Hollows of this kind devoured. */
    public int hollows(HollowKind kind) {
        return hollows.getOrDefault(kind, 0);
    }

    /** Hollows of every kind devoured. */
    public int totalHollows() {
        int total = 0;
        for (int count : hollows.values()) {
            total += count;
        }
        return total;
    }

    /** Souls of every sort devoured: Pluses, Hollows and players. */
    public int meals() {
        return pluses + totalHollows() + players;
    }

    public HollowProgress withSatiety(float value) {
        return new HollowProgress(value, souls, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath, evolution,
                arrancar);
    }

    public HollowProgress withSouls(int value) {
        return new HollowProgress(satiety, value, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath, evolution,
                arrancar);
    }

    public HollowProgress withPluses(int value) {
        return new HollowProgress(satiety, souls, value, hollows, players, sight, lastMealAt, purifiedOnDeath, evolution,
                arrancar);
    }

    /** The count for one kind; zero or less takes the kind off. */
    public HollowProgress withHollows(HollowKind kind, int count) {
        // Built on an empty EnumMap: copying an empty map that is not one would throw.
        EnumMap<HollowKind, Integer> next = new EnumMap<>(HollowKind.class);
        next.putAll(hollows);
        next.put(kind, count);
        return new HollowProgress(satiety, souls, pluses, next, players, sight, lastMealAt, purifiedOnDeath, evolution,
                arrancar);
    }

    public HollowProgress withPlayers(int value) {
        return new HollowProgress(satiety, souls, pluses, hollows, value, sight, lastMealAt, purifiedOnDeath, evolution,
                arrancar);
    }

    public HollowProgress withSight(boolean value) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, value, lastMealAt, purifiedOnDeath, evolution,
                arrancar);
    }

    public HollowProgress withLastMealAt(long time) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, sight, time, purifiedOnDeath, evolution,
                arrancar);
    }

    public HollowProgress withPurifiedOnDeath(boolean purified) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, sight, lastMealAt, purified, evolution,
                arrancar);
    }

    public HollowProgress withEvolution(Evolution value) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath, value,
                arrancar);
    }

    public HollowProgress withArrancar(Arrancar value) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath, evolution,
                value);
    }

    /**
     * No longer a Hollow: the hunger unset, the souls within gone, the purified mark cleared and the road
     * as a Menos begun again. What it devoured, its sight, its last meal, the highest form it ever
     * reached and its Arrancar record (so an old blade never counts as current again) stay on the record.
     */
    public HollowProgress restarted() {
        return new HollowProgress(UNSET, 0, pluses, hollows, players, sight, lastMealAt, false, evolution.restarted(),
                arrancar);
    }

    /**
     * A Hollow's road through the Menos forms: what it has devoured since its present form began, how long
     * an Adjuchas has gone without a Hollow, how often it has sunk back, and why it may grow no further.
     * Saved with the rest, read leniently key by key, so a save from before the Menos reads as {@link #NONE}.
     *
     * @param atTier        the form the counts below belong to; when the form changes any other way (a
     *                      command, sinking back), the counts start again (see MenosEvolution#tick)
     * @param peakTier      the highest form ever reached, which a purification or sinking back keeps, so the
     *                      reiryoku a form gives the first time is never given twice
     * @param tierHollows   Hollows (mobs, Menos and Hollow players) devoured since the form began
     * @param tierMenos     Menos of the soul's own class or above devoured since the form began
     * @param hungerMinutes whole minutes of play an Adjuchas has gone without devouring a Hollow
     * @param regressions   how often an Adjuchas has sunk back into a Gillian
     * @param stall         why an Adjuchas grows no further: {@link #STALL_NONE}, {@link #STALL_UNDESTINED}
     *                      or {@link #STALL_FULL}
     */
    public record Evolution(int atTier, int peakTier, int tierHollows, int tierMenos, int hungerMinutes,
                            int regressions, int stall) {
        /** Nothing holds it back. */
        public static final int STALL_NONE = 0;
        /** The soul is not destined to become a Vasto Lorde. */
        public static final int STALL_UNDESTINED = 1;
        /** The server holds as many Vasto Lorde as it allows. */
        public static final int STALL_FULL = 2;

        public static final Evolution NONE = new Evolution(1, 1, 0, 0, 0, 0, STALL_NONE);

        public static final Codec<Evolution> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.lenientOptionalFieldOf("at_tier", 1).forGetter(Evolution::atTier),
                Codec.INT.lenientOptionalFieldOf("peak_tier", 1).forGetter(Evolution::peakTier),
                Codec.INT.lenientOptionalFieldOf("tier_hollows", 0).forGetter(Evolution::tierHollows),
                Codec.INT.lenientOptionalFieldOf("tier_menos", 0).forGetter(Evolution::tierMenos),
                Codec.INT.lenientOptionalFieldOf("hunger_minutes", 0).forGetter(Evolution::hungerMinutes),
                Codec.INT.lenientOptionalFieldOf("regressions", 0).forGetter(Evolution::regressions),
                Codec.INT.lenientOptionalFieldOf("stall", STALL_NONE).forGetter(Evolution::stall)
        ).apply(instance, Evolution::new));

        /** Keeps every value in range: both forms real, the highest never below the present, and no count below zero. */
        public Evolution {
            atTier = HollowTier.byTier(atTier).tier();
            peakTier = Math.max(atTier, HollowTier.byTier(peakTier).tier());
            tierHollows = Math.max(0, tierHollows);
            tierMenos = Math.max(0, tierMenos);
            hungerMinutes = Math.max(0, hungerMinutes);
            regressions = Math.max(0, regressions);
            stall = Mth.clamp(stall, STALL_NONE, STALL_FULL);
        }

        /** A form just begun: the counts and stall cleared, the highest form raised to it, the times it sank back kept. */
        public Evolution startedAt(int tier) {
            return new Evolution(tier, Math.max(peakTier, tier), 0, 0, 0, regressions, STALL_NONE);
        }

        /** No longer a Hollow: all of it gone but the highest form it ever reached. */
        public Evolution restarted() {
            return new Evolution(1, peakTier, 0, 0, 0, 0, STALL_NONE);
        }

        public Evolution withTierHollows(int value) {
            return new Evolution(atTier, peakTier, value, tierMenos, hungerMinutes, regressions, stall);
        }

        public Evolution withTierMenos(int value) {
            return new Evolution(atTier, peakTier, tierHollows, value, hungerMinutes, regressions, stall);
        }

        public Evolution withHungerMinutes(int value) {
            return new Evolution(atTier, peakTier, tierHollows, tierMenos, value, regressions, stall);
        }

        public Evolution withRegressions(int value) {
            return new Evolution(atTier, peakTier, tierHollows, tierMenos, hungerMinutes, value, stall);
        }

        public Evolution withStall(int value) {
            return new Evolution(atTier, peakTier, tierHollows, tierMenos, hungerMinutes, regressions, value);
        }
    }

    /**
     * The road out of the mask: never torn, or torn naturally (perfected false) or by the Hōgyoku (true).
     * Saved with the rest, read leniently key by key, so a save from before the Arrancar reads as
     * {@link #NONE}, and one from before the Gran Rey Cero as having no wait for it. The form the Arrancar rose
     * from is not kept here: it is the soul's Hollow tier.
     *
     * @param perfected          whether the Hōgyoku made it, which a later natural road never takes away
     * @param failures           tries at tearing the mask that failed, each making the next likelier
     * @param lastTearAt         when it last tried and failed, for the wait before the next try
     * @param releaseReadyAt     no Resurrección before this game time: the wait after the last one ended
     * @param forging            how many times its blade has been forged; only a blade of this forging is its own,
     *                           so every older copy crumbles, and 0 means it never had one
     * @param granReyCeroReadyAt no Gran Rey Cero before this game time: the wait after the last one fired, kept
     *                           across a relog as the release's is (see {@link GranReyCero})
     */
    public record Arrancar(boolean perfected, int failures, long lastTearAt, long releaseReadyAt, int forging,
                           long granReyCeroReadyAt) {
        public static final Arrancar NONE = new Arrancar(false, 0, NEVER, NEVER, 0, NEVER);

        public static final Codec<Arrancar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.lenientOptionalFieldOf("perfected", false).forGetter(Arrancar::perfected),
                Codec.INT.lenientOptionalFieldOf("failures", 0).forGetter(Arrancar::failures),
                Codec.LONG.lenientOptionalFieldOf("last_tear_at", NEVER).forGetter(Arrancar::lastTearAt),
                Codec.LONG.lenientOptionalFieldOf("release_ready_at", NEVER).forGetter(Arrancar::releaseReadyAt),
                Codec.INT.lenientOptionalFieldOf("forging", 0).forGetter(Arrancar::forging),
                Codec.LONG.lenientOptionalFieldOf("gran_rey_cero_ready_at", NEVER).forGetter(Arrancar::granReyCeroReadyAt)
        ).apply(instance, Arrancar::new));

        /** Keeps the counts from going below zero. */
        public Arrancar {
            failures = Math.max(0, failures);
            forging = Math.max(0, forging);
        }

        /** The five-part record of 5e, with no Gran Rey Cero wait. */
        public Arrancar(boolean perfected, int failures, long lastTearAt, long releaseReadyAt, int forging) {
            this(perfected, failures, lastTearAt, releaseReadyAt, forging, NEVER);
        }

        /** Whether its blade was ever forged: false for a save from before the Arrancar, or a state set some other way. */
        public boolean forged() {
            return forging > 0;
        }

        /**
         * The soul has just become an Arrancar: perfected if this road or an earlier one was the Hōgyoku's, a
         * blade forged anew (so any older one crumbles), and no wait before its first release or its first Gran
         * Rey Cero, as a new life starts with none. The failed tries and the last one are kept.
         */
        public Arrancar becoming(boolean perfected) {
            return new Arrancar(this.perfected || perfected, failures, lastTearAt, NEVER, forging + 1, NEVER);
        }

        public Arrancar withFailures(int value) {
            return new Arrancar(perfected, value, lastTearAt, releaseReadyAt, forging, granReyCeroReadyAt);
        }

        public Arrancar withLastTearAt(long time) {
            return new Arrancar(perfected, failures, time, releaseReadyAt, forging, granReyCeroReadyAt);
        }

        public Arrancar withReleaseReadyAt(long time) {
            return new Arrancar(perfected, failures, lastTearAt, time, forging, granReyCeroReadyAt);
        }

        public Arrancar withForging(int value) {
            return new Arrancar(perfected, failures, lastTearAt, releaseReadyAt, value, granReyCeroReadyAt);
        }

        public Arrancar withGranReyCeroReadyAt(long time) {
            return new Arrancar(perfected, failures, lastTearAt, releaseReadyAt, forging, time);
        }
    }
}
