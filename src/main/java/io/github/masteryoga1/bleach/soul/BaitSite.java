package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.config.BleachConfig;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;

/**
 * One place where Hollows are being drawn: a crushed Hollow Bait, or the contest the young Quincy
 * Hunter is running with a soul. Where it is, whose it is, how far it has come and how it stands.
 * Kept, with the others, in {@link BaitSites}; run once a second by {@link BaitEvents}. Mutable, and
 * on the server only.
 */
public final class BaitSite {
    public static final long NEVER = ShinigamiProgress.NEVER;

    private static final int TICKS_PER_MINUTE = 1200;

    // The id, centre and owner are needed; everything else is read leniently, so one bad value falls
    // back to its default on its own.
    public static final Codec<BaitSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(BaitSite::id),
            BlockPos.CODEC.fieldOf("centre").forGetter(BaitSite::centre),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(BaitSite::owner),
            UUIDUtil.CODEC.optionalFieldOf("rival").forGetter(site -> Optional.ofNullable(site.rival())),
            Codec.BOOL.lenientOptionalFieldOf("contest", false).forGetter(BaitSite::contest),
            Codec.INT.lenientOptionalFieldOf("active_ticks", 0).forGetter(BaitSite::activeTicks),
            Codec.INT.lenientOptionalFieldOf("frozen_ticks", 0).forGetter(BaitSite::frozenTicks),
            Codec.INT.lenientOptionalFieldOf("next_wave_in", 0).forGetter(BaitSite::nextWaveIn),
            Codec.INT.lenientOptionalFieldOf("arrived", 0).forGetter(BaitSite::arrived),
            Codec.INT.lenientOptionalFieldOf("waves", 0).forGetter(BaitSite::waves),
            Codec.INT.lenientOptionalFieldOf("owner_kills", 0).forGetter(BaitSite::ownerKills),
            Codec.INT.lenientOptionalFieldOf("rival_kills", 0).forGetter(BaitSite::rivalKills),
            Codec.INT.lenientOptionalFieldOf("finale_ticks", 0).forGetter(BaitSite::finaleTicks),
            Codec.BOOL.lenientOptionalFieldOf("plus_lost", false).forGetter(BaitSite::plusLost),
            Codec.LONG.lenientOptionalFieldOf("last_ping_at", NEVER).forGetter(BaitSite::lastPingAt)
    ).apply(instance, BaitSite::decoded));

    private final UUID id;
    private final BlockPos centre;
    private final UUID owner;
    @Nullable
    private final UUID rival;
    private final boolean contest;
    private int activeTicks;
    private int frozenTicks;
    private int nextWaveIn;
    private int arrived;
    private int waves;
    private int ownerKills;
    private int rivalKills;
    private int finaleTicks;
    private boolean plusLost;
    private long lastPingAt;

    /**
     * @param id          the site's own id, which its Hollows and rifts carry
     * @param centre      where the bait was crushed, or where the Hunter stood
     * @param owner       the soul who crushed it, or who took up the contest
     * @param rival       the Hunter, for a contest
     * @param contest     a contest with the Hunter rather than a bait
     * @param activeTicks how long it has run while its centre was loaded and Hollows could come
     * @param frozenTicks how long it has been frozen since it last ran
     * @param nextWaveIn  ticks until the next wave
     * @param arrived     Hollows that have come down here
     * @param waves       waves sent
     * @param ownerKills  Hollows the owner destroyed here, in a contest
     * @param rivalKills  Hollows the Hunter destroyed here, in a contest
     * @param finaleTicks ticks left of the cracked sky, or 0 when it is not cracked
     * @param plusLost    whether a Plus was devoured here
     * @param lastPingAt  when a wave last beeped the pagers and chilled the Quincy around, or {@link #NEVER}
     */
    public BaitSite(UUID id, BlockPos centre, UUID owner, @Nullable UUID rival, boolean contest, int activeTicks,
                    int frozenTicks, int nextWaveIn, int arrived, int waves, int ownerKills, int rivalKills,
                    int finaleTicks, boolean plusLost, long lastPingAt) {
        this.id = id;
        this.centre = centre.immutable();
        this.owner = owner;
        this.rival = rival;
        this.contest = contest;
        this.activeTicks = Math.max(0, activeTicks);
        this.frozenTicks = Math.max(0, frozenTicks);
        this.nextWaveIn = Math.max(0, nextWaveIn);
        this.arrived = Math.max(0, arrived);
        this.waves = Math.max(0, waves);
        this.ownerKills = Math.max(0, ownerKills);
        this.rivalKills = Math.max(0, rivalKills);
        this.finaleTicks = Math.max(0, finaleTicks);
        this.plusLost = plusLost;
        this.lastPingAt = lastPingAt;
    }

    /** A new site with a fresh id, its first wave {@code firstWaveTicks} away. */
    public static BaitSite create(UUID owner, BlockPos centre, boolean contest, @Nullable UUID rival,
                                  int firstWaveTicks) {
        return new BaitSite(UUID.randomUUID(), centre, owner, rival, contest, 0, 0, firstWaveTicks, 0, 0, 0, 0, 0,
                false, NEVER);
    }

    private static BaitSite decoded(UUID id, BlockPos centre, UUID owner, Optional<UUID> rival, boolean contest,
                                    int activeTicks, int frozenTicks, int nextWaveIn, int arrived, int waves,
                                    int ownerKills, int rivalKills, int finaleTicks, boolean plusLost,
                                    long lastPingAt) {
        return new BaitSite(id, centre, owner, rival.orElse(null), contest, activeTicks, frozenTicks, nextWaveIn,
                arrived, waves, ownerKills, rivalKills, finaleTicks, plusLost, lastPingAt);
    }

    // ---- Queries ----

    /** Whether the sky over it is cracked. */
    public boolean inFinale() {
        return finaleTicks > 0;
    }

    /** Whether this point is within the site's radius and {@code extra} blocks more, across the ground. */
    public boolean contains(Vec3 pos, double extra) {
        double reach = radius() + extra;
        double dx = pos.x - (centre.getX() + 0.5);
        double dz = pos.z - (centre.getZ() + 0.5);
        return dx * dx + dz * dz <= reach * reach;
    }

    /** How long it runs, in active ticks: a bait's configured minutes, or a contest's. */
    public int durationTicks() {
        return (contest ? BleachConfig.get(BleachConfig.CONTEST_MINUTES)
                : BleachConfig.get(BleachConfig.BAIT_DURATION_MINUTES)) * TICKS_PER_MINUTE;
    }

    /** The configured radius of every site, in blocks. */
    public static double radius() {
        return BleachConfig.get(BleachConfig.BAIT_RADIUS);
    }

    // ---- Fields ----

    public UUID id() {
        return id;
    }

    public BlockPos centre() {
        return centre;
    }

    public UUID owner() {
        return owner;
    }

    @Nullable
    public UUID rival() {
        return rival;
    }

    public boolean contest() {
        return contest;
    }

    public int activeTicks() {
        return activeTicks;
    }

    public void setActiveTicks(int value) {
        activeTicks = Math.max(0, value);
    }

    public int frozenTicks() {
        return frozenTicks;
    }

    public void setFrozenTicks(int value) {
        frozenTicks = Math.max(0, value);
    }

    public int nextWaveIn() {
        return nextWaveIn;
    }

    public void setNextWaveIn(int value) {
        nextWaveIn = Math.max(0, value);
    }

    public int arrived() {
        return arrived;
    }

    public void setArrived(int value) {
        arrived = Math.max(0, value);
    }

    public int waves() {
        return waves;
    }

    public void setWaves(int value) {
        waves = Math.max(0, value);
    }

    public int ownerKills() {
        return ownerKills;
    }

    public void setOwnerKills(int value) {
        ownerKills = Math.max(0, value);
    }

    public int rivalKills() {
        return rivalKills;
    }

    public void setRivalKills(int value) {
        rivalKills = Math.max(0, value);
    }

    public int finaleTicks() {
        return finaleTicks;
    }

    public void setFinaleTicks(int value) {
        finaleTicks = Math.max(0, value);
    }

    public boolean plusLost() {
        return plusLost;
    }

    public void setPlusLost(boolean value) {
        plusLost = value;
    }

    public long lastPingAt() {
        return lastPingAt;
    }

    public void setLastPingAt(long value) {
        lastPingAt = value;
    }
}
