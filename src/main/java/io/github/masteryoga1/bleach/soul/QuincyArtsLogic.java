package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.quincy.QuincyStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of the Quincy arts that come after the third tier: the Sanrei Glove and its week of
 * training, Quincy: Letzt Stil and the seal it leaves, winning the power back, Ginrei Kojaku, Seele
 * Schneider, the Gintō spells, and Quincy: Vollständig with Sklaverei. Like {@link QuincyLogic},
 * these only read a {@link SoulData}, a player and the config, change nothing and work on either
 * side, so game tests, the client and {@link QuincyArtsEvents} all ask the same questions. Every
 * factor here is exactly 1 for a soul whose power is untouched and who wears no glove, so the 4a
 * numbers hold for them. {@code now} is the level's game time.
 */
public final class QuincyArtsLogic {
    // ---- What other players' clients see (the bits of SoulAppearance#quincyLook) ----

    public static final int LOOK_GLOVE = 1;
    public static final int LOOK_GLOVE_MASTERED = 2;
    public static final int LOOK_LETZT = 4;
    public static final int LOOK_GINREI = 8;
    public static final int LOOK_VOLL = 16;
    public static final int LOOK_SKLAVEREI = 32;
    public static final int LOOK_HALO_BROKEN = 64;
    public static final int LOOK_SEALED = 128;

    // ---- Timings and shapes ----

    /** Presses of the Quincy Form key closer together than this are one press. */
    public static final int FORM_DEBOUNCE_TICKS = 5;
    /** The second press that tears the mastered glove must come this soon after the first. */
    public static final int TEAR_WINDOW_TICKS = 60;
    /** An unmastered glove pulls the bow apart once it is held this long past a full draw. */
    public static final int GLOVE_STRAIN_EXTRA_TICKS = 40;
    /** In Letzt Stil, a draw held this long past full looses a Letzt Pfeil. */
    public static final int LETZT_CHARGE_EXTRA_TICKS = 40;
    /** Ginrei Kojaku streams arrows from this long past a full draw, one every interval. */
    public static final int STREAM_DELAY_TICKS = 10;
    public static final int STREAM_INTERVAL_TICKS = 5;
    public static final float STREAM_SPEED = 2.6F;
    public static final float STREAM_INACCURACY = 1.0F;
    /** Licht Regen gathers for this long, then looses this many arrows a tick. */
    public static final int LICHT_GATHER_TICKS = 30;
    public static final int LICHT_PER_TICK = 4;
    public static final float LICHT_SPEED = 2.8F;
    public static final float LICHT_INACCURACY = 2.0F;
    public static final int LICHT_LIFE_TICKS = 40;
    public static final double LICHT_RANGE = 48.0;
    /** Only an arrow drawn at least this far can find the place beside another Quincy's heart. */
    public static final float RESTORE_CHARGE = 0.9F;
    /**
     * Where beside the heart an arrow must cross the body's middle: this far along the body's own
     * left (the heart side, negative is towards its right), and between these shares of its height.
     */
    public static final double HEART_LATERAL_MIN = -0.10, HEART_LATERAL_MAX = 0.20;
    public static final double HEART_HEIGHT_MIN = 0.55, HEART_HEIGHT_MAX = 0.85;
    /** The shot must come from in front: its heading against the body's facing at most this (within 60 degrees). */
    public static final double HEART_FRONT_DOT = -0.5;
    /**
     * Where a Quincy Elder aims his restoring shot: this far along the body's own left, and at this
     * share of its height, well inside the place beside the heart that counts.
     */
    public static final double HEART_AIM_LATERAL = 0.05, HEART_AIM_HEIGHT = 0.70;
    /** How long the weakness after winning the power back lasts. */
    public static final int RESTORE_EFFECT_TICKS = 600;
    /**
     * For this long after winning the power back no Heilig Pfeil strikes the Quincy, so the flank
     * arrows that fly beside a restoring shot cannot harm the one it just restored.
     */
    public static final int RESTORE_GRACE_TICKS = 2;
    public static final float SEELE_FIRED_SPEED_MULTIPLIER = 1.2F;
    /** A spent Seele Schneider that never lands drops its hilt after this long in the air. */
    public static final int SEELE_SPENT_AIR_TICKS = 1200;
    /** Gritz from the fifth blade of a Sprenger formation, and on what survives the blast. */
    public static final int SPRENGER_BIND_TICKS = 60;
    /** A formation binds again only this long after it last bound, so planting a corner again is no endless bind. */
    public static final int SPRENGER_BIND_COOLDOWN_TICKS = 200;
    public static final int SPRENGER_SURVIVOR_GRITZ_TICKS = 40;
    /** How far from the caster (or a blade that just stuck) Sprenger's blades are looked for. */
    public static final double SPRENGER_RANGE = 16.0;
    /** Heizen's pillar: this far either way of its centre, and this tall. */
    public static final double HEIZEN_HALF_WIDTH = 2.5;
    public static final double HEIZEN_HEIGHT = 6.0;
    public static final int HEIZEN_FUSE_TICKS = 10;
    public static final int SPRENGER_FUSE_TICKS = 10;
    public static final int SKLAVEREI_INTERVAL_TICKS = 10;
    public static final int SKLAVEREI_MAX_TARGETS = 8;
    /** Sklaverei never draws a soul's reiryoku below this. */
    public static final float SKLAVEREI_REIRYOKU_FLOOR = 0.5F;
    /** A projectile at least this close below the eyes breaks the halo. */
    public static final double HALO_HEAD_MARGIN = 0.3;
    /** Mining Fatigue after a day of training with the glove counts. */
    public static final int GLOVE_FATIGUE_TICKS = 1200;
    /** How long filling an empty Gintō takes. */
    public static final int FILL_TICKS = 32;
    public static final int GINTO_STACK = 16;
    /** Gritz holds a boss for this share of the time. */
    public static final int GRITZ_BOSS_DIVISOR = 4;

