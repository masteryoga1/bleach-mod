package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.registry.BleachTags;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of the Quincy path: the bloodline, training and tiers, the reishi pool and gathering it,
 * the Heilig Bogen and its arrows, Hirenkyaku and reishi footing, Blut Vene and Blut Arterie, and
 * Hollow poison. Like {@link ShinigamiLogic}, these only read and change a {@link SoulData} and the
 * config, never send anything and work on either side, so game tests and the client can call them.
 * The tier is the soul's {@link PowerPath#QUINCY} tier; everything else is its {@link QuincyProgress}.
 */
public final class QuincyLogic {
    /** Which dash the movement key does. */
    public enum Movement { SHUNPO, HIRENKYAKU }

    public static final int MAX_TIER = 3;
    /** A release drawn for less than this does nothing at all. */
    public static final int MIN_DRAW_TICKS = 4;
    /** The bow is fully drawn after this long; holding it longer costs reishi. */
    public static final int FULL_DRAW_TICKS = 20;
    /** A latent soul's draw must be held this long to count as training. */
    public static final int AWAKENING_DRAW_TICKS = 20;
    public static final int AWAKENING_TRAINING = 2;
    /** A latent soul trains this many times faster with a live Hollow near. */
    public static final int AWAKENING_HOLLOW_MULTIPLIER = 3;
    public static final double AWAKENING_HOLLOW_RANGE = 24.0;
    public static final int AWAKENING_COOLDOWN_TICKS = 30;
    public static final int MEDITATION_TRAINING = 1;
    public static final int HIT_TRAINING = 2;
    public static final int MASK_HIT_TRAINING = 4;
    public static final int HIT_TRAINING_COOLDOWN_TICKS = 10;
    public static final int DESTROY_TRAINING = 10;
    public static final int FISHBONE_D_TRAINING = 30;
    public static final int HIRENKYAKU_TRAINING = 1;
    public static final int HIRENKYAKU_TRAINING_COOLDOWN_TICKS = 200;
    public static final int ARROW_XP_COOLDOWN_TICKS = 20;
    /** A shot weaker than this is not worth firing. */
    public static final float MIN_SHOT_POWER = 0.1F;
    /** Only a draw at least this strong pierces. */
    public static final float PIERCE_POWER = 0.9F;
    /** Reiryoku a shot never spends below, when it falls back on reiryoku. */
    public static final float SHOT_RESERVE = 0.5F;
    /** The share of full damage a tap does; a full draw does all of it. */
    public static final float TAP_DAMAGE_FRACTION = 0.25F;
    public static final double DAMAGE_PER_REIATSU_CONTROL_LEVEL = 0.1;
    public static final float BASE_SPEED = 1.4F, FULL_DRAW_EXTRA_SPEED = 1.8F;
    public static final float BASE_INACCURACY = 2.0F, FULL_DRAW_INACCURACY_DROP = 1.75F;
    public static final float HOLD_UPKEEP_PER_SECOND = 0.5F;
    public static final double REISHI_PER_MAX_REIRYOKU = 0.1;
    public static final double CHANNEL_PER_REIATSU_CONTROL_LEVEL = 0.02;
    public static final int GATHER_INTERVAL_TICKS = 20;
    public static final double NETHER_DENSITY = 0.6, END_DENSITY = 0.8;
    public static final double RICH_BIOME_MULTIPLIER = 1.3, THIN_BIOME_MULTIPLIER = 0.7;
    public static final double RIFT_DENSITY_BONUS = 0.5, RIFT_DENSITY_RANGE = 24.0;
    public static final double AURA_DENSITY_BONUS = 0.5, MAX_DENSITY = 3.0;
    /** Where the words for reishi density change: thin below the first, then ordinary, rich and dense. */
    public static final double THIN_BELOW = 0.85, ORDINARY_BELOW = 1.15, RICH_BELOW = 2.0;
    public static final double HIRENKYAKU_DISTANCE_PER_TIER = 1.5;
    public static final double HIRENKYAKU_DISTANCE_PER_REIATSU_CONTROL_LEVEL = 0.1;
    public static final double VENE_PER_TIER = 0.05, ARTERIE_PER_TIER = 0.05;
    /** Reishi Blut Vene spends for each point of damage it stops. */
    public static final float VENE_COST_PER_DAMAGE = 0.5F;
    public static final float VENE_ACTIVATION_COST = 2.0F, ARTERIE_ACTIVATION_COST = 3.0F;
    public static final int BLUT_SWITCH_TICKS = 10, BLUT_PRESS_DEBOUNCE_TICKS = 5;
    /** A soul that also holds a Shinigami's power gets this share of Blut's strength. */
    public static final float HYBRID_BLUT_MULTIPLIER = 0.5F, VENE_POISON_MULTIPLIER = 0.5F;
    /** A Hollow whose bite carries an effect poisons this much more. */
    public static final float POISON_ON_HIT_MULTIPLIER = 1.5F;
    public static final double POISON_RESISTANCE_PER_TIER = 0.1, POISON_MIN_RESISTANCE = 0.5;
    public static final double POISON_EASY = 0.5, POISON_HARD = 1.25;
    /** Hollow poison only starts to wear off this long after it last built up. */
    public static final int POISON_DECAY_DELAY_TICKS = 1200;
    public static final double CHANNEL_DECAY_PER_MINUTE = 12.0, CHANNEL_DECAY_MIN_DENSITY = 1.25;
    public static final float PURIFICATION_CURE = 10.0F;
    public static final double PURIFICATION_CURE_RANGE = 16.0;
    public static final int PANG_INTERVAL_TICKS = 80, NAUSEA_TICKS = 60;
    public static final float PANG_DAMAGE = 1.0F, PANG_MIN_HEALTH = 2.0F;
    /** Where Hollow poison starts to slow gathering, to block Quincy arts, and to bring pangs. */
    public static final float POISON_SLOW = 25.0F, POISON_BLOCK = 50.0F, POISON_PANG = 75.0F;
    /** The share of maximum reiryoku a soul torn apart keeps. */
    public static final double DESTROYED_REIRYOKU_FRACTION = 0.9;
    public static final double DESTROYED_NEARBY_RANGE = 64.0;
    public static final long SENSE_DISTANCE_STEP = 10L;

