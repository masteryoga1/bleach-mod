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
