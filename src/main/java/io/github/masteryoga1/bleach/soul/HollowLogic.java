package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.registry.BleachTags;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The rules for Hollows and Pluses: who Hollows hunt, when a Garganta opens and what comes through
 * it, the first Hollow, purification and what a kill is worth, and a Plus's chain. Like
 * {@link SoulLogic}, these read only their arguments and the config, so game tests can call them
 * directly. {@code now} is the level's game time.
 */
public final class HollowLogic {
    /** The reiatsu the arrival chance is set for: a new soul's. */
    public static final double REFERENCE_REIATSU = 40.0;
    /** How far around a soul Hollows count towards the cap on arrivals. */
    public static final double NEARBY_HOLLOW_RANGE = 64.0;
    /** How far around a soul a Plus makes the Hexapodus likelier, and counts towards the cap on wandering Pluses. */
    public static final double PLUS_NEARBY_RANGE = 48.0;
    /** How far an aware soul keeps a Hollow from leaving, and hears what happens to a Plus. */
    public static final double AWARE_NEAR_RANGE = 32.0;
    /** Ticks between arrival checks for each soul: half a minute. */
    public static final int ARRIVAL_CHECK_TICKS = 600;
    /** Ticks between checks for a wandering Plus, and where in that cycle each soul is checked. */
    public static final int PLUS_CHECK_TICKS = 1200;
    public static final int PLUS_CHECK_OFFSET = 300;
    /** How long the first Hollow's rift stays shut after a wounded Shinigami stumbles in: half a minute. */
    public static final int FIRST_HOLLOW_WOUNDED_DELAY = 600;
    /** How long the first Hollow's rift stays shut after the soul hears it coming. */
    public static final int FIRST_HOLLOW_DELAY = 60;
    /** Blows to a Hollow's body in a row before the soul is told to aim for the mask. */
    public static final int MASK_HINT_HITS = 5;

    private static final int TICKS_PER_MINUTE = 1200;
    /** The Hexapodus comes this many times as often where there are Pluses to hunt. */
    private static final int HEXAPODUS_PLUS_WEIGHT_FACTOR = 3;
    private static final float LOW_HEALTH_FRACTION = 0.3F;

    private HollowLogic() {
    }

    // ---- The hunt ----

