package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.QuincyNpc;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The rules for Hollow Bait and the Hunter's contest: when a bait may be crushed, how its waves
 * grow as Hollows arrive, when the sky cracks, and who won. Pure, so game tests can call them
 * directly; the sites themselves are run by {@link BaitEvents}.
 */
public final class BaitLogic {
    /** Why a bait cannot be crushed here and now, in the order they are checked. */
    public enum Refusal {
        UNAWARE, HOLLOW, WRONG_WORLD, QUIET, FIRST_HOLLOW, OWNED, ACADEMY, TOO_CLOSE, TOO_MANY, OTHERS;

        /** The message the player is shown: {@code message.bleach.bait.refused.<refusal>}. */
        public String key() {
            return "message.bleach.bait.refused." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Why a site ended. */
    public enum End {
        /** Its time ran out. */
        EXPIRED,
        /** The sky cracked and the Hollows were drawn up. */
        FINALE,
        /** Frozen too long, unloaded or in a world where no Hollows come (a contest is called off instead). */
        ABANDONED,
        /** Stopped by command. */
        STOPPED,
        /** A contest was settled. */
        CONTEST;

        /** The message the players at the site are shown: {@code message.bleach.bait.ended.<end>}. */
        public String key() {
            return "message.bleach.bait.ended." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a contest with the Hunter ended, for the challenger. */
    public enum Outcome {
        WIN, LOSS, TIE, VOID
    }

    /**
     * Everything the refusals weigh, gathered on the server.
     *
     * @param aware             the crusher has a soul and is spiritually aware
     * @param hollow            the crusher is a Hollow or an Arrancar
     * @param overworld         the bait is in the Overworld
     * @param quiet             no Hollows can come through here now (Peaceful, mob spawning off, arrivals or bait off)
     * @param awaitsFirstHollow the crusher's first Hollow is still to come
     * @param ownsSite          the crusher already owns a site, bait or contest
     * @param academyNear       an Academy instructor is close
     * @param siteNear          another site's centre is too close
     * @param sites             how many sites there are
     * @param maxSites          how many there may be
     * @param othersAtRisk      PvP is off and someone else who would be hunted, and is no ally, is in range
     */
    public record CrushContext(boolean aware, boolean hollow, boolean overworld, boolean quiet,
                               boolean awaitsFirstHollow, boolean ownsSite, boolean academyNear, boolean siteNear,
                               int sites, int maxSites, boolean othersAtRisk) {
    }

    private static final int TICKS_PER_SECOND = 20;
    /** From this share of the arrivals the finale needs, waves bring two tears, and from the next, three. */
    private static final float TWO_PICKS_FROM = 0.4F, THREE_PICKS_FROM = 0.8F;
    /** A finale needs this many arrivals before it counts as seen through without loss. */
    private static final int NO_LOSS_MIN_ARRIVED = 10;

    private BaitLogic() {
    }

    /**
     * The first reason this bait cannot be crushed, or null if it can. A command skips them all but
     * the wrong world (Peaceful is the command's own check).
     */
    @Nullable
    public static Refusal refusal(CrushContext c, boolean byCommand) {
        if (byCommand) {
            return c.overworld() ? null : Refusal.WRONG_WORLD;
        }
        if (!c.aware()) {
            return Refusal.UNAWARE;
        }
        if (c.hollow()) {
            return Refusal.HOLLOW;
        }
        if (!c.overworld()) {
            return Refusal.WRONG_WORLD;
        }
        if (c.quiet()) {
            return Refusal.QUIET;
        }
        if (c.awaitsFirstHollow()) {
            return Refusal.FIRST_HOLLOW;
        }
        if (c.ownsSite()) {
            return Refusal.OWNED;
        }
        if (c.academyNear()) {
            return Refusal.ACADEMY;
        }
        if (c.siteNear()) {
            return Refusal.TOO_CLOSE;
        }
        if (c.sites() >= c.maxSites()) {
            return Refusal.TOO_MANY;
        }
        return c.othersAtRisk() ? Refusal.OTHERS : null;
    }

    /** How far a bait has come towards cracking the sky, from 0 to 1, by the Hollows that have arrived. */
    public static float progress(int arrived, int forFinale) {
        return forFinale <= 0 ? 1.0F : Mth.clamp((float) arrived / forFinale, 0.0F, 1.0F);
    }

    /** The wait to the next wave, in ticks: from {@code startSeconds} at the beginning to {@code endSeconds} at the finale. */
    public static int waveIntervalTicks(float p, int startSeconds, int endSeconds) {
        float seconds = Mth.lerp(Mth.clamp(p, 0.0F, 1.0F), (float) startSeconds, (float) endSeconds);
        return Math.max(TICKS_PER_SECOND, Math.round(seconds * TICKS_PER_SECOND));
    }

    /** How many tears a wave opens: one, then two from 0.4 of the way, then three from 0.8. */
    public static int picksPerWave(float p) {
        if (p >= THREE_PICKS_FROM) {
            return 3;
        }
        return p >= TWO_PICKS_FROM ? 2 : 1;
    }

    /** The reiatsu a wave answers to: rising as the bait goes on, and never less than the soul it comes for. */
    public static float lureReiatsu(float soulReiatsu, float p, float start, float gain) {
        return Math.max(soulReiatsu, start + gain * Mth.clamp(p, 0.0F, 1.0F));
    }

    /** How many of a site's Hollows may be about at once: more for each soul they hunt past the first, up to the cap. */
    public static int aliveCap(int huntedSouls, int base, int perExtraSoul, int cap) {
        long wanted = (long) base + (long) perExtraSoul * Math.max(0, huntedSouls - 1);
        return (int) Math.min(cap, wanted);
    }

    /** How long the cracked sky holds open, in ticks: whole seconds between the two, in either order. */
    public static int finaleHoldTicks(int minSeconds, int maxSeconds, RandomSource random) {
        int low = Math.max(0, Math.min(minSeconds, maxSeconds));
        int high = Math.max(0, Math.max(minSeconds, maxSeconds));
        return (low + random.nextInt(high - low + 1)) * TICKS_PER_SECOND;
    }

    /**
     * How a contest ended. It is off if the challenger is not online in the world, or a player drove
     * the Hunter off; if he is gone any other way (killed, unloaded, retreated), it ends on the tally
     * at once, the challenger winning a tie with a Hollow to their name; and if the time ran out with
     * him there, more Hollows destroyed wins.
     *
     * @param hunterGone      why the Hunter went, if he left, or null
     * @param hunterGoneAtAll whether the Hunter is gone, for whatever reason, before the time ran out
     */
    public static Outcome contestOutcome(int ownerKills, int rivalKills, boolean challengerOnline,
                                         @Nullable QuincyNpc.LeaveCause hunterGone, boolean hunterGoneAtAll) {
        if (!challengerOnline || hunterGone == QuincyNpc.LeaveCause.HURT_BY_PLAYER) {
            return Outcome.VOID;
        }
        // A reason for going means he is gone, whatever the flag says.
        if (hunterGoneAtAll || hunterGone != null) {
            if (ownerKills >= rivalKills && ownerKills >= 1) {
                return Outcome.WIN;
            }
            return ownerKills < rivalKills ? Outcome.LOSS : Outcome.TIE;
        }
        if (ownerKills > rivalKills) {
            return Outcome.WIN;
        }
        return ownerKills < rivalKills ? Outcome.LOSS : Outcome.TIE;
    }

    /** Whether a finale was seen through without loss: enough Hollows came, and not one Plus was devoured. */
    public static boolean noLossFinale(int arrived, boolean plusLost) {
        return arrived >= NO_LOSS_MIN_ARRIVED && !plusLost;
    }
}