    /** The Gintō tier, and the tier of Heizen, the glove and Letzt Stil. */
    private static final int GINTO_TIER = 2;
    private static final int GLOVE_TIER = 3;
    /** How far behind the shooter the body's middle may be: point-blank shots still count. */
    private static final double HEART_BEHIND = -0.6;
    private static final double MIN_HEADING_SQR = 1.0E-6;
    private static final long TICKS_PER_DAY = 24000L;

    private QuincyArtsLogic() {
    }

    // ---- Reading the arts ----

    public static QuincyArts arts(SoulData data) {
        return data.quincy().arts();
    }

    /** Every change to the arts goes through here, so the soul is marked for syncing when it changes. */
    public static void setArts(SoulData data, QuincyArts arts) {
        data.setQuincy(data.quincy().withArts(arts));
    }

    public static QuincyStage stage(SoulData data) {
        return arts(data).stage();
    }

    /** The power burned out in Letzt Stil and has not come back yet. */
    public static boolean isSealed(SoulData data) {
        return stage(data) == QuincyStage.SEALED;
    }

    public static boolean inLetzt(SoulData data) {
        return stage(data) == QuincyStage.LETZT_STIL;
    }

    /** The power came back as Ginrei Kojaku. */
    public static boolean isRestored(SoulData data) {
        return stage(data) == QuincyStage.RESTORED;
    }

    public static boolean inVollstandig(SoulData data) {
        return arts(data).forms().vollstandig();
    }

    /** Sklaverei, which only runs inside Vollständig. */
    public static boolean sklavereiOn(SoulData data) {
        QuincyForms forms = arts(data).forms();
        return forms.vollstandig() && forms.sklaverei();
    }

    public static boolean gloveWorn(SoulData data) {
        return arts(data).glove().worn();
    }

    /** The Sanrei Glove is on the hand and no longer fights the draw. */
    public static boolean gloveMastered(SoulData data) {
        SanreiProgress glove = arts(data).glove();
        return glove.worn() && glove.mastered();
    }

    /** The Sanrei Glove is on the hand and still fights every draw. */
    public static boolean handicapped(SoulData data) {
        SanreiProgress glove = arts(data).glove();
        return glove.worn() && !glove.mastered();
    }

    /** An awakened Quincy who also holds a Shinigami's power: their forms give half the bonus. */
    public static boolean isHybrid(SoulData data) {
        return QuincyLogic.isAwakened(data) && ShinigamiLogic.hasShinigamiPower(data);
    }

    /** A form's multiplier for this soul: half the bonus ({@code 1 + (m - 1) / 2}) for a hybrid. */
    public static double bonus(SoulData data, double multiplier) {
        return isHybrid(data) ? 1.0 + (multiplier - 1.0) / 2.0 : multiplier;
    }

    // ---- The bow in each form ----
    // Only one row of the table applies at a time: the glove only while the power is untouched,
    // Vollständig only once it is restored, so a stray flag in another stage changes no number.

    /** Ticks to a full draw: half a second in Letzt Stil, less as Ginrei Kojaku, two seconds in the unmastered glove. */
    public static int drawTicks(SoulData data) {
        return switch (stage(data)) {
            case LETZT_STIL -> Math.max(1, BleachConfig.get(BleachConfig.LETZT_DRAW_TICKS));
            case RESTORED -> Math.max(1, BleachConfig.get(BleachConfig.GINREI_DRAW_TICKS));
            case UNTOUCHED -> handicapped(data)
                    ? Math.max(1, BleachConfig.get(BleachConfig.SANREI_DRAW_TICKS)) : QuincyLogic.FULL_DRAW_TICKS;
            case SEALED -> QuincyLogic.FULL_DRAW_TICKS;
        };
    }

    /** The same choice as {@link #drawTicks}, made from another player's look bits, for drawing their bow. */
    public static int drawTicksForLook(int look) {
        if ((look & LOOK_LETZT) != 0) {
            return Math.max(1, BleachConfig.get(BleachConfig.LETZT_DRAW_TICKS));
        }
        if ((look & LOOK_GINREI) != 0) {
            return Math.max(1, BleachConfig.get(BleachConfig.GINREI_DRAW_TICKS));
        }
        if ((look & LOOK_SEALED) == 0 && (look & LOOK_GLOVE) != 0 && (look & LOOK_GLOVE_MASTERED) == 0) {
            return Math.max(1, BleachConfig.get(BleachConfig.SANREI_DRAW_TICKS));
        }
        return QuincyLogic.FULL_DRAW_TICKS;
    }

