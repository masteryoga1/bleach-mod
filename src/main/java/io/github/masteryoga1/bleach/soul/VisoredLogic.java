package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.Menos;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The rules of the Visored: a Shinigami with a Hollow inside. Who can take a Hollow into its wounds and what
 * wounds it, when the rooted Hollow speaks and can be faced, what the soul screen says meanwhile, how strong the
 * inner Hollow is and where its losing blow stops, and what the beaten Hollow's mask costs, how long it lasts and
 * grows, what it gives and how long the next one waits. With the mask on come its two arts: the masked Cero, once
 * masks have been worn long enough ({@link #maskedCero}), and the black Getsuga Tenshō ({@link #blackGetsuga}).
 * Before the win, the Hollow may seize the body near death ({@link #mayBeTakenOver}), and behind the mask when its
 * host reaches for its power at low health ({@link #losesControl}); with a server's leave, a fight lost once too
 * often makes the soul a Hollow ({@link #fallsOnLoss}). Whether it holds the body now is server-side only
 * ({@link #takenOver}). A Visored is a soul with Shinigami power whose Hollow is beaten ({@link #isVisored}); there
 * is no Visored state and no Hollow tier, so to every other rule it is a Shinigami, masked or not. Like
 * {@link ArrancarLogic}, these read only their arguments and the config, send nothing and are safe on both sides, so
 * game tests and the client can call them directly; the one write is {@link #setRecord}. What they do to a player,
 * the fight itself, the mask's upkeep and the Hollow's hold are in {@link VisoredEvents}, and the Hollow that steps
 * out is {@link InnerHollow}. {@code now} is the level's game time.
 */
public final class VisoredLogic {
    /** Blocks from its host at which the inner Hollow steps out. */
    public static final int SPAWN_DISTANCE = 4;
    /** Speed with which the ring pushes a creature back, in blocks a tick. */
    public static final double PUSH_STRENGTH = 0.6;
    /** Blocks past the ring at which a host who got out anyway breaks the fight off. */
    public static final int LEAVE_MARGIN = 16;
    /** Ticks without a blow from its host before the inner Hollow starts to mend. */
    public static final int MEND_DELAY_TICKS = 100;
    /** Ticks between one word from a rooted Hollow and the next: every 5 minutes. */
    public static final int VOICE_TICKS = 6000;
    /** How many plain lines the Hollow takes turns with ({@code speech.voice.0} to {@code .2}). */
    public static final int VOICE_LINES = 3;
    /** The inner Hollow's blow never goes past this, however strong its host. */
    public static final float MAX_BLOW = 20.0F;
    /** The inner Hollow's health never goes past this, vanilla's own cap on maximum health. */
    public static final float MAX_HOLLOW_HEALTH = 1024.0F;
    /** Ticks of Nausea as the Hollow takes root. */
    public static final int ROOT_NAUSEA_TICKS = 160;
    /** Ticks of Weakness as the Hollow takes root. */
    public static final int ROOT_WEAKNESS_TICKS = 600;
    /** Ticks of Weakness and Slowness after a lost fight. */
    public static final int LOSS_EFFECT_TICKS = 600;
    /** Ticks past the time limit an inner Hollow may live before it ends its own fight, whatever happened to it. */
    public static final int LIFE_MARGIN_TICKS = 600;
    /** The mask's movement speed, a share of base speed (transient). */
    public static final ResourceLocation MASK_SPEED_ID = BleachMod.id("visored_mask_speed");
    /** The mask's melee damage, a multiplier of everything else (transient). */
    public static final ResourceLocation MASK_DAMAGE_ID = BleachMod.id("visored_mask_damage");
    /** The melee damage the Hollow adds while it holds its host's body, a multiplier of everything else (transient). */
    public static final ResourceLocation TAKEOVER_DAMAGE_ID = BleachMod.id("visored_takeover_damage");

    private static final long TICKS_PER_MINUTE = 1200L;

    private VisoredLogic() {
    }

    // ---- The record ----

    /** The soul's Hollow: {@link ShinigamiProgress.Visored#NONE} while there is none. */
    public static ShinigamiProgress.Visored record(SoulData data) {
        return data.shinigami().visored();
    }

    /** Saves the soul's Hollow, which marks the soul to be sent to its owner if anything changed. */
    public static void setRecord(SoulData data, ShinigamiProgress.Visored record) {
        data.setShinigami(data.shinigami().withVisored(record));
    }

    /** Whether the server lets souls take a Hollow in and face it at all. */
    public static boolean enabled() {
        return BleachConfig.get(BleachConfig.VISORED_ENABLED);
    }

    /** Whether a Hollow has taken root in this soul, beaten or not. */
    public static boolean isRooted(SoulData data) {
        return record(data).rooted();
    }

    /** Whether this soul is a Visored: it holds a Shinigami's power, and has beaten the Hollow inside it. */
    public static boolean isVisored(SoulData data) {
        return ShinigamiLogic.hasShinigamiPower(data) && record(data).subdued();
    }

    // ---- The route ----

    /**
     * Whether a Hollow can take root in this soul's wounds. Only a soul of very high reiryoku could hold one
     * (canon: captains and lieutenants), so it needs its own Shinigami power, not lent away, a zanpakutō whose
     * name it knows and enough maximum reiryoku. A Quincy whose power has awakened cannot be Hollowfied, and a
     * soul takes only one Hollow in.
     */
    public static boolean mayTakeRoot(SoulData data, long now) {
        return enabled()
                && ShinigamiLogic.hasShinigamiPower(data)
                && !ShinigamiLogic.isPowerLent(data, now)
                && data.zanpakuto().named()
                && data.maxReiryoku() >= BleachConfig.get(BleachConfig.VISORED_MIN_MAX_REIRYOKU)
                && !QuincyLogic.isAwakened(data)
                && !record(data).rooted();
    }

    /**
     * Whether a blow from this creature can leave Hollow reiatsu in this soul's wound: a Menos-class Hollow, which
     * is a Gillian, an Adjuchas, an Arrancar, or a Hollow or Arrancar player of that rank, the mod's stand-in for
     * Aizen's wound. A Substitute takes it from any Hollow, mob or player, as Ichigo's came another way. Its own
     * inner Hollow never counts.
     */
    public static boolean isWoundingSource(LivingEntity attacker, SoulData victim) {
        if (attacker instanceof InnerHollow) {
            return false;
        }
        if (attacker instanceof Menos || attacker instanceof ArrancarNpc) {
            return true;
        }
        SoulData soul = attacker instanceof Player && SoulLogic.hasSoul(attacker) ? SoulLogic.get(attacker) : null;
        if (soul != null && (HollowTier.of(soul).isMenos() || ArrancarLogic.isArrancar(soul))) {
            return true;
        }
        if (!ShinigamiLogic.isSubstitute(victim)) {
            return false;
        }
        return attacker instanceof Hollow || soul != null && HollowPlayerLogic.isHollowOrArrancar(soul);
    }

    /** The wounds that root the Hollow in this soul: fewer for a Substitute. */
    public static int woundsToRoot(SoulData data) {
        return ShinigamiLogic.isSubstitute(data)
                ? BleachConfig.get(BleachConfig.VISORED_SUBSTITUTE_WOUNDS_TO_ROOT)
                : BleachConfig.get(BleachConfig.VISORED_WOUNDS_TO_ROOT);
    }

    /** Whether a blow that left this much health is a wound: alive, and at a quarter of its health or less. */
    public static boolean isWound(float healthAfter, float maxHealth) {
        return healthAfter > 0.0F && healthAfter <= maxHealth * BleachConfig.get(BleachConfig.VISORED_WOUND_HEALTH_SHARE);
    }

    /** Whether enough time has passed since the last counted wound for another to count. */
    public static boolean woundSpaced(ShinigamiProgress.Visored record, long now) {
        return now - record.lastWoundAt() >= BleachConfig.get(BleachConfig.VISORED_WOUND_SPACING_SECONDS) * 20L;
    }

    // ---- The fight ----

    /**
     * Whether the Hollow is ready to be faced in Jinzen: rooted, not beaten, in a soul with Shinigami power, and
     * past its stirring or the rest after the last fight. A fight under way sets the wait to its own end, so a
     * host sitting in Jinzen mid-fight never reads as ready.
     */
    public static boolean duelReady(SoulData data, long now) {
        ShinigamiProgress.Visored record = record(data);
        return enabled() && record.rooted() && !record.subdued() && ShinigamiLogic.hasShinigamiPower(data)
                && now >= record.duelReadyAt();
    }

    /**
     * The inner Hollow's maximum health for a host of this maximum reiryoku: 30 + 2·√r at the defaults, so 90 at
     * 900. The square root keeps it a hard fight at every strength.
     */
    public static float hollowHealth(float hostMaxReiryoku) {
        double root = Math.sqrt(atLeastZero(hostMaxReiryoku));
        double health = BleachConfig.get(BleachConfig.INNER_HOLLOW_HEALTH_BASE)
                + BleachConfig.get(BleachConfig.INNER_HOLLOW_HEALTH_PER_ROOT_REIRYOKU) * root;
        return (float) Math.min(MAX_HOLLOW_HEALTH, health);
    }

    /** The inner Hollow's blow for a host of this maximum reiryoku: 2 + 0.1·√r at the defaults, so 5 at 900. */
    public static float hollowBlow(float hostMaxReiryoku) {
        double root = Math.sqrt(atLeastZero(hostMaxReiryoku));
        double blow = BleachConfig.get(BleachConfig.INNER_HOLLOW_BLOW_BASE)
                + BleachConfig.get(BleachConfig.INNER_HOLLOW_BLOW_PER_ROOT_REIRYOKU) * root;
        return (float) Math.min(MAX_BLOW, blow);
    }

    /**
     * Whether a blow of this much damage, after armour and before absorption, would leave its host at the loss
     * health or less: the Hollow's losing blow, which ends the fight.
     */
    public static boolean isLosingBlow(float health, float absorption, float damage) {
        return health - Math.max(0.0F, damage - absorption) <= lossHealth();
    }

    /**
     * The damage a losing blow of {@code damage} is cut to, so that it leaves exactly {@code lossHealth} once the
     * absorption is spent; health already at or below it is never lowered. Never more than the blow itself.
     */
    public static float clampedBlow(float damage, float health, float absorption, float lossHealth) {
        return Math.min(damage, Math.max(0.0F, absorption + health - lossHealth));
    }

    /** Blocks from the centre of a fight to its ring. */
    public static int arenaRadius() {
        return BleachConfig.get(BleachConfig.INNER_HOLLOW_ARENA_RADIUS);
    }

    /** Ticks before a fight is lost. */
    public static long timeLimitTicks() {
        return BleachConfig.get(BleachConfig.INNER_HOLLOW_TIME_LIMIT_SECONDS) * 20L;
    }

    /** Ticks after a lost or broken-off fight before the Hollow can be faced again. */
    public static long retryTicks() {
        return BleachConfig.get(BleachConfig.INNER_HOLLOW_RETRY_MINUTES) * TICKS_PER_MINUTE;
    }

    /** Ticks after rooting before the Hollow can first be faced. */
    private static long stirTicks() {
        return BleachConfig.get(BleachConfig.VISORED_STIR_MINUTES) * TICKS_PER_MINUTE;
    }

    /** The host's health where the Hollow's last blow stops. */
    private static float lossHealth() {
        return (float) BleachConfig.get(BleachConfig.INNER_HOLLOW_LOSS_HEALTH);
    }

    // ---- The voice and the soul screen ----

    /**
     * What the rooted Hollow says to its host this tick, as the last part of a
     * {@code message.bleach.visored.speech.*} key, or null when it says nothing. It speaks once every
     * {@link #VOICE_TICKS}, on a tick fixed by the soul's id so that not every Hollow on a server speaks at once:
     * {@code ready} once it can be faced, {@code taunt} if it has beaten its host before, and otherwise one of
     * the plain {@code voice.<n>} lines in turn. It is silent with the path off, once beaten, and while the
     * soul's power is gone or lent away (it is the lender's Hollow); {@link VisoredEvents} also keeps it quiet
     * during a fight.
     */
    @Nullable
    public static String voiceKey(SoulData data, UUID id, long now) {
        ShinigamiProgress.Visored record = record(data);
        if (!enabled() || !record.rooted() || record.subdued() || !ShinigamiLogic.hasShinigamiPower(data)
                || ShinigamiLogic.isPowerLent(data, now)
                || Math.floorMod(now, VOICE_TICKS) != Math.floorMod(id.hashCode(), VOICE_TICKS)) {
            return null;
        }
        if (duelReady(data, now)) {
            return record.losses() > 0 ? "taunt" : "ready";
        }
        return "voice." + Math.floorMod(now / VOICE_TICKS, VOICE_LINES);
    }

    /**
     * The soul screen's next step while a Hollow is rooted and not yet beaten, as the last part of a
     * {@code screen.bleach.soul.next.*} key: {@code hollowfied} while it stirs, {@code hollowfied.waiting} while
     * it rests after beating its host, and {@code hollowfied.ready} once it can be faced; once it is beaten,
     * {@code visored} (the Hollow Mask key) until the first mask is worn. Null, so the soul hears its usual line,
     * with the path off, with no Hollow, once the mask has been worn, and for a soul without Shinigami power: a
     * Hollow or an Arrancar with a rooted record hears its own line, not one about Jinzen.
     */
    @Nullable
    public static String nextStepKey(SoulData data, long now) {
        if (!enabled() || !ShinigamiLogic.hasShinigamiPower(data)) {
            return null;
        }
        ShinigamiProgress.Visored record = record(data);
        if (!record.rooted()) {
            return null;
        }
        if (record.subdued()) {
            return record.maskChangedAt() == ShinigamiProgress.NEVER ? "visored" : null;
        }
        if (now >= record.duelReadyAt()) {
            return "hollowfied.ready";
        }
        return record.losses() > 0 ? "hollowfied.waiting" : "hollowfied";
    }

    /**
     * Puts the record's clocks back in reach of the game time, for a world whose time was changed or a save
     * copied between worlds: no wound, rooting, win or change of the mask lies in the future, no wait is longer
     * than the longest the config gives (the stirring or the rest after a fight, and the longest wait after a
     * mask), and no mask on lasts longer than {@code max_seconds} from now. A time that never happened stays so,
     * except that a mask on with no start (a missing or damaged {@code mask_changed_at}) starts now, so it is never
     * counted as worn since {@link ShinigamiProgress#NEVER}.
     */
    public static ShinigamiProgress.Visored normalise(ShinigamiProgress.Visored record, long now) {
        ShinigamiProgress.Visored out = record;
        if (out.lastWoundAt() > now) {
            out = out.withLastWoundAt(now);
        }
        if (out.rootedAt() > now) {
            out = out.withRootedAt(now);
        }
        if (out.subduedAt() > now) {
            out = out.withSubduedAt(now);
        }
        long latestReady = now + Math.max(stirTicks(), retryTicks());
        if (out.duelReadyAt() > latestReady) {
            out = out.withDuelReadyAt(latestReady);
        }
        if (out.maskChangedAt() > now) {
            out = out.withMaskChangedAt(now);
        }
        long latestEnd = now + maskMaxTicks();
        if (out.maskEndsAt() > latestEnd) {
            out = out.withMaskEndsAt(latestEnd);
        }
        long longestWait = Math.max(Math.max(BleachConfig.get(BleachConfig.VISORED_MASK_CRUMBLE_WAIT_SECONDS),
                BleachConfig.get(BleachConfig.VISORED_MASK_MIN_WAIT_SECONDS)),
                BleachConfig.get(BleachConfig.VISORED_MASK_MAX_SECONDS)) * 20L;
        if (out.maskReadyAt() > now + longestWait) {
            out = out.withMaskReadyAt(now + longestWait);
        }
        if (out.masked() && out.maskChangedAt() == ShinigamiProgress.NEVER) {
            out = out.withMaskChangedAt(now);
        }
        return out;
    }

    // ---- The mask ----

    /**
     * Whether the Hollow's mask is on: read from the saved record alone, with no config, so it is safe on the client
     * (its HUD and look) and from {@link SoulLogic#reiatsu} anywhere. The upkeep in {@link VisoredEvents} takes off a
     * mask its soul may no longer wear.
     */
    public static boolean isMasked(SoulData data) {
        return record(data).masked();
    }

    /**
     * Whether the mask on has cracked: blows in this wearing have taken half the health share that shatters it.
     * The share is server-side only, so on a client's copy this is false; the look carries the crack.
     */
    public static boolean maskCracked(SoulData data) {
        return isMasked(data)
                && data.maskDamageShare >= BleachConfig.get(BleachConfig.VISORED_MASK_SHATTER_HEALTH_SHARE) / 2.0;
    }

    /**
     * How long a mask put on now lasts, in ticks, for this many ticks of masks worn before: {@code base_seconds}
     * and {@code seconds_per_minute_worn} more for every minute worn, at most {@code max_seconds}. That is 11 s at
     * first and 3 minutes after 56 min 20 s at the defaults. Never less than a tick.
     */
    public static long maskDurationTicks(long trainedTicks) {
        double grown = BleachConfig.get(BleachConfig.VISORED_MASK_BASE_SECONDS) * 20.0
                + Math.floor(Math.max(0L, trainedTicks)
                        * BleachConfig.get(BleachConfig.VISORED_MASK_SECONDS_PER_MINUTE_WORN) / 60.0);
        return Math.max(1L, (long) Math.min((double) maskMaxTicks(), grown));
    }

    /** Reiryoku the mask costs to call: {@code cost_share} of the maximum, and never less than {@code min_cost}. */
    public static float maskCost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.VISORED_MASK_MIN_COST),
                BleachConfig.get(BleachConfig.VISORED_MASK_COST_SHARE) * Math.max(0.0F, data.maxReiryoku()));
    }

    /**
     * Reiryoku the mask drains each tick it is on: {@code drain_share_per_second} of the maximum a second, shrunk
     * by Reiatsu Control as Shikai's drain is ({@link ZanpakutoLogic#drainFraction}).
     */
    public static float maskDrainPerTick(SoulData data) {
        return (float) (Math.max(0.0F, data.maxReiryoku())
                * BleachConfig.get(BleachConfig.VISORED_MASK_DRAIN_SHARE_PER_SECOND)
                * ZanpakutoLogic.drainFraction(data) / 20.0);
    }

    /**
     * What the mask weighs the soul's reiatsu by, in {@link SoulLogic#reiatsu}: {@code reiatsu_multiplier} while it
     * is on, so a masked Visored reads as an Espada does, and 1 otherwise. Reads the config only while masked.
     */
    public static float reiatsuFactor(SoulData data) {
        return isMasked(data) ? (float) BleachConfig.get(BleachConfig.VISORED_MASK_REIATSU_MULTIPLIER) : 1.0F;
    }

    /**
     * Ticks this wearing has lasted by {@code now}, never past its end: 0 when the start is unknown, and never more
     * than {@code max_seconds}, so a damaged stamp cannot count days.
     */
    public static long maskWorn(ShinigamiProgress.Visored record, long now) {
        if (record.maskChangedAt() == ShinigamiProgress.NEVER) {
            return 0L;
        }
        long worn = Math.min(now, record.maskEndsAt()) - record.maskChangedAt();
        return Math.max(0L, Math.min(maskMaxTicks(), worn));
    }

    /**
     * Ticks before the next mask after one worn this long: as long as it was worn, and at least
     * {@code crumble_wait_seconds} when it crumbled, ran dry or shattered, or {@code min_wait_seconds} otherwise. A
     * crumble never waits less than taking it off would have.
     */
    public static long maskWaitTicks(boolean crumbled, long worn) {
        long least = (crumbled
                ? BleachConfig.get(BleachConfig.VISORED_MASK_CRUMBLE_WAIT_SECONDS)
                : BleachConfig.get(BleachConfig.VISORED_MASK_MIN_WAIT_SECONDS)) * 20L;
        return Math.max(least, worn);
    }

    /**
     * The record once the mask on comes off now: the change stamped (the wait's start), no end, the wait after it
     * and the time worn added to the training. In creative there is no wait and nothing is trained.
     */
    public static ShinigamiProgress.Visored maskEnded(ShinigamiProgress.Visored record, long now, boolean crumbled,
                                                      boolean creative) {
        long worn = maskWorn(record, now);
        long wait = creative ? 0L : maskWaitTicks(crumbled, worn);
        long trained = creative ? record.maskTrainedTicks() : record.maskTrainedTicks() + worn;
        return record.withMaskChangedAt(now).withMaskEndsAt(ShinigamiProgress.NEVER).withMaskReadyAt(now + wait)
                .withMaskTrainedTicks(trained);
    }

    /** How much of the mask on is left, 0 to 1: 0 when off or its span is not positive. */
    public static float maskLeft(ShinigamiProgress.Visored record, long now) {
        if (!record.masked()) {
            return 0.0F;
        }
        long span = record.maskEndsAt() - record.maskChangedAt();
        if (span <= 0L) {
            return 0.0F;
        }
        return clampShare((double) (record.maskEndsAt() - now) / span);
    }

    /**
     * How far the wait has run, 0 to 1: 1 when ready, 0 when the wait's start is unknown (NEVER) or its span is not
     * positive.
     */
    public static float maskRecovered(ShinigamiProgress.Visored record, long now) {
        if (now >= record.maskReadyAt()) {
            return 1.0F;
        }
        if (record.maskChangedAt() == ShinigamiProgress.NEVER) {
            return 0.0F;
        }
        long span = record.maskReadyAt() - record.maskChangedAt();
        if (span <= 0L) {
            return 0.0F;
        }
        return clampShare((double) (now - record.maskChangedAt()) / span);
    }

    /** The longest a mask lasts, in ticks. */
    private static long maskMaxTicks() {
        return BleachConfig.get(BleachConfig.VISORED_MASK_MAX_SECONDS) * 20L;
    }

    /** The share held between 0 and 1. */
    private static float clampShare(double share) {
        return (float) Math.max(0.0, Math.min(1.0, share));
    }

    // ---- The masked arts ----

    /** Ticks of masks worn, in all, before the mask's Cero answers: {@code cero_after_minutes_worn} minutes. */
    public static long ceroUnlockTicks() {
        return BleachConfig.get(BleachConfig.VISORED_CERO_AFTER_MINUTES_WORN) * TICKS_PER_MINUTE;
    }

    /**
     * Whether the soul's masks have been worn long enough for the masked Cero, read from the saved record alone: the
     * wearing on now counts once it ends, as it does for the mask's own growth.
     */
    public static boolean ceroUnlocked(SoulData data) {
        return record(data).maskTrainedTicks() >= ceroUnlockTicks();
    }

    /**
     * Whether this soul gathers the masked Cero now: its mask on, a Visored, the path on and its masks worn long
     * enough. Client-safe (the record and the server's synced config); reads the config only while masked.
     */
    public static boolean maskedCero(SoulData data) {
        return isMasked(data) && isVisored(data) && enabled() && ceroUnlocked(data);
    }

    /** Whether a Getsuga Tenshō loosed now is the black one: its mask on, a Visored and the path on. */
    public static boolean blackGetsuga(SoulData data) {
        return isMasked(data) && isVisored(data) && enabled();
    }

    /** What the mask multiplies a Getsuga Tenshō's damage by: {@code black_getsuga_multiplier} for the black one, else 1. */
    public static float getsugaFactor(SoulData data) {
        return blackGetsuga(data) ? (float) BleachConfig.get(BleachConfig.VISORED_BLACK_GETSUGA_MULTIPLIER) : 1.0F;
    }

    // ---- The Hollow's hold ----

    /**
     * Whether the inner Hollow holds this soul's body now. Server-side only: the time it lets go is not saved or sent, so
     * this is false on a client's copy (its owner's client reads {@link HollowPlayerLogic#LOOK_TAKEN_OVER}).
     */
    public static boolean takenOver(SoulData data) {
        return data.takeoverEndsAt != ShinigamiProgress.NEVER;
    }

    /** Whether {@code spacing_seconds} have passed since the last takeover began. */
    public static boolean takeoverSpaced(SoulData data, long now) {
        return now - data.lastTakeoverAt >= BleachConfig.get(BleachConfig.VISORED_TAKEOVER_SPACING_SECONDS) * 20L;
    }

    /**
     * Whether a blow near death may let this soul's Hollow seize its body: the path on, rooted and not yet beaten, with
     * its own Shinigami power not lent away, not already seized, and the spacing past.
     */
    public static boolean mayBeTakenOver(SoulData data, long now) {
        ShinigamiProgress.Visored record = record(data);
        return enabled() && record.rooted() && !record.subdued() && ShinigamiLogic.hasShinigamiPower(data)
                && !ShinigamiLogic.isPowerLent(data, now) && !takenOver(data) && takeoverSpaced(data, now);
    }

    /**
     * Whether a blow that took the health from {@code before} to {@code after} brought it near death: from above
     * {@code health_share} of the maximum to that share or less, and still alive. One blow below the line does not count
     * again.
     */
    public static boolean isTakeoverBlow(float before, float after, float maxHealth) {
        double line = maxHealth * BleachConfig.get(BleachConfig.VISORED_TAKEOVER_HEALTH_SHARE);
        return after > 0.0F && after <= line && before > line;
    }

    /**
     * The chance a blow near death lets the Hollow in: {@code chance}, or {@code chance_after_getsuga} when higher within
     * {@code getsuga_window_seconds} of a Getsuga Tenshō loosed at {@code lastGetsugaAt}.
     */
    public static double takeoverChance(long lastGetsugaAt, long now) {
        double chance = BleachConfig.get(BleachConfig.VISORED_TAKEOVER_CHANCE);
        long window = BleachConfig.get(BleachConfig.VISORED_TAKEOVER_GETSUGA_WINDOW_SECONDS) * 20L;
        long since = now - lastGetsugaAt;
        return window > 0L && since >= 0L && since <= window
                ? Math.max(chance, BleachConfig.get(BleachConfig.VISORED_TAKEOVER_CHANCE_AFTER_GETSUGA)) : chance;
    }

    /** Whether a roll from 0 (inclusive) to 1 (exclusive) lets the Hollow in at this chance: 1 always, 0 never. */
    public static boolean takeoverRolled(double roll, double chance) {
        return roll < chance;
    }

    /**
     * Whether a Getsuga Tenshō loosed now, at this health, makes the Visored lose control: the black one (its mask on,
     * the path on), its power not lent, not already seized, the spacing past, and alive at {@code masked_health_share}
     * of its maximum health or less.
     */
    public static boolean losesControl(SoulData data, float health, float maxHealth, long now) {
        return blackGetsuga(data) && !ShinigamiLogic.isPowerLent(data, now) && !takenOver(data)
                && takeoverSpaced(data, now) && health > 0.0F
                && health <= maxHealth * BleachConfig.get(BleachConfig.VISORED_TAKEOVER_MASKED_HEALTH_SHARE);
    }

    /** How long the Hollow holds the body, in ticks: {@code masked_seconds} behind a mask, else {@code seconds}. */
    public static long takeoverTicks(boolean masked) {
        return (masked ? BleachConfig.get(BleachConfig.VISORED_TAKEOVER_MASKED_SECONDS)
                : BleachConfig.get(BleachConfig.VISORED_TAKEOVER_SECONDS)) * 20L;
    }

    /**
     * Whether a fight lost that brings the count to {@code losses} makes the soul a Hollow: with {@code fall_into_hollow}
     * on, at {@code losses_to_fall} or past it.
     */
    public static boolean fallsOnLoss(int losses) {
        return BleachConfig.get(BleachConfig.VISORED_FALL_INTO_HOLLOW)
                && losses >= BleachConfig.get(BleachConfig.VISORED_LOSSES_TO_FALL);
    }

    // ---- Helpers ----

    /** Whole seconds from {@code now} until {@code at}, rounded up, and never below 0: for the fight's bar. */
    public static long secondsUntil(long at, long now) {
        return Math.max(0L, (long) Math.ceil((at - now) / 20.0));
    }

    /** The value, or 0 when it is negative or not a number. */
    private static double atLeastZero(float value) {
        return value > 0.0F ? value : 0.0;
    }
}