    /**
     * Whether Hollows hunt this creature: a player who can see them (not in creative or spectator
     * mode, and not a Hollow or Arrancar unless the server says so), or a Plus spirit that is not
     * passing on. The unaware are hunted only if the server says so; nothing else is ever prey.
     */
    public static boolean isPrey(LivingEntity target) {
        if (target instanceof Player player) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || !SoulLogic.hasSoul(player)) {
                return false;
            }
            SoulData data = SoulLogic.get(player);
            if (data.state() == SoulState.HOLLOW || data.state() == SoulState.ARRANCAR) {
                return BleachConfig.get(BleachConfig.HUNT_HOLLOW_PLAYERS);
            }
            return SoulLogic.isAware(data) || BleachConfig.get(BleachConfig.HUNT_UNAWARE);
        }
        if (target instanceof PlusSpirit plus) {
            return plus.isAlive() && !plus.isDeparting();
        }
        return false;
    }

    /**
     * How much a Hollow wants this prey: stronger souls smell better and nearer ones are easier. A
     * Plus is a favourite (the Hexapodus's above all), and so is the soul that drew the Hollow here,
     * Fishbone D's quarry most of all.
     */
    public static double preyScore(Hollow hunter, LivingEntity prey, double distance) {
        double weight;
        if (prey instanceof PlusSpirit) {
            weight = hunter.kind() == HollowKind.HEXAPODUS ? 10.0 : 3.0;
        } else if (hunter instanceof FishboneD fishbone && prey.getUUID().equals(fishbone.quarry())) {
            weight = 5.0;
        } else if (prey.getUUID().equals(hunter.drawer())) {
            weight = 2.0;
        } else {
            weight = 1.0;
        }
        return weight * Math.sqrt(Math.max(1.0, SoulLogic.reiatsu(prey))) / (1.0 + distance / 8.0);
    }

    // ---- Garganta arrivals ----

    /** Whether this soul is still waiting for its first Hollow, which comes before any other. */
    public static boolean awaitsFirstHollow(SoulData data) {
        return BleachConfig.get(BleachConfig.FIRST_HOLLOW) && !data.hunt().firstHollowDone()
                && (data.state() == SoulState.HUMAN || data.state() == SoulState.PLUS) && SoulLogic.isAware(data);
    }

    /**
     * The chance a minute that a Garganta opens near this soul. It grows with the square root of
     * reiatsu up to a cap, then is likelier at night, for a Plus, and while reiatsu is released.
     * None for the unaware, for Hollows and Arrancar, or before the first Hollow.
     */
    public static double arrivalChancePerMinute(SoulData data, boolean night) {
        if (!SoulLogic.isAware(data) || data.state() == SoulState.HOLLOW || data.state() == SoulState.ARRANCAR
                || awaitsFirstHollow(data)) {
            return 0.0;
        }
        double reiatsu = Math.max(data.maxReiryoku(), 1.0F);
        double chance = Math.min(BleachConfig.get(BleachConfig.MAX_ARRIVAL_CHANCE_PER_MINUTE),
                BleachConfig.get(BleachConfig.ARRIVAL_CHANCE_PER_MINUTE) * Math.sqrt(reiatsu / REFERENCE_REIATSU));
        if (data.isReiatsuReleased()) {
            chance *= BleachConfig.get(BleachConfig.ARRIVAL_RELEASED_MULTIPLIER);
        }
        if (night) {
            chance *= BleachConfig.get(BleachConfig.ARRIVAL_NIGHT_MULTIPLIER);
        }
        if (data.state() == SoulState.PLUS) {
            chance *= BleachConfig.get(BleachConfig.ARRIVAL_PLUS_MULTIPLIER);
        }
        return Math.min(1.0, chance);
    }

    /** The chance for one arrival check, every {@link #ARRIVAL_CHECK_TICKS} ticks. */
    public static double arrivalChancePerCheck(SoulData data, boolean night) {
        return Math.min(1.0, arrivalChancePerMinute(data, night) * ((double) ARRIVAL_CHECK_TICKS / TICKS_PER_MINUTE));
    }

    /** Whether long enough has passed since the last Garganta opened for this soul. */
    public static boolean arrivalCooledDown(HuntRecord hunt, long now) {
        return now - hunt.lastArrivalAt() >= BleachConfig.get(BleachConfig.ARRIVAL_COOLDOWN_SECONDS) * 20L;
    }

    /** Whether Garganta open in this world at all: turned on, in the Overworld, not Peaceful, with mob spawning on. */
    public static boolean worldAllowsArrivals(Difficulty difficulty, boolean mobSpawning, boolean overworld) {
        return BleachConfig.get(BleachConfig.GARGANTA_ARRIVALS) && overworld && mobSpawning
                && difficulty != Difficulty.PEACEFUL;
    }

    /** The kinds a soul with this much reiatsu can draw, in table order. Fishbone D comes only for a first Hollow. */
    public static List<HollowKind> arrivalPool(float reiatsu) {
        List<HollowKind> pool = new ArrayList<>();
        for (HollowKind kind : HollowKind.values()) {
            if (kind.arrivalWeight > 0 && kind.minReiatsu <= reiatsu) {
                pool.add(kind);
            }
        }
        return pool;
    }

    /** Picks the kind of an arrival by weight. The Hexapodus comes three times as often where there are Pluses. */
    public static HollowKind pickArrival(float reiatsu, boolean plusesNearby, RandomSource random) {
        List<HollowKind> pool = arrivalPool(reiatsu);
        int total = 0;
        for (HollowKind kind : pool) {
            total += arrivalWeight(kind, plusesNearby);
        }
        if (total <= 0) {
            return HollowKind.SPIDER;
        }
        int roll = random.nextInt(total);
        for (HollowKind kind : pool) {
            roll -= arrivalWeight(kind, plusesNearby);
            if (roll < 0) {
                return kind;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static int arrivalWeight(HollowKind kind, boolean plusesNearby) {
        return kind == HollowKind.HEXAPODUS && plusesNearby
                ? kind.arrivalWeight * HEXAPODUS_PLUS_WEIGHT_FACTOR : kind.arrivalWeight;
    }

    /** How many Hound Hollows come as a pack: two, and one more each at 200 and 400 reiatsu. */
    public static int packSize(float reiatsu) {
        return 2 + (reiatsu >= 200.0F ? 1 : 0) + (reiatsu >= 400.0F ? 1 : 0);
    }

    /** The chance a second Hollow of the same kind comes along: none up to 150 reiatsu, at most a half. */
    public static double secondHollowChance(float reiatsu) {
        return Mth.clamp((reiatsu - 150.0) / 600.0, 0.0, 0.5);
    }

    /**
     * How many Hollows of this kind come through one rift: a Hound pack, or one Hollow with a chance
     * of a second (never for Fishbone D), and never more than {@code room}, though always at least one.
     *
     * @param roll a random number from 0 to 1, against {@link #secondHollowChance}
     */
    public static int groupSize(HollowKind kind, float reiatsu, int room, double roll) {
        int wanted;
        if (kind == HollowKind.HOUND) {
            wanted = packSize(reiatsu);
        } else if (kind == HollowKind.FISHBONE_D) {
            wanted = 1;
        } else {
            wanted = roll < secondHollowChance(reiatsu) ? 2 : 1;
        }
        return Math.max(1, Math.min(wanted, room));
    }

    // ---- Difficulty and size ----

    /** Health on Easy is five sevenths of Normal's (70 becomes 50); the same elsewhere. */
    public static double difficultyHealthFactor(Difficulty difficulty) {
        return difficulty == Difficulty.EASY ? 5.0 / 7.0 : 1.0;
    }

    /** Armour is doubled on Hard. */
    public static double difficultyArmorFactor(Difficulty difficulty) {
        return difficulty == Difficulty.HARD ? 2.0 : 1.0;
    }

    /** How long a Hollow's bite effect lasts, in seconds: none on Easy and Peaceful, or for kinds without one. */
    public static int onHitSeconds(HollowKind kind, Difficulty difficulty) {
        if (kind.onHit == null) {
            return 0;
        }
        return switch (difficulty) {
            case NORMAL -> kind.onHit.normalSeconds();
            case HARD -> kind.onHit.hardSeconds();
            default -> 0;
        };
    }

    /** A Hollow's reiatsu grows with its size. */
    public static float reiatsuFor(HollowKind kind, float scale) {
        return kind.reiatsu * scale;
    }

    /**
     * Whether a Hollow heals now: nothing has hurt it for {@link Hollow#REGEN_DELAY_TICKS}, and a blow
     * to its mask no longer stops it.
     */
    public static boolean mayRegenerate(long now, long lastHurtAt, long pausedUntil) {
        return now - lastHurtAt >= Hollow.REGEN_DELAY_TICKS && now >= pausedUntil;
    }

    // ---- The first Hollow ----

    /** Stamps the play time a soul is first seen to be aware at, once. */
    public static HuntRecord noteAwareness(HuntRecord hunt, SoulData data, int playTicks) {
        if (hunt.awareSincePlayTicks() < 0 && SoulLogic.isAware(data)) {
            return hunt.withAwareSincePlayTicks(playTicks);
        }
        return hunt;
    }

    /**
     * Whether the first Hollow comes for this soul now: it is still awaited, the soul has played long
     * enough since becoming aware, it is night, and long enough has passed since the last try.
     */
    public static boolean firstHollowDue(SoulData data, int playTicks, long now, boolean night) {
        HuntRecord hunt = data.hunt();
        return awaitsFirstHollow(data) && hunt.awareSincePlayTicks() >= 0
                && (long) playTicks - hunt.awareSincePlayTicks()
                        >= (long) BleachConfig.get(BleachConfig.FIRST_HOLLOW_DELAY_MINUTES) * TICKS_PER_MINUTE
                && night && firstHollowMayReturn(hunt, now);
    }

    /**
     * Whether long enough has passed since the first Hollow was last sent for this soul, or last
     * retreated, for it to come again. Until then it is still on its way, or licking its wounds.
     */
    public static boolean firstHollowMayReturn(HuntRecord hunt, long now) {
        return now - hunt.lastArrivalAt()
                >= (long) BleachConfig.get(BleachConfig.FIRST_HOLLOW_RETRY_MINUTES) * TICKS_PER_MINUTE;
    }

    // ---- Purification and kills ----

    /** Whose blade sends a Hollow's soul on: a Shinigami's power, not lent away, or an Academy student's. */
    public static boolean canPurify(SoulData killer, long now) {
        return ShinigamiLogic.canPerformKonso(killer, now) || ShinigamiLogic.isStudent(killer);
    }

    /**
     * Whether this killing blow purifies the Hollow instead of destroying it: a technique that
     * purifies, or a zanpakutō in the killer's own hand (a sweep counts, a thrown blade does not), from
     * a soul that can purify.
     */
    public static boolean purifies(DamageSource source, @Nullable SoulData killer, long now) {
        if (killer == null || !canPurify(killer, now)) {
            return false;
        }
        if (source.is(BleachTags.PURIFIES)) {
            return true;
        }
        ItemStack weapon = source.getWeaponItem();
        return weapon != null && weapon.is(BleachTags.ZANPAKUTO)
                && source.getEntity() != null && source.getDirectEntity() == source.getEntity();
    }

    /**
     * Maximum reiryoku a kill grows by: a share of the victim's reiatsu, down to a tenth against one
     * far weaker than the killer, and half as much again for a purification.
     */
    public static double killGrowth(float victimReiatsu, float killerReiatsu, boolean purified) {
        double gap = killerReiatsu <= 0.0F ? 1.0 : Mth.clamp(2.0 * victimReiatsu / killerReiatsu, 0.1, 1.0);
        double growth = victimReiatsu * BleachConfig.get(BleachConfig.SPIRIT_KILL_GROWTH_PER_REIATSU) * gap;
        return purified ? growth * BleachConfig.get(BleachConfig.PURIFICATION_GROWTH_MULTIPLIER) : growth;
    }

    /** Zanjutsu or Hakuda XP for a landed hit: more on a Hollow, more still on its mask. */
    public static int hitXp(boolean onHollow, boolean onMask) {
        if (!onHollow) {
            return 1;
        }
        int xp = BleachConfig.get(BleachConfig.HOLLOW_HIT_STAT_XP);
        return onMask ? xp + BleachConfig.get(BleachConfig.MASK_HIT_STAT_XP_BONUS) : xp;
    }

    // ---- Pluses ----

    /** How long a Plus spirit's chain lasts, in ticks. */
    public static int plusChainTicks() {
        return BleachConfig.get(BleachConfig.PLUS_CHAIN_MINUTES) * TICKS_PER_MINUTE;
    }

    /**
     * A Hollow's bite takes part of a Plus's chain, but never the last minute of it; a chain already
     * that short is left alone.
     *
     * @return whether the chain changed
     */
    public static boolean biteChain(SoulData plus) {
        int floor = PlusSpirit.BITE_FLOOR_TICKS;
        int ticks = plus.chainTicks();
        if (plus.state() != SoulState.PLUS || plus.chainMaxTicks() <= 0 || ticks <= floor) {
            return false;
        }
        int bitten = Math.max(floor, ticks - BleachConfig.get(BleachConfig.HOLLOW_BITE_CHAIN_COST_SECONDS) * 20);
        if (bitten == ticks) {
            return false;
        }
        plus.setChainTicks(bitten);
        return true;
    }

    /** How fast a Plus spirit's chain erodes: faster when badly hurt, slower near a Shinigami. */
    public static double plusErosionSpeed(float healthFraction, boolean nearShinigami) {
        double speed = 1.0;
        if (healthFraction < LOW_HEALTH_FRACTION) {
            speed *= BleachConfig.get(BleachConfig.LOW_HEALTH_EROSION_MULTIPLIER);
        }
        if (nearShinigami) {
            speed *= BleachConfig.get(BleachConfig.NEAR_SHINIGAMI_EROSION_MULTIPLIER);
        }
        return speed;
    }

    /** Whether a Plus whose chain runs out can become a Hollow here: not on Peaceful, where its last link holds. */
    public static boolean mayHollowfy(Difficulty difficulty) {
        return difficulty != Difficulty.PEACEFUL;
    }

    /** The kind of Hollow a Plus becomes: Spider 30, Serpent 25, Hound 20, Hexapodus 15. Never Fishbone D. */
    public static HollowKind pickPlusHollow(RandomSource random) {
        int roll = random.nextInt(90);
        if (roll < 30) {
            return HollowKind.SPIDER;
        }
        if (roll < 55) {
            return HollowKind.SERPENT;
        }
        return roll < 75 ? HollowKind.HOUND : HollowKind.HEXAPODUS;
    }

    /** The chance for one wandering-Plus check (once a minute), three times as high in a village by default. */
    public static double plusChancePerCheck(boolean inVillage) {
        double chance = BleachConfig.get(BleachConfig.PLUS_CHANCE_PER_MINUTE);
        if (inVillage) {
            chance *= BleachConfig.get(BleachConfig.PLUS_VILLAGE_MULTIPLIER);
        }
        return Math.min(1.0, chance);
    }
}