    /** How fast reiryoku comes back with poison past {@link #POISON_SLOW}. */
    private static final double POISON_SLOW_REGEN = 0.85;
    /** How fast reishi gathers with poison past {@link #POISON_SLOW}, and past {@link #POISON_BLOCK}. */
    private static final float POISON_SLOW_GATHER = 0.75F;
    private static final float POISON_BLOCK_GATHER = 0.5F;
    private static final double TICKS_PER_MINUTE = 1200.0;

    private QuincyLogic() {
    }

    // ---- Bloodline and state ----

    /**
     * The soul's bloodline as the rules see it. A soul with a Quincy tier but no bloodline (an old
     * save, or a tier given by command) is Gemischt.
     */
    public static Bloodline bloodline(SoulData data) {
        Bloodline stored = data.quincy().bloodline();
        return stored == Bloodline.NONE && data.tier(PowerPath.QUINCY) > 0 ? Bloodline.GEMISCHT : stored;
    }

    /** The Quincy tier, from 0 to {@link #MAX_TIER}: a higher tier set by command acts as the highest. */
    public static int tier(SoulData data) {
        return Mth.clamp(data.tier(PowerPath.QUINCY), 0, MAX_TIER);
    }

    /** Whether the soul's Quincy power has awakened. */
    public static boolean isAwakened(SoulData data) {
        return tier(data) >= 1;
    }

    /** Quincy blood that has not awakened yet. */
    public static boolean isLatent(SoulData data) {
        return bloodline(data) != Bloodline.NONE && tier(data) == 0;
    }

    /** Whether the server lets one soul hold both a Quincy's and a Shinigami's power. */
    public static boolean hybridsAllowed() {
        return BleachConfig.get(BleachConfig.QUINCY_ALLOW_HYBRIDS);
    }

    /** An awakened Quincy cannot take a Shinigami's power or enroll at the Academy, unless hybrids are allowed. */
    public static boolean blocksShinigamiPower(SoulData data) {
        return isAwakened(data) && !hybridsAllowed();
    }

    /** Whether training can awaken this soul: latent Quincy blood in a living Human, without a Shinigami's power in the way. */
    public static boolean canAwaken(SoulData data) {
        return isLatent(data) && data.state() == SoulState.HUMAN
                && (!ShinigamiLogic.hasShinigamiPower(data) || hybridsAllowed());
    }

    /**
     * Whether the soul can use its Quincy power now: awakened, alive or a Shinigami, and not sealed
     * after Letzt Stil. The seal switches off everything that asks this: the bow, Hirenkyaku,
     * footing, Blut, gathering and Hollow poison taking hold.
     */
    public static boolean canUsePowers(SoulData data) {
        return isAwakened(data) && (data.state() == SoulState.HUMAN || data.state() == SoulState.SHINIGAMI)
                && !QuincyArtsLogic.isSealed(data);
    }

    /**
     * Why this soul cannot draw on the Quincy Cross, as a message key, or null if it can, with no
     * time given: a sealed soul is always refused (see {@link #drawRefusal(SoulData, long)}).
     */
    @Nullable
    public static String drawRefusal(SoulData data) {
        return drawRefusal(data, Long.MIN_VALUE / 2);
    }