    /**
     * How strong a draw of this many ticks is, from 0 to 1, on a bow that is full after
     * {@code drawTicks}. The curve is the vanilla bow's, and for 20 ticks it is exactly
     * {@code BowItem.getPowerForTime}: the same float steps in the same order.
     */
    public static float drawPower(int ticks, int drawTicks) {
        if (ticks <= 0) {
            return 0.0F;
        }
        float f = (float) ticks / (float) Math.max(1, drawTicks);
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(1.0F, f);
    }

    /** How much harder the soul's arrows hit in its form: a tenth with the mastered glove, more in each later form. */
    public static float damageMultiplier(SoulData data) {
        return (float) damageFactor(data);
    }

    /** The most a boosted arrow may do: the form's own cap, never below the Heilig Bogen's. */
    public static float damageCap(SoulData data) {
        return (float) capFor(data);
    }

    /** Damage boosted by the form, up to its cap. Unchanged, cap and all, where the form adds nothing. */
    public static float boostDamage(SoulData data, float damage) {
        double factor = damageFactor(data);
        if (factor == 1.0) {
            return damage;
        }
        // In doubles, so 10 x 1.3 is 13 and not a float step off it.
        return (float) Math.min(capFor(data), damage * factor);
    }

    /** What a full draw does now, boosted: the strength the stream, Licht Regen and Letzt Pfeil are measured against. */
    public static float boostedFullDamage(SoulData data) {
        return QuincyLogic.arrowDamage(data, 1.0F);
    }

    /** One more target pierced by a near-full draw in Letzt Stil. */
    public static int extraPierce(SoulData data, float power) {
        return inLetzt(data) && power >= QuincyLogic.PIERCE_POWER ? 1 : 0;
    }

    public static float shotSpeedMultiplier(SoulData data) {
        return inLetzt(data) ? (float) BleachConfig.get(BleachConfig.LETZT_SHOT_SPEED_MULTIPLIER) : 1.0F;
    }

    /** How much more reishi the soul holds: three times in Letzt Stil, half again in Vollständig. */
    public static float poolMultiplier(SoulData data) {
        return (float) switch (stage(data)) {
            case LETZT_STIL -> bonus(data, BleachConfig.get(BleachConfig.LETZT_POOL_MULTIPLIER));
            case RESTORED -> inVollstandig(data) ? bonus(data, BleachConfig.get(BleachConfig.VOLL_POOL_MULTIPLIER)) : 1.0;
            case UNTOUCHED, SEALED -> 1.0;
        };
    }

    /** How fast reishi gathers: half in the unmastered glove, four times in Letzt Stil, not at all while sealed. */
    public static float gatherMultiplier(SoulData data) {
        return (float) switch (stage(data)) {
            case UNTOUCHED -> handicapped(data) ? BleachConfig.get(BleachConfig.SANREI_GATHER_MULTIPLIER) : 1.0;
            case LETZT_STIL -> bonus(data, BleachConfig.get(BleachConfig.LETZT_GATHER_MULTIPLIER));
            case SEALED -> 0.0;
            case RESTORED -> 1.0;
        };
    }

    /** The reishi density gathering counts: in Letzt Stil, everywhere is at least dense. */
    public static double gatherDensity(SoulData data, double density) {
        return inLetzt(data) ? Math.max(density, BleachConfig.get(BleachConfig.LETZT_MIN_DENSITY)) : density;
    }

    /** Letzt Stil gathers reishi through Blut and while standing on reishi, which stop it otherwise. */
    public static boolean gathersThroughEverything(SoulData data) {
        return inLetzt(data);
    }

    /** What holding a full draw costs: twice as much in the unmastered glove. */
    public static float holdUpkeepMultiplier(SoulData data) {
        return stage(data) == QuincyStage.UNTOUCHED && handicapped(data)
                ? (float) BleachConfig.get(BleachConfig.SANREI_HOLD_UPKEEP_MULTIPLIER) : 1.0F;
    }

    /** How much further Hirenkyaku goes: half again in Letzt Stil, past the usual cap. */
    public static double hirenkyakuDistanceMultiplier(SoulData data) {
        return inLetzt(data) ? BleachConfig.get(BleachConfig.LETZT_HIRENKYAKU_DISTANCE_MULTIPLIER) : 1.0;
    }

    public static float hirenkyakuCostMultiplier(SoulData data) {
        return inLetzt(data) ? (float) BleachConfig.get(BleachConfig.LETZT_HIRENKYAKU_COST_MULTIPLIER) : 1.0F;
    }

    public static float hirenkyakuCooldownMultiplier(SoulData data) {
        return inLetzt(data) ? (float) BleachConfig.get(BleachConfig.LETZT_HIRENKYAKU_COOLDOWN_MULTIPLIER) : 1.0F;
    }

