package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

/**
 * Everything the mod knows about one soul: its state, its spiritual energy, its stats and which
 * powers have awakened. Stored on players as the {@link SoulAttachments#SOUL} attachment, saved
 * with the player and sent to their client so the HUD and stats screen can show it.
 */
public final class SoulData {
    private static final long NEVER = Long.MIN_VALUE / 2;

    // Saved data is read leniently: a stat, power or state name this version does not know (renamed,
    // removed, or from a newer version) is dropped instead of making NeoForge discard the whole soul.
    private static final Codec<Map<Stat, Integer>> STAT_MAP_CODEC = enumKeyedMap(Stat.class, Stat.values());
    private static final Codec<Map<PowerPath, Integer>> PATH_MAP_CODEC =
            enumKeyedMap(PowerPath.class, PowerPath.values());

    public static final Codec<SoulData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SoulState.CODEC.lenientOptionalFieldOf("state", SoulState.HUMAN).forGetter(SoulData::state),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(SoulData::isInitialized),
            Codec.FLOAT.optionalFieldOf("reiryoku", 0.0F).forGetter(SoulData::reiryoku),
            Codec.FLOAT.optionalFieldOf("max_reiryoku", 0.0F).forGetter(SoulData::maxReiryoku),
            STAT_MAP_CODEC.optionalFieldOf("stat_levels", Map.of()).forGetter(data -> data.levels),
            STAT_MAP_CODEC.optionalFieldOf("stat_xp", Map.of()).forGetter(data -> data.xp),
            PATH_MAP_CODEC.optionalFieldOf("path_tiers", Map.of()).forGetter(data -> data.tiers),
            Codec.LONG.optionalFieldOf("last_close_call", NEVER).forGetter(SoulData::lastCloseCallTime)
    ).apply(instance, SoulData::new));

    private SoulState state = SoulState.HUMAN;
    private boolean initialized;
    private float reiryoku;
    private float maxReiryoku;
    private final EnumMap<Stat, Integer> levels = new EnumMap<>(Stat.class);
    private final EnumMap<Stat, Integer> xp = new EnumMap<>(Stat.class);
    private final EnumMap<PowerPath, Integer> tiers = new EnumMap<>(PowerPath.class);
    /** Game time of the last close call that grew reiryoku, so relogging or dying does not skip the cooldown. */
    private long lastCloseCallTime = NEVER;

    // Server-side bookkeeping. Not saved or synced; it starts over when the player logs in or respawns.
    private boolean dirty = true;
    double lastX;
    double lastZ;
    boolean hasLastPosition;
    int sprintTicks;
    int meditationTicks;
    int meditationGrants;
    int lastCombatXpTick = -1000;

    public SoulData() {
    }

    private SoulData(SoulState state, boolean initialized, float reiryoku, float maxReiryoku,
                     Map<Stat, Integer> levels, Map<Stat, Integer> xp, Map<PowerPath, Integer> tiers,
                     long lastCloseCallTime) {
        this.state = state;
        this.initialized = initialized;
        this.maxReiryoku = Math.max(0.0F, maxReiryoku);
        this.reiryoku = Mth.clamp(reiryoku, 0.0F, this.maxReiryoku);
        this.levels.putAll(levels);
        this.xp.putAll(xp);
        this.tiers.putAll(tiers);
        this.lastCloseCallTime = lastCloseCallTime;
    }

    /** A map keyed by enum name that skips names it does not recognise when reading. */
    private static <E extends Enum<E> & StringRepresentable> Codec<Map<E, Integer>> enumKeyedMap(
            Class<E> type, E[] values) {
        return Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
                raw -> {
                    EnumMap<E, Integer> map = new EnumMap<>(type);
                    for (E value : values) {
                        Integer amount = raw.get(value.getSerializedName());
                        if (amount != null) {
                            map.put(value, amount);
                        }
                    }
                    return map;
                },
                map -> {
                    Map<String, Integer> raw = new LinkedHashMap<>();
                    map.forEach((key, amount) -> raw.put(key.getSerializedName(), amount));
                    return raw;
                });
    }

    /** XP needed to go from {@code level} to the next level. */
    public static int xpToNextLevel(int level) {
        return 20 + 40 * level;
    }

    public SoulState state() {
        return state;
    }

    public void setState(SoulState state) {
        this.state = state;
        markDirty();
    }

    /** False until the server gives a new player their starting reiryoku. */
    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        initialized = true;
        markDirty();
    }

    public float reiryoku() {
        return reiryoku;
    }

    public void setReiryoku(float value) {
        float clamped = Mth.clamp(value, 0.0F, maxReiryoku);
        if (clamped != reiryoku) {
            reiryoku = clamped;
            markDirty();
        }
    }

    public float maxReiryoku() {
        return maxReiryoku;
    }

    public void setMaxReiryoku(float value) {
        maxReiryoku = Math.max(0.0F, value);
        reiryoku = Math.min(reiryoku, maxReiryoku);
        markDirty();
    }

    /** Current reiryoku as a fraction of the maximum, from 0 to 1. */
    public float reiryokuFraction() {
        return maxReiryoku <= 0.0F ? 0.0F : reiryoku / maxReiryoku;
    }

    public int level(Stat stat) {
        return levels.getOrDefault(stat, 0);
    }

    /** Sets a stat's level and clears its progress towards the next one. */
    public void setLevel(Stat stat, int level) {
        levels.put(stat, Math.max(0, level));
        xp.remove(stat);
        markDirty();
    }

    public int xp(Stat stat) {
        return xp.getOrDefault(stat, 0);
    }

    /**
     * Adds training XP to a stat, levelling it up as many times as the XP covers.
     *
     * @return how many levels the stat gained
     */
    public int addXp(Stat stat, int amount, int maxLevel) {
        int level = level(stat);
        if (amount <= 0 || level >= maxLevel) {
            return 0;
        }
        int total = xp(stat) + amount;
        int gained = 0;
        while (level < maxLevel && total >= xpToNextLevel(level)) {
            total -= xpToNextLevel(level);
            level++;
            gained++;
        }
        levels.put(stat, level);
        xp.put(stat, level >= maxLevel ? 0 : total);
        markDirty();
        return gained;
    }

    public int tier(PowerPath path) {
        return tiers.getOrDefault(path, 0);
    }

    public void setTier(PowerPath path, int tier) {
        if (tier <= 0) {
            tiers.remove(path);
        } else {
            tiers.put(path, tier);
        }
        markDirty();
    }

    public long lastCloseCallTime() {
        return lastCloseCallTime;
    }

    public void setLastCloseCallTime(long gameTime) {
        lastCloseCallTime = gameTime;
    }

    /** Whether the client's copy is out of date. */
    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    /** A copy of the saved data, without the server-side bookkeeping. */
    public SoulData copy() {
        return new SoulData(state, initialized, reiryoku, maxReiryoku, levels, xp, tiers, lastCloseCallTime);
    }
}