    /**
     * Why this soul cannot draw on the Quincy Cross now, as a message key, or null if it can. Only
     * reads what the client knows as well as the server (the bloodline, state, tiers and arts), so
     * both agree on whether a draw starts. A sealed soul may draw only once the solo rite is open to
     * it, and then the draw is the rite.
     */
    @Nullable
    public static String drawRefusal(SoulData data, long now) {
        if (bloodline(data) == Bloodline.NONE && tier(data) == 0) {
            return "message.bleach.quincy_cross.no_blood";
        }
        if (isAwakened(data)) {
            if (data.state() != SoulState.HUMAN && data.state() != SoulState.SHINIGAMI) {
                return "message.bleach.quincy_cross.spirit";
            }
            return QuincyArtsLogic.isSealed(data) && !QuincyArtsLogic.riteOpen(data, now)
                    ? "message.bleach.letzt_stil.sealed" : null;
        }
        boolean shinigamiInTheWay = ShinigamiLogic.hasShinigamiPower(data) && !hybridsAllowed();
        if (data.state() != SoulState.HUMAN) {
            return shinigamiInTheWay ? "message.bleach.quincy_cross.shinigami" : "message.bleach.quincy_cross.spirit";
        }
        return shinigamiInTheWay ? "message.bleach.quincy_cross.shinigami" : null;
    }

    /** Whether either hand holds a Quincy Cross. */
    public static boolean holdsCross(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof QuincyCrossItem
                || entity.getOffhandItem().getItem() instanceof QuincyCrossItem;
    }

    /**
     * Whether the bloodline question is still to be settled for this soul: a living Human without a
     * Quincy tier or a Shinigami's power, who has not been settled yet.
     */
    public static boolean needsBloodlineChoice(SoulData data) {
        return !data.quincy().decided() && data.state() == SoulState.HUMAN && data.tier(PowerPath.QUINCY) <= 0
                && !ShinigamiLogic.hasShinigamiPower(data);
    }

    /**
     * Settles a save from before the bloodline existed that already has a Quincy tier: it is Gemischt
     * blood, decided, and nobody is asked.
     *
     * @return true if it changed anything
     */
    public static boolean settleOldSave(SoulData data) {
        QuincyProgress quincy = data.quincy();
        if (quincy.decided() || data.tier(PowerPath.QUINCY) <= 0) {
            return false;
        }
        if (quincy.bloodline() == Bloodline.NONE) {
            quincy = quincy.withBloodline(Bloodline.GEMISCHT);
        }
        data.setQuincy(quincy.withDecided(true));
        return true;
    }

    /**
     * The blood a choice gives: none for a Human, Quincy blood (Echt one time in four) for a Quincy,
     * and a small chance of Quincy blood when fate decides.
     */
    public static Bloodline rollBloodline(BloodlineChoice choice, RandomSource random) {
        return switch (choice) {
            case HUMAN -> Bloodline.NONE;
            case QUINCY -> random.nextDouble() < BleachConfig.get(BleachConfig.QUINCY_ECHT_CHANCE)
                    ? Bloodline.ECHT : Bloodline.GEMISCHT;
            case FATE -> random.nextDouble() < BleachConfig.get(BleachConfig.QUINCY_CHANCE)
                    ? rollBloodline(BloodlineChoice.QUINCY, random) : Bloodline.NONE;
        };
    }

    /**
     * The last part of the {@code screen.bleach.soul.next.*} key for a living Human who is not a
     * Substitute: {@code human} without blood that can awaken, {@code quincy.latent} for blood still
     * waiting, {@code quincy.poisoned} for a Quincy that Hollow poison is getting to, {@code quincy.}
     * and the tier below the third, and past it the step the arts have reached (see
     * {@link QuincyArtsLogic#nextStepKey}).
     */
    public static String nextStepKey(SoulData data, long now) {
        if (isAwakened(data)) {
            if (data.quincy().poison() >= POISON_BLOCK) {
                return "quincy.poisoned";
            }
            int tier = tier(data);
            return tier < MAX_TIER ? "quincy." + tier : QuincyArtsLogic.nextStepKey(data, now);
        }
        return canAwaken(data) ? "quincy.latent" : "human";
    }

    // ---- Training ----

    /** The tier this much training earns. */
    public static int tierForTraining(int training) {
        if (training >= BleachConfig.get(BleachConfig.QUINCY_TIER_3_TRAINING)) {
            return 3;
        }
        if (training >= BleachConfig.get(BleachConfig.QUINCY_TIER_2_TRAINING)) {
            return 2;
        }
        return training >= BleachConfig.get(BleachConfig.QUINCY_TIER_1_TRAINING) ? 1 : 0;
    }