    /**
     * How much the soul's reiatsu counts for, against its maximum reiryoku: twice in Letzt Stil,
     * half again in Vollständig, and half while sealed, unless a Shinigami's power holds it up.
     * Reads only the arts and the Shinigami power, never {@link SoulLogic#reiatsu}, which calls this.
     */
    public static double reiatsuFactor(SoulData data) {
        return switch (stage(data)) {
            case LETZT_STIL -> bonus(data, BleachConfig.get(BleachConfig.LETZT_REIATSU_MULTIPLIER));
            case SEALED -> isHybrid(data) ? 1.0 : BleachConfig.get(BleachConfig.SEALED_REIATSU_MULTIPLIER);
            case RESTORED -> inVollstandig(data) ? bonus(data, BleachConfig.get(BleachConfig.VOLL_REIATSU_MULTIPLIER)) : 1.0;
            case UNTOUCHED -> 1.0;
        };
    }

    /** The share Letzt Stil adds to movement speed (half of it for a hybrid), or none. */
    public static double movementBonus(SoulData data) {
        if (!inLetzt(data)) {
            return 0.0;
        }
        // bonus(1 + b) - 1, written so a plain Quincy gets exactly b.
        double extra = BleachConfig.get(BleachConfig.LETZT_MOVEMENT_BONUS);
        return isHybrid(data) ? extra / 2.0 : extra;
    }

    private static double damageFactor(SoulData data) {
        return switch (stage(data)) {
            case UNTOUCHED -> gloveMastered(data) ? BleachConfig.get(BleachConfig.SANREI_MASTERED_DAMAGE_MULTIPLIER) : 1.0;
            case LETZT_STIL -> bonus(data, BleachConfig.get(BleachConfig.LETZT_DAMAGE_MULTIPLIER));
            case RESTORED -> inVollstandig(data)
                    ? bonus(data, BleachConfig.get(BleachConfig.VOLL_DAMAGE_MULTIPLIER))
                    : BleachConfig.get(BleachConfig.GINREI_DAMAGE_MULTIPLIER);
            case SEALED -> 1.0;
        };
    }

    private static double capFor(SoulData data) {
        double cap = switch (stage(data)) {
            case UNTOUCHED -> gloveMastered(data) ? BleachConfig.get(BleachConfig.SANREI_MASTERED_DAMAGE_CAP) : 0.0;
            case LETZT_STIL -> BleachConfig.get(BleachConfig.LETZT_DAMAGE_CAP);
            case RESTORED -> BleachConfig.get(inVollstandig(data) ? BleachConfig.VOLL_DAMAGE_CAP : BleachConfig.GINREI_DAMAGE_CAP);
            case SEALED -> 0.0;
        };
        // Raising the Heilig Bogen's cap never lowers a form's.
        return Math.max(BleachConfig.get(BleachConfig.BOGEN_MAX_DAMAGE), cap);
    }

    // ---- The Sanrei Glove ----

    /**
     * Why this soul cannot put the glove on, as a {@code message.bleach.sanrei_glove.*} key, or null
     * if it can: below the third tier, past the untouched power, unable to use it now, or wearing it
     * already.
     */
    @Nullable
    public static String gloveRefusal(SoulData data) {
        if (QuincyLogic.tier(data) < GLOVE_TIER) {
            return "message.bleach.sanrei_glove.tier";
        }
        if (stage(data) != QuincyStage.UNTOUCHED) {
            return "message.bleach.sanrei_glove.stage";
        }
        if (!QuincyLogic.canUsePowers(data)) {
            return "message.bleach.sanrei_glove.powerless";
        }
        return gloveWorn(data) ? "message.bleach.sanrei_glove.already_worn" : null;
    }

    /**
     * Which day of training this is. The later of the game time and the time of day counts, so
     * sleeping through the night starts a new day too.
     */
    public static long gloveDay(long gameTime, long dayTime) {
        return Math.floorDiv(Math.max(gameTime, dayTime), TICKS_PER_DAY);
    }

    /** Today's glove training: none yet if the count saved is from an earlier day. */
    public static int gloveToday(SanreiProgress glove, long gameTime, long dayTime) {
        return glove.day() == gloveDay(gameTime, dayTime) ? glove.today() : 0;
    }

    /**
     * Adds glove training on this day. A new day starts today's count again; today's count stops at
     * the day's points, and reaching them counts the day once. Enough counted days master the glove,
     * after which nothing more is added. The caller checks the glove is being worn.
     */
    public static GloveGain addGlovePoints(SanreiProgress glove, int points, long day) {
        if (glove.mastered() || points <= 0) {
            return new GloveGain(glove, false, false);
        }
        int perDay = Math.max(1, BleachConfig.get(BleachConfig.SANREI_POINTS_PER_DAY));
        int today = glove.day() == day ? glove.today() : 0;
        int next = (int) Math.min(perDay, (long) today + points);
        boolean dayCounted = today < perDay && next >= perDay;
        int days = dayCounted ? glove.days() + 1 : glove.days();
        boolean masteredNow = days >= BleachConfig.get(BleachConfig.SANREI_DAYS_TO_MASTER);
        SanreiProgress after = glove.withDay(day).withToday(next).withDays(days).withMastered(masteredNow);
        return new GloveGain(after, dayCounted, masteredNow);
    }

