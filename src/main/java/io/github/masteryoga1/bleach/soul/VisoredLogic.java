package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.Menos;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The rules of the Visored: a Shinigami with a Hollow inside. Who can take a Hollow into its wounds and what
 * wounds it, when the rooted Hollow speaks and can be faced, what the soul screen says meanwhile, how strong the
 * inner Hollow is and where its losing blow stops. A Visored is a soul with Shinigami power whose Hollow is
 * beaten ({@link #isVisored}); there is no Visored state and no Hollow tier, so to every other rule it is a
 * Shinigami. Like {@link ArrancarLogic}, these read only their arguments and the config, send nothing and are
 * safe on both sides, so game tests and the client can call them directly; the one write is
 * {@link #setRecord}. What they do to a player, and the fight itself, are in {@link VisoredEvents}, and the
 * Hollow that steps out is {@link InnerHollow}. {@code now} is the level's game time.
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
     * it rests after beating its host, and {@code hollowfied.ready} once it can be faced. Null, so the soul hears
     * its usual line, with the path off, with no Hollow or a beaten one, and for a soul without Shinigami power:
     * a Hollow or an Arrancar with a rooted record hears its own line, not one about Jinzen.
     */
    @Nullable
    public static String nextStepKey(SoulData data, long now) {
        if (!enabled() || !ShinigamiLogic.hasShinigamiPower(data)) {
            return null;
        }
        ShinigamiProgress.Visored record = record(data);
        if (!record.rooted() || record.subdued()) {
            return null;
        }
        if (now >= record.duelReadyAt()) {
            return "hollowfied.ready";
        }
        return record.losses() > 0 ? "hollowfied.waiting" : "hollowfied";
    }

    /**
     * Puts the record's clocks back in reach of the game time, for a world whose time was changed or a save
     * copied between worlds: no wound, rooting or win lies in the future, and no wait is longer than the longest
     * the config gives (the stirring or the rest after a fight). A time that never happened stays so.
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
        return out;
    }

    /** Whole seconds from {@code now} until {@code at}, rounded up, and never below 0: for the fight's bar. */
    public static long secondsUntil(long at, long now) {
        return Math.max(0L, (long) Math.ceil((at - now) / 20.0));
    }

    /** The value, or 0 when it is negative or not a number. */
    private static double atLeastZero(float value) {
        return value > 0.0F ? value : 0.0;
    }
}