    /**
     * Adds training. It always adds up, but raises the tier only for a soul that is awakened or can
     * awaken, and never lowers it. Each tier gained grows maximum reiryoku, and the reishi pool
     * refills to its new size. With no points, this only brings the tier up to the training.
     *
     * @return how many tiers were gained
     */
    public static int addTraining(SoulData data, int points) {
        QuincyProgress quincy = data.quincy();
        int training = (int) Math.min(Integer.MAX_VALUE, (long) quincy.training() + Math.max(0, points));
        data.setQuincy(quincy.withTraining(training));
        if (!isAwakened(data) && !canAwaken(data)) {
            return 0;
        }
        int before = tier(data);
        int after = tierForTraining(training);
        if (after <= before || data.tier(PowerPath.QUINCY) >= after) {
            return 0;
        }
        data.setTier(PowerPath.QUINCY, after);
        int gained = after - before;
        SoulLogic.grow(data, gained * BleachConfig.get(BleachConfig.QUINCY_TIER_UP_GROWTH));
        setReishi(data, maxReishi(data));
        return gained;
    }

    // ---- Reishi ----

    /**
     * How much reishi the soul can hold: 30, 20 more a tier and a tenth of its maximum reiryoku, once
     * awakened, and more in Letzt Stil and Vollständig (see {@link QuincyArtsLogic#poolMultiplier}).
     */
    public static float maxReishi(SoulData data) {
        if (!isAwakened(data)) {
            return 0.0F;
        }
        return (float) ((BleachConfig.get(BleachConfig.REISHI_BASE_MAX)
                + BleachConfig.get(BleachConfig.REISHI_MAX_PER_TIER) * tier(data)
                + REISHI_PER_MAX_REIRYOKU * data.maxReiryoku()) * QuincyArtsLogic.poolMultiplier(data));
    }

    /** The reishi the soul holds, never more than it can hold now. */
    public static float reishi(SoulData data) {
        return Math.min(data.quincy().reishi(), maxReishi(data));
    }

    /** Sets the reishi held, kept between 0 and the most the soul can hold. */
    public static void setReishi(SoulData data, float value) {
        float max = maxReishi(data);
        float clamped = Float.isFinite(value) ? Mth.clamp(value, 0.0F, max) : 0.0F;
        data.setQuincy(data.quincy().withReishi(clamped));
    }

    /**
     * Spends reishi if there is enough.
     *
     * @return false, spending nothing, when there is not enough
     */
    public static boolean trySpendReishi(SoulData data, float amount) {
        if (amount <= 0.0F) {
            return true;
        }
        float reishi = reishi(data);
        if (reishi < amount) {
            return false;
        }
        setReishi(data, reishi - amount);
        return true;
    }

    /**
     * Pays one tick of something that runs on reishi.
     *
     * @return false when the reishi ran dry, which leaves none
     */
    public static boolean drainReishi(SoulData data, float amount) {
        if (amount <= 0.0F) {
            return true;
        }
        float left = reishi(data) - amount;
        if (left > 0.0F) {
            setReishi(data, left);
            return true;
        }
        setReishi(data, 0.0F);
        return false;
    }

    /**
     * Reishi gathered each second: a trickle on its own, much more while meditating with the Cross in
     * hand (channelling), more where reishi is dense and in the full Quincy garb, less as Hollow
     * poison builds, and as the arts make it (see {@link QuincyArtsLogic#gatherMultiplier}). None for
     * a soul that cannot use its Quincy power.
     */
    public static float gatherPerSecond(SoulData data, double density, boolean channelling, boolean fullGarb) {
        if (!canUsePowers(data)) {
            return 0.0F;
        }
        double rate = channelling
                ? BleachConfig.get(BleachConfig.REISHI_CHANNEL_PER_SECOND)
                        * (1.0 + CHANNEL_PER_REIATSU_CONTROL_LEVEL * data.level(Stat.REIATSU_CONTROL))
                : BleachConfig.get(BleachConfig.REISHI_PASSIVE_PER_SECOND);
        density = QuincyArtsLogic.gatherDensity(data, density);
        rate *= Math.max(0.0, density);
        if (fullGarb) {
            rate *= 1.0 + BleachConfig.get(BleachConfig.QUINCY_GARB_GATHER_BONUS);
        }
        rate *= QuincyArtsLogic.gatherMultiplier(data);
        return (float) (rate * poisonGatherMultiplier(data.quincy().poison()));
    }

    /** How rich in reishi a world is: the Nether and the End less so than the Overworld and the rest. */
    public static double dimensionDensity(ResourceKey<Level> dimension) {
        if (dimension == Level.NETHER) {
            return NETHER_DENSITY;
        }
        return dimension == Level.END ? END_DENSITY : 1.0;
    }