    /**
     * What {@link #addGlovePoints} did.
     *
     * @param glove       the glove's progress afterwards
     * @param dayCounted  whether today's points were reached just now, counting the day
     * @param masteredNow whether the glove became mastered just now
     */
    public record GloveGain(SanreiProgress glove, boolean dayCounted, boolean masteredNow) {
    }

    // ---- Quincy: Letzt Stil and the seal ----

    /**
     * Why this soul cannot tear the glove off for Letzt Stil, as a {@code message.bleach.letzt_stil.*}
     * key, or null if it can: turned off on the server, already done once, unable to use the power,
     * too poisoned, or without the mastered glove on.
     */
    @Nullable
    public static String letztRefusal(SoulData data) {
        if (!BleachConfig.get(BleachConfig.LETZT_ENABLED)) {
            return "message.bleach.letzt_stil.disabled";
        }
        if (stage(data) != QuincyStage.UNTOUCHED) {
            return "message.bleach.letzt_stil.once";
        }
        if (!QuincyLogic.canUsePowers(data)) {
            return "message.bleach.letzt_stil.powerless";
        }
        if (data.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
            return "message.bleach.letzt_stil.poisoned";
        }
        return gloveMastered(data) ? null : "message.bleach.letzt_stil.no_glove";
    }

    /** How long the power must have been sealed before it can return: less when another Quincy's arrow brings it back. */
    public static long sealedTicksNeeded(boolean byArrow) {
        return BleachConfig.get(byArrow ? BleachConfig.RESTORE_ARROW_SEALED_TICKS : BleachConfig.RESTORE_SEALED_TICKS);
    }

    /**
     * Whether the sealed wait counts towards another Quincy's arrow: when the solo rite is switched
     * off, that is the only way back. Otherwise it counts towards the rite.
     */
    public static boolean waitsForArrow() {
        return !BleachConfig.get(BleachConfig.RESTORE_SOLO_RITE);
    }

    /** How long a seal lasts before the power can come back the way the wait counts towards (see {@link #waitsForArrow}). */
    public static long sealedWaitTicks() {
        return sealedTicksNeeded(waitsForArrow());
    }

    /** Whether the seal has lasted {@link #sealedWaitTicks}. */
    public static boolean sealedWaitOver(SoulData data, long now) {
        return sealedLongEnough(data, now, waitsForArrow());
    }

    /** Whether the power has been sealed long enough to return this way. Never for a time before the seal. */
    public static boolean sealedLongEnough(SoulData data, long now, boolean byArrow) {
        QuincyArts arts = arts(data);
        return arts.stage() == QuincyStage.SEALED && now >= arts.stageSince()
                && elapsed(now, arts.stageSince()) >= sealedTicksNeeded(byArrow);
    }

    /** Whether a sealed soul may hold the Cross for the solo rite now (the draw itself is allowed). */
    public static boolean riteOpen(SoulData data, long now) {
        return isSealed(data) && BleachConfig.get(BleachConfig.RESTORE_SOLO_RITE) && sealedLongEnough(data, now, false);
    }

    /** Worn down to near collapse: low health and an empty stomach. */
    public static boolean exhausted(Player player) {
        return player.getHealth() <= BleachConfig.get(BleachConfig.RESTORE_MAX_HEALTH_FRACTION) * player.getMaxHealth()
                && player.getFoodData().getFoodLevel() <= BleachConfig.get(BleachConfig.RESTORE_MAX_FOOD);
    }

    /** Whether this player won the Quincy power back in the last {@link #RESTORE_GRACE_TICKS}. */
    public static boolean justRestored(Player player, long now) {
        if (!SoulLogic.hasSoul(player)) {
            return false;
        }
        QuincyArts arts = arts(SoulLogic.get(player));
        long since = now - arts.stageSince();
        return arts.stage() == QuincyStage.RESTORED && since >= 0 && since <= RESTORE_GRACE_TICKS;
    }

    /** Whether this sealed soul's power can return now, by the rite or by another Quincy's arrow. */
    public static boolean restorable(Player player, SoulData data, long now, boolean byArrow) {
        return sealedLongEnough(data, now, byArrow) && exhausted(player);
    }

    /**
     * Why the solo rite cannot be held here and now, as a {@code message.bleach.rite.*} key, or null
     * if it can: too soon ({@code too_soon}, which takes the time left; also for a soul the rite is not
     * open to at all), a body not worn down ({@code not_exhausted}), or reishi too thin around the eyes
     * ({@code thin}). The place is only weighed on the server, where it is called.
     */
    @Nullable
    public static String riteRefusal(Player player, SoulData data, long now) {
        if (!riteOpen(data, now)) {
            return "message.bleach.rite.too_soon";
        }
        if (!exhausted(player)) {
            return "message.bleach.rite.not_exhausted";
        }
        return QuincyLogic.placeDensity(player.level(), player.getEyePosition()) < QuincyLogic.THIN_BELOW
                ? "message.bleach.rite.thin" : null;
    }

