package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the mod knows about one soul: its state, its spiritual energy, its stats, which
 * powers have awakened, its record against Hollows, its bond with its zanpakutō, its Quincy
 * blood and its life as a Hollow. Stored as the {@link SoulAttachments#SOUL} attachment on players
 * and on spirit mobs (Shinigami NPCs, Hollows, Pluses) and saved with them.
 * A player's own soul is also sent to their client so the HUD and stats screen can show it.
 *
 * <p>The codec holds 16 fields, the most {@code RecordCodecBuilder} takes: a new field goes inside one of
 * the nested records ({@link HollowProgress} has room).
 */
public final class SoulData {
    private static final long NEVER = Long.MIN_VALUE / 2;
    /** No hit has passed through since the last tick. Fire ticks never reach this, burning or not. */
    static final int NO_FIRE_RECORDED = Integer.MAX_VALUE;

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
            Codec.LONG.optionalFieldOf("last_close_call", NEVER).forGetter(SoulData::lastCloseCallTime),
            Codec.INT.optionalFieldOf("chain_ticks", 0).forGetter(SoulData::chainTicks),
            Codec.INT.optionalFieldOf("chain_max_ticks", 0).forGetter(SoulData::chainMaxTicks),
            Codec.BOOL.optionalFieldOf("reiatsu_released", false).forGetter(SoulData::isReiatsuReleased),
            ShinigamiProgress.CODEC.lenientOptionalFieldOf("shinigami", ShinigamiProgress.NONE)
                    .forGetter(SoulData::shinigami),
            HuntRecord.CODEC.lenientOptionalFieldOf("hunt", HuntRecord.NONE).forGetter(SoulData::hunt),
            ZanpakutoBond.CODEC.lenientOptionalFieldOf("zanpakuto", ZanpakutoBond.NONE).forGetter(SoulData::zanpakuto),
            QuincyProgress.CODEC.lenientOptionalFieldOf("quincy", QuincyProgress.NONE).forGetter(SoulData::quincy),
            HollowProgress.CODEC.lenientOptionalFieldOf("hollow", HollowProgress.NONE).forGetter(SoulData::hollow)
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
    /** Ticks left on a Plus's Chain of Fate, and what it started at. */
    private int chainTicks;
    private int chainMaxTicks;
    /** Whether the soul is flooding the area with its reiatsu (the pressure aura). */
    private boolean reiatsuReleased;
    /** The Academy, the Asauchi, Konsō and power given or taken: see {@link ShinigamiLogic}. */
    private ShinigamiProgress shinigami = ShinigamiProgress.NONE;
    /** The first Hollow, Garganta arrivals, and the Hollows and Pluses this soul has dealt with: see {@link HollowLogic}. */
    private HuntRecord hunt = HuntRecord.NONE;
    /** The Asauchi bond, the hidden leaning, and the zanpakutō's name once learned: see {@link ZanpakutoLogic}. */
    private ZanpakutoBond zanpakuto = ZanpakutoBond.NONE;
    /** Quincy blood, training, reishi, Hollow poison and Blut: see {@link QuincyLogic}. */
    private QuincyProgress quincy = QuincyProgress.NONE;
    /** Soul hunger, the souls devoured and the Hollow sight: see {@link HollowPlayerLogic}. */
    private HollowProgress hollow = HollowProgress.NONE;

    // Server-side bookkeeping. Not saved or synced; it starts over when the player logs in or respawns.
    private boolean dirty = true;
    double lastX;
    double lastZ;
    boolean hasLastPosition;
    int sprintTicks;
    int meditationTicks;
    int meditationGrants;
    /** Ticks spent meditating without a break, for the Academy reminder. */
    int meditatingFor;
    int lastCombatXpTick = -1000;
    float chainErosion;
    int encroachmentTicks;
    /** Set on the respawned copy when this death turned a Human into a Plus, with where it happened. */
    boolean becamePlus;
    Vec3 diedAt;
    boolean nearShinigami;
    boolean wasEncroaching;
    /** What players tracking this one were last told, so it is only re-sent when it changes. */
    SoulAppearance lastSentAppearance;
    /** Tick of the last release or seal, for the release cooldown. */
    int lastReleaseChangeTick = -1000;
    /** Fire ticks from just before an unaware player's hit passed through, to undo what the hit set alight. */
    int fireBeforeIgnoredHit = NO_FIRE_RECORDED;
    /** Set on the respawned copy when this death turned a Substitute into a Shinigami. */
    boolean becameShinigami;
    /** What another player has offered this soul and is waiting on it to accept. */
    SoulOffer pendingOffer;
    /** The Human this Shinigami is using their blade on, asking to lend them power, and when they last did. */
    UUID powerConfirmTarget;
    long powerConfirmAt = NEVER;
    /** Game time of this soul's last offer, so offers cannot be spammed. */
    long lastOfferAt = NEVER;
    /** Game time of this soul's last flash step, for the cooldown. */
    long lastFlashStepAt = NEVER;
    /** The Fishbone D hunting this soul as its first Hollow, once it has landed. */
    UUID firstHollowId;
    /** No Garganta opens near this soul before this game time: set on joining, respawning and changing dimension. */
    long arrivalGraceUntil = NEVER;
    /** Game time this Plus was last told a Hollow's bite gnawed at its chain, so the message is not spammed. */
    long lastBiteMessageAt = NEVER;
    /** Blows that landed on a Hollow's body in a row, without one to the mask, for the hint to aim for the mask. */
    int bodyHitsWithoutMask;
    /** The Jinzen this soul is sitting in, from settling down to opening its eyes: see {@link JinzenLogic}. */
    JinzenSession jinzen;
    /** Tick of the last Shikai release or seal, so the blade settles before it changes again. */
    int lastShikaiChangeTick = -1000;
    /**
     * Whether a zanpakutō that can seal is released, draining reiryoku. A constant-release blade never
     * sets this. Not saved: a release does not outlast the session, and a stale released blade seals.
     */
    boolean shikaiActive;
    /** Ticks in a row the released blade has been out of the soul's hand. */
    int shikaiAwayTicks;
    /** Game time a release last trained Reiatsu Control. */
    long lastShikaiXpAt = NEVER;
    /** Game time a Getsuga Tenshō started gathering, or {@link #NEVER} when none is. */
    long gatherStartedAt = NEVER;
    /** Game time of the last Getsuga Tenshō, for the cooldown. */
    long lastGetsugaAt = NEVER;
    /** Game time Zabimaru's blade, lashed out, has drawn back together, or {@link #NEVER}. */
    long lashRetractUntil = NEVER;
    /** Tick of the last Hakuda blow on a Hollow that counted towards the power leaning. */
    int lastAffinityHitTick = -1000;
    /** Client side, for one click: sneak-use with the Asauchi gave way to a full off hand. */
    boolean offHandRefused;
    /** Whether this soul has been asked the bloodline question this session, so it is asked once. */
    boolean bloodlineAsked;
    /** Game time a latent soul's draw last counted as training. */
    long lastDrawTrainingAt = NEVER;
    /** Game time a Heilig Pfeil hit on a Hollow last trained this soul. */
    long lastHitTrainingAt = NEVER;
    /** Game time a Hirenkyaku last trained this soul. */
    long lastHirenTrainingAt = NEVER;
    /** Game time a Heilig Pfeil hit on a Hollow last trained Reiatsu Control. */
    long lastArrowXpAt = NEVER;
    /** Game time a latent soul was last told its reishi slipped away, so it is not told every draw. */
    long lastFizzleMessageAt = NEVER;
    /** Game time Hollow poison last built up; it only wears off a while after. Starts over on logging in. */
    long lastPoisonAt = NEVER;
    /** Game time of the last pang of heavy Hollow poison. */
    long lastPangAt = NEVER;
    /** Game time the Blut key was last pressed, so a held or repeated key is ignored. */
    long lastBlutPressAt = NEVER;
    /** Blut cannot change again before this game time. */
    long blutSettlesAt = NEVER;
    /** How rich in reishi the soul's surroundings are, worked out once a second (1 is ordinary). */
    double reishiDensity = 1.0;
    /** The Hollow poison stage the soul was last told about (see {@link QuincyLogic#poisonStage}). */
    int poisonStage;
    /** Set on the respawned copy when this death left an awakened Quincy alive. */
    boolean quincyReborn;
    /** Set on the respawned copy when this death came from the soul being torn apart. */
    boolean soulReformed;
    /** Game time the Quincy Form key was last pressed, so a held or repeated key is ignored. */
    long lastFormPressAt = NEVER;
    /** Game time of the first press that tears the mastered Sanrei Glove off, waiting for the second. */
    long formArmedAt = NEVER;
    /** Game time a Licht Regen started gathering, or {@link #NEVER} when none is. */
    long lichtGatherFrom = NEVER;
    /** Game time from which a Sprenger formation's fifth blade may bind again. */
    long sprengerBindReadyAt = NEVER;
    /** The Licht Regen volley still falling, or null. Lost on logging out, as a volley is short. */
    @Nullable
    LichtVolley lichtVolley;
    /** Game time this Hollow last took damage above zero, for the wait before it regenerates. */
    long hollowHurtAt = NEVER;
    /** A blow to this Hollow's mask stops it regenerating until this game time. */
    long hollowRegenPausedUntil = NEVER;
    /** This Hollow's mask is drawn cracked until this game time. */
    long hollowCrackedUntil = NEVER;
    /** Soul hunger spent and not yet taken off the saved value, so it is not re-sent every tick. */
    float hollowSatietyDebt;
    /** Ticks this Hollow has spent starving since its last starving hurt. */
    int hollowStarveTicks;
    /** Game time of the last heartbeat of soul hunger. */
    long lastHungerPangAt = NEVER;
    /** Game time this hungry Hollow last smelled for souls. */
    long lastScentAt = NEVER;
    /** Game time this Hollow was last told its hide sheds armour. */
    long lastShedMessageAt = NEVER;
    /** Game time of the last refusal-type message (devouring, food, villagers), so they are not spammed. */
    long lastHollowMessageAt = NEVER;
    /** The {@link HollowPlayerLogic.Band} this Hollow was last in, by position, or -1 before the first look. */
    int lastHollowBand = -1;
    /** Whether the Hollow body (claws, hide, leap and sight) is on this player, so leaving it takes them off. */
    boolean hollowBodyApplied;
    /** Set on the respawned copy when a purifying blow freed this Hollow, for the respawn to say so. */
    boolean hollowCleansed;
    /** The entity this Hollow is devouring, by id, or -1. */
    int devourTargetId = -1;
    /** Ticks the Devour key has been held on the prey. */
    int devourHeldTicks;
    /** Whether the prey is a Plus, which takes a Plus's hold, for the devourer's own devour bar. */
    boolean devourPlus;
    /** No hold can start before this game time. */
    long devourReadyAt = NEVER;
    /** Game time this Plus started gnawing at its own chain, or {@link #NEVER} when it is not. */
    long gnawStartedAt = NEVER;
    /**
     * Ticks this Plus has gnawed at its chain, counted on across presses, so letting go and pressing again
     * never dodges the pains.
     */
    int gnawTicks;
    /**
     * Ticks this Adjuchas has gone without a Hollow since the last whole minute was counted into the saved
     * record, so the soul is re-sent once a minute and not every tick (see {@link MenosEvolution}).
     */
    int menosHungerTicks;
    /** The Cero this Menos is gathering, by entity id, or -1 (see {@link HollowCero}). */
    int ceroBeamId = -1;
    /** Game time the Cero being gathered started, or {@link #NEVER}. */
    long ceroStartedAt = NEVER;
    /** Game time this Menos last fired a Cero, for the cooldown. */
    long lastCeroAt = NEVER;
    /** Game time a blow last cracked this Hollow's whole mask, or {@link #NEVER}: a fresh crack breaks a Cero's gather. */
    long hollowFreshCrackAt = NEVER;
    /**
     * Game time of the first press that arms a Menos's tear at its own mask, waiting for the second, or
     * {@link #NEVER} (see {@link ArrancarEvents}).
     */
    long tearArmedAt = NEVER;
    /** Game time the tear at the mask began, held still, or {@link #NEVER} when it is not being torn. */
    long tearStartedAt = NEVER;
    /** Where the tear began: a step more than a block from here breaks it. */
    @Nullable
    Vec3 tearFrom;
    /**
     * Whether this Arrancar is in Resurrección (see {@link ResurreccionEvents}). Not saved: a release does
     * not outlast the session, and the wait after it is on the saved record.
     */
    boolean resurreccionActive;
    /** Game time the Resurrección began, or {@link #NEVER}. */
    long resurreccionStartedAt = NEVER;
    /** Ticks this Arrancar has gone without its own blade, until a new one forms from its mask. */
    int arrancarBladelessTicks;

    public SoulData() {
    }

    private SoulData(SoulState state, boolean initialized, float reiryoku, float maxReiryoku,
                     Map<Stat, Integer> levels, Map<Stat, Integer> xp, Map<PowerPath, Integer> tiers,
                     long lastCloseCallTime, int chainTicks, int chainMaxTicks, boolean reiatsuReleased,
                     ShinigamiProgress shinigami, HuntRecord hunt, ZanpakutoBond zanpakuto, QuincyProgress quincy,
                     HollowProgress hollow) {
        this.state = state;
        this.initialized = initialized;
        this.maxReiryoku = Math.max(0.0F, maxReiryoku);
        this.reiryoku = Mth.clamp(reiryoku, 0.0F, this.maxReiryoku);
        this.levels.putAll(levels);
        this.xp.putAll(xp);
        this.tiers.putAll(tiers);
        this.lastCloseCallTime = lastCloseCallTime;
        this.chainMaxTicks = Math.max(0, chainMaxTicks);
        this.chainTicks = Mth.clamp(chainTicks, 0, this.chainMaxTicks);
        this.reiatsuReleased = reiatsuReleased;
        this.shinigami = shinigami;
        this.hunt = hunt;
        this.zanpakuto = zanpakuto;
        this.quincy = quincy;
        this.hollow = hollow;
    }

    /** A map keyed by enum name that skips names it does not recognise when reading. */
    static <E extends Enum<E> & StringRepresentable> Codec<Map<E, Integer>> enumKeyedMap(
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

    public int chainTicks() {
        return chainTicks;
    }

    public int chainMaxTicks() {
        return chainMaxTicks;
    }

    /** Remaining Chain of Fate as a fraction of its starting length, from 0 to 1. */
    public float chainFraction() {
        return chainMaxTicks <= 0 ? 0.0F : (float) chainTicks / chainMaxTicks;
    }

    /** Gives the soul a new chain, full length. */
    public void startChain(int ticks) {
        chainMaxTicks = Math.max(1, ticks);
        chainTicks = chainMaxTicks;
        chainErosion = 0.0F;
        markDirty();
    }

    public void setChainTicks(int ticks) {
        int clamped = Mth.clamp(ticks, 0, chainMaxTicks);
        if (clamped != chainTicks) {
            chainTicks = clamped;
            markDirty();
        }
    }

    public void clearChain() {
        chainTicks = 0;
        chainMaxTicks = 0;
        chainErosion = 0.0F;
        markDirty();
    }

    public boolean isReiatsuReleased() {
        return reiatsuReleased;
    }

    public void setReiatsuReleased(boolean released) {
        if (released != reiatsuReleased) {
            reiatsuReleased = released;
            markDirty();
        }
    }

    public ShinigamiProgress shinigami() {
        return shinigami;
    }

    public void setShinigami(ShinigamiProgress progress) {
        if (!progress.equals(shinigami)) {
            shinigami = progress;
            markDirty();
        }
    }

    public HuntRecord hunt() {
        return hunt;
    }

    public void setHunt(HuntRecord record) {
        if (!record.equals(hunt)) {
            hunt = record;
            markDirty();
        }
    }

    public ZanpakutoBond zanpakuto() {
        return zanpakuto;
    }

    public void setZanpakuto(ZanpakutoBond bond) {
        if (!bond.equals(zanpakuto)) {
            zanpakuto = bond;
            markDirty();
        }
    }

    public QuincyProgress quincy() {
        return quincy;
    }

    public void setQuincy(QuincyProgress progress) {
        if (!progress.equals(quincy)) {
            quincy = progress;
            markDirty();
        }
    }

    public HollowProgress hollow() {
        return hollow;
    }

    public void setHollow(HollowProgress progress) {
        if (!progress.equals(hollow)) {
            hollow = progress;
            markDirty();
        }
    }

    /**
     * Whether this soul is being torn apart by Hollow poison or a broken chain. Saved, so a death that
     * waits on the death screen through a logout or a restart still knows.
     */
    public boolean isSoulDestroyed() {
        return quincy.tornApart();
    }

    /** Marks the soul as torn apart, just before the death that follows (see {@link SoulLogic#afterDeath}). */
    public void markSoulDestroyed() {
        setQuincy(quincy.withTornApart(true));
    }

    /** Takes the mark back when the death it was for did not happen. */
    public void clearSoulDestroyed() {
        setQuincy(quincy.withTornApart(false));
    }

    /** Whether this respawned soul is an awakened Quincy who came back alive. Server side only. */
    public boolean wasQuincyReborn() {
        return quincyReborn;
    }

    /** Whether this respawned soul was torn apart and knit itself together again. Server side only. */
    public boolean wasSoulReformed() {
        return soulReformed;
    }

    /** How rich in reishi the soul's surroundings were when last worked out (1 is ordinary). Server side only. */
    public double reishiDensity() {
        return reishiDensity;
    }

    /** The Jinzen this soul is sitting in, or null. Server side only. */
    @Nullable
    public JinzenSession jinzen() {
        return jinzen;
    }

    /** Notes, on the client, that this click's sneak-use with the Asauchi gave way to a full off hand. */
    public void noteOffHandRefused() {
        offHandRefused = true;
    }

    /** Whether this click's sneak-use with the Asauchi gave way to a full off hand; forgets it either way. */
    public boolean takeOffHandRefused() {
        boolean refused = offHandRefused;
        offHandRefused = false;
        return refused;
    }

    /** Whether a zanpakutō that can seal is released. Server side only: the client reads the blade itself. */
    public boolean isShikaiActive() {
        return shikaiActive;
    }

    /**
     * Whether the Resurrección flag is up, as it stands. Server side only. The rules ask
     * {@link ArrancarLogic#released}, which also asks that the soul is an Arrancar.
     */
    public boolean isResurreccionActive() {
        return resurreccionActive;
    }

    /** Whether a Getsuga Tenshō is gathering. Server side only. */
    public boolean isGathering() {
        return gatherStartedAt != NEVER;
    }

    /** Whether a Licht Regen is gathering. Server side only. */
    boolean lichtGathering() {
        return lichtGatherFrom != NEVER;
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
        return new SoulData(state, initialized, reiryoku, maxReiryoku, levels, xp, tiers, lastCloseCallTime,
                chainTicks, chainMaxTicks, reiatsuReleased, shinigami, hunt, zanpakuto, quincy, hollow);
    }

    /**
     * What the owner's client is sent: a copy of the saved data without the hidden leaning, which
     * decides the spirit and is never shown to players.
     */
    public SoulData forClient() {
        SoulData copy = copy();
        copy.zanpakuto = zanpakuto.withoutAffinity();
        return copy;
    }
}