    /**
     * How rich in reishi this place is: its world, then its biome (forests and meadows rich, deserts
     * and the deep dark thin), and more near an open Garganta, never above {@link #MAX_DENSITY}. Works
     * on either side; the server also counts released reiatsu nearby (see {@link QuincyEvents}).
     */
    public static double placeDensity(Level level, Vec3 at) {
        double density = dimensionDensity(level.dimension());
        Holder<Biome> biome = level.getBiome(BlockPos.containing(at));
        if (biome.is(BleachTags.REISHI_RICH)) {
            density *= RICH_BIOME_MULTIPLIER;
        } else if (biome.is(BleachTags.REISHI_THIN)) {
            density *= THIN_BIOME_MULTIPLIER;
        }
        double rangeSqr = RIFT_DENSITY_RANGE * RIFT_DENSITY_RANGE;
        // Only a tear that has opened: a dormant one gives nothing away before it does.
        if (!level.getEntitiesOfClass(GargantaRift.class, new AABB(at, at).inflate(RIFT_DENSITY_RANGE),
                rift -> rift.age() >= 0 && rift.distanceToSqr(at) <= rangeSqr).isEmpty()) {
            density += RIFT_DENSITY_BONUS;
        }
        return Math.min(MAX_DENSITY, density);
    }

    /** The {@code density.bleach.*} key for how a density feels: thin, ordinary, rich or dense. */
    public static String densityKey(double density) {
        if (density < THIN_BELOW) {
            return "density.bleach.thin";
        }
        if (density < ORDINARY_BELOW) {
            return "density.bleach.ordinary";
        }
        return density < RICH_BELOW ? "density.bleach.rich" : "density.bleach.dense";
    }

    // ---- The Heilig Bogen ----

    /**
     * What a fully drawn Heilig Pfeil does before the target is weighed: a base, more for each tier,
     * for the root of maximum reiryoku and for Reiatsu Control, up to the cap.
     */
    public static float fullDamage(SoulData data) {
        double damage = BleachConfig.get(BleachConfig.BOGEN_BASE_DAMAGE)
                + BleachConfig.get(BleachConfig.BOGEN_DAMAGE_PER_TIER) * tier(data)
                + BleachConfig.get(BleachConfig.BOGEN_DAMAGE_PER_ROOT_REIRYOKU) * Math.sqrt(Math.max(0.0F, data.maxReiryoku()))
                + DAMAGE_PER_REIATSU_CONTROL_LEVEL * data.level(Stat.REIATSU_CONTROL);
        return (float) Math.min(BleachConfig.get(BleachConfig.BOGEN_MAX_DAMAGE), damage);
    }

    /**
     * What an arrow drawn this far (0 a tap, 1 a full draw) does: a quarter of full damage up to all
     * of it, then boosted by the soul's form (see {@link QuincyArtsLogic#boostDamage}).
     * {@link #fullDamage} itself stays unboosted.
     */
    public static float arrowDamage(SoulData data, float power) {
        float drawn = fullDamage(data) * (TAP_DAMAGE_FRACTION + (1.0F - TAP_DAMAGE_FRACTION) * power);
        return QuincyArtsLogic.boostDamage(data, drawn);
    }

    /** A Hollow mob, or a player who is a Hollow or an Arrancar. */
    public static boolean isHollowLike(LivingEntity target) {
        if (target instanceof Player player) {
            if (!SoulLogic.hasSoul(player)) {
                return false;
            }
            SoulState state = SoulLogic.get(player).state();
            return state == SoulState.HOLLOW || state == SoulState.ARRANCAR;
        }
        return target.getType().is(BleachTags.HOLLOWS);
    }

    /** An arrow's damage against this target: Hollows take a quarter more. */
    public static float damageAgainst(float damage, LivingEntity target) {
        return isHollowLike(target) ? damage * (float) BleachConfig.get(BleachConfig.BOGEN_HOLLOW_DAMAGE_MULTIPLIER) : damage;
    }

    /** How many targets a shot passes through before it stops: one from tier 2 and two from tier 3, at a near-full draw. */
    public static int pierceFor(SoulData data, float power) {
        if (power < PIERCE_POWER) {
            return 0;
        }
        int tier = tier(data);
        return tier >= 3 ? 2 : tier == 2 ? 1 : 0;
    }

    /** The reishi a shot drawn this far costs. */
    public static float shotCost(float power) {
        return (float) (BleachConfig.get(BleachConfig.BOGEN_BASE_COST)
                + BleachConfig.get(BleachConfig.BOGEN_FULL_DRAW_EXTRA_COST) * power);
    }

