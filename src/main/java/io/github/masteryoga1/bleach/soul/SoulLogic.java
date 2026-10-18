package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** The rules for spiritual energy and stats. Kept apart from the events so game tests can call them directly. */
public final class SoulLogic {
    public static final ResourceLocation HOHO_SPEED_ID = BleachMod.id("hoho_speed");

    /** How long exhaustion lasts after running out of reiryoku, in ticks. */
    public static final int EXHAUSTION_TICKS = 100;

    /** Creature types that are spirits, hidden from the spiritually unaware: Shinigami NPCs, Hollows, Pluses and the Garganta. */
    public static final TagKey<EntityType<?>> SPIRITS = TagKey.create(Registries.ENTITY_TYPE, BleachMod.id("spirits"));

    /** Share of maximum reiryoku needed to start releasing reiatsu. */
    public static final float RELEASE_MIN_FRACTION = 0.1F;

    /** How long each pulse of the pressure aura lingers, in ticks. */
    public static final int PRESSURE_EFFECT_TICKS = 30;

    private SoulLogic() {
    }

    public static SoulData get(LivingEntity entity) {
        return entity.getData(SoulAttachments.SOUL);
    }

    /** Gives a new soul its starting reiryoku, full. */
    public static void initialize(SoulData data) {
        float start = (float) BleachConfig.get(BleachConfig.STARTING_REIRYOKU);
        data.setMaxReiryoku(start);
        data.setReiryoku(start);
        data.markInitialized();
    }