    /** Whether this shooter's full draw can bring another Quincy's power back: a third-tier Quincy whose own power works. */
    public static boolean canRestoreOthers(Player owner) {
        if (!SoulLogic.hasSoul(owner)) {
            return false;
        }
        SoulData data = SoulLogic.get(owner);
        return QuincyLogic.tier(data) >= QuincyLogic.MAX_TIER && QuincyLogic.canUsePowers(data);
    }

    /**
     * Whether a shot from {@code from} along {@code heading} crosses a body's middle just beside the
     * heart: from in front (within 60 degrees), a little to the body's own left (the shooter's right),
     * at chest height. Pure geometry on a standing body of this height (a sneaking one is shorter),
     * facing its body yaw; at yaw 0 it faces +z and its left is +x.
     */
    public static boolean besideTheHeart(Vec3 from, Vec3 heading, Vec3 feet, float bodyYawDeg, float height) {
        if (!(heading.lengthSqr() >= MIN_HEADING_SQR) || !(height > 0.0F)) {
            return false;
        }
        double yaw = Math.toRadians(bodyYawDeg);
        Vec3 facing = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        Vec3 h = heading.normalize();
        double towards = h.dot(facing);
        if (!(towards <= HEART_FRONT_DOT)) {
            return false;
        }
        // How far along the shot the body's middle plane lies; a little behind still counts.
        double t = feet.subtract(from).dot(facing) / towards;
        if (!(t >= HEART_BEHIND)) {
            return false;
        }
        Vec3 crossing = from.add(h.scale(t));
        double lateral = crossing.subtract(feet).dot(left);
        double up = (crossing.y - feet.y) / height;
        return lateral >= HEART_LATERAL_MIN && lateral <= HEART_LATERAL_MAX
                && up >= HEART_HEIGHT_MIN && up <= HEART_HEIGHT_MAX;
    }

    /**
     * The point a shot aims at to pass just beside the heart of a standing body of this height with
     * its feet here, facing its body yaw: a little to its own left, at chest height, in the body's
     * middle plane (see {@link #besideTheHeart}).
     */
    public static Vec3 heartPoint(Vec3 feet, float bodyYawDeg, float height) {
        double yaw = Math.toRadians(bodyYawDeg);
        Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        return feet.add(left.scale(HEART_AIM_LATERAL)).add(0.0, HEART_AIM_HEIGHT * height, 0.0);
    }

    // ---- Quincy: Vollständig and Sklaverei ----

    /** Vollständig opens to a restored soul once it has trained this much more since its power returned. */
    public static boolean vollUnlocked(SoulData data) {
        return isRestored(data) && vollTrainingLeft(data) <= 0;
    }

    /** Training still needed for Vollständig, never below none. */
    public static int vollTrainingLeft(SoulData data) {
        long needed = (long) arts(data).trainingAtRestore() + BleachConfig.get(BleachConfig.VOLL_TRAINING_AFTER_RESTORE);
        return (int) Mth.clamp(needed - data.quincy().training(), 0L, Integer.MAX_VALUE);
    }

    /**
     * Why Vollständig cannot start now, as a {@code message.bleach.vollstandig.*} key, or null if it
     * can: not unlocked ({@code locked}, which takes the training left), still settling
     * ({@code cooldown}, the time left), too little reishi ({@code reishi}, the reishi needed), too
     * poisoned, or unable to use the power.
     */
    @Nullable
    public static String vollRefusal(SoulData data, long now) {
        if (!vollUnlocked(data)) {
            return "message.bleach.vollstandig.locked";
        }
        if (now < arts(data).forms().vollReadyAt()) {
            return "message.bleach.vollstandig.cooldown";
        }
        if (QuincyLogic.reishi(data) < BleachConfig.get(BleachConfig.VOLL_START_MIN_REISHI)) {
            return "message.bleach.vollstandig.reishi";
        }
        if (data.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
            return "message.bleach.vollstandig.poisoned";
        }
        return QuincyLogic.canUsePowers(data) ? null : "message.bleach.vollstandig.powerless";
    }

    /** Reishi Vollständig costs each second: more while flying, and more with Sklaverei on. */
    public static float vollUpkeepPerSecond(SoulData data, boolean flying) {
        double upkeep = BleachConfig.get(BleachConfig.VOLL_UPKEEP_PER_SECOND);
        if (flying) {
            upkeep += BleachConfig.get(BleachConfig.VOLL_FLIGHT_UPKEEP_PER_SECOND);
        }
        if (sklavereiOn(data)) {
            upkeep += BleachConfig.get(BleachConfig.SKLAVEREI_UPKEEP_PER_SECOND);
        }
        return (float) upkeep;
    }

    // ---- Gintō ----

    /** Gritz and Wolke come at the second tier, Heizen at the third, Sprenger with the restored power. */
    public static boolean knowsSpell(SoulData data, GintoSpell spell) {
        int tier = QuincyLogic.tier(data);
        return switch (spell) {
            case GRITZ, WOLKE -> tier >= GINTO_TIER;
            case HEIZEN -> tier >= GLOVE_TIER;
            case SPRENGER -> isRestored(data);
        };
    }

