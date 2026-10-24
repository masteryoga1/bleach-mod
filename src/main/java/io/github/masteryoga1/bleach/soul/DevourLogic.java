package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of devouring: what a Hollow player may seize in its jaws and when, how long the hold takes,
 * what a meal is worth, and the blow that swallows the soul. Also a Plus gnawing at its own Chain of
 * Fate, the quick way to become a Hollow. Nothing here changes the world or sends anything, so game
 * tests can ask it directly; the hold itself is in {@link DevourEvents}.
 *
 * <p>In canon a Hollow feeds on souls to fill the hole where its heart was: Pluses most of all, which
 * cannot fight back, but other Hollows and living souls with reiatsu too.
 */
public final class DevourLogic {
    /** The damage of being devoured: it passes armour, shields and the invulnerability frames, but not a totem. */
    public static final ResourceKey<DamageType> DEVOURED =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("devoured"));
    /** The devourer's slowdown while it holds its prey: half speed. */
    public static final ResourceLocation DEVOURING_ID = BleachMod.id("devouring");
    public static final double DEVOURING_SLOWDOWN = -0.5;
    /** Without a creature under the crosshair, the nearest prey within 45 degrees of the look counts. */
    public static final double CONE_COS = 0.7071;
    /** How much further than the player's reach that prey may stand. */
    public static final double CONE_EXTRA_REACH = 0.5;
    /** The slack on the reach, as vanilla gives an attack or a use: {@code canInteractWithEntity(target, 1.0)}. */
    public static final double REACH_GRACE = 1.0;
    /** How long a seize lasts unless the hold renews it, so it lapses soon after a hold ends. */
    public static final int SEIZE_TICKS = 10;
    public static final int PARTICLE_INTERVAL_TICKS = 6;
    /** How often gnawing at the chain hurts. */
    public static final int GNAW_PAIN_INTERVAL_TICKS = 40;

    private static final long NEVER = HollowProgress.NEVER;

    private DevourLogic() {
    }

    /** What kind of soul a meal is. */
    public enum Prey {
        PLUS,
        HOLLOW,
        PLAYER
    }

    /** Why a hold could not start, in the order they are checked. */
    public enum Refusal {
        /** The devourer is no Hollow. Never told: the client sends the key only for a Hollow or a Plus. */
        NOT_HOLLOW,
        /** A hold ended a moment ago. Never told. */
        COOLDOWN,
        /** Nothing alive within reach, or the devourer itself. */
        NO_TARGET,
        /** Something without a soul worth devouring: villagers, animals, mundane monsters, golems, NPCs. */
        NO_SOUL,
        OUT_OF_REACH,
        /** A soul leaving for good, or a player who cannot be devoured now. */
        PROTECTED,
        /** Prey with far more reiatsu than the devourer. */
        TOO_STRONG,
        /** Prey not yet worn down far enough. */
        NOT_WEAK;

        /** The last part of the message key, {@code message.bleach.devour.<key>}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Whether a Plus may gnaw at its own chain: {@link #GNAWING}, or why not. */
    public enum Gnaw {
        GNAWING,
        /** Not a Plus, or a Plus with no chain. Never told. */
        NOT_PLUS,
        /** An awakened Quincy, whose chain breaking would tear the soul apart. */
        QUINCY,
        /** Hollowfication is off on this server, gnawing is turned off, or the player is in creative or spectator mode. */
        OFF;

        /** The last part of the message key, {@code message.bleach.gnaw.<key>}; only QUINCY and OFF have one. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What a meal is worth, measured before the kill.
     *
     * @param kind        the Hollow's kind; null unless the prey is a Hollow mob
     * @param reiatsu     the prey's reiatsu
     * @param satiety     the soul hunger it restores
     * @param souls       the souls it adds, before the ten-minute gate on players
     * @param stolenSouls the souls a devoured Hollow player loses by dying, which pass to its devourer
     * @param hollowLike  whether the prey was a Hollow, mob or player
     */
    public record Meal(Prey prey, @Nullable HollowKind kind, float reiatsu, float satiety, int souls, int stolenSouls,
                       boolean hollowLike) {
    }

    /** What kind of soul this creature is to a Hollow: a Plus spirit, a Hollow mob, a player with a soul, or none. */
    @Nullable
    public static Prey preyOf(@Nullable Entity entity) {
        if (entity instanceof PlusSpirit) {
            return Prey.PLUS;
        }
        if (entity instanceof Hollow) {
            return Prey.HOLLOW;
        }
        if (entity instanceof Player player && SoulLogic.hasSoul(player)) {
            return Prey.PLAYER;
        }
        return null;
    }

    /**
     * Why this Hollow player may not start devouring the target now, or null when it may. The reasons
     * are checked in the order of {@link Refusal}, so the first that applies is the one told.
     */
    @Nullable
    public static Refusal refusal(Player devourer, SoulData data, @Nullable Entity target, long now) {
        if (!HollowPlayerLogic.isHollow(data)) {
            return Refusal.NOT_HOLLOW;
        }
        if (now < data.devourReadyAt) {
            return Refusal.COOLDOWN;
        }
        if (!(target instanceof LivingEntity prey) || !prey.isAlive() || prey.isRemoved() || prey == devourer) {
            return Refusal.NO_TARGET;
        }
        if (preyOf(prey) == null) {
            return Refusal.NO_SOUL;
        }
        if (!devourer.canInteractWithEntity(prey, REACH_GRACE) || !devourer.hasLineOfSight(prey)) {
            return Refusal.OUT_OF_REACH;
        }
        if (protectedPrey(devourer, prey)) {
            return Refusal.PROTECTED;
        }
        double most = SoulLogic.reiatsu(devourer) * QuincyLogic.reiatsuFactor(devourer)
                * BleachConfig.get(BleachConfig.DEVOUR_REIATSU_RATIO);
        if (SoulLogic.reiatsu(prey) > most) {
            return Refusal.TOO_STRONG;
        }
        return weakEnough(prey) ? null : Refusal.NOT_WEAK;
    }

    /**
     * Whether this prey is beyond the devourer's jaws for now: a Plus or a Hollow leaving for good (or a
     * Hollow dissolving), or a player in creative or spectator mode, unaware, kept safe by the server's
     * PvP setting or a shared team, or on a server where players cannot be devoured.
     */
    public static boolean protectedPrey(Player devourer, LivingEntity prey) {
        if (prey instanceof PlusSpirit plus) {
            return plus.isDeparting();
        }
        if (prey instanceof Hollow hollow) {
            return hollow.isPurifying() || hollow.isDeparting();
        }
        if (prey instanceof Player player) {
            // canHarmPlayer is virtual: on the server it also asks whether PvP is on.
            return player.isCreative() || player.isSpectator() || !SoulLogic.hasSoul(player)
                    || !SoulLogic.isAware(SoulLogic.get(player)) || !BleachConfig.get(BleachConfig.DEVOUR_PLAYERS)
                    || !devourer.canHarmPlayer(player);
        }
        return false;
    }

    /**
     * Whether this prey is worn down enough to be devoured: a Plus always, as it cannot fight back; a
     * Hollow at or below its share of its health (a larger share once its mask is cracked); a player at
     * or below the same share as a whole-masked Hollow.
     */
    public static boolean weakEnough(LivingEntity prey) {
        if (prey instanceof PlusSpirit) {
            return true;
        }
        double share = prey instanceof Hollow hollow && hollow.isMaskCracked()
                ? BleachConfig.get(BleachConfig.DEVOUR_CRACKED_HEALTH_FRACTION)
                : BleachConfig.get(BleachConfig.DEVOUR_HEALTH_FRACTION);
        return prey.getHealth() <= prey.getMaxHealth() * share;
    }

    /** How many ticks the key must be held on this prey: a Plus is swallowed quicker than anything else. */
    public static int holdTicks(LivingEntity prey) {
        return prey instanceof PlusSpirit
                ? BleachConfig.get(BleachConfig.DEVOUR_PLUS_HOLD_TICKS)
                : BleachConfig.get(BleachConfig.DEVOUR_HOLD_TICKS);
    }

    /** The soul hunger a meal restores: a fixed amount for a Plus, otherwise more for more reiatsu, up to a cap. */
    public static float satietyFor(Prey prey, float reiatsu) {
        if (prey == Prey.PLUS) {
            return (float) BleachConfig.get(BleachConfig.PLUS_SATIETY);
        }
        double satiety = BleachConfig.get(BleachConfig.MEAL_SATIETY_BASE)
                + BleachConfig.get(BleachConfig.MEAL_SATIETY_PER_REIATSU) * reiatsu;
        return (float) Math.min(BleachConfig.get(BleachConfig.MEAL_SATIETY_MAX), satiety);
    }

    /** The souls a meal adds: one for every so much of the prey's reiatsu, rounded half up, and at least one. */
    public static int soulsFor(float reiatsu) {
        return (int) Math.max(1L, Math.round(reiatsu / BleachConfig.get(BleachConfig.REIATSU_PER_SOUL)));
    }

    /**
     * What devouring this prey would be worth, measured now, before the killing blow changes it. A
     * Hollow player's lost souls are the share it would lose by dying.
     *
     * @throws IllegalArgumentException for a creature with no soul worth devouring (see {@link #preyOf})
     */
    public static Meal meal(Player devourer, LivingEntity prey) {
        Prey kind = preyOf(prey);
        if (kind == null) {
            throw new IllegalArgumentException("Nothing to devour in " + prey);
        }
        float reiatsu = SoulLogic.reiatsu(prey);
        boolean hollowPlayer = prey instanceof Player player && HollowPlayerLogic.isHollowPlayer(player);
        int stolen = hollowPlayer ? HollowPlayerLogic.soulsLostOnDeath(SoulLogic.get(prey).hollow().souls()) : 0;
        HollowKind hollowKind = prey instanceof Hollow hollow ? hollow.kind() : null;
        return new Meal(kind, hollowKind, reiatsu, satietyFor(kind, reiatsu), soulsFor(reiatsu), stolen,
                prey instanceof Hollow || hollowPlayer);
    }

    /**
     * The blow that swallows the prey: ten times what keeps it alive and then some, so it lands whole
     * through any reiatsu difference or resistance that leaves a tenth of a blow. A finite amount, so
     * a totem can still save a player.
     */
    public static float killingBlow(LivingEntity prey) {
        return (prey.getHealth() + prey.getAbsorptionAmount()) * 10.0F + 20.0F;
    }

    /** Being devoured by this creature, which is both the direct and the causing entity. */
    public static DamageSource source(Level level, Entity devourer) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEVOURED),
                devourer, devourer);
    }

    /** Whether this damage is a devourer's jaws closing. */
    public static boolean isDevoured(DamageSource source) {
        return source.is(DEVOURED);
    }

    /**
     * The nearest creature the Hollow player could start devouring, when nothing is under its crosshair:
     * within its reach and a half, with its middle within 45 degrees of where it looks. Null if none.
     */
    @Nullable
    public static LivingEntity nearestPrey(Player devourer, SoulData data, long now) {
        double range = devourer.entityInteractionRange() + CONE_EXTRA_REACH;
        Vec3 eye = devourer.getEyePosition();
        Vec3 look = devourer.getViewVector(1.0F);
        LivingEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (LivingEntity candidate : devourer.level().getEntitiesOfClass(LivingEntity.class,
                devourer.getBoundingBox().inflate(range), other -> other != devourer)) {
            Vec3 toward = candidate.getBoundingBox().getCenter().subtract(eye);
            double distanceSqr = toward.lengthSqr();
            // One whose middle is at the eye is straight ahead; normalising so short a vector gives nothing.
            boolean ahead = distanceSqr < 1.0E-6 || look.dot(toward.normalize()) >= CONE_COS;
            if (ahead && distanceSqr <= range * range && distanceSqr < nearestSqr
                    && refusal(devourer, data, candidate, now) == null) {
                nearest = candidate;
                nearestSqr = distanceSqr;
            }
        }
        return nearest;
    }

    /**
     * Whether this player may gnaw at its own chain now: only a Plus that still has one, never an
     * awakened Quincy (whose soul would be torn apart), and only where Hollowfication and gnawing are on
     * and the player is in survival or adventure mode.
     */
    public static Gnaw gnawRefusal(Player player, SoulData data) {
        if (data.state() != SoulState.PLUS || data.chainMaxTicks() <= 0) {
            return Gnaw.NOT_PLUS;
        }
        if (QuincyLogic.isAwakened(data)) {
            return Gnaw.QUINCY;
        }
        if (!BleachConfig.get(BleachConfig.HOLLOWFICATION) || BleachConfig.get(BleachConfig.GNAW_MULTIPLIER) <= 1.0
                || player.isCreative() || player.isSpectator()) {
            return Gnaw.OFF;
        }
        return Gnaw.GNAWING;
    }

    /** How many times faster the chain wears away: the gnaw multiplier while the Plus gnaws at it, else 1. */
    public static double gnawSpeed(SoulData data) {
        return gnawing(data) ? BleachConfig.get(BleachConfig.GNAW_MULTIPLIER) : 1.0;
    }

    /** Whether this Plus is gnawing at its chain now. */
    public static boolean gnawing(SoulData data) {
        return data.gnawStartedAt != NEVER;
    }
}