    /**
     * Whether this soul can perceive spirits: every spirit can, Humans above the threshold can, and so
     * can anyone whose power has awakened, however little reiryoku they have.
     */
    public static boolean isAware(SoulData data) {
        return data.state().isSpirit() || hasAwakenedPower(data)
                || data.maxReiryoku() >= BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD);
    }

    /** Whether this player sees spirits: in creative or spectator mode, or with a spiritually aware soul. */
    public static boolean seesSpirits(Player player) {
        return player.isCreative() || player.isSpectator() || hasSoul(player) && isAware(get(player));
    }

    /**
     * Whether this Human is just below the awareness threshold, where spirits cannot be seen yet but
     * give themselves away: hidden spirits shimmer faintly and Hollow screams sound far off.
     */
    public static boolean inShimmerBand(SoulData data) {
        double fraction = BleachConfig.get(BleachConfig.SHIMMER_FRACTION);
        return data.state() == SoulState.HUMAN && data.isInitialized() && !isAware(data) && fraction < 1.0
                && data.maxReiryoku() >= BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD) * fraction;
    }

    /** Whether any {@link PowerPath} has awakened in this soul. */
    public static boolean hasAwakenedPower(SoulData data) {
        for (PowerPath path : PowerPath.values()) {
            if (data.tier(path) > 0) {
                return true;
            }
        }
        return false;
    }

    public static float regenPerTick(SoulData data, boolean resting) {
        double perSecond = data.maxReiryoku() * BleachConfig.get(BleachConfig.REGEN_FRACTION_PER_SECOND);
        perSecond *= 1.0 + data.level(Stat.REIATSU_CONTROL)
                * BleachConfig.get(BleachConfig.REIATSU_CONTROL_REGEN_PER_LEVEL);
        if (resting) {
            perSecond *= BleachConfig.get(BleachConfig.REST_REGEN_MULTIPLIER);
        }
        return (float) (perSecond / 20.0);
    }

    /** Regenerates one tick's worth of reiryoku. */
    public static void regenTick(SoulData data, boolean resting) {
        regenTick(data, resting, 1.0);
    }

    /** Regenerates one tick's worth of reiryoku, scaled by {@code multiplier} (see ShinigamiLogic#regenMultiplier). */
    public static void regenTick(SoulData data, boolean resting, double multiplier) {
        if (data.reiryoku() < data.maxReiryoku() && multiplier > 0.0) {
            data.setReiryoku(data.reiryoku() + (float) (regenPerTick(data, resting) * multiplier));
        }
    }

    /**
     * Spends reiryoku if there is enough. Spending the last of it exhausts the soul with Slowness
     * and Weakness.
     *
     * @return false, spending nothing, when there is not enough reiryoku
     */
    public static boolean trySpend(LivingEntity entity, SoulData data, float amount) {
        if (amount <= 0.0F) {
            return true;
        }
        if (data.reiryoku() < amount) {
            return false;
        }
        data.setReiryoku(data.reiryoku() - amount);
        if (data.reiryoku() < 0.5F) {
            data.setReiryoku(0.0F);
            exhaust(entity);
        }
        return true;
    }

    public static void exhaust(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTION_TICKS, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUSTION_TICKS, 0));
    }

    /** Raises maximum reiryoku, up to the configured cap. */
    public static void grow(SoulData data, double amount) {
        if (amount <= 0.0) {
            return;
        }
        double cap = BleachConfig.get(BleachConfig.MAX_REIRYOKU);
        if (data.maxReiryoku() < cap) {
            data.setMaxReiryoku((float) Math.min(cap, data.maxReiryoku() + amount));
        }
    }

    public static int maxStatLevel() {
        return BleachConfig.get(BleachConfig.MAX_STAT_LEVEL);
    }

    /** Adds training XP to a stat. Each level gained grows reiryoku and is announced to players. */
    public static void addStatXp(LivingEntity entity, SoulData data, Stat stat, int amount) {
        int gained = data.addXp(stat, amount, maxStatLevel());
        if (gained <= 0) {
            return;
        }
        grow(data, gained * BleachConfig.get(BleachConfig.LEVEL_UP_GROWTH));
        applyStatModifiers(entity, data);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.bleach.stat_level_up",
                    stat.displayName(), data.level(stat)), true);
        }
    }

    /** Extra damage multiplier for sword hits from Zanjutsu. */
    public static float zanjutsuMultiplier(SoulData data) {
        return (float) (1.0 + data.level(Stat.ZANJUTSU) * BleachConfig.get(BleachConfig.ZANJUTSU_DAMAGE_PER_LEVEL));
    }

    /** Extra flat damage for unarmed hits from Hakuda. */
    public static float hakudaBonus(SoulData data) {
        return (float) (data.level(Stat.HAKUDA) * BleachConfig.get(BleachConfig.HAKUDA_DAMAGE_PER_LEVEL));
    }

    /**
     * Applies the stats that map onto vanilla attributes. The modifiers are transient, so this runs
     * again whenever the player logs in, respawns or a stat changes.
     */
    public static void applyStatModifiers(LivingEntity entity, SoulData data) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        int hoho = data.level(Stat.HOHO);
        if (hoho <= 0) {
            speed.removeModifier(HOHO_SPEED_ID);
        } else {
            speed.addOrUpdateTransientModifier(new AttributeModifier(HOHO_SPEED_ID,
                    hoho * BleachConfig.get(BleachConfig.HOHO_SPEED_PER_LEVEL),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** Whether this creature is a spirit: a player in a spirit state, or a creature type tagged as one. */
    public static boolean isSpirit(LivingEntity entity) {
        if (entity instanceof Player) {
            return entity.hasData(SoulAttachments.SOUL) && get(entity).state().isSpirit();
        }
        return entity.getType().is(SPIRITS);
    }

    /**
     * Whether this creature has a soul the mod tracks: a player once initialized, or a spirit mob
     * (Shinigami NPC, Hollow or Plus) once it has been given one.
     */
    public static boolean hasSoul(Entity entity) {
        return entity.hasData(SoulAttachments.SOUL) && entity.getData(SoulAttachments.SOUL).isInitialized();
    }

    /**
     * A creature's spiritual pressure: maximum reiryoku for a soul (players and spirit mobs alike),
     * weighed by a Quincy's form (see {@link QuincyArtsLogic#reiatsuFactor}), and max health as a
     * stand-in for creatures without one.
     */
    public static float reiatsu(LivingEntity entity) {
        if (!hasSoul(entity)) {
            return entity.getMaxHealth();
        }
        SoulData data = get(entity);
        return (float) (data.maxReiryoku() * QuincyArtsLogic.reiatsuFactor(data));
    }

    /**
     * Reiatsu difference: how much of a hit gets through when the victim's reiatsu dwarfs the
     * attacker's. Full damage up to the configured ratio, then falling off, never below the minimum.
     */
    public static float reiatsuDamageMultiplier(float attackerReiatsu, float victimReiatsu) {
        if (attackerReiatsu <= 0.0F || victimReiatsu <= 0.0F) {
            return 1.0F;
        }
        double ratio = BleachConfig.get(BleachConfig.REIATSU_DIFFERENCE_RATIO);
        double difference = victimReiatsu / attackerReiatsu;
        if (difference <= ratio) {
            return 1.0F;
        }
        return (float) Math.max(BleachConfig.get(BleachConfig.REIATSU_DIFFERENCE_MIN_DAMAGE), ratio / difference);
    }

    // ---- Death and the Chain of Fate ----

    public static int chainLifeTicks() {
        return BleachConfig.get(BleachConfig.CHAIN_OF_FATE_MINUTES) * 60 * 20;
    }

    /**
     * Gives a Plus without a chain a fresh one. Saves from before every Plus had a chain can hold one.
     *
     * @return whether a chain was added
     */
    public static boolean ensureChain(SoulData data) {
        if (data.state() != SoulState.PLUS || data.chainMaxTicks() > 0) {
            return false;
        }
        data.startChain(chainLifeTicks());
        return true;
    }

    /** Whether the Plus's chain is in its last stretch, where the links eat each other. */
    public static boolean isEncroaching(SoulData data) {
        return data.state() == SoulState.PLUS && data.chainMaxTicks() > 0
                && data.chainFraction() <= BleachConfig.get(BleachConfig.ENCROACHMENT_FRACTION);
    }

    /**
     * Erodes a Plus's chain by one tick at the given speed (1 = normal).
     *
     * @return true if the chain ran out on this tick
     */
    public static boolean erodeChain(SoulData data, double speed) {
        boolean hollowfication = BleachConfig.get(BleachConfig.HOLLOWFICATION);
        if (data.chainTicks() <= 0) {
            // Already gone (a save or command left it empty): it breaks now, unless that is turned off.
            return hollowfication;
        }
        // With Hollowfication off the last link holds, and the soul stays in Encroachment.
        int floor = hollowfication ? 0 : 1;
        if (data.chainTicks() <= floor || speed <= 0.0) {
            return false;
        }
        data.chainErosion += (float) speed;
        int whole = (int) data.chainErosion;
        if (whole <= 0) {
            return false;
        }
        data.chainErosion -= whole;
        data.setChainTicks(Math.max(floor, data.chainTicks() - whole));
        return data.chainTicks() <= 0;
    }

    /**
     * What a soul becomes after its body dies. A soul torn apart (see {@link QuincyEvents#destroySoul})
     * knits itself together again as a living Human, weaker. Otherwise a Substitute's Shinigami power
     * holds their soul together and they become a Shinigami; an awakened Quincy's blood holds theirs
     * to the body and they wake alive; any other spiritually aware Human lingers as a Plus with a
     * fresh Chain of Fate; a Plus that dies loses part of its chain (an awakened Quincy Plus whose
     * chain that breaks is torn apart at once, as above). Everyone keeps their progress,
     * but a Quincy loses their Blut and gathered reishi and half their Hollow poison, a Letzt Stil
     * burns out into the seal, and every Quincy form ends (see {@link QuincyArtsLogic#afterDeath}).
     */
    public static SoulData afterDeath(SoulData old) {
        SoulData next = old.copy();
        next.setReiatsuReleased(false);
        next.setReiryoku(next.maxReiryoku() * (float) BleachConfig.get(BleachConfig.RESPAWN_REIRYOKU_FRACTION));
        QuincyProgress quincy = old.quincy().withBlut(BlutMode.OFF).withReishi(0.0F).withTornApart(false)
                .withArts(QuincyArtsLogic.afterDeath(old.quincy().arts()));
        if (old.isSoulDestroyed()) {
            return reformed(old, next, quincy);
        }
        next.setQuincy(quincy.withPoison(old.quincy().poison() * 0.5F));
        boolean intoSpirit = old.state() == SoulState.HUMAN && BleachConfig.get(BleachConfig.DEATH_INTO_PLUS);
        if (intoSpirit && old.tier(PowerPath.SHINIGAMI) > 0) {
            next.setState(SoulState.SHINIGAMI);
            next.clearChain();
            next.becameShinigami = true;
        } else if (intoSpirit && QuincyLogic.isAwakened(old) && !BleachConfig.get(BleachConfig.QUINCY_DEATH_INTO_PLUS)) {
            next.clearChain();
            next.quincyReborn = true;
        } else if (intoSpirit && (BleachConfig.get(BleachConfig.ALWAYS_BECOME_PLUS) || isAware(old))) {
            next.setState(SoulState.PLUS);
            next.startChain(chainLifeTicks());
            next.becamePlus = true;
        } else if (old.state() == SoulState.PLUS && next.chainMaxTicks() > 0) {
            int cost = BleachConfig.get(BleachConfig.PLUS_DEATH_CHAIN_COST_SECONDS) * 20;
            if (next.chainTicks() - cost <= 0 && QuincyLogic.isAwakened(old) && BleachConfig.get(BleachConfig.HOLLOWFICATION)) {
                // An awakened Quincy's chain breaking tears the soul apart: done now, not one death later.
                return reformed(old, next, quincy);
            }
            // Leave the last link for the next tick, so a chain that breaks does so after respawning.
            next.setChainTicks(Math.max(1, next.chainTicks() - cost));
        }
        return next;
    }

    /** A torn soul knits itself together as a living Human, with less reiryoku and no Hollow poison. */
    private static SoulData reformed(SoulData old, SoulData next, QuincyProgress quincy) {
        next.setState(SoulState.HUMAN);
        next.clearChain();
        next.setMaxReiryoku((float) (old.maxReiryoku() * QuincyLogic.DESTROYED_REIRYOKU_FRACTION));
        next.setReiryoku(next.maxReiryoku() * (float) BleachConfig.get(BleachConfig.RESPAWN_REIRYOKU_FRACTION));
        next.setQuincy(quincy.withPoison(0.0F));
        next.soulReformed = true;
        return next;
    }

    /** The chain is gone: the hole opens, the mask forms, and the soul becomes a Hollow. */
    public static void hollowfy(LivingEntity entity, SoulData data) {
        data.setState(SoulState.HOLLOW);
        data.clearChain();
        data.setReiatsuReleased(false);
        if (data.tier(PowerPath.HOLLOW) <= 0) {
            data.setTier(PowerPath.HOLLOW, 1);
        }
        grow(data, BleachConfig.get(BleachConfig.HOLLOWFICATION_GROWTH));
        data.setReiryoku(data.maxReiryoku());
        entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
    }

    /**
     * Changes state with the side effects a state brings: a Plus gets a chain, and a Shinigami or a
     * Hollow its first tier. Every other state loses the chain.
     */
    public static void changeState(SoulData data, SoulState state) {
        data.setState(state);
        if (state == SoulState.PLUS) {
            if (data.chainMaxTicks() <= 0) {
                data.startChain(chainLifeTicks());
            }
        } else {
            data.clearChain();
        }
        if (state == SoulState.HOLLOW && data.tier(PowerPath.HOLLOW) <= 0) {
            data.setTier(PowerPath.HOLLOW, 1);
        }
        if (state == SoulState.SHINIGAMI && data.tier(PowerPath.SHINIGAMI) <= 0) {
            data.setTier(PowerPath.SHINIGAMI, 1);
        }
    }

    // ---- Reiatsu release (the pressure aura) ----

    public static boolean canRelease(SoulData data) {
        return isAware(data) && data.reiryoku() >= data.maxReiryoku() * RELEASE_MIN_FRACTION;
    }

    public static float pressureRadius(SoulData data) {
        return (float) Math.min(BleachConfig.get(BleachConfig.PRESSURE_MAX_RADIUS),
                BleachConfig.get(BleachConfig.PRESSURE_BASE_RADIUS) + Math.sqrt(data.maxReiryoku()) / 2.0);
    }

    public static float releaseDrainPerTick(SoulData data) {
        return (float) (data.maxReiryoku() * BleachConfig.get(BleachConfig.PRESSURE_DRAIN_FRACTION_PER_SECOND) / 20.0);
    }

    /**
     * Pays one tick of an ongoing technique. Running dry exhausts the soul.
     *
     * @return false when the soul ran dry
     */
    public static boolean drain(LivingEntity entity, SoulData data, float amount) {
        float left = data.reiryoku() - amount;
        if (left >= 0.5F) {
            data.setReiryoku(left);
            return true;
        }
        data.setReiryoku(0.0F);
        exhaust(entity);
        return false;
    }

    /**
     * Weighs a creature down with released reiatsu: Slowness and Mining Fatigue, plus Weakness for
     * the far weaker. Refreshed while the aura lasts.
     *
     * @return how hard it was hit: 0 unaffected, 1 to 3 for each doubling past the minimum ratio
     */
    public static int applyPressure(LivingEntity target, float userReiatsu) {
        double minRatio = BleachConfig.get(BleachConfig.PRESSURE_MIN_RATIO);
        double ratio = userReiatsu / Math.max(1.0E-3F, reiatsu(target));
        if (ratio < minRatio) {
            return 0;
        }
        int level = ratio >= minRatio * 4 ? 3 : ratio >= minRatio * 2 ? 2 : 1;
        // Ambient and without particles, but with an icon so a player knows why they feel heavy.
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PRESSURE_EFFECT_TICKS, level - 1, true, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, PRESSURE_EFFECT_TICKS, level - 1, true, false, true));
        if (level >= 3) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PRESSURE_EFFECT_TICKS, 0, true, false, true));
        }
        return level;
    }
}
