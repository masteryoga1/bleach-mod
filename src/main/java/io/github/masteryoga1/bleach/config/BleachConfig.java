package io.github.masteryoga1.bleach.config;

import io.github.masteryoga1.bleach.quincy.BloodlineMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server settings, saved per world in {@code serverconfig/bleach-server.toml} and sent to every
 * client that joins.
 */
public final class BleachConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue STARTING_REIRYOKU;
    public static final ModConfigSpec.DoubleValue AWARENESS_THRESHOLD;
    public static final ModConfigSpec.DoubleValue MAX_REIRYOKU;
    public static final ModConfigSpec.DoubleValue REGEN_FRACTION_PER_SECOND;
    public static final ModConfigSpec.DoubleValue REST_REGEN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RESPAWN_REIRYOKU_FRACTION;
    public static final ModConfigSpec.DoubleValue SHIMMER_FRACTION;

    public static final ModConfigSpec.DoubleValue MUNDANE_KILL_GROWTH_PER_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue SPIRIT_KILL_GROWTH_PER_REIATSU;
    public static final ModConfigSpec.DoubleValue PURIFICATION_GROWTH_MULTIPLIER;
    public static final ModConfigSpec.IntValue PLAYER_KILL_GROWTH_COOLDOWN_MINUTES;
    public static final ModConfigSpec.DoubleValue CLOSE_CALL_GROWTH;
    public static final ModConfigSpec.IntValue CLOSE_CALL_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue LEVEL_UP_GROWTH;

    public static final ModConfigSpec.IntValue MAX_STAT_LEVEL;
    public static final ModConfigSpec.DoubleValue ZANJUTSU_DAMAGE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue HAKUDA_DAMAGE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue HOHO_SPEED_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue REIATSU_CONTROL_REGEN_PER_LEVEL;

    public static final ModConfigSpec.BooleanValue DEATH_INTO_PLUS;
    public static final ModConfigSpec.BooleanValue ALWAYS_BECOME_PLUS;
    public static final ModConfigSpec.IntValue PLUS_DEATH_CHAIN_COST_SECONDS;

    public static final ModConfigSpec.IntValue CHAIN_OF_FATE_MINUTES;
    public static final ModConfigSpec.DoubleValue LOW_HEALTH_EROSION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue NEAR_SHINIGAMI_EROSION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ENCROACHMENT_FRACTION;
    public static final ModConfigSpec.BooleanValue HOLLOWFICATION;
    public static final ModConfigSpec.DoubleValue HOLLOWFICATION_GROWTH;
    public static final ModConfigSpec.IntValue HOLLOW_BITE_CHAIN_COST_SECONDS;

    public static final ModConfigSpec.DoubleValue REIATSU_DIFFERENCE_RATIO;
    public static final ModConfigSpec.DoubleValue REIATSU_DIFFERENCE_MIN_DAMAGE;
    public static final ModConfigSpec.DoubleValue PRESSURE_DRAIN_FRACTION_PER_SECOND;
    public static final ModConfigSpec.DoubleValue PRESSURE_MIN_RATIO;
    public static final ModConfigSpec.DoubleValue PRESSURE_BASE_RADIUS;
    public static final ModConfigSpec.DoubleValue PRESSURE_MAX_RADIUS;

    public static final ModConfigSpec.DoubleValue AWAKENING_GROWTH;

    public static final ModConfigSpec.IntValue GRADUATION_ZANJUTSU_LEVEL;
    public static final ModConfigSpec.IntValue GRADUATION_HAKUDA_LEVEL;
    public static final ModConfigSpec.IntValue GRADUATION_HOHO_LEVEL;
    public static final ModConfigSpec.IntValue GRADUATION_KIDO_LEVEL;
    public static final ModConfigSpec.IntValue ASAUCHI_REISSUE_MINUTES;
    public static final ModConfigSpec.IntValue ACADEMY_LOCATE_RADIUS_CHUNKS;
    public static final ModConfigSpec.BooleanValue ACADEMY_GUARANTEE;

    public static final ModConfigSpec.DoubleValue KONSO_GROWTH;
    public static final ModConfigSpec.IntValue KONSO_REWARDED_PER_DAY;
    public static final ModConfigSpec.BooleanValue KONSO_SENDS_TO_ACADEMY;

    public static final ModConfigSpec.DoubleValue TRANSFER_FRACTION;
    public static final ModConfigSpec.IntValue GIVER_RECOVERY_MINUTES;
    public static final ModConfigSpec.DoubleValue LENT_REGEN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue WOUNDED_ENCOUNTERS;
    public static final ModConfigSpec.DoubleValue ENCOUNTER_MIN_REIRYOKU;
    public static final ModConfigSpec.DoubleValue ENCOUNTER_CHANCE_PER_MINUTE;
    public static final ModConfigSpec.IntValue ENCOUNTER_COOLDOWN_MINUTES;

    public static final ModConfigSpec.IntValue SHUNPO_MIN_HOHO_LEVEL;
    public static final ModConfigSpec.DoubleValue SHUNPO_BASE_DISTANCE;
    public static final ModConfigSpec.DoubleValue SHUNPO_DISTANCE_PER_HOHO_LEVEL;
    public static final ModConfigSpec.DoubleValue SHUNPO_MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue SHUNPO_COST_FRACTION;
    public static final ModConfigSpec.DoubleValue SHUNPO_MASTER_COST_FRACTION;
    public static final ModConfigSpec.IntValue SHUNPO_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue SHUNPO_MASTER_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue SHUNPO_MASTERY_HOHO_LEVEL;
    public static final ModConfigSpec.IntValue SHUNPO_HOHO_XP;

    public static final ModConfigSpec.IntValue FOOTING_MIN_HOHO_LEVEL;
    public static final ModConfigSpec.DoubleValue FOOTING_DRAIN_FRACTION_PER_SECOND;
    public static final ModConfigSpec.DoubleValue FOOTING_HOHO_DRAIN_REDUCTION_PER_LEVEL;

    public static final ModConfigSpec.DoubleValue SHIHAKUSHO_SET_REGEN_BONUS;

    public static final ModConfigSpec.BooleanValue GARGANTA_ARRIVALS;
    public static final ModConfigSpec.DoubleValue ARRIVAL_CHANCE_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue MAX_ARRIVAL_CHANCE_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue ARRIVAL_NIGHT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARRIVAL_PLUS_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARRIVAL_RELEASED_MULTIPLIER;
    public static final ModConfigSpec.IntValue ARRIVAL_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue ARRIVAL_GRACE_SECONDS;
    public static final ModConfigSpec.IntValue MAX_NEARBY_HOLLOWS;
    public static final ModConfigSpec.IntValue ACADEMY_SAFE_RADIUS;

    public static final ModConfigSpec.DoubleValue HOLLOW_HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HOLLOW_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MASK_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HOLLOW_REGEN_PER_SECOND;
    public static final ModConfigSpec.IntValue MASK_REGEN_PAUSE_SECONDS;
    public static final ModConfigSpec.BooleanValue HUNT_UNAWARE;
    public static final ModConfigSpec.BooleanValue HUNT_HOLLOW_PLAYERS;
    public static final ModConfigSpec.IntValue HOLLOW_LEAVE_AFTER_MINUTES;
    public static final ModConfigSpec.IntValue HOLLOW_HIT_STAT_XP;
    public static final ModConfigSpec.IntValue MASK_HIT_STAT_XP_BONUS;
    public static final ModConfigSpec.IntValue PURIFICATION_ZANJUTSU_XP;

    public static final ModConfigSpec.BooleanValue FIRST_HOLLOW;
    public static final ModConfigSpec.IntValue FIRST_HOLLOW_DELAY_MINUTES;
    public static final ModConfigSpec.IntValue FIRST_HOLLOW_RETRY_MINUTES;
    public static final ModConfigSpec.BooleanValue FIRST_HOLLOW_WOUNDED_SHINIGAMI;

    public static final ModConfigSpec.BooleanValue AMBIENT_PLUSES;
    public static final ModConfigSpec.DoubleValue PLUS_CHANCE_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue PLUS_VILLAGE_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_NEARBY_PLUSES;
    public static final ModConfigSpec.IntValue PLUS_CHAIN_MINUTES;
    public static final ModConfigSpec.BooleanValue VILLAGER_PLUSES;
    public static final ModConfigSpec.IntValue VILLAGER_PLUS_CAP;

    public static final ModConfigSpec.IntValue DENREISHINKI_RANGE;

    public static final ModConfigSpec.DoubleValue BOND_MULTIPLIER;
    public static final ModConfigSpec.IntValue BOND_PER_MINUTE_CARRIED;
    public static final ModConfigSpec.IntValue BOND_PER_MEDITATION;
    public static final ModConfigSpec.IntValue BOND_PER_JINZEN;
    public static final ModConfigSpec.IntValue JINZEN_BOND_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue BOND_PER_PURIFIED_REIATSU;
    public static final ModConfigSpec.IntValue BOND_PURIFICATION_CAP;
    public static final ModConfigSpec.IntValue BOND_PER_CLOSE_CALL;
    public static final ModConfigSpec.DoubleValue SUBSTITUTE_CLOSE_CALL_MULTIPLIER;
    public static final ModConfigSpec.IntValue BOND_PER_SLEEP;
    public static final ModConfigSpec.IntValue SLEEP_BOND_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue SUBSTITUTE_CONSTANT_LEAN;
    public static final ModConfigSpec.DoubleValue NAME_MIN_REIRYOKU;
    public static final ModConfigSpec.DoubleValue NAME_GROWTH;
    public static final ModConfigSpec.IntValue JINZEN_RETRY_SECONDS;
    public static final ModConfigSpec.BooleanValue KEEP_ON_DEATH;

    public static final ModConfigSpec.DoubleValue SHIKAI_DRAIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DRAIN_REDUCTION_PER_REIATSU_CONTROL_LEVEL;
    public static final ModConfigSpec.DoubleValue MIN_DRAIN_FRACTION;
    public static final ModConfigSpec.DoubleValue CONSTANT_RELEASE_ARRIVAL_MULTIPLIER;
    public static final ModConfigSpec.IntValue LASH_RETRACT_TICKS;

    public static final ModConfigSpec.DoubleValue GETSUGA_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue GETSUGA_DAMAGE_PER_ROOT_REIRYOKU;
    public static final ModConfigSpec.DoubleValue GETSUGA_MAX_DAMAGE;
    public static final ModConfigSpec.DoubleValue GETSUGA_UNNAMED_MULTIPLIER;
    public static final ModConfigSpec.IntValue GETSUGA_NAMED_AFTER;
    public static final ModConfigSpec.DoubleValue GETSUGA_MIN_COST;
    public static final ModConfigSpec.DoubleValue GETSUGA_TAP_COST_FRACTION;
    public static final ModConfigSpec.DoubleValue GETSUGA_FULL_COST_FRACTION;
    public static final ModConfigSpec.IntValue GETSUGA_FULL_CHARGE_TICKS;
    public static final ModConfigSpec.IntValue GETSUGA_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue GETSUGA_GATHER_FADE_TICKS;
    public static final ModConfigSpec.DoubleValue GETSUGA_RANGE;
    public static final ModConfigSpec.BooleanValue GETSUGA_BREAKS_BLOCKS;
    public static final ModConfigSpec.DoubleValue GETSUGA_MAX_HARDNESS;
    public static final ModConfigSpec.IntValue GETSUGA_BLOCK_BUDGET;

    public static final ModConfigSpec.EnumValue<BloodlineMode> QUINCY_BLOODLINE_MODE;
    public static final ModConfigSpec.DoubleValue QUINCY_CHANCE;
    public static final ModConfigSpec.DoubleValue QUINCY_ECHT_CHANCE;
    public static final ModConfigSpec.BooleanValue QUINCY_GIVE_CROSS;
    public static final ModConfigSpec.BooleanValue QUINCY_ALLOW_HYBRIDS;
    public static final ModConfigSpec.BooleanValue QUINCY_DEATH_INTO_PLUS;
    public static final ModConfigSpec.DoubleValue QUINCY_SENSE_RANGE;
    public static final ModConfigSpec.IntValue QUINCY_TIER_1_TRAINING;
    public static final ModConfigSpec.IntValue QUINCY_TIER_2_TRAINING;
    public static final ModConfigSpec.IntValue QUINCY_TIER_3_TRAINING;
    public static final ModConfigSpec.DoubleValue QUINCY_TIER_UP_GROWTH;
    public static final ModConfigSpec.DoubleValue REISHI_BASE_MAX;
    public static final ModConfigSpec.DoubleValue REISHI_MAX_PER_TIER;
    public static final ModConfigSpec.DoubleValue REISHI_PASSIVE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue REISHI_CHANNEL_PER_SECOND;
    public static final ModConfigSpec.DoubleValue QUINCY_GARB_GATHER_BONUS;
    public static final ModConfigSpec.DoubleValue BOGEN_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue BOGEN_DAMAGE_PER_TIER;
    public static final ModConfigSpec.DoubleValue BOGEN_DAMAGE_PER_ROOT_REIRYOKU;
    public static final ModConfigSpec.DoubleValue BOGEN_MAX_DAMAGE;
    public static final ModConfigSpec.DoubleValue BOGEN_HOLLOW_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BOGEN_BASE_COST;
    public static final ModConfigSpec.DoubleValue BOGEN_FULL_DRAW_EXTRA_COST;
    public static final ModConfigSpec.DoubleValue BOGEN_REIRYOKU_FALLBACK_RATE;
    public static final ModConfigSpec.IntValue PFEIL_LIFE_TICKS;
    public static final ModConfigSpec.IntValue HIRENKYAKU_MIN_TIER;
    public static final ModConfigSpec.DoubleValue HIRENKYAKU_BASE_DISTANCE;
    public static final ModConfigSpec.DoubleValue HIRENKYAKU_MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue HIRENKYAKU_COST;
    public static final ModConfigSpec.DoubleValue HIRENKYAKU_MASTER_COST;
    public static final ModConfigSpec.IntValue HIRENKYAKU_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue HIRENKYAKU_MASTER_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue QUINCY_FOOTING_REISHI_PER_SECOND;
    public static final ModConfigSpec.DoubleValue VENE_REDUCTION;
    public static final ModConfigSpec.DoubleValue VENE_MAX_REDUCTION;
    public static final ModConfigSpec.DoubleValue ARTERIE_BONUS;
    public static final ModConfigSpec.DoubleValue ARTERIE_MAX_BONUS;
    public static final ModConfigSpec.DoubleValue VENE_DRAIN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue ARTERIE_DRAIN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue ARTERIE_REIATSU_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue HOLLOW_POISON_ENABLED;
    public static final ModConfigSpec.DoubleValue HOLLOW_POISON_PER_DAMAGE;
    public static final ModConfigSpec.DoubleValue HOLLOW_POISON_DECAY_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue HOLLOW_POISON_AURA_PER_SECOND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("reiryoku");
        STARTING_REIRYOKU = builder
                .comment("Maximum reiryoku a new player starts with.")
                .defineInRange("starting_reiryoku", 40.0, 0.0, 1_000_000.0);
        AWARENESS_THRESHOLD = builder
                .comment("Maximum reiryoku a living Human needs to be spiritually aware.")
                .defineInRange("awareness_threshold", 30.0, 0.0, 1_000_000.0);
        MAX_REIRYOKU = builder
                .comment("Hard cap on maximum reiryoku.")
                .defineInRange("max_reiryoku", 10_000.0, 1.0, 1_000_000.0);
        REGEN_FRACTION_PER_SECOND = builder
                .comment("Share of maximum reiryoku regained each second.")
                .defineInRange("regen_fraction_per_second", 0.02, 0.0, 1.0);
        REST_REGEN_MULTIPLIER = builder
                .comment("Regeneration multiplier while meditating (crouching still) or sleeping.")
                .defineInRange("rest_regen_multiplier", 2.0, 1.0, 100.0);
        RESPAWN_REIRYOKU_FRACTION = builder
                .comment("Share of maximum reiryoku a player has after respawning.")
                .defineInRange("respawn_reiryoku_fraction", 0.5, 0.0, 1.0);
        SHIMMER_FRACTION = builder
                .comment("Share of the awareness threshold at which an unaware Human starts to sense spirits: a faint shimmer and distant screams. 1 turns it off.")
                .defineInRange("shimmer_fraction", 0.67, 0.0, 1.0);
        builder.pop();

        builder.push("growth");
        MUNDANE_KILL_GROWTH_PER_MAX_HEALTH = builder
                .comment("Maximum reiryoku gained per point of max health of a hostile creature that is not a spirit.")
                .defineInRange("mundane_kill_growth_per_max_health", 0.0, 0.0, 100.0);
        SPIRIT_KILL_GROWTH_PER_REIATSU = builder
                .comment("Maximum reiryoku gained per point of a slain Hollow's reiatsu, down to a tenth against one far weaker than you.")
                .defineInRange("spirit_kill_growth_per_reiatsu", 0.025, 0.0, 100.0);
        PURIFICATION_GROWTH_MULTIPLIER = builder
                .comment("Extra growth for purifying a Hollow with a zanpakutō.")
                .defineInRange("purification_growth_multiplier", 1.5, 1.0, 100.0);
        PLAYER_KILL_GROWTH_COOLDOWN_MINUTES = builder
                .comment("Minutes before killing another Hollow or Arrancar player can grow your reiryoku again.")
                .defineInRange("player_kill_growth_cooldown_minutes", 10, 0, 1_440);
        CLOSE_CALL_GROWTH = builder
                .comment("Maximum reiryoku gained for surviving a hit that leaves you below 30% health.")
                .defineInRange("close_call_growth", 0.25, 0.0, 1_000.0);
        CLOSE_CALL_COOLDOWN_SECONDS = builder
                .comment("Seconds before another close call can grow your reiryoku.")
                .defineInRange("close_call_cooldown_seconds", 30, 0, 86_400);
        LEVEL_UP_GROWTH = builder
                .comment("Maximum reiryoku gained each time a stat levels up.")
                .defineInRange("level_up_growth", 1.0, 0.0, 1_000.0);
        builder.pop();

        builder.push("stats");
        MAX_STAT_LEVEL = builder
                .comment("Highest level a Zankensoki stat or Reiatsu Control can reach.")
                .defineInRange("max_level", 50, 1, 1_000);
        ZANJUTSU_DAMAGE_PER_LEVEL = builder
                .comment("Extra sword damage per Zanjutsu level, as a share of the hit (0.03 = +3%).")
                .defineInRange("zanjutsu_damage_per_level", 0.03, 0.0, 10.0);
        HAKUDA_DAMAGE_PER_LEVEL = builder
                .comment("Extra unarmed damage per Hakuda level, in half-hearts.")
                .defineInRange("hakuda_damage_per_level", 0.25, 0.0, 100.0);
        HOHO_SPEED_PER_LEVEL = builder
                .comment("Extra movement speed per Hohō level, as a share of base speed (0.01 = +1%).")
                .defineInRange("hoho_speed_per_level", 0.01, 0.0, 1.0);
        REIATSU_CONTROL_REGEN_PER_LEVEL = builder
                .comment("Extra reiryoku regeneration per Reiatsu Control level (0.02 = +2%).")
                .defineInRange("reiatsu_control_regen_per_level", 0.02, 0.0, 10.0);
        builder.pop();

        builder.push("death");
        DEATH_INTO_PLUS = builder
                .comment("Whether a spiritually aware Human who dies comes back as a Plus at the place they died.")
                .define("death_into_plus", true);
        ALWAYS_BECOME_PLUS = builder
                .comment("Whether every Human who dies becomes a Plus, aware or not.")
                .define("always_become_plus", false);
        PLUS_DEATH_CHAIN_COST_SECONDS = builder
                .comment("Seconds of Chain of Fate a Plus loses each time it dies.")
                .defineInRange("plus_death_chain_cost_seconds", 300, 0, 86_400);
        builder.pop();

        builder.push("chain_of_fate");
        CHAIN_OF_FATE_MINUTES = builder
                .comment("How long a new Plus's Chain of Fate lasts, in minutes of play.")
                .defineInRange("chain_of_fate_minutes", 60, 1, 10_080);
        LOW_HEALTH_EROSION_MULTIPLIER = builder
                .comment("How much faster the chain erodes while the Plus is below 30% health.")
                .defineInRange("low_health_erosion_multiplier", 2.0, 1.0, 100.0);
        NEAR_SHINIGAMI_EROSION_MULTIPLIER = builder
                .comment("Erosion speed while a Shinigami is within 16 blocks (0.5 = half speed).")
                .defineInRange("near_shinigami_erosion_multiplier", 0.5, 0.0, 1.0);
        ENCROACHMENT_FRACTION = builder
                .comment("Share of the chain left when Encroachment starts: the links eat each other, causing pain and brief blindness.")
                .defineInRange("encroachment_fraction", 0.25, 0.0, 1.0);
        HOLLOWFICATION = builder
                .comment("Whether a Plus whose chain runs out becomes a Hollow. If false the chain stops at its last link.")
                .define("hollowfication", true);
        HOLLOWFICATION_GROWTH = builder
                .comment("Maximum reiryoku gained on becoming a Hollow.")
                .defineInRange("hollowfication_growth", 50.0, 0.0, 100_000.0);
        HOLLOW_BITE_CHAIN_COST_SECONDS = builder
                .comment("Seconds of chain a Hollow's bite takes from a Plus, never below a minute.")
                .defineInRange("hollow_bite_chain_cost_seconds", 30, 0, 3_600);
        builder.pop();

        builder.push("reiatsu");
        REIATSU_DIFFERENCE_RATIO = builder
                .comment("How many times stronger a soul's reiatsu must be than its attacker's before hits are weakened.")
                .defineInRange("difference_ratio", 3.0, 1.0, 1_000.0);
        REIATSU_DIFFERENCE_MIN_DAMAGE = builder
                .comment("The smallest share of damage a much weaker attacker still deals.")
                .defineInRange("difference_min_damage", 0.1, 0.0, 1.0);
        PRESSURE_DRAIN_FRACTION_PER_SECOND = builder
                .comment("Share of maximum reiryoku the pressure aura drains each second.")
                .defineInRange("pressure_drain_fraction_per_second", 0.05, 0.0, 1.0);
        PRESSURE_MIN_RATIO = builder
                .comment("How many times stronger you must be for your pressure aura to affect someone.")
                .defineInRange("pressure_min_ratio", 2.0, 1.0, 1_000.0);
        PRESSURE_BASE_RADIUS = builder
                .comment("Pressure aura radius in blocks before reiatsu is added.")
                .defineInRange("pressure_base_radius", 6.0, 1.0, 64.0);
        PRESSURE_MAX_RADIUS = builder
                .comment("Largest pressure aura radius in blocks.")
                .defineInRange("pressure_max_radius", 24.0, 1.0, 128.0);
        builder.pop();

        builder.push("shinigami");
        AWAKENING_GROWTH = builder
                .comment("Maximum reiryoku gained on graduating from the Academy or receiving a Shinigami's power.")
                .defineInRange("awakening_growth", 25.0, 0.0, 10_000.0);
        builder.pop();

        builder.push("academy");
        GRADUATION_ZANJUTSU_LEVEL = builder
                .comment("Zanjutsu level a student needs to graduate.")
                .defineInRange("graduation_zanjutsu_level", 5, 0, 100);
        GRADUATION_HAKUDA_LEVEL = builder
                .comment("Hakuda level a student needs to graduate.")
                .defineInRange("graduation_hakuda_level", 5, 0, 100);
        GRADUATION_HOHO_LEVEL = builder
                .comment("Hohō level a student needs to graduate.")
                .defineInRange("graduation_hoho_level", 5, 0, 100);
        GRADUATION_KIDO_LEVEL = builder
                .comment("Kidō level a student needs to graduate. Until kidō exists this is checked against Reiatsu Control, which kidō rests on.")
                .defineInRange("graduation_kido_level", 2, 0, 100);
        ASAUCHI_REISSUE_MINUTES = builder
                .comment("Minutes before the Academy hands the same soul another Asauchi.")
                .defineInRange("asauchi_reissue_minutes", 10, 0, 1_440);
        ACADEMY_LOCATE_RADIUS_CHUNKS = builder
                .comment("How far, in chunks, to search for an Academy building. Beyond this, one is built near world spawn if 'guarantee' is on. 0 never searches.")
                .defineInRange("locate_radius_chunks", 64, 0, 256);
        ACADEMY_GUARANTEE = builder
                .comment("Whether an Academy is built near world spawn when none is within reach.")
                .define("guarantee", true);
        builder.pop();

        builder.push("konso");
        KONSO_GROWTH = builder
                .comment("Maximum reiryoku a Shinigami gains for each Konsō they perform.")
                .defineInRange("growth", 3.0, 0.0, 1_000.0);
        KONSO_REWARDED_PER_DAY = builder
                .comment("How many Konsō a day grow a Shinigami's reiryoku.")
                .defineInRange("rewarded_per_day", 10, 0, 1_000);
        KONSO_SENDS_TO_ACADEMY = builder
                .comment("Whether a player sent on by Konsō arrives at the nearest Academy, standing in for the Rukongai.")
                .define("sends_to_academy", true);
        builder.pop();

        builder.push("substitute");
        TRANSFER_FRACTION = builder
                .comment("Share of a Shinigami player's maximum reiryoku that flows out when they give their power to a Human.")
                .defineInRange("transfer_fraction", 0.5, 0.0, 1.0);
        GIVER_RECOVERY_MINUTES = builder
                .comment("Minutes before a Shinigami who gave their power away gets it back.")
                .defineInRange("giver_recovery_minutes", 20, 0, 1_440);
        LENT_REGEN_MULTIPLIER = builder
                .comment("Reiryoku regeneration multiplier for a Shinigami whose power has not come back yet.")
                .defineInRange("lent_regen_multiplier", 0.5, 0.0, 1.0);
        WOUNDED_ENCOUNTERS = builder
                .comment("Whether a wounded Shinigami sometimes appears at night near a spiritually aware Human.")
                .define("wounded_encounters", true);
        ENCOUNTER_MIN_REIRYOKU = builder
                .comment("Maximum reiryoku a Human needs before a wounded Shinigami can find them.")
                .defineInRange("encounter_min_reiryoku", 60.0, 0.0, 10_000.0);
        ENCOUNTER_CHANCE_PER_MINUTE = builder
                .comment("Chance each minute at night that a wounded Shinigami appears near such a Human.")
                .defineInRange("encounter_chance_per_minute", 0.1, 0.0, 1.0);
        ENCOUNTER_COOLDOWN_MINUTES = builder
                .comment("Minutes before the same Human can meet another wounded Shinigami.")
                .defineInRange("encounter_cooldown_minutes", 60, 0, 10_000);
        builder.pop();

        builder.push("shunpo");
        SHUNPO_MIN_HOHO_LEVEL = builder
                .comment("Hohō level needed to flash step.")
                .defineInRange("min_hoho_level", 5, 0, 100);
        SHUNPO_BASE_DISTANCE = builder
                .comment("Flash step distance in blocks before Hohō is added.")
                .defineInRange("base_distance", 6.0, 1.0, 64.0);
        SHUNPO_DISTANCE_PER_HOHO_LEVEL = builder
                .comment("Extra flash step distance in blocks per Hohō level.")
                .defineInRange("distance_per_hoho_level", 0.2, 0.0, 4.0);
        SHUNPO_MAX_DISTANCE = builder
                .comment("Longest flash step in blocks.")
                .defineInRange("max_distance", 16.0, 1.0, 64.0);
        SHUNPO_COST_FRACTION = builder
                .comment("Share of maximum reiryoku a flash step costs at the lowest Hohō level that can use it.")
                .defineInRange("cost_fraction", 0.05, 0.0, 1.0);
        SHUNPO_MASTER_COST_FRACTION = builder
                .comment("Share of maximum reiryoku a flash step costs at the mastery Hohō level.")
                .defineInRange("master_cost_fraction", 0.025, 0.0, 1.0);
        SHUNPO_COOLDOWN_TICKS = builder
                .comment("Ticks between flash steps at the lowest Hohō level that can use it.")
                .defineInRange("cooldown_ticks", 30, 5, 200);
        SHUNPO_MASTER_COOLDOWN_TICKS = builder
                .comment("Ticks between flash steps at the mastery Hohō level.")
                .defineInRange("master_cooldown_ticks", 10, 5, 200);
        SHUNPO_MASTERY_HOHO_LEVEL = builder
                .comment("Hohō level at which flash steps are cheapest and quickest.")
                .defineInRange("mastery_hoho_level", 50, 1, 100);
        SHUNPO_HOHO_XP = builder
                .comment("Hohō XP each flash step trains.")
                .defineInRange("hoho_xp", 2, 0, 100);
        builder.pop();

        builder.push("reishi_footing");
        FOOTING_MIN_HOHO_LEVEL = builder
                .comment("Hohō level needed to stand on hardened reishi in mid-air.")
                .defineInRange("min_hoho_level", 3, 0, 100);
        FOOTING_DRAIN_FRACTION_PER_SECOND = builder
                .comment("Share of maximum reiryoku reishi footing drains each second, before Hohō lowers it.")
                .defineInRange("drain_fraction_per_second", 0.015, 0.0, 1.0);
        FOOTING_HOHO_DRAIN_REDUCTION_PER_LEVEL = builder
                .comment("How much each Hohō level lowers the footing drain (0.015 = 1.5% less). It never falls below a quarter.")
                .defineInRange("hoho_drain_reduction_per_level", 0.015, 0.0, 0.1);
        builder.pop();

        builder.push("shihakusho");
        SHIHAKUSHO_SET_REGEN_BONUS = builder
                .comment("Extra reiryoku regeneration from wearing the full Shihakushō, for Shinigami and Academy students (0.1 = +10%).")
                .defineInRange("set_regen_bonus", 0.1, 0.0, 10.0);
        builder.pop();

        builder.push("garganta");
        GARGANTA_ARRIVALS = builder
                .comment("Whether Hollows come through Garganta near spiritually aware souls.")
                .define("arrivals", true);
        ARRIVAL_CHANCE_PER_MINUTE = builder
                .comment("Chance a minute at 40 reiatsu; grows with the square root of reiatsu.")
                .defineInRange("chance_per_minute", 0.06, 0.0, 1.0);
        MAX_ARRIVAL_CHANCE_PER_MINUTE = builder
                .comment("Highest chance a minute, however strong the soul, before the multipliers below.")
                .defineInRange("max_chance_per_minute", 0.4, 0.0, 1.0);
        ARRIVAL_NIGHT_MULTIPLIER = builder
                .comment("How much likelier an arrival is at night.")
                .defineInRange("night_multiplier", 1.5, 0.0, 10.0);
        ARRIVAL_PLUS_MULTIPLIER = builder
                .comment("How much likelier an arrival is for a Plus.")
                .defineInRange("plus_multiplier", 1.5, 0.0, 10.0);
        ARRIVAL_RELEASED_MULTIPLIER = builder
                .comment("How much likelier an arrival is while reiatsu is released.")
                .defineInRange("released_multiplier", 2.0, 0.0, 10.0);
        ARRIVAL_COOLDOWN_SECONDS = builder
                .comment("Seconds that pass at least between two arrivals near the same soul.")
                .defineInRange("cooldown_seconds", 120, 0, 86_400);
        ARRIVAL_GRACE_SECONDS = builder
                .comment("Seconds without arrivals after joining, respawning or changing dimension.")
                .defineInRange("grace_seconds", 60, 0, 3_600);
        MAX_NEARBY_HOLLOWS = builder
                .comment("No arrivals while this many Hollows are within 64 blocks.")
                .defineInRange("max_nearby_hollows", 4, 1, 32);
        ACADEMY_SAFE_RADIUS = builder
                .comment("No tears this close to an Academy instructor, in blocks.")
                .defineInRange("academy_safe_radius", 32, 0, 256);
        builder.pop();

        builder.push("hollows");
        HOLLOW_HEALTH_MULTIPLIER = builder
                .comment("Multiplier on every Hollow's health.")
                .defineInRange("health_multiplier", 1.0, 0.1, 100.0);
        HOLLOW_DAMAGE_MULTIPLIER = builder
                .comment("Multiplier on every Hollow's attack damage.")
                .defineInRange("damage_multiplier", 1.0, 0.1, 100.0);
        MASK_DAMAGE_MULTIPLIER = builder
                .comment("Damage multiplier for a blow or arrow that strikes a Hollow's mask.")
                .defineInRange("mask_damage_multiplier", 2.0, 1.0, 10.0);
        HOLLOW_REGEN_PER_SECOND = builder
                .comment("Health a Hollow heals each second once nothing has hurt it for 5 seconds.")
                .defineInRange("regeneration_per_second", 0.5, 0.0, 100.0);
        MASK_REGEN_PAUSE_SECONDS = builder
                .comment("Seconds a Hollow cannot heal after a blow to its mask.")
                .defineInRange("mask_regeneration_pause_seconds", 10, 0, 600);
        HUNT_UNAWARE = builder
                .comment("Whether Hollows hunt players who cannot see them.")
                .define("hunt_unaware", false);
        HUNT_HOLLOW_PLAYERS = builder
                .comment("Whether Hollows hunt players who are Hollows or Arrancar themselves.")
                .define("hunt_hollow_players", false);
        HOLLOW_LEAVE_AFTER_MINUTES = builder
                .comment("Minutes a Hollow with no prey near an aware soul waits before it leaves through a tear. 0 means they never leave.")
                .defineInRange("leave_after_minutes", 2, 0, 1_440);
        HOLLOW_HIT_STAT_XP = builder
                .comment("Zanjutsu or Hakuda XP for a landed hit on a Hollow.")
                .defineInRange("hit_stat_xp", 2, 1, 100);
        MASK_HIT_STAT_XP_BONUS = builder
                .comment("Extra Zanjutsu or Hakuda XP when that hit strikes the mask.")
                .defineInRange("mask_hit_stat_xp_bonus", 1, 0, 100);
        PURIFICATION_ZANJUTSU_XP = builder
                .comment("Zanjutsu XP for purifying a Hollow.")
                .defineInRange("purification_zanjutsu_xp", 5, 0, 1_000);
        builder.pop();

        builder.push("first_hollow");
        FIRST_HOLLOW = builder
                .comment("Whether a soul's first Hollow is Fishbone D, who comes for it alone before any other Hollow does.")
                .define("enabled", true);
        FIRST_HOLLOW_DELAY_MINUTES = builder
                .comment("Minutes of play while aware before Fishbone D comes, on the first night after that.")
                .defineInRange("delay_minutes", 10, 0, 1_440);
        FIRST_HOLLOW_RETRY_MINUTES = builder
                .comment("Minutes before Fishbone D comes back after it retreats.")
                .defineInRange("retry_minutes", 10, 1, 1_440);
        FIRST_HOLLOW_WOUNDED_SHINIGAMI = builder
                .comment("Whether a wounded Shinigami stumbles in just ahead of Fishbone D for a Human who could take her power. Also needs substitute.wounded_encounters.")
                .define("wounded_shinigami", true);
        builder.pop();

        builder.push("pluses");
        AMBIENT_PLUSES = builder
                .comment("Whether Pluses sometimes wander near spiritually aware souls.")
                .define("ambient", true);
        PLUS_CHANCE_PER_MINUTE = builder
                .comment("Chance each minute that a Plus appears near such a soul.")
                .defineInRange("chance_per_minute", 0.1, 0.0, 1.0);
        PLUS_VILLAGE_MULTIPLIER = builder
                .comment("How much likelier a Plus is in a village.")
                .defineInRange("village_multiplier", 3.0, 0.0, 100.0);
        MAX_NEARBY_PLUSES = builder
                .comment("No more Pluses appear while this many are within 48 blocks.")
                .defineInRange("max_nearby", 3, 0, 32);
        PLUS_CHAIN_MINUTES = builder
                .comment("How long a Plus spirit's Chain of Fate lasts, in minutes of play.")
                .defineInRange("chain_minutes", 20, 1, 10_080);
        VILLAGER_PLUSES = builder
                .comment("Whether villagers who die leave a Plus.")
                .define("villager_pluses", true);
        VILLAGER_PLUS_CAP = builder
                .comment("A villager who dies leaves no Plus while this many Pluses are within 16 blocks.")
                .defineInRange("villager_cap", 8, 0, 64);
        builder.pop();

        builder.push("denreishinki");
        DENREISHINKI_RANGE = builder
                .comment("How far, in blocks, a Denreishinki hears tears open and Pluses turn Hollow, and finds the nearest Hollow.")
                .defineInRange("range", 96, 16, 512);
        builder.pop();

        builder.push("zanpakuto");
        BOND_MULTIPLIER = builder
                .comment("Multiplier on all Asauchi bond, from every source below. The bond is full at 1000.")
                .defineInRange("bond_multiplier", 1.0, 0.0, 100.0);
        BOND_PER_MINUTE_CARRIED = builder
                .comment("Bond gained for each minute a soul carries a nameless Asauchi.")
                .defineInRange("bond_per_minute_carried", 2, 0, 1_000);
        BOND_PER_MEDITATION = builder
                .comment("Bond gained each time meditating trains Reiatsu Control, while a nameless Asauchi is carried.")
                .defineInRange("bond_per_meditation", 1, 0, 1_000);
        BOND_PER_JINZEN = builder
                .comment("Bond gained for sitting in Jinzen with a nameless Asauchi.")
                .defineInRange("bond_per_jinzen", 25, 0, 1_000);
        JINZEN_BOND_COOLDOWN_SECONDS = builder
                .comment("Seconds before another Jinzen can grow the bond.")
                .defineInRange("jinzen_bond_cooldown_seconds", 300, 0, 86_400);
        BOND_PER_PURIFIED_REIATSU = builder
                .comment("Bond gained per point of a Hollow's reiatsu for purifying it with a nameless Asauchi.")
                .defineInRange("bond_per_purified_reiatsu", 0.5, 0.0, 100.0);
        BOND_PURIFICATION_CAP = builder
                .comment("Most bond a single purification can give.")
                .defineInRange("bond_purification_cap", 50, 0, 1_000);
        BOND_PER_CLOSE_CALL = builder
                .comment("Bond gained for surviving a close call that grows reiryoku, while a nameless Asauchi is carried.")
                .defineInRange("bond_per_close_call", 15, 0, 1_000);
        SUBSTITUTE_CLOSE_CALL_MULTIPLIER = builder
                .comment("Multiplier on the close-call bond for a Substitute Shinigami, whose power grows in a fight.")
                .defineInRange("substitute_close_call_multiplier", 2.0, 0.0, 10.0);
        BOND_PER_SLEEP = builder
                .comment("Bond gained for a full night's sleep with a nameless Asauchi.")
                .defineInRange("bond_per_sleep", 30, 0, 1_000);
        SLEEP_BOND_COOLDOWN_MINUTES = builder
                .comment("Minutes before another night's sleep can grow the bond.")
                .defineInRange("sleep_bond_cooldown_minutes", 20, 0, 1_440);
        SUBSTITUTE_CONSTANT_LEAN = builder
                .comment("Extra leaning towards a constant-release spirit, such as Zangetsu, for a Substitute Shinigami.")
                .defineInRange("substitute_constant_lean", 60, 0, 10_000);
        NAME_MIN_REIRYOKU = builder
                .comment("Maximum reiryoku a soul needs before its spirit will set its test and tell it its name.")
                .defineInRange("name_min_reiryoku", 100.0, 0.0, 100_000.0);
        NAME_GROWTH = builder
                .comment("Maximum reiryoku gained on learning the zanpakutō's name.")
                .defineInRange("name_growth", 10.0, 0.0, 1_000.0);
        JINZEN_RETRY_SECONDS = builder
                .comment("Seconds before a soul that failed its spirit's test can face it again.")
                .defineInRange("jinzen_retry_seconds", 180, 0, 86_400);
        KEEP_ON_DEATH = builder
                .comment("Whether a soul keeps its own named zanpakutō when it dies, instead of dropping it.")
                .define("keep_on_death", true);
        builder.pop();

        builder.push("shikai");
        SHIKAI_DRAIN_MULTIPLIER = builder
                .comment("Multiplier on the reiryoku every Shikai drains while it lasts. Constant releases never drain.")
                .defineInRange("drain_multiplier", 1.0, 0.0, 10.0);
        DRAIN_REDUCTION_PER_REIATSU_CONTROL_LEVEL = builder
                .comment("How much each Reiatsu Control level lowers the Shikai drain (0.02 = 2% less).")
                .defineInRange("drain_reduction_per_reiatsu_control_level", 0.02, 0.0, 1.0);
        MIN_DRAIN_FRACTION = builder
                .comment("The smallest share of its drain a Shikai keeps, however high Reiatsu Control goes.")
                .defineInRange("min_drain_fraction", 0.4, 0.0, 1.0);
        CONSTANT_RELEASE_ARRIVAL_MULTIPLIER = builder
                .comment("How much likelier a Hollow arrival is for a soul whose constant-release zanpakutō has been released, as it never seals.")
                .defineInRange("constant_release_arrival_multiplier", 1.25, 0.0, 10.0);
        LASH_RETRACT_TICKS = builder
                .comment("Ticks Zabimaru's reach stays short after a lash, while its segments draw back together.")
                .defineInRange("lash_retract_ticks", 20, 0, 200);
        builder.pop();

        builder.push("getsuga_tensho");
        GETSUGA_BASE_DAMAGE = builder
                .comment("Getsuga Tenshō damage before reiryoku is added.")
                .defineInRange("base_damage", 8.0, 0.0, 1_000.0);
        GETSUGA_DAMAGE_PER_ROOT_REIRYOKU = builder
                .comment("Extra damage per square root of maximum reiryoku.")
                .defineInRange("damage_per_root_reiryoku", 0.8, 0.0, 100.0);
        GETSUGA_MAX_DAMAGE = builder
                .comment("Highest damage, however strong the soul, before the charge scales it (from 60% ungathered to 120% fully gathered).")
                .defineInRange("max_damage", 60.0, 0.0, 10_000.0);
        GETSUGA_UNNAMED_MULTIPLIER = builder
                .comment("Damage multiplier until Zangetsu names the technique.")
                .defineInRange("unnamed_multiplier", 0.75, 0.0, 1.0);
        GETSUGA_NAMED_AFTER = builder
                .comment("How many Getsuga Tenshō a soul looses before Zangetsu names the technique.")
                .defineInRange("named_after", 10, 0, 10_000);
        GETSUGA_MIN_COST = builder
                .comment("The least reiryoku one costs.")
                .defineInRange("min_cost", 3.0, 0.0, 10_000.0);
        GETSUGA_TAP_COST_FRACTION = builder
                .comment("Share of maximum reiryoku one costs when loosed without gathering.")
                .defineInRange("tap_cost_fraction", 0.06, 0.0, 1.0);
        GETSUGA_FULL_COST_FRACTION = builder
                .comment("Share of maximum reiryoku one costs when fully gathered.")
                .defineInRange("full_cost_fraction", 0.16, 0.0, 1.0);
        GETSUGA_FULL_CHARGE_TICKS = builder
                .comment("Ticks the Technique key must be held to gather a full charge.")
                .defineInRange("full_charge_ticks", 30, 1, 200);
        GETSUGA_COOLDOWN_TICKS = builder
                .comment("Ticks before another can be gathered.")
                .defineInRange("cooldown_ticks", 20, 0, 1_200);
        GETSUGA_GATHER_FADE_TICKS = builder
                .comment("Ticks gathered reiatsu holds before it fades unused.")
                .defineInRange("gather_fade_ticks", 200, 20, 2_400);
        GETSUGA_RANGE = builder
                .comment("How far a Getsuga Tenshō flies, in blocks.")
                .defineInRange("range", 40.0, 4.0, 128.0);
        GETSUGA_BREAKS_BLOCKS = builder
                .comment("Whether it cuts through soft blocks on its way. Mob griefing, spawn protection and building rights still apply.")
                .define("breaks_blocks", true);
        GETSUGA_MAX_HARDNESS = builder
                .comment("The hardest block it cuts through (dirt is 0.5, stone 1.5). Blocks with a block entity, such as chests and signs, and blocks the Wither cannot break are never cut.")
                .defineInRange("max_hardness", 0.8, 0.0, 50.0);
        GETSUGA_BLOCK_BUDGET = builder
                .comment("Most blocks one can cut through.")
                .defineInRange("block_budget", 24, 0, 256);
        builder.pop();

        builder.push("quincy");
        QUINCY_BLOODLINE_MODE = builder
                .comment("How a new living Human without Shinigami power learns their bloodline: CHOICE asks them once, RANDOM rolls it for them as if they let fate decide, and OFF leaves it to commands.")
                .defineEnum("bloodline_mode", BloodlineMode.CHOICE);
        QUINCY_CHANCE = builder
                .comment("Chance of Quincy blood for a soul that lets fate decide, or that RANDOM rolls for.")
                .defineInRange("quincy_chance", 0.05, 0.0, 1.0);
        QUINCY_ECHT_CHANCE = builder
                .comment("Chance that Quincy blood is Echt, pure, rather than Gemischt, mixed.")
                .defineInRange("echt_chance", 0.25, 0.0, 1.0);
        QUINCY_GIVE_CROSS = builder
                .comment("Whether a soul that learns it has Quincy blood is handed a Quincy Cross.")
                .define("give_cross", true);
        QUINCY_ALLOW_HYBRIDS = builder
                .comment("Whether an awakened Quincy may still take a Shinigami's power or enroll at the Academy, and a Substitute Shinigami may still awaken as a Quincy.")
                .define("allow_hybrids", false);
        QUINCY_DEATH_INTO_PLUS = builder
                .comment("Whether an awakened Quincy who dies lingers as a Plus like any aware Human, instead of waking alive.")
                .define("death_into_plus", false);
        QUINCY_SENSE_RANGE = builder
                .comment("How far, in blocks, a Quincy senses a Garganta open and which way it lies.")
                .defineInRange("sense_range", 128.0, 0.0, 512.0);

        builder.push("training");
        QUINCY_TIER_1_TRAINING = builder
                .comment("Training a soul with Quincy blood needs to awaken, reaching the first Quincy tier.")
                .defineInRange("tier_1", 40, 1, 100_000);
        QUINCY_TIER_2_TRAINING = builder
                .comment("Training needed for the second tier: Hirenkyaku, reishi footing and arrows that pierce.")
                .defineInRange("tier_2", 150, 1, 100_000);
        QUINCY_TIER_3_TRAINING = builder
                .comment("Training needed for the third and highest tier.")
                .defineInRange("tier_3", 500, 1, 100_000);
        QUINCY_TIER_UP_GROWTH = builder
                .comment("Maximum reiryoku gained for each Quincy tier reached.")
                .defineInRange("tier_up_growth", 10.0, 0.0, 10_000.0);
        builder.pop();

        builder.push("reishi");
        REISHI_BASE_MAX = builder
                .comment("Reishi an awakened Quincy can hold before tiers and a tenth of maximum reiryoku are added.")
                .defineInRange("base_max", 30.0, 0.0, 10_000.0);
        REISHI_MAX_PER_TIER = builder
                .comment("Extra reishi a Quincy can hold for each tier.")
                .defineInRange("max_per_tier", 20.0, 0.0, 10_000.0);
        REISHI_PASSIVE_PER_SECOND = builder
                .comment("Reishi a Quincy gathers each second without trying, where reishi is neither rich nor thin.")
                .defineInRange("passive_per_second", 0.4, 0.0, 1_000.0);
        REISHI_CHANNEL_PER_SECOND = builder
                .comment("Reishi gathered each second while meditating with a Quincy Cross in hand, before Reiatsu Control adds 2% a level.")
                .defineInRange("channel_per_second", 3.0, 0.0, 1_000.0);
        QUINCY_GARB_GATHER_BONUS = builder
                .comment("Extra reishi gathering from wearing the full Quincy garb (0.15 = +15%).")
                .defineInRange("garb_bonus", 0.15, 0.0, 10.0);
        builder.pop();

        builder.push("heilig_bogen");
        BOGEN_BASE_DAMAGE = builder
                .comment("Heilig Pfeil damage at full draw before tiers, reiryoku and Reiatsu Control add to it.")
                .defineInRange("base_damage", 3.0, 0.0, 1_000.0);
        BOGEN_DAMAGE_PER_TIER = builder
                .comment("Extra full-draw damage for each Quincy tier.")
                .defineInRange("damage_per_tier", 2.0, 0.0, 1_000.0);
        BOGEN_DAMAGE_PER_ROOT_REIRYOKU = builder
                .comment("Extra full-draw damage per square root of maximum reiryoku.")
                .defineInRange("damage_per_root_reiryoku", 0.6, 0.0, 100.0);
        BOGEN_MAX_DAMAGE = builder
                .comment("Highest full-draw damage, however strong the soul. A quick tap does a quarter of the full draw.")
                .defineInRange("max_damage", 35.0, 0.0, 10_000.0);
        BOGEN_HOLLOW_DAMAGE_MULTIPLIER = builder
                .comment("Damage multiplier for a Heilig Pfeil that strikes a Hollow, or a Hollow or Arrancar player.")
                .defineInRange("hollow_damage_multiplier", 1.25, 0.0, 10.0);
        BOGEN_BASE_COST = builder
                .comment("Reishi the shortest draw costs to fire.")
                .defineInRange("base_cost", 1.0, 0.0, 1_000.0);
        BOGEN_FULL_DRAW_EXTRA_COST = builder
                .comment("Extra reishi a full draw costs on top of that.")
                .defineInRange("full_draw_extra_cost", 3.0, 0.0, 1_000.0);
        BOGEN_REIRYOKU_FALLBACK_RATE = builder
                .comment("Reiryoku burned for each point of reishi a shot is short. When that would leave too little reiryoku, or this is 0, the shot fires weaker instead.")
                .defineInRange("reiryoku_fallback_rate", 2.0, 0.0, 100.0);
        PFEIL_LIFE_TICKS = builder
                .comment("Ticks a Heilig Pfeil flies before it fades.")
                .defineInRange("life_ticks", 30, 1, 200);
        builder.pop();

        builder.push("hirenkyaku");
        HIRENKYAKU_MIN_TIER = builder
                .comment("Quincy tier needed for Hirenkyaku and for standing on reishi footing.")
                .defineInRange("min_tier", 2, 1, 3);
        HIRENKYAKU_BASE_DISTANCE = builder
                .comment("Hirenkyaku distance in blocks at that tier, before higher tiers and Reiatsu Control add to it.")
                .defineInRange("base_distance", 7.0, 1.0, 64.0);
        HIRENKYAKU_MAX_DISTANCE = builder
                .comment("Longest Hirenkyaku in blocks.")
                .defineInRange("max_distance", 16.0, 1.0, 64.0);
        HIRENKYAKU_COST = builder
                .comment("Reishi a Hirenkyaku costs below the third tier.")
                .defineInRange("cost", 3.0, 0.0, 1_000.0);
        HIRENKYAKU_MASTER_COST = builder
                .comment("Reishi a Hirenkyaku costs at the third tier.")
                .defineInRange("master_cost", 2.0, 0.0, 1_000.0);
        HIRENKYAKU_COOLDOWN_TICKS = builder
                .comment("Ticks after a Hirenkyaku before the next dash, Shunpo included, below the third tier.")
                .defineInRange("cooldown_ticks", 20, 0, 1_200);
        HIRENKYAKU_MASTER_COOLDOWN_TICKS = builder
                .comment("Ticks after a Hirenkyaku before the next dash at the third tier.")
                .defineInRange("master_cooldown_ticks", 12, 0, 1_200);
        QUINCY_FOOTING_REISHI_PER_SECOND = builder
                .comment("Reishi a Quincy's reishi footing drains each second, in place of the reiryoku a Shinigami pays.")
                .defineInRange("footing_reishi_per_second", 1.0, 0.0, 1_000.0);
        builder.pop();

        builder.push("blut");
        VENE_REDUCTION = builder
                .comment("Share of each hit Blut Vene stops at the first Quincy tier (0.2 = 20%), growing 5% a tier.")
                .defineInRange("vene_reduction", 0.2, 0.0, 1.0);
        VENE_MAX_REDUCTION = builder
                .comment("The largest share of a hit Blut Vene can stop.")
                .defineInRange("vene_max_reduction", 0.4, 0.0, 1.0);
        ARTERIE_BONUS = builder
                .comment("Extra damage Blut Arterie adds to each hit at the second Quincy tier (0.25 = +25%), growing 5% a tier.")
                .defineInRange("arterie_bonus", 0.25, 0.0, 10.0);
        ARTERIE_MAX_BONUS = builder
                .comment("The most extra damage Blut Arterie can add.")
                .defineInRange("arterie_max_bonus", 0.5, 0.0, 10.0);
        VENE_DRAIN_PER_SECOND = builder
                .comment("Reishi Blut Vene drains each second, besides half a reishi for each point of damage it stops.")
                .defineInRange("vene_drain_per_second", 0.8, 0.0, 1_000.0);
        ARTERIE_DRAIN_PER_SECOND = builder
                .comment("Reishi Blut Arterie drains each second.")
                .defineInRange("arterie_drain_per_second", 1.2, 0.0, 1_000.0);
        ARTERIE_REIATSU_MULTIPLIER = builder
                .comment("How many times over a Quincy's reiatsu counts in the reiatsu difference while Blut Arterie runs.")
                .defineInRange("arterie_reiatsu_multiplier", 1.5, 1.0, 10.0);
        builder.pop();

        builder.push("hollow_poison");
        HOLLOW_POISON_ENABLED = builder
                .comment("Whether Hollow reiatsu poisons awakened Quincy, up to 100, where the soul tears apart.")
                .define("enabled", true);
        HOLLOW_POISON_PER_DAMAGE = builder
                .comment("Poison a Quincy takes for each point of damage a Hollow deals them, mob or player, before difficulty, Blut Vene and their tier change it.")
                .defineInRange("per_damage", 1.0, 0.0, 100.0);
        HOLLOW_POISON_DECAY_PER_MINUTE = builder
                .comment("Poison that wears off each minute, once a minute has passed without more.")
                .defineInRange("decay_per_minute", 4.0, 0.0, 100.0);
        HOLLOW_POISON_AURA_PER_SECOND = builder
                .comment("Poison a Quincy takes each second inside the released reiatsu of a Hollow or Arrancar player.")
                .defineInRange("aura_per_second", 1.0, 0.0, 100.0);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    private BleachConfig() {
    }

    /** The configured value, or its default when no world is loaded (for example on the title screen). */
    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** The configured value, or its default when no world is loaded. */
    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** The configured value, or its default when no world is loaded. */
    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** The configured value, or its default when no world is loaded. */
    public static <T extends Enum<T>> T get(ModConfigSpec.EnumValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