    public static float shotSpeed(float power) {
        return BASE_SPEED + FULL_DRAW_EXTRA_SPEED * power;
    }

    public static float shotInaccuracy(float power) {
        return BASE_INACCURACY - FULL_DRAW_INACCURACY_DROP * power;
    }

    /**
     * Pays for a shot drawn this far. Reishi pays first; what it is short of, reiryoku pays at the
     * fallback rate, as long as that leaves the reserve. If even that cannot pay, the shot goes out as
     * strong as reishi and spare reiryoku together allow.
     *
     * @return the power paid for, or -1, spending nothing, if no shot worth firing can be paid for
     */
    public static float payForShot(SoulData data, float power) {
        float cost = shotCost(power);
        float reishi = reishi(data);
        if (reishi >= cost) {
            setReishi(data, reishi - cost);
            return power;
        }
        double rate = BleachConfig.get(BleachConfig.BOGEN_REIRYOKU_FALLBACK_RATE);
        float need = (float) ((cost - reishi) * rate);
        if (rate > 0.0 && data.reiryoku() - need >= SHOT_RESERVE) {
            setReishi(data, 0.0F);
            data.setReiryoku(data.reiryoku() - need);
            return power;
        }
        double extra = BleachConfig.get(BleachConfig.BOGEN_FULL_DRAW_EXTRA_COST);
        double available = reishi + (rate > 0.0 ? Math.max(0.0, (data.reiryoku() - SHOT_RESERVE) / rate) : 0.0);
        if (extra <= 0.0) {
            return -1.0F;
        }
        float weaker = (float) Math.min(power, (available - BleachConfig.get(BleachConfig.BOGEN_BASE_COST)) / extra);
        if (!(weaker >= MIN_SHOT_POWER)) {
            return -1.0F;
        }
        float weakerCost = shotCost(weaker);
        float fromReishi = Math.min(reishi, weakerCost);
        setReishi(data, reishi - fromReishi);
        float rest = weakerCost - fromReishi;
        if (rest > 0.0F && rate > 0.0) {
            // Never below the reserve: an affordable shot only misses it by rounding.
            float reiryoku = data.reiryoku();
            data.setReiryoku(Math.max(Math.min(reiryoku, SHOT_RESERVE), reiryoku - (float) (rest * rate)));
        }
        return weaker;
    }

    // ---- Movement ----

    /**
     * Which dash the movement key does for this soul. A Quincy who knows Hirenkyaku uses it while
     * holding the Cross, or always if they cannot flash step; anyone who can flash step does that;
     * a Quincy without a Shinigami's power is told Hirenkyaku is not ready (or, sealed, why it is
     * gone); everyone else flash steps, or is told why not.
     */
    public static Movement movementFor(SoulData data, boolean holdsCross, long now) {
        boolean shunpo = ShinigamiLogic.canFlashStep(data, now);
        if (knowsHirenkyaku(data) && (holdsCross || !shunpo)) {
            return Movement.HIRENKYAKU;
        }
        if (shunpo) {
            return Movement.SHUNPO;
        }
        return (canUsePowers(data) || QuincyArtsLogic.isSealed(data)) && !ShinigamiLogic.hasShinigamiPower(data)
                ? Movement.HIRENKYAKU : Movement.SHUNPO;
    }

    /** Hirenkyaku comes with the second tier. */
    public static boolean knowsHirenkyaku(SoulData data) {
        return canUsePowers(data) && tier(data) >= BleachConfig.get(BleachConfig.HIRENKYAKU_MIN_TIER);
    }

    /**
     * How far a Hirenkyaku goes, in blocks: more at the third tier and with Reiatsu Control, up to
     * the cap. Slowness shortens it by a quarter per level, as it does Shunpo, and Letzt Stil carries
     * it past the cap (see {@link QuincyArtsLogic#hirenkyakuDistanceMultiplier}).
     *
     * @param slownessAmplifier the Slowness amplifier on the soul, or -1 for none
     */
    public static double hirenkyakuDistance(SoulData data, int slownessAmplifier) {
        double distance = Math.min(BleachConfig.get(BleachConfig.HIRENKYAKU_MAX_DISTANCE),
                BleachConfig.get(BleachConfig.HIRENKYAKU_BASE_DISTANCE)
                        + HIRENKYAKU_DISTANCE_PER_TIER * Math.max(0, tier(data) - 2)
                        + HIRENKYAKU_DISTANCE_PER_REIATSU_CONTROL_LEVEL * data.level(Stat.REIATSU_CONTROL));
        if (slownessAmplifier >= 0) {
            distance *= Math.max(0.0, 1.0 - 0.25 * (slownessAmplifier + 1));
        }
        return distance * QuincyArtsLogic.hirenkyakuDistanceMultiplier(data);
    }