    /** The spells this soul knows, in the order sneak-use cycles through them. */
    public static List<GintoSpell> knownSpells(SoulData data) {
        List<GintoSpell> known = new ArrayList<>();
        for (GintoSpell spell : GintoSpell.values()) {
            if (knowsSpell(data, spell)) {
                known.add(spell);
            }
        }
        return List.copyOf(known);
    }

    /** The spell a Gintō casts: the one chosen, or the first known if that one is not known (yet, or any more). */
    public static GintoSpell selectedSpell(SoulData data) {
        GintoSpell chosen = arts(data).ginto();
        if (knowsSpell(data, chosen)) {
            return chosen;
        }
        List<GintoSpell> known = knownSpells(data);
        return known.isEmpty() ? chosen : known.get(0);
    }

    /**
     * Why this soul cannot fill an empty Gintō, as a {@code message.bleach.ginto.*} key, or null if it
     * can: below the second tier, unable to use the power (sealed among them), or short of reishi
     * ({@code fill_reishi}, which takes the cost).
     */
    @Nullable
    public static String fillRefusal(SoulData data) {
        if (QuincyLogic.tier(data) < GINTO_TIER) {
            return "message.bleach.ginto.fill_tier";
        }
        if (!QuincyLogic.canUsePowers(data)) {
            return "message.bleach.ginto.powerless";
        }
        return QuincyLogic.reishi(data) < BleachConfig.get(BleachConfig.GINTO_FILL_COST)
                ? "message.bleach.ginto.fill_reishi" : null;
    }

    /**
     * Why this soul cannot cast this spell, as a {@code message.bleach.ginto.*} key, or null if it
     * can. The reishi is already in the tube, so a sealed or poisoned soul may cast; it only needs to
     * be an awakened Quincy alive or a Shinigami ({@code powerless} otherwise) who knows the spell
     * ({@code unknown}, which takes the spell's name). The caller counts the tubes.
     */
    @Nullable
    public static String castRefusal(SoulData data, GintoSpell spell) {
        if (!QuincyLogic.isAwakened(data) || (data.state() != SoulState.HUMAN && data.state() != SoulState.SHINIGAMI)) {
            return "message.bleach.ginto.powerless";
        }
        return knowsSpell(data, spell) ? null : "message.bleach.ginto.unknown";
    }

    /**
     * How long Gritz binds: up to its full time, less against stronger reiatsu (never below a
     * quarter), a quarter of that for a boss, and always at least a tick.
     */
    public static int gritzTicks(double casterReiatsu, double targetReiatsu, boolean boss) {
        double share = Mth.clamp(casterReiatsu / Math.max(1.0, targetReiatsu), 0.25, 1.0);
        int ticks = (int) Math.round(BleachConfig.get(BleachConfig.GRITZ_MAX_TICKS) * share);
        return Math.max(1, boss ? ticks / GRITZ_BOSS_DIVISOR : ticks);
    }

    // ---- Ginrei Kojaku and Seele Schneider ----

    /** Whether a Seele Schneider in this soul's main hand takes its blade: restored power, usable, not too poisoned. */
    public static boolean canFormSeele(SoulData data) {
        return isRestored(data) && QuincyLogic.canUsePowers(data) && data.quincy().poison() < QuincyLogic.POISON_BLOCK;
    }

    /**
     * Whether a held Ginrei Kojaku streams arrows: restored and usable power, not gathering Licht
     * Regen, and no Seele Schneider waiting in the other hand to be fired instead.
     */
    public static boolean streamAllowed(Player player, SoulData data) {
        return isRestored(data) && QuincyLogic.canUsePowers(data) && !data.lichtGathering() && !holdsSeeleOpposite(player);
    }

    /** Whether the hand that is not drawing holds a Seele Schneider. */
    public static boolean holdsSeeleOpposite(Player player) {
        InteractionHand other = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return SeeleSchneiderItem.isSeele(player.getItemInHand(other));
    }

    // ---- Showing it ----

    /** The look bits other players' clients get (see the {@code LOOK_*} constants). None before the power awakens. */
    public static int quincyLook(SoulData data) {
        if (!QuincyLogic.isAwakened(data)) {
            return 0;
        }
        QuincyArts arts = arts(data);
        int look = 0;
        if (arts.glove().worn()) {
            look |= arts.glove().mastered() ? LOOK_GLOVE | LOOK_GLOVE_MASTERED : LOOK_GLOVE;
        }
        look |= switch (arts.stage()) {
            case UNTOUCHED -> 0;
            case LETZT_STIL -> LOOK_LETZT;
            case SEALED -> LOOK_SEALED;
            case RESTORED -> LOOK_GINREI;
        };
        if (arts.forms().vollstandig()) {
            look |= LOOK_VOLL;
        }
        if (sklavereiOn(data)) {
            look |= LOOK_SKLAVEREI;
        }
        if (arts.forms().haloBroken()) {
            look |= LOOK_HALO_BROKEN;
        }
        return look;
    }

