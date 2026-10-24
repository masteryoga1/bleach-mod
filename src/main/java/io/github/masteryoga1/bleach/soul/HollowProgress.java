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

/**
 * A soul's life as a Hollow: its soul hunger, the souls it has devoured and what they were, whether it
 * sees in the dark, when it last fed, and whether the blow that killed it purified it. The last of
 * {@link SoulData}'s sixteen saved fields, so every later Hollow field (the Menos tiers, mutations,
 * the Arrancar) goes in here or in a record nested in it. Saved with the soul and sent to its owner's
 * client. The rules are in {@link HollowPlayerLogic}, and what they do to the body in {@link HollowBody}.
 * Times are game times in ticks; {@link #NEVER} means it never happened.
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
 */
public record HollowProgress(float satiety, int souls, int pluses, Map<HollowKind, Integer> hollows, int players,
                             boolean sight, long lastMealAt, boolean purifiedOnDeath) {
    public static final float UNSET = -1.0F;
    public static final float MAX_SATIETY = 100.0F;
    public static final long NEVER = Long.MIN_VALUE / 2;

    public static final HollowProgress NONE = new HollowProgress(UNSET, 0, 0, Map.of(), 0, true, NEVER, false);

    // Read leniently, as the soul's own maps are: a kind this version does not know is dropped.
    private static final Codec<Map<HollowKind, Integer>> KIND_MAP =
            SoulData.enumKeyedMap(HollowKind.class, HollowKind.values());

    // Every field is read leniently, so one bad value falls back to its default on its own, and a save
    // from before Hollow players has no "hollow" at all and reads as NONE. Eight of the builder's sixteen fields.
    public static final Codec<HollowProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.lenientOptionalFieldOf("satiety", UNSET).forGetter(HollowProgress::satiety),
            Codec.INT.lenientOptionalFieldOf("souls", 0).forGetter(HollowProgress::souls),
            Codec.INT.lenientOptionalFieldOf("pluses", 0).forGetter(HollowProgress::pluses),
            KIND_MAP.lenientOptionalFieldOf("hollows", Map.of()).forGetter(HollowProgress::hollows),
            Codec.INT.lenientOptionalFieldOf("players", 0).forGetter(HollowProgress::players),
            Codec.BOOL.lenientOptionalFieldOf("sight", true).forGetter(HollowProgress::sight),
            Codec.LONG.lenientOptionalFieldOf("last_meal_at", NEVER).forGetter(HollowProgress::lastMealAt),
            Codec.BOOL.lenientOptionalFieldOf("purified_on_death", false).forGetter(HollowProgress::purifiedOnDeath)
    ).apply(instance, HollowProgress::new));

    /** The record on its own, for a payload that needs it; the soul itself travels whole (see SyncSoulPayload). */
    public static final StreamCodec<ByteBuf, HollowProgress> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /**
     * Keeps every value in range. Satiety that is not a number or is below zero means the hunger has not
     * started, and is exactly {@link #UNSET}, so equal records compare equal (a record compares -0.0 and
     * 0.0 as different); otherwise it is at most {@link #MAX_SATIETY}. Counts are never negative, and the
     * kinds keep only real counts.
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
        return new HollowProgress(value, souls, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath);
    }

    public HollowProgress withSouls(int value) {
        return new HollowProgress(satiety, value, pluses, hollows, players, sight, lastMealAt, purifiedOnDeath);
    }

    public HollowProgress withPluses(int value) {
        return new HollowProgress(satiety, souls, value, hollows, players, sight, lastMealAt, purifiedOnDeath);
    }

    /** The count for one kind; zero or less takes the kind off. */
    public HollowProgress withHollows(HollowKind kind, int count) {
        // Built on an empty EnumMap: copying an empty map that is not one would throw.
        EnumMap<HollowKind, Integer> next = new EnumMap<>(HollowKind.class);
        next.putAll(hollows);
        next.put(kind, count);
        return new HollowProgress(satiety, souls, pluses, next, players, sight, lastMealAt, purifiedOnDeath);
    }

    public HollowProgress withPlayers(int value) {
        return new HollowProgress(satiety, souls, pluses, hollows, value, sight, lastMealAt, purifiedOnDeath);
    }

    public HollowProgress withSight(boolean value) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, value, lastMealAt, purifiedOnDeath);
    }

    public HollowProgress withLastMealAt(long time) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, sight, time, purifiedOnDeath);
    }

    public HollowProgress withPurifiedOnDeath(boolean purified) {
        return new HollowProgress(satiety, souls, pluses, hollows, players, sight, lastMealAt, purified);
    }

    /**
     * No longer a Hollow: the hunger unset, the souls within gone and the purified mark cleared. What it
     * devoured, its sight and its last meal stay on the record.
     */
    public HollowProgress restarted() {
        return new HollowProgress(UNSET, 0, pluses, hollows, players, sight, lastMealAt, false);
    }
}