    /** Reishi a Hirenkyaku costs, less at the third tier, and less again in Letzt Stil. */
    public static float hirenkyakuCost(SoulData data) {
        return (float) (BleachConfig.get(tier(data) >= MAX_TIER
                ? BleachConfig.HIRENKYAKU_MASTER_COST : BleachConfig.HIRENKYAKU_COST)
                * QuincyArtsLogic.hirenkyakuCostMultiplier(data));
    }

    /**
     * Ticks between two dashes after a Hirenkyaku, fewer at the third tier and fewer again in Letzt
     * Stil, though never none unless the server sets none.
     */
    public static int hirenkyakuCooldownTicks(SoulData data) {
        int base = BleachConfig.get(tier(data) >= MAX_TIER
                ? BleachConfig.HIRENKYAKU_MASTER_COOLDOWN_TICKS : BleachConfig.HIRENKYAKU_COOLDOWN_TICKS);
        return base == 0 ? 0 : Math.max(1, Math.round(base * QuincyArtsLogic.hirenkyakuCooldownMultiplier(data)));
    }

    /** A Quincy who knows Hirenkyaku also stands on reishi, unless Hollow poison clouds it. */
    public static boolean canStandOnReishi(SoulData data) {
        return knowsHirenkyaku(data) && data.quincy().poison() < POISON_BLOCK;
    }

    /** Whether this soul's reishi footing is paid in reishi: a Quincy's, unless it could stand there as a Shinigami. */
    public static boolean footingOnReishi(SoulData data, long now) {
        return canStandOnReishi(data) && !ShinigamiLogic.shinigamiFooting(data, now);
    }

    // ---- Blut ----

    /** Whether the soul's blood is ready for this Blut: Echt Quincy get Vene at tier 1 and Arterie at 2, Gemischt a tier later. */
    public static boolean blutUnlocked(SoulData data, BlutMode mode) {
        if (mode == BlutMode.OFF) {
            return true;
        }
        int needed = blutUnlockTier(bloodline(data), mode);
        return canUsePowers(data) && tier(data) >= needed;
    }

    /** The tier at which this bloodline gets this Blut. */
    static int blutUnlockTier(Bloodline bloodline, BlutMode mode) {
        int echt = mode == BlutMode.ARTERIE ? 2 : mode == BlutMode.VENE ? 1 : 0;
        return bloodline == Bloodline.ECHT || mode == BlutMode.OFF ? echt : echt + 1;
    }

    /** What the Blut key turns to: off to Vene, Vene to Arterie (or off, if Arterie is not ready), Arterie back to Vene; sneaking turns it off. */
    public static BlutMode nextBlut(SoulData data, BlutMode current, boolean sneaking) {
        if (sneaking) {
            return BlutMode.OFF;
        }
        return switch (current) {
            case OFF -> BlutMode.VENE;
            case VENE -> blutUnlocked(data, BlutMode.ARTERIE) ? BlutMode.ARTERIE : BlutMode.OFF;
            case ARTERIE -> BlutMode.VENE;
        };
    }

    /** The Blut working now: what the soul is calling on, if it can use its Quincy power. */
    public static BlutMode activeBlut(SoulData data) {
        return canUsePowers(data) ? data.quincy().blut() : BlutMode.OFF;
    }

    /** A soul that also holds a Shinigami's power gets half of Blut's strength. */
    public static float blutStrengthMultiplier(SoulData data) {
        return ShinigamiLogic.hasShinigamiPower(data) ? HYBRID_BLUT_MULTIPLIER : 1.0F;
    }

    /** The share of each hit Blut Vene stops: a fifth, more each tier, up to the cap. */
    public static float veneReduction(SoulData data) {
        double reduction = Math.min(BleachConfig.get(BleachConfig.VENE_MAX_REDUCTION),
                BleachConfig.get(BleachConfig.VENE_REDUCTION) + VENE_PER_TIER * (tier(data) - 1));
        return (float) Math.max(0.0, reduction) * blutStrengthMultiplier(data);
    }

    /** How much harder Blut Arterie makes each hit: a quarter, more each tier past the second, up to the cap. */
    public static float arterieBonus(SoulData data) {
        double bonus = Math.min(BleachConfig.get(BleachConfig.ARTERIE_MAX_BONUS),
                BleachConfig.get(BleachConfig.ARTERIE_BONUS) + ARTERIE_PER_TIER * (tier(data) - 2));
        return (float) Math.max(0.0, bonus) * blutStrengthMultiplier(data);
    }

