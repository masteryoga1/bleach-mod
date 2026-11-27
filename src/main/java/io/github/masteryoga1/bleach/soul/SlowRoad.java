package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The slow road to Bankai, by Jinzen alone, for a world with no Tenshintai to hand. A soul whose power is its own, whose
 * bond is full, whose spirit has a Bankai, whose own Shikai has been released for {@code [bankai.road] slow_road_hours}
 * hours of play in all and whose maximum reiryoku is at least {@code min_max_reiryoku} has its spirit step out of the
 * blade as it settles in Jinzen, for a Bankai trial where it sits, as the command's bout
 * ({@link BankaiTrial#begin(Player, long)}), at most one a game day: one begun less than a day before, by any road,
 * holds it back. Every end of that bout is the trial's own.
 *
 * <p>The hours are the record's {@code shikai_ticks}, counted on the soul's own tick while it holds its own blade released
 * in Shikai in its main hand (a constant release too), its power its own and Bankai not reached. They are held back on
 * the soul and written a second's worth at a time, or on the first tick that does not count, so the soul is sent at most
 * once a second for them and a seal loses nothing; a logout or a death loses at most the second not yet written.
 * Revoking Bankai, resetting the zanpakutō or binding another spirit clears what was written.
 */
public final class SlowRoad {
    /** Ticks in an hour of play: the road's hours are the player's, not the game's. */
    public static final long HOUR_TICKS = 72_000L;
    /** Counted ticks held back on the soul before they are written onto its record. */
    private static final int WRITE_TICKS = 20;
    private static final long MINUTE_TICKS = HOUR_TICKS / 60L;

    /** Whether the road opens now: OPEN, closed to this soul (OFF), or the first gate it has still to pass. */
    public enum Gate { OPEN, OFF, HOURS, WEAK, RESTING }

    private SlowRoad() {
    }

    /** One of the soul's ticks: counted and held back, the held-back ticks written once a second's worth or as it stops. */
    public static void count(Player player, SoulData data, long now) {
        if (counts(player, data, now)) {
            if (++data.roadTicks >= WRITE_TICKS) {
                write(data);
            }
        } else if (data.roadTicks > 0) {
            write(data);
        }
    }

    /**
     * Whether this tick counts: no spectator, its own blade in its main hand released in Shikai, Bankai not reached, and
     * its power its own ({@link Materialisation#mayCall}: named and wielding, neither lent nor seized, so never a student).
     */
    private static boolean counts(Player player, SoulData data, long now) {
        ItemStack held = player.getMainHandItem();
        return !player.isSpectator() && ZanpakutoLogic.isOwnBlade(held, player)
                && ZanpakutoItem.blade(held).stage() == ZanpakutoStage.SHIKAI
                && !data.zanpakuto().bankai().achieved() && Materialisation.mayCall(data, now);
    }

    private static void write(SoulData data) {
        ZanpakutoBond bond = data.zanpakuto();
        data.setZanpakuto(bond.withBankai(bond.bankai().trained(data.roadTicks)));
        data.roadTicks = 0;
    }

    /** The ticks this soul has spent in its Shikai on the road: those written and those held back. */
    public static long ticks(SoulData data) {
        return data.zanpakuto().bankai().shikaiTicks() + data.roadTicks;
    }

    /**
     * The road for this soul now, in this order: OFF once Bankai is reached, below a full bond, with its power not its
     * own or with a spirit that has no Bankai in this world; HOURS short of its hours; WEAK below the maximum reiryoku;
     * RESTING within a game day of the last bout's start, by any road (a last bout after now, from a save carried to a
     * younger world, holds nothing back); else OPEN.
     */
    public static Gate gate(Level level, SoulData data, long now) {
        ZanpakutoBond bond = data.zanpakuto();
        if (bond.bankai().achieved() || !bond.isFull() || !Materialisation.mayCall(data, now)
                || ZanpakutoSpirits.get(level, bond.spirit()).flatMap(ZanpakutoSpirit::bankai).isEmpty()) {
            return Gate.OFF;
        }
        if (ticks(data) < hoursTicks()) {
            return Gate.HOURS;
        }
        if (data.maxReiryoku() < minMaxReiryoku()) {
            return Gate.WEAK;
        }
        long last = bond.bankai().lastTryAt();
        return last <= now && now - last < Level.TICKS_PER_DAY ? Gate.RESTING : Gate.OPEN;
    }

    /**
     * Jinzen's hand-over once the soul has settled and no inner Hollow waits, at the gate read before its world opens:
     * open, its Bankai trial begins where it sits or is refused as the trial says; any other, nothing is tried (null).
     */
    @Nullable
    public static BankaiTrial.Start handOver(Player player, Gate gate, long now) {
        return gate == Gate.OPEN ? BankaiTrial.begin(player, now) : null;
    }

    /**
     * The visit's last line, for the gate the hand-over read and what came of its bout: why the bout was refused, in the
     * words the trial's command uses, or what the first gate short of which the soul stands still asks; null when the
     * road is closed to the soul, or its bout began.
     */
    @Nullable
    public static Component pageLine(Player player, SoulData data, long now, Gate gate, @Nullable BankaiTrial.Start start) {
        if (start != null && start != BankaiTrial.Start.STARTED) {
            Component refused = Component.translatable("commands.bleach.quincy.arts_refused", player.getDisplayName(),
                    Component.translatable(start.key()));
            return Component.translatable("jinzen.bleach.road.held", refused);
        }
        return switch (gate) {
            case HOURS -> {
                long minutes = Math.ceilDiv(hoursTicks() - ticks(data), MINUTE_TICKS);
                yield Component.translatable("jinzen.bleach.road.hours", minutes / 60L, minutes % 60L);
            }
            case WEAK -> Component.translatable("jinzen.bleach.road.weak", (int) Math.ceil(minMaxReiryoku()));
            case RESTING -> Component.translatable("jinzen.bleach.road.resting",
                    JinzenLogic.duration(data.zanpakuto().bankai().lastTryAt() + Level.TICKS_PER_DAY - now));
            default -> null;
        };
    }

    /** The hours in Shikai the road asks ({@code [bankai.road] slow_road_hours}), in ticks. */
    public static long hoursTicks() {
        return BleachConfig.get(BleachConfig.BANKAI_ROAD_HOURS) * HOUR_TICKS;
    }

    /** The maximum reiryoku the road asks ({@code [bankai.road] min_max_reiryoku}). */
    public static double minMaxReiryoku() {
        return BleachConfig.get(BleachConfig.BANKAI_ROAD_MIN_MAX_REIRYOKU);
    }

    /**
     * The soul screen's row in the Bankai's place before it is reached: how long the soul has held its own Shikai
     * released, in hours and minutes of play rounded down, for a soul that may hold Bankai, once that is a minute; null
     * otherwise. It reads the soul alone, which on the client holds what was last written.
     */
    @Nullable
    public static Component screenLine(SoulData data) {
        long ticks = ticks(data);
        if (ticks < MINUTE_TICKS || data.zanpakuto().bankai().achieved() || !BankaiLogic.mayHold(data)) {
            return null;
        }
        long minutes = ticks / MINUTE_TICKS;
        return Component.translatable("screen.bleach.soul.shikai_time", minutes / 60L, minutes % 60L);
    }
}
