package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The slow road to Bankai, by Jinzen alone, for a world with no Tenshintai to hand: so far, its hours.
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
