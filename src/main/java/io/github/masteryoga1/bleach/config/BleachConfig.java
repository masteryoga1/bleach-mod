package io.github.masteryoga1.bleach.config;

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

    public static final ModConfigSpec.DoubleValue KILL_GROWTH_PER_MAX_HEALTH;
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
        builder.pop();

        builder.push("growth");
        KILL_GROWTH_PER_MAX_HEALTH = builder
                .comment("Maximum reiryoku gained per point of max health of a hostile creature you kill.")
                .defineInRange("kill_growth_per_max_health", 0.02, 0.0, 100.0);
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
}