    /** Reishi it costs to call on this Blut. */
    public static float blutActivationCost(BlutMode mode) {
        return switch (mode) {
            case OFF -> 0.0F;
            case VENE -> VENE_ACTIVATION_COST;
            case ARTERIE -> ARTERIE_ACTIVATION_COST;
        };
    }

    /** Reishi this Blut drains each second it runs. */
    public static float blutDrainPerSecond(BlutMode mode) {
        return switch (mode) {
            case OFF -> 0.0F;
            case VENE -> (float) BleachConfig.get(BleachConfig.VENE_DRAIN_PER_SECOND);
            case ARTERIE -> (float) BleachConfig.get(BleachConfig.ARTERIE_DRAIN_PER_SECOND);
        };
    }

    /** How much more an attacker's reiatsu counts in the reiatsu difference: half again under Blut Arterie. */
    public static float reiatsuFactor(LivingEntity attacker) {
        if (attacker instanceof Player player && SoulLogic.hasSoul(player)
                && activeBlut(SoulLogic.get(player)) == BlutMode.ARTERIE) {
            return (float) BleachConfig.get(BleachConfig.ARTERIE_REIATSU_MULTIPLIER);
        }
        return 1.0F;
    }

    // ---- Hollow poison ----

    /** Whether this creature's reiatsu poisons a Quincy: a Hollow mob, or a player who is a Hollow or an Arrancar. */
    public static boolean isHollowSource(@Nullable Entity cause) {
        if (cause instanceof Hollow) {
            return true;
        }
        if (cause instanceof Player player && SoulLogic.hasSoul(player)) {
            SoulState state = SoulLogic.get(player).state();
            return state == SoulState.HOLLOW || state == SoulState.ARRANCAR;
        }
        return false;
    }

    /**
     * Hollow poison a hit of this much damage leaves: more from a bite that carries an effect, half
     * under Blut Vene, less at higher tiers (never below half), and scaled by the difficulty, with
     * Peaceful counted as Easy.
     */
    public static float poisonFrom(SoulData data, float damage, boolean onHitEffect, Difficulty difficulty) {
        if (damage <= 0.0F) {
            return 0.0F;
        }
        double poison = damage * BleachConfig.get(BleachConfig.HOLLOW_POISON_PER_DAMAGE);
        if (onHitEffect) {
            poison *= POISON_ON_HIT_MULTIPLIER;
        }
        if (activeBlut(data) == BlutMode.VENE) {
            poison *= VENE_POISON_MULTIPLIER;
        }
        poison *= Math.max(POISON_MIN_RESISTANCE, 1.0 - POISON_RESISTANCE_PER_TIER * (tier(data) - 1));
        poison *= switch (difficulty) {
            case PEACEFUL, EASY -> POISON_EASY;
            case NORMAL -> 1.0;
            case HARD -> POISON_HARD;
        };
        return (float) poison;
    }

    /** The stage this much poison has reached: 0 below 25, then 1, 2 and 3 at 25, 50 and 75, and 4 at 100. */
    public static int poisonStage(float poison) {
        if (poison >= QuincyProgress.MAX_POISON) {
            return 4;
        }
        if (poison >= POISON_PANG) {
            return 3;
        }
        if (poison >= POISON_BLOCK) {
            return 2;
        }
        return poison >= POISON_SLOW ? 1 : 0;
    }

    /** How fast reishi gathers with this much poison: three quarters from 25, half from 50. */
    public static float poisonGatherMultiplier(float poison) {
        if (poison >= POISON_BLOCK) {
            return POISON_BLOCK_GATHER;
        }
        return poison >= POISON_SLOW ? POISON_SLOW_GATHER : 1.0F;
    }

    /** How fast reiryoku comes back with this soul's poison: a little slower from 25. */
    public static double poisonRegenMultiplier(SoulData data) {
        return data.quincy().poison() >= POISON_SLOW ? POISON_SLOW_REGEN : 1.0;
    }

    /** Poison that wears off each tick once it has had time to: faster while channelling where reishi is rich. */
    public static float poisonDecayPerTick(boolean channellingInDenseReishi) {
        double perMinute = BleachConfig.get(BleachConfig.HOLLOW_POISON_DECAY_PER_MINUTE);
        if (channellingInDenseReishi) {
            perMinute = Math.max(perMinute, CHANNEL_DECAY_PER_MINUTE);
        }
        return (float) (perMinute / TICKS_PER_MINUTE);
    }

    /**
     * Washes some poison away, never below none.
     *
     * @return true if the poison went down
     */
    public static boolean cure(SoulData data, float amount) {
        float poison = data.quincy().poison();
        if (poison <= 0.0F || amount <= 0.0F) {
            return false;
        }
        data.setQuincy(data.quincy().withPoison(Math.max(0.0F, poison - amount)));
        return true;
    }
}