    /** A span of ticks as minutes and seconds ({@code m:ss}), rounded up so a wait reads 0:00 only once it is over. */
    public static String formatTicks(long ticks) {
        long clamped = Math.max(0L, ticks);
        long seconds = clamped / 20L + (clamped % 20L == 0L ? 0L : 1L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    /**
     * The {@code screen.bleach.soul.next.*} key past the third tier, for an awakened soul that Hollow
     * poison is not getting to (see {@link QuincyLogic#nextStepKey}): wear the glove, train in it,
     * tear it, spend the burning power, wait out the seal or win the power back, then train on to
     * Vollständig.
     */
    public static String nextStepKey(SoulData data, long now) {
        QuincyArts arts = arts(data);
        return switch (arts.stage()) {
            case UNTOUCHED -> arts.glove().worn()
                    ? (arts.glove().mastered() ? "quincy.glove_mastered" : "quincy.glove")
                    : "quincy." + QuincyLogic.tier(data);
            case LETZT_STIL -> "quincy.letzt_stil";
            case SEALED -> !sealedWaitOver(data, now) ? "quincy.sealed"
                    : waitsForArrow() ? "quincy.sealed_ready_arrow" : "quincy.sealed_ready";
            case RESTORED -> vollUnlocked(data) ? "quincy.vollstandig" : "quincy.restored";
        };
    }

    /**
     * Gives a soul just reset the landing still to cushion from the arts it had (see
     * {@link QuincyArtsEvents#endAll}), so a reset in the air does not hurt. Nothing else carries over.
     */
    public static void keepLandingGrace(QuincyArts ended, SoulData fresh) {
        if (ended.forms().landingGrace()) {
            setArts(fresh, arts(fresh).withForms(arts(fresh).forms().withLandingGrace(true)));
        }
    }

    // ---- Death and loading ----

    /**
     * What the arts become when the body dies. A Letzt Stil burns out at once: the power is sealed
     * from the moment it would have been. Every form ends (Vollständig, Sklaverei, the halo, the
     * landing grace and Ransōtengai's threads, which let go quietly); the cooldowns, the glove and
     * every other stage stay as they were.
     */
    public static QuincyArts afterDeath(QuincyArts arts) {
        QuincyArts after = arts.stage() == QuincyStage.LETZT_STIL
                ? arts.withStage(QuincyStage.SEALED, arts.letztEndsAt()) : arts;
        QuincyForms forms = after.forms();
        QuincyForms ended = forms.withVollstandig(false).withSklaverei(false).withHaloBroken(false).withLandingGrace(false)
                .withRansotengaiSince(ShinigamiProgress.NEVER);
        return ended.equals(forms) ? after : after.withForms(ended);
    }

    /**
     * Puts saved clocks back in reach of the game time, for a world whose time was changed or a save
     * copied between worlds: no stage starts in the future (except a seal from a death in Letzt Stil,
     * which starts when the Letzt Stil would have ended, at most its full time ahead), a Letzt Stil
     * never has more than its full time left (one that does burns out now), no cooldown is longer
     * than its full length, and the training at the restoration is never more than the soul has.
     */
    public static QuincyArts normalise(QuincyArts arts, int training, long now) {
        QuincyArts out = arts;
        long letztDuration = BleachConfig.get(BleachConfig.LETZT_DURATION_TICKS);
        long latestSince = out.stage() == QuincyStage.SEALED ? now + letztDuration : now;
        if (out.stageSince() > latestSince) {
            out = out.withStage(out.stage(), now);
        }
        if (out.stage() == QuincyStage.LETZT_STIL && out.letztEndsAt() > now && out.letztEndsAt() - now > letztDuration) {
            out = out.withLetztEndsAt(now);
        }
        if (out.trainingAtRestore() > training) {
            out = out.withTrainingAtRestore(Math.max(0, training));
        }
        QuincyForms forms = out.forms();
        long vollLatest = now + BleachConfig.get(BleachConfig.VOLL_COOLDOWN_TICKS);
        if (forms.vollReadyAt() > vollLatest) {
            forms = forms.withVollReadyAt(vollLatest);
        }
        long lichtLatest = now + BleachConfig.get(BleachConfig.LICHT_REGEN_COOLDOWN_TICKS);
        if (forms.lichtRegenReadyAt() > lichtLatest) {
            forms = forms.withLichtRegenReadyAt(lichtLatest);
        }
        long threadsLatest = now + QuincyRansotengai.cooldownTicks();
        if (forms.ransotengaiReadyAt() > threadsLatest) {
            forms = forms.withRansotengaiReadyAt(threadsLatest);
        }
        return forms.equals(out.forms()) ? out : out.withForms(forms);
    }

    /** Ticks from {@code then} to {@code now}: none for a time still to come, and never wrapping round. */
    private static long elapsed(long now, long then) {
        if (now <= then) {
            return 0L;
        }
        long difference = now - then;
        return difference < 0L ? Long.MAX_VALUE : difference;
    }
}
