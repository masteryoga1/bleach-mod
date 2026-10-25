package io.github.masteryoga1.bleach.config;

import io.github.masteryoga1.bleach.quincy.BloodlineMode;
import io.github.masteryoga1.bleach.soul.HollowTier;
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
    public static final ModConfigSpec.DoubleValue MASK_FRAGMENT_CHANCE;
    public static final ModConfigSpec.BooleanValue HUNT_UNAWARE;
    public static final ModConfigSpec.BooleanValue HUNT_HOLLOW_PLAYERS;
    public static final ModConfigSpec.IntValue HOLLOW_LEAVE_AFTER_MINUTES;
    public static final ModConfigSpec.IntValue HOLLOW_HIT_STAT_XP;
    public static final ModConfigSpec.IntValue MASK_HIT_STAT_XP_BONUS;
    public static final ModConfigSpec.IntValue PURIFICATION_ZANJUTSU_XP;

    // Hollow players
    public static final ModConfigSpec.DoubleValue SATIETY_START;
    public static final ModConfigSpec.DoubleValue SATIETY_DRAIN_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue SATIETY_PER_EXHAUSTION;
    public static final ModConfigSpec.DoubleValue SATIETY_RESPAWN_FLOOR;
    public static final ModConfigSpec.DoubleValue PEACEFUL_SATIETY_PER_SECOND;
    public static final ModConfigSpec.DoubleValue RAVENOUS_BELOW;
    public static final ModConfigSpec.DoubleValue RAVENOUS_REGEN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RAVENOUS_CLAW_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HUNGRY_REIRYOKU_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue STARVING_DAMAGE;
    public static final ModConfigSpec.IntValue STARVING_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue HOLLOW_REGEN_BASE;
    public static final ModConfigSpec.DoubleValue HOLLOW_REGEN_PER_100;
    public static final ModConfigSpec.DoubleValue HOLLOW_REGEN_MAX;
    public static final ModConfigSpec.DoubleValue REGEN_SATIETY_PER_HEALTH;
    public static final ModConfigSpec.DoubleValue CLAW_DAMAGE_BASE;
    public static final ModConfigSpec.DoubleValue CLAW_DAMAGE_PER_SQRT;
    public static final ModConfigSpec.DoubleValue CLAW_DAMAGE_MAX;
    public static final ModConfigSpec.DoubleValue CLAW_ATTACKS_PER_SECOND;
    public static final ModConfigSpec.DoubleValue HIDE_ARMOR_BASE;
    public static final ModConfigSpec.DoubleValue HIDE_ARMOR_PER_SQRT;
    public static final ModConfigSpec.DoubleValue HIDE_ARMOR_MAX;
    public static final ModConfigSpec.BooleanValue HOLLOW_WEARS_ARMOR;
    public static final ModConfigSpec.BooleanValue HOLLOW_NIGHT_SIGHT;
    public static final ModConfigSpec.DoubleValue HOLLOW_SAFE_FALL;
    public static final ModConfigSpec.BooleanValue VILLAGERS_TRADE_WITH_HOLLOWS;
    public static final ModConfigSpec.IntValue DEVOUR_HOLD_TICKS;
    public static final ModConfigSpec.IntValue DEVOUR_PLUS_HOLD_TICKS;
    public static final ModConfigSpec.IntValue DEVOUR_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue DEVOUR_HEALTH_FRACTION;
    public static final ModConfigSpec.DoubleValue DEVOUR_CRACKED_HEALTH_FRACTION;
    public static final ModConfigSpec.DoubleValue DEVOUR_REIATSU_RATIO;
    public static final ModConfigSpec.BooleanValue DEVOUR_PLAYERS;
    public static final ModConfigSpec.DoubleValue DEVOUR_HEAL_FRACTION;
    public static final ModConfigSpec.DoubleValue DEVOUR_REIRYOKU_FRACTION;
    public static final ModConfigSpec.DoubleValue DEVOUR_GROWTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PLUS_SATIETY;
    public static final ModConfigSpec.DoubleValue MEAL_SATIETY_BASE;
    public static final ModConfigSpec.DoubleValue MEAL_SATIETY_PER_REIATSU;
    public static final ModConfigSpec.DoubleValue MEAL_SATIETY_MAX;
    public static final ModConfigSpec.DoubleValue REIATSU_PER_SOUL;
    public static final ModConfigSpec.DoubleValue DEATH_SOUL_LOSS_FRACTION;
    public static final ModConfigSpec.DoubleValue GNAW_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HOLLOW_ARRIVAL_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue RIVALS_HUNT_DRAWER;
    public static final ModConfigSpec.IntValue SCENT_RANGE;
    public static final ModConfigSpec.IntValue SCENT_INTERVAL_SECONDS;
    public static final ModConfigSpec.DoubleValue SCENT_BELOW;
    public static final ModConfigSpec.BooleanValue PURIFICATION_FREES;
    public static final ModConfigSpec.BooleanValue INSTRUCTOR_PURIFIES;
    public static final ModConfigSpec.DoubleValue HOLLOW_PLAYER_MASK_DAMAGE_MULTIPLIER;

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
    public static final ModConfigSpec.IntValue SANREI_DRAW_TICKS;
    public static final ModConfigSpec.DoubleValue SANREI_HOLD_UPKEEP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SANREI_GATHER_MULTIPLIER;
    public static final ModConfigSpec.IntValue SANREI_POINTS_PER_DAY;
    public static final ModConfigSpec.IntValue SANREI_DAYS_TO_MASTER;
    public static final ModConfigSpec.DoubleValue SANREI_MASTERED_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SANREI_MASTERED_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue SANREI_FLANK_DAMAGE_FRACTION;
    public static final ModConfigSpec.DoubleValue SANREI_FLANK_COST;
    public static final ModConfigSpec.BooleanValue LETZT_ENABLED;
    public static final ModConfigSpec.IntValue LETZT_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue LETZT_POOL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_GATHER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_MIN_DENSITY;
    public static final ModConfigSpec.IntValue LETZT_DRAW_TICKS;
    public static final ModConfigSpec.DoubleValue LETZT_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue LETZT_SHOT_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_MOVEMENT_BONUS;
    public static final ModConfigSpec.DoubleValue LETZT_REIATSU_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_HIRENKYAKU_DISTANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_HIRENKYAKU_COST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_HIRENKYAKU_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_PFEIL_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LETZT_PFEIL_MAX_DAMAGE;
    public static final ModConfigSpec.IntValue LETZT_PFEIL_PIERCE;
    public static final ModConfigSpec.DoubleValue LETZT_PFEIL_SPEED;
    public static final ModConfigSpec.DoubleValue LETZT_PFEIL_COST;
    public static final ModConfigSpec.BooleanValue LETZT_PFEIL_BREAKS_BLOCKS;
    public static final ModConfigSpec.IntValue LETZT_PFEIL_CARVE_BLOCKS;
    public static final ModConfigSpec.DoubleValue LETZT_PFEIL_MAX_HARDNESS;
    public static final ModConfigSpec.IntValue RESTORE_SEALED_TICKS;
    public static final ModConfigSpec.IntValue RESTORE_ARROW_SEALED_TICKS;
    public static final ModConfigSpec.BooleanValue RESTORE_SOLO_RITE;
    public static final ModConfigSpec.IntValue RESTORE_RITE_TICKS;
    public static final ModConfigSpec.DoubleValue RESTORE_MAX_HEALTH_FRACTION;
    public static final ModConfigSpec.IntValue RESTORE_MAX_FOOD;
    public static final ModConfigSpec.DoubleValue SEALED_REIATSU_MULTIPLIER;
    public static final ModConfigSpec.IntValue GINREI_DRAW_TICKS;
    public static final ModConfigSpec.DoubleValue GINREI_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GINREI_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue GINREI_STREAM_COST;
    public static final ModConfigSpec.DoubleValue GINREI_STREAM_DAMAGE_FRACTION;
    public static final ModConfigSpec.DoubleValue LICHT_REGEN_COST;
    public static final ModConfigSpec.IntValue LICHT_REGEN_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue LICHT_REGEN_ARROWS;
    public static final ModConfigSpec.DoubleValue LICHT_REGEN_DAMAGE_FRACTION;
    public static final ModConfigSpec.DoubleValue SEELE_FORM_COST;
    public static final ModConfigSpec.DoubleValue SEELE_UPKEEP_PER_SECOND;
    public static final ModConfigSpec.DoubleValue SEELE_DAMAGE;
    public static final ModConfigSpec.DoubleValue SEELE_ATTACK_SPEED;
    public static final ModConfigSpec.DoubleValue SEELE_HOLLOW_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SEELE_ARMOR_IGNORED;
    public static final ModConfigSpec.DoubleValue SEELE_DRAIN_FRACTION;
    public static final ModConfigSpec.DoubleValue SEELE_DRAIN_MAX;
    public static final ModConfigSpec.DoubleValue SEELE_FIRED_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SEELE_FIRED_COST;
    public static final ModConfigSpec.IntValue SEELE_STUCK_DESPAWN_TICKS;
    public static final ModConfigSpec.DoubleValue GINTO_FILL_COST;
    public static final ModConfigSpec.IntValue GINTO_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue GINTO_RANGE;
    public static final ModConfigSpec.DoubleValue HEIZEN_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEIZEN_MAX_DAMAGE;
    public static final ModConfigSpec.IntValue GRITZ_MAX_TICKS;
    public static final ModConfigSpec.IntValue WOLKE_TICKS;
    public static final ModConfigSpec.DoubleValue WOLKE_RADIUS;
    public static final ModConfigSpec.DoubleValue SPRENGER_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SPRENGER_MAX_DAMAGE;
    public static final ModConfigSpec.IntValue VOLL_TRAINING_AFTER_RESTORE;
    public static final ModConfigSpec.DoubleValue VOLL_START_COST;
    public static final ModConfigSpec.DoubleValue VOLL_START_MIN_REISHI;
    public static final ModConfigSpec.IntValue VOLL_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue VOLL_POOL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue VOLL_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue VOLL_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue VOLL_REIATSU_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue VOLL_UPKEEP_PER_SECOND;
    public static final ModConfigSpec.DoubleValue VOLL_FLIGHT_UPKEEP_PER_SECOND;
    public static final ModConfigSpec.DoubleValue VOLL_HALO_BREAK_FRACTION;
    public static final ModConfigSpec.DoubleValue SKLAVEREI_RADIUS;
    public static final ModConfigSpec.DoubleValue SKLAVEREI_UPKEEP_PER_SECOND;
    public static final ModConfigSpec.DoubleValue SKLAVEREI_HOLLOW_DAMAGE;
    public static final ModConfigSpec.DoubleValue SKLAVEREI_PLAYER_DRAIN;
    public static final ModConfigSpec.DoubleValue SKLAVEREI_PROJECTILE_GAIN;
    public static final ModConfigSpec.DoubleValue SKLAVEREI_MAX_GAIN_PER_PULSE;
    public static final ModConfigSpec.DoubleValue SKLAVEREI_POISON_PER_HOLLOW;
    public static final ModConfigSpec.BooleanValue ELDER_VISITS;
    public static final ModConfigSpec.IntValue ELDER_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue ELDER_LESSON_TRAINING;
    public static final ModConfigSpec.IntValue ELDER_MAX_LESSONS;
    public static final ModConfigSpec.IntValue ELDER_RESTORE_AFTER_TICKS;
    public static final ModConfigSpec.DoubleValue ELDER_ARROW_DAMAGE;
    public static final ModConfigSpec.BooleanValue HUNTER_ENABLED;
    public static final ModConfigSpec.DoubleValue HUNTER_CHANCE;
    public static final ModConfigSpec.IntValue HUNTER_GUARANTEE_KILLS;
    public static final ModConfigSpec.IntValue HUNTER_COOLDOWN_MINUTES;
    public static final ModConfigSpec.DoubleValue HUNTER_ARROW_DAMAGE;
    public static final ModConfigSpec.DoubleValue HUNTER_IN_BAIT_CHANCE;
    public static final ModConfigSpec.BooleanValue HOLLOWS_HUNT_HUNTER;
    public static final ModConfigSpec.DoubleValue RANSOTENGAI_DRAIN_PERCENT;
    public static final ModConfigSpec.DoubleValue RANSOTENGAI_MIN_REIRYOKU;
    public static final ModConfigSpec.DoubleValue RANSOTENGAI_FAILING_HEALTH;
    public static final ModConfigSpec.IntValue RANSOTENGAI_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue RANSOTENGAI_COLLAPSE_SECONDS;
    public static final ModConfigSpec.IntValue RANSOTENGAI_TRIAL_SECONDS;
    public static final ModConfigSpec.IntValue RANSOTENGAI_TRIAL_HOLD_SECONDS;
    public static final ModConfigSpec.DoubleValue RANSOTENGAI_HYBRID_DRAIN;

    public static final ModConfigSpec.BooleanValue BAIT_ENABLED;
    public static final ModConfigSpec.IntValue BAIT_DURATION_MINUTES;
    public static final ModConfigSpec.IntValue BAIT_RADIUS;
    public static final ModConfigSpec.IntValue BAIT_MAX_SITES;
    public static final ModConfigSpec.IntValue BAIT_SITE_SPACING;
    public static final ModConfigSpec.IntValue BAIT_FINALE_ARRIVALS;
    public static final ModConfigSpec.IntValue BAIT_FIRST_WAVE_SECONDS;
    public static final ModConfigSpec.IntValue BAIT_INTERVAL_START_SECONDS;
    public static final ModConfigSpec.IntValue BAIT_INTERVAL_END_SECONDS;
    public static final ModConfigSpec.IntValue BAIT_MAX_ALIVE;
    public static final ModConfigSpec.IntValue BAIT_ALIVE_PER_SOUL;
    public static final ModConfigSpec.IntValue BAIT_ALIVE_CAP;
    public static final ModConfigSpec.DoubleValue BAIT_LURE_START;
    public static final ModConfigSpec.DoubleValue BAIT_LURE_GAIN;
    public static final ModConfigSpec.IntValue BAIT_FINALE_MIN_SECONDS;
    public static final ModConfigSpec.IntValue BAIT_FINALE_MAX_SECONDS;
    public static final ModConfigSpec.IntValue BAIT_ABANDON_MINUTES;
    public static final ModConfigSpec.IntValue CONTEST_MINUTES;

    public static final ModConfigSpec.DoubleValue MENOS_FINALE_GILLIAN_CHANCE;
    public static final ModConfigSpec.DoubleValue MENOS_ARRIVAL_SHARE;
    public static final ModConfigSpec.DoubleValue MENOS_GILLIAN_MIN_REIATSU;
    public static final ModConfigSpec.DoubleValue MENOS_ADJUCHAS_MIN_REIATSU;
    public static final ModConfigSpec.DoubleValue MENOS_ADJUCHAS_SHARE;
    public static final ModConfigSpec.IntValue MENOS_ADJUCHAS_PACK_SIZE;
    public static final ModConfigSpec.DoubleValue MENOS_ADJUCHAS_EXTRA_AT_REIATSU;
    public static final ModConfigSpec.IntValue MENOS_MAX_ALIVE;
    public static final ModConfigSpec.IntValue MENOS_SPACING;
    public static final ModConfigSpec.IntValue MENOS_LIFETIME_MINUTES;
    public static final ModConfigSpec.DoubleValue GILLIAN_RETREAT_HEALTH;
    public static final ModConfigSpec.IntValue GILLIAN_FEED_INTERVAL_SECONDS;
    public static final ModConfigSpec.DoubleValue GILLIAN_FEED_REACH;
    public static final ModConfigSpec.BooleanValue GILLIAN_TRAMPLES_LEAVES;
    public static final ModConfigSpec.DoubleValue ADJUCHAS_HUNGER_HEALTH;
    public static final ModConfigSpec.BooleanValue CERO_BREAKS_BLOCKS;
    public static final ModConfigSpec.DoubleValue CERO_MAX_HARDNESS;
    public static final ModConfigSpec.DoubleValue CERO_BLAST_SHARE;
    public static final ModConfigSpec.DoubleValue GILLIAN_CERO_DAMAGE;
    public static final ModConfigSpec.IntValue GILLIAN_CERO_CHARGE_TICKS;
    public static final ModConfigSpec.IntValue GILLIAN_CERO_AIM_LOCK_TICKS;
    public static final ModConfigSpec.DoubleValue GILLIAN_CERO_RANGE;
    public static final ModConfigSpec.DoubleValue GILLIAN_CERO_MIN_RANGE;
    public static final ModConfigSpec.DoubleValue GILLIAN_CERO_RADIUS;
    public static final ModConfigSpec.DoubleValue GILLIAN_CERO_BLAST_RADIUS;
    public static final ModConfigSpec.IntValue GILLIAN_CERO_MAX_BLOCKS;
    public static final ModConfigSpec.IntValue GILLIAN_CERO_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue GILLIAN_CERO_KNOCKBACK;
    public static final ModConfigSpec.DoubleValue ADJUCHAS_CERO_DAMAGE;
    public static final ModConfigSpec.IntValue ADJUCHAS_CERO_CHARGE_TICKS;
    public static final ModConfigSpec.IntValue ADJUCHAS_CERO_AIM_LOCK_TICKS;
    public static final ModConfigSpec.DoubleValue ADJUCHAS_CERO_RANGE;
    public static final ModConfigSpec.DoubleValue ADJUCHAS_CERO_MIN_RANGE;
    public static final ModConfigSpec.DoubleValue ADJUCHAS_CERO_RADIUS;
    public static final ModConfigSpec.DoubleValue ADJUCHAS_CERO_BLAST_RADIUS;
    public static final ModConfigSpec.IntValue ADJUCHAS_CERO_MAX_BLOCKS;
    public static final ModConfigSpec.IntValue ADJUCHAS_CERO_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue ADJUCHAS_CERO_KNOCKBACK;

    /** The numbers of one Menos form a Hollow player can rise to, each in its own section of {@code [hollow_evolution]}. */
    public record MenosForm(ModConfigSpec.DoubleValue scale, ModConfigSpec.DoubleValue healthBonus,
            ModConfigSpec.DoubleValue speedMultiplier, ModConfigSpec.DoubleValue clawMultiplier,
            ModConfigSpec.DoubleValue clawAttacksPerSecond, ModConfigSpec.DoubleValue hideBonus,
            ModConfigSpec.DoubleValue regenerationMultiplier, ModConfigSpec.DoubleValue stepBonus,
            ModConfigSpec.DoubleValue reiryokuGrowth, ModConfigSpec.DoubleValue ceroDamage,
            ModConfigSpec.IntValue ceroChargeTicks, ModConfigSpec.DoubleValue ceroRadius) {
    }

    public static final MenosForm GILLIAN_FORM;
    public static final MenosForm ADJUCHAS_FORM;
    public static final MenosForm VASTO_LORDE_FORM;
    public static final ModConfigSpec.IntValue GILLIAN_SOULS;
    public static final ModConfigSpec.IntValue GILLIAN_HOLLOWS;
    public static final ModConfigSpec.IntValue ADJUCHAS_HOLLOWS;
    public static final ModConfigSpec.IntValue ADJUCHAS_MENOS;
    public static final ModConfigSpec.IntValue VASTO_HOLLOWS;
    public static final ModConfigSpec.IntValue VASTO_ADJUCHAS;
    public static final ModConfigSpec.DoubleValue VASTO_DESTINED_SHARE;
    public static final ModConfigSpec.IntValue VASTO_MAX;
    public static final ModConfigSpec.EnumValue<HollowTier.Regression> ADJUCHAS_REGRESSION;
    public static final ModConfigSpec.IntValue REGRESS_MINUTES;
    public static final ModConfigSpec.BooleanValue GILLIAN_MINDLESS;
    public static final ModConfigSpec.DoubleValue MENOS_RIVAL_SHARE;
    public static final ModConfigSpec.DoubleValue CERO_MIN_CHARGE;
    public static final ModConfigSpec.DoubleValue CERO_COST_FRACTION;
    public static final ModConfigSpec.DoubleValue CERO_MIN_COST;
    public static final ModConfigSpec.IntValue CERO_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue CERO_DAMAGE_PER_ROOT_REIRYOKU;
    public static final ModConfigSpec.DoubleValue CERO_MAX_DAMAGE;
    public static final ModConfigSpec.DoubleValue CERO_RANGE;

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
        MASK_FRAGMENT_CHANCE = builder
                .comment("Chance that a player's blow which freshly cracks a Hollow's mask knocks a fragment loose, once in each Hollow's life. Fishbone D always gives one.")
                .defineInRange("mask_fragment_chance", 0.25, 0.0, 1.0);
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

        builder.push("hollow_player");
        SATIETY_START = builder
                .comment("Soul hunger a Hollow starts at, out of 100.")
                .defineInRange("satiety_start", 60.0, 0.0, 100.0);
        SATIETY_DRAIN_PER_MINUTE = builder
                .comment("Soul hunger a Hollow loses each minute, doing nothing.")
                .defineInRange("drain_per_minute", 2.0, 0.0, 100.0);
        SATIETY_PER_EXHAUSTION = builder
                .comment("Soul hunger lost per point of vanilla exhaustion (sprinting, jumping, fighting).")
                .defineInRange("satiety_per_exhaustion", 0.6, 0.0, 10.0);
        SATIETY_RESPAWN_FLOOR = builder
                .comment("A Hollow comes back from death with at least this much soul hunger.")
                .defineInRange("respawn_floor", 30.0, 0.0, 100.0);
        PEACEFUL_SATIETY_PER_SECOND = builder
                .comment("Soul hunger a Hollow regains each second on Peaceful.")
                .defineInRange("peaceful_refill_per_second", 1.0, 0.0, 100.0);
        RAVENOUS_BELOW = builder
                .comment("Below this soul hunger a Hollow is ravenous.")
                .defineInRange("ravenous_below", 30.0, 0.0, 100.0);
        RAVENOUS_REGEN_MULTIPLIER = builder
                .comment("Multiplier on a ravenous Hollow's regeneration.")
                .defineInRange("ravenous_regeneration_multiplier", 0.5, 0.0, 1.0);
        RAVENOUS_CLAW_MULTIPLIER = builder
                .comment("Multiplier on the claws of a ravenous or starving Hollow.")
                .defineInRange("ravenous_claw_multiplier", 1.2, 1.0, 5.0);
        HUNGRY_REIRYOKU_MULTIPLIER = builder
                .comment("Multiplier on reiryoku regeneration while ravenous or starving.")
                .defineInRange("hungry_reiryoku_multiplier", 0.5, 0.0, 1.0);
        STARVING_DAMAGE = builder
                .comment("Damage a starving Hollow takes each interval (with vanilla's floors by difficulty).")
                .defineInRange("starving_damage", 1.0, 0.0, 100.0);
        STARVING_INTERVAL_TICKS = builder
                .comment("Ticks between two starving hurts.")
                .defineInRange("starving_interval_ticks", 80, 1, 1_200);
        HOLLOW_REGEN_BASE = builder
                .comment("Health a Hollow player heals each second, at any reiryoku.")
                .defineInRange("regeneration_base", 1.0, 0.0, 100.0);
        HOLLOW_REGEN_PER_100 = builder
                .comment("Extra health a second for each 100 maximum reiryoku.")
                .defineInRange("regeneration_per_100_reiryoku", 0.25, 0.0, 100.0);
        HOLLOW_REGEN_MAX = builder
                .comment("Most health a Hollow player heals each second.")
                .defineInRange("regeneration_max", 4.0, 0.0, 100.0);
        REGEN_SATIETY_PER_HEALTH = builder
                .comment("Soul hunger each healed point of health costs.")
                .defineInRange("regeneration_satiety_per_health", 1.5, 0.0, 100.0);
        CLAW_DAMAGE_BASE = builder
                .comment("Bare-handed damage a Hollow's claws add.")
                .defineInRange("claw_damage_base", 3.0, 0.0, 100.0);
        CLAW_DAMAGE_PER_SQRT = builder
                .comment("Claw damage added per square root of maximum reiryoku.")
                .defineInRange("claw_damage_per_sqrt_reiryoku", 0.25, 0.0, 10.0);
        CLAW_DAMAGE_MAX = builder
                .comment("Most damage the claws add.")
                .defineInRange("claw_damage_max", 12.0, 0.0, 1_000.0);
        CLAW_ATTACKS_PER_SECOND = builder
                .comment("Bare-handed attack speed of a Hollow.")
                .defineInRange("claw_attacks_per_second", 2.0, 0.25, 20.0);
        HIDE_ARMOR_BASE = builder
                .comment("Armour a Hollow's hide gives.")
                .defineInRange("hide_armor_base", 4.0, 0.0, 30.0);
        HIDE_ARMOR_PER_SQRT = builder
                .comment("Hide armour added per square root of maximum reiryoku.")
                .defineInRange("hide_armor_per_sqrt_reiryoku", 0.4, 0.0, 10.0);
        HIDE_ARMOR_MAX = builder
                .comment("Most armour the hide gives.")
                .defineInRange("hide_armor_max", 16.0, 0.0, 30.0);
        HOLLOW_WEARS_ARMOR = builder
                .comment("Whether Hollow players can wear armour (elytra, carved pumpkins and skulls always can).")
                .define("wears_armor", false);
        HOLLOW_NIGHT_SIGHT = builder
                .comment("Whether Hollow players see in the dark (each can turn it off with Sneak + Devour).")
                .define("night_sight", true);
        HOLLOW_SAFE_FALL = builder
                .comment("Extra blocks a Hollow player can fall unharmed.")
                .defineInRange("safe_fall_extra", 3.0, 0.0, 64.0);
        VILLAGERS_TRADE_WITH_HOLLOWS = builder
                .comment("Whether villagers and wandering traders trade with Hollow players.")
                .define("villagers_trade", false);
        DEVOUR_HOLD_TICKS = builder
                .comment("Ticks the Devour key must be held on a Hollow or a player.")
                .defineInRange("devour_hold_ticks", 40, 1, 600);
        DEVOUR_PLUS_HOLD_TICKS = builder
                .comment("Ticks the Devour key must be held on a Plus.")
                .defineInRange("devour_plus_hold_ticks", 20, 1, 600);
        DEVOUR_COOLDOWN_TICKS = builder
                .comment("Ticks after a hold ends before the next can start.")
                .defineInRange("devour_cooldown_ticks", 20, 0, 1_200);
        DEVOUR_HEALTH_FRACTION = builder
                .comment("A Hollow or player can be devoured at or below this share of its health.")
                .defineInRange("devour_health_fraction", 0.3, 0.0, 1.0);
        DEVOUR_CRACKED_HEALTH_FRACTION = builder
                .comment("The same share for a Hollow whose mask is cracked.")
                .defineInRange("devour_cracked_health_fraction", 0.5, 0.0, 1.0);
        DEVOUR_REIATSU_RATIO = builder
                .comment("Prey with more than this many times the devourer's reiatsu cannot be devoured.")
                .defineInRange("devour_reiatsu_ratio", 3.0, 0.0, 100.0);
        DEVOUR_PLAYERS = builder
                .comment("Whether Hollow players can devour other players (PvP and team rules still apply).")
                .define("devour_players", true);
        DEVOUR_HEAL_FRACTION = builder
                .comment("Share of maximum health a meal heals.")
                .defineInRange("devour_heal_fraction", 0.25, 0.0, 1.0);
        DEVOUR_REIRYOKU_FRACTION = builder
                .comment("Share of maximum reiryoku a meal refills.")
                .defineInRange("devour_reiryoku_fraction", 0.2, 0.0, 1.0);
        DEVOUR_GROWTH_MULTIPLIER = builder
                .comment("Reiryoku growth from a meal, as a multiple of slaying the same soul.")
                .defineInRange("devour_growth_multiplier", 2.0, 0.0, 100.0);
        PLUS_SATIETY = builder
                .comment("Soul hunger a devoured Plus restores.")
                .defineInRange("plus_satiety", 30.0, 0.0, 100.0);
        MEAL_SATIETY_BASE = builder
                .comment("Soul hunger any other meal restores, before its reiatsu.")
                .defineInRange("meal_satiety_base", 15.0, 0.0, 100.0);
        MEAL_SATIETY_PER_REIATSU = builder
                .comment("Soul hunger restored per point of the prey's reiatsu.")
                .defineInRange("meal_satiety_per_reiatsu", 0.4, 0.0, 10.0);
        MEAL_SATIETY_MAX = builder
                .comment("Most soul hunger one meal restores.")
                .defineInRange("meal_satiety_max", 60.0, 0.0, 100.0);
        REIATSU_PER_SOUL = builder
                .comment("Prey reiatsu per soul taken in (at least one).")
                .defineInRange("reiatsu_per_soul", 10.0, 1.0, 1_000.0);
        DEATH_SOUL_LOSS_FRACTION = builder
                .comment("Share of its souls a Hollow loses when it dies (taken by whoever devoured it).")
                .defineInRange("death_soul_loss_fraction", 0.25, 0.0, 1.0);
        GNAW_MULTIPLIER = builder
                .comment("How much faster a Plus's chain erodes while it gnaws at it. 1 turns gnawing off.")
                .defineInRange("gnaw_multiplier", 40.0, 1.0, 1_000.0);
        HOLLOW_ARRIVAL_MULTIPLIER = builder
                .comment("Multiplier on the Garganta chance for Hollow players. 0 turns their arrivals off.")
                .defineInRange("arrival_multiplier", 1.0, 0.0, 10.0);
        RIVALS_HUNT_DRAWER = builder
                .comment("Whether Hollows that come for a Hollow player hunt it.")
                .define("rivals_hunt_drawer", true);
        SCENT_RANGE = builder
                .comment("Blocks a hungry Hollow can smell a soul from. 0 turns the scent off.")
                .defineInRange("scent_range", 48, 0, 256);
        SCENT_INTERVAL_SECONDS = builder
                .comment("Seconds between two scents.")
                .defineInRange("scent_interval_seconds", 15, 1, 600);
        SCENT_BELOW = builder
                .comment("A Hollow smells souls only below this soul hunger.")
                .defineInRange("scent_below", 50.0, 0.0, 100.0);
        PURIFICATION_FREES = builder
                .comment("Whether a Hollow player killed by a purifying blow comes back as a soul.")
                .define("purification_frees", true);
        INSTRUCTOR_PURIFIES = builder
                .comment("Whether the Academy's instructor offers to purify a Hollow player.")
                .define("instructor_purifies", true);
        HOLLOW_PLAYER_MASK_DAMAGE_MULTIPLIER = builder
                .comment("Damage multiplier for a blow that strikes a Hollow player's mask.")
                .defineInRange("mask_damage_multiplier", 1.5, 1.0, 10.0);
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
                .comment("Highest full-draw damage, however strong the soul. A release under a fifth of a second fires nothing; the quickest shot does about a third of the full draw.")
                .defineInRange("max_damage", 35.0, 0.0, 10_000.0);
        BOGEN_HOLLOW_DAMAGE_MULTIPLIER = builder
                .comment("Damage multiplier for a Heilig Pfeil that strikes a Hollow, or a Hollow or Arrancar player.")
                .defineInRange("hollow_damage_multiplier", 1.25, 0.0, 10.0);
        BOGEN_BASE_COST = builder
                .comment("Reishi a shot costs before the draw adds to it (the quickest shot that fires costs about half a reishi more).")
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
                .comment("Ticks after a dash of either kind before the next Hirenkyaku, below the third tier. Shunpo keeps its own cooldown, counted from the same moment.")
                .defineInRange("cooldown_ticks", 20, 0, 1_200);
        HIRENKYAKU_MASTER_COOLDOWN_TICKS = builder
                .comment("The same at the third tier.")
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

        builder.push("sanrei");
        SANREI_DRAW_TICKS = builder
                .comment("Ticks a full draw takes while the Sanrei Glove is worn and not yet mastered.")
                .defineInRange("draw_ticks", 40, 1, 200);
        SANREI_HOLD_UPKEEP_MULTIPLIER = builder
                .comment("How many times over holding a full draw costs while the unmastered glove is worn.")
                .defineInRange("hold_upkeep_multiplier", 2.0, 0.0, 100.0);
        SANREI_GATHER_MULTIPLIER = builder
                .comment("Reishi gathering multiplier while the unmastered glove is worn.")
                .defineInRange("gather_multiplier", 0.5, 0.0, 10.0);
        SANREI_POINTS_PER_DAY = builder
                .comment("Glove training points that make a day count: 2 for a full draw, 1 for a Heilig Pfeil hit on a Hollow and 5 for a destroyed soul.")
                .defineInRange("points_per_day", 80, 1, 100_000);
        SANREI_DAYS_TO_MASTER = builder
                .comment("Separate days of training that master the glove; sleeping through the night starts a new day.")
                .defineInRange("days_to_master", 7, 1, 365);
        SANREI_MASTERED_DAMAGE_MULTIPLIER = builder
                .comment("Heilig Pfeil damage multiplier while the mastered glove is worn.")
                .defineInRange("mastered_damage_multiplier", 1.1, 1.0, 10.0);
        SANREI_MASTERED_DAMAGE_CAP = builder
                .comment("Highest full-draw damage while the mastered glove is worn, never below the Heilig Bogen's own max_damage.")
                .defineInRange("mastered_damage_cap", 40.0, 0.0, 10_000.0);
        SANREI_FLANK_DAMAGE_FRACTION = builder
                .comment("Share of a full draw's damage each of the two arrows loosed beside it with the mastered glove does.")
                .defineInRange("flank_damage_fraction", 0.5, 0.0, 1.0);
        SANREI_FLANK_COST = builder
                .comment("Reishi the two flank arrows cost together; they are not loosed when it cannot be paid.")
                .defineInRange("flank_cost", 1.0, 0.0, 1_000.0);
        builder.pop();

        builder.push("letzt_stil");
        LETZT_ENABLED = builder
                .comment("Whether tearing off the mastered Sanrei Glove burns a Quincy's power out in Letzt Stil, once per soul, and seals it after.")
                .define("enabled", true);
        LETZT_DURATION_TICKS = builder
                .comment("Ticks Letzt Stil lasts before the power is sealed.")
                .defineInRange("duration_ticks", 1_800, 20, 72_000);
        LETZT_POOL_MULTIPLIER = builder
                .comment("How many times over the reishi a Quincy can hold during Letzt Stil; a soul that also has a Shinigami's power gets half the extra, as with Letzt Stil's damage, gathering, speed and reiatsu.")
                .defineInRange("pool_multiplier", 3.0, 1.0, 100.0);
        LETZT_GATHER_MULTIPLIER = builder
                .comment("Reishi gathering multiplier during Letzt Stil, which gathers during Blut and on reishi footing too.")
                .defineInRange("gather_multiplier", 4.0, 0.0, 100.0);
        LETZT_MIN_DENSITY = builder
                .comment("Reishi density Letzt Stil gathers at wherever the reishi around it is thinner than that.")
                .defineInRange("min_density", 2.0, 0.0, 10.0);
        LETZT_DRAW_TICKS = builder
                .comment("Ticks a full draw takes during Letzt Stil.")
                .defineInRange("draw_ticks", 10, 1, 200);
        LETZT_DAMAGE_MULTIPLIER = builder
                .comment("Heilig Pfeil damage multiplier during Letzt Stil.")
                .defineInRange("damage_multiplier", 1.6, 1.0, 100.0);
        LETZT_DAMAGE_CAP = builder
                .comment("Highest full-draw damage during Letzt Stil, never below the Heilig Bogen's own max_damage.")
                .defineInRange("damage_cap", 60.0, 0.0, 10_000.0);
        LETZT_SHOT_SPEED_MULTIPLIER = builder
                .comment("Heilig Pfeil speed multiplier during Letzt Stil.")
                .defineInRange("shot_speed_multiplier", 1.25, 0.1, 10.0);
        LETZT_MOVEMENT_BONUS = builder
                .comment("Extra movement speed during Letzt Stil (0.2 = +20%).")
                .defineInRange("movement_bonus", 0.2, 0.0, 10.0);
        LETZT_REIATSU_MULTIPLIER = builder
                .comment("How many times over a Quincy's reiatsu counts in the reiatsu difference during Letzt Stil.")
                .defineInRange("reiatsu_multiplier", 2.0, 1.0, 100.0);
        LETZT_HIRENKYAKU_DISTANCE_MULTIPLIER = builder
                .comment("Hirenkyaku distance multiplier during Letzt Stil, applied after the longest dash is reached.")
                .defineInRange("hirenkyaku_distance_multiplier", 1.5, 0.1, 10.0);
        LETZT_HIRENKYAKU_COST_MULTIPLIER = builder
                .comment("Hirenkyaku reishi cost multiplier during Letzt Stil.")
                .defineInRange("hirenkyaku_cost_multiplier", 0.5, 0.0, 10.0);
        LETZT_HIRENKYAKU_COOLDOWN_MULTIPLIER = builder
                .comment("Hirenkyaku cooldown multiplier during Letzt Stil.")
                .defineInRange("hirenkyaku_cooldown_multiplier", 0.5, 0.0, 10.0);
        LETZT_PFEIL_DAMAGE_MULTIPLIER = builder
                .comment("Damage of the Letzt Pfeil, the shot of a draw held two seconds past full, as a multiple of a full draw's.")
                .defineInRange("pfeil_damage_multiplier", 3.0, 0.0, 100.0);
        LETZT_PFEIL_MAX_DAMAGE = builder
                .comment("Highest Letzt Pfeil damage.")
                .defineInRange("pfeil_max_damage", 120.0, 0.0, 10_000.0);
        LETZT_PFEIL_PIERCE = builder
                .comment("How many creatures a Letzt Pfeil passes through.")
                .defineInRange("pfeil_pierce", 10, 0, 100);
        LETZT_PFEIL_SPEED = builder
                .comment("Letzt Pfeil speed in blocks a tick.")
                .defineInRange("pfeil_speed", 3.5, 0.1, 10.0);
        LETZT_PFEIL_COST = builder
                .comment("Reishi a Letzt Pfeil costs; without it the charged draw looses an ordinary full-draw arrow.")
                .defineInRange("pfeil_cost", 15.0, 0.0, 1_000.0);
        LETZT_PFEIL_BREAKS_BLOCKS = builder
                .comment("Whether a Letzt Pfeil carves through soft blocks where block protection allows it.")
                .define("pfeil_breaks_blocks", true);
        LETZT_PFEIL_CARVE_BLOCKS = builder
                .comment("Most blocks one Letzt Pfeil carves through.")
                .defineInRange("pfeil_carve_blocks", 16, 0, 256);
        LETZT_PFEIL_MAX_HARDNESS = builder
                .comment("Hardest block a Letzt Pfeil carves through (dirt is 0.5, stone 1.5, obsidian 50).")
                .defineInRange("pfeil_max_hardness", 3.0, 0.0, 100.0);
        builder.pop();

        builder.push("restoration");
        RESTORE_SEALED_TICKS = builder
                .comment("Ticks a sealed Quincy's power stays gone before the rite can bring it back.")
                .defineInRange("sealed_ticks", 48_000, 0, 10_000_000);
        RESTORE_ARROW_SEALED_TICKS = builder
                .comment("Ticks a sealed Quincy's power stays gone before another Quincy's full-draw arrow beside the heart can bring it back.")
                .defineInRange("arrow_sealed_ticks", 6_000, 0, 10_000_000);
        RESTORE_SOLO_RITE = builder
                .comment("Whether a sealed Quincy can win their power back alone, holding the Cross through a rite.")
                .define("solo_rite", true);
        RESTORE_RITE_TICKS = builder
                .comment("Ticks the Cross must be held through the rite.")
                .defineInRange("rite_ticks", 100, 1, 6_000);
        RESTORE_MAX_HEALTH_FRACTION = builder
                .comment("Share of maximum health a sealed Quincy must be down to for their power to return.")
                .defineInRange("max_health_fraction", 0.3, 0.0, 1.0);
        RESTORE_MAX_FOOD = builder
                .comment("Food level, out of 20, a sealed Quincy must be down to for their power to return.")
                .defineInRange("max_food", 6, 0, 20);
        SEALED_REIATSU_MULTIPLIER = builder
                .comment("How much a sealed Quincy's reiatsu counts for in the reiatsu difference, unless they have a Shinigami's power.")
                .defineInRange("sealed_reiatsu_multiplier", 0.5, 0.0, 1.0);
        builder.pop();

        builder.push("ginrei_kojaku");
        GINREI_DRAW_TICKS = builder
                .comment("Ticks a full draw of Ginrei Kojaku, the restored Quincy's bow, takes.")
                .defineInRange("draw_ticks", 15, 1, 200);
        GINREI_DAMAGE_MULTIPLIER = builder
                .comment("Heilig Pfeil damage multiplier from Ginrei Kojaku.")
                .defineInRange("damage_multiplier", 1.2, 1.0, 100.0);
        GINREI_DAMAGE_CAP = builder
                .comment("Highest full-draw damage from Ginrei Kojaku, never below the Heilig Bogen's own max_damage.")
                .defineInRange("damage_cap", 45.0, 0.0, 10_000.0);
        GINREI_STREAM_COST = builder
                .comment("Reishi each arrow of the stream from a held Ginrei Kojaku draw costs.")
                .defineInRange("stream_cost", 0.75, 0.0, 1_000.0);
        GINREI_STREAM_DAMAGE_FRACTION = builder
                .comment("Share of a full draw's damage each stream arrow does.")
                .defineInRange("stream_damage_fraction", 0.25, 0.0, 1.0);
        LICHT_REGEN_COST = builder
                .comment("Reishi Licht Regen costs.")
                .defineInRange("licht_regen_cost", 25.0, 0.0, 1_000.0);
        LICHT_REGEN_COOLDOWN_TICKS = builder
                .comment("Ticks after Licht Regen before it can be called on again.")
                .defineInRange("licht_regen_cooldown_ticks", 300, 0, 72_000);
        LICHT_REGEN_ARROWS = builder
                .comment("Arrows a Licht Regen volley looses.")
                .defineInRange("licht_regen_arrows", 20, 1, 200);
        LICHT_REGEN_DAMAGE_FRACTION = builder
                .comment("Share of a full draw's damage each Licht Regen arrow does.")
                .defineInRange("licht_regen_damage_fraction", 0.25, 0.0, 1.0);
        builder.pop();

        builder.push("seele_schneider");
        SEELE_FORM_COST = builder
                .comment("Reishi forming a Seele Schneider's blade costs.")
                .defineInRange("form_cost", 3.0, 0.0, 1_000.0);
        SEELE_UPKEEP_PER_SECOND = builder
                .comment("Reishi a formed Seele Schneider drains each second.")
                .defineInRange("upkeep_per_second", 0.25, 0.0, 1_000.0);
        SEELE_DAMAGE = builder
                .comment("Attack damage of a formed Seele Schneider.")
                .defineInRange("damage", 7.0, 1.0, 1_000.0);
        SEELE_ATTACK_SPEED = builder
                .comment("Attack speed of a formed Seele Schneider (a sword's is 1.6).")
                .defineInRange("attack_speed", 1.8, 0.1, 4.0);
        SEELE_HOLLOW_MULTIPLIER = builder
                .comment("Damage multiplier for a formed Seele Schneider's cut on a Hollow, or a Hollow or Arrancar player.")
                .defineInRange("hollow_multiplier", 1.25, 1.0, 10.0);
        SEELE_ARMOR_IGNORED = builder
                .comment("Share of armour a Seele Schneider ignores, cutting or loosed.")
                .defineInRange("armor_ignored", 0.5, 0.0, 1.0);
        SEELE_DRAIN_FRACTION = builder
                .comment("Share of a Seele Schneider cut's damage that comes back as reishi, twice that from a Hollow or a soul releasing its reiatsu.")
                .defineInRange("drain_fraction", 0.3, 0.0, 10.0);
        SEELE_DRAIN_MAX = builder
                .comment("Most reishi one Seele Schneider cut gives back.")
                .defineInRange("drain_max", 5.0, 0.0, 1_000.0);
        SEELE_FIRED_DAMAGE_MULTIPLIER = builder
                .comment("Damage multiplier for a Seele Schneider loosed from a full Ginrei Kojaku draw, over the arrow it replaces.")
                .defineInRange("fired_damage_multiplier", 1.5, 0.0, 10.0);
        SEELE_FIRED_COST = builder
                .comment("Extra reishi loosing a Seele Schneider costs; without it the draw looses an ordinary arrow.")
                .defineInRange("fired_cost", 2.0, 0.0, 1_000.0);
        SEELE_STUCK_DESPAWN_TICKS = builder
                .comment("Ticks a stuck Seele Schneider stands before its blade fades and it drops as a hilt.")
                .defineInRange("stuck_despawn_ticks", 6_000, 20, 1_000_000);
        builder.pop();

        builder.push("ginto");
        GINTO_FILL_COST = builder
                .comment("Reishi filling an empty Gintō costs.")
                .defineInRange("fill_cost", 8.0, 0.0, 1_000.0);
        GINTO_COOLDOWN_TICKS = builder
                .comment("Ticks after a Gintō spell before the next.")
                .defineInRange("cooldown_ticks", 40, 0, 1_200);
        GINTO_RANGE = builder
                .comment("How far, in blocks, a Gintō spell reaches.")
                .defineInRange("range", 24.0, 1.0, 64.0);
        HEIZEN_DAMAGE_MULTIPLIER = builder
                .comment("Heizen damage as a multiple of the caster's full-draw Heilig Pfeil damage, twice that on a Hollow.")
                .defineInRange("heizen_damage_multiplier", 1.5, 0.0, 100.0);
        HEIZEN_MAX_DAMAGE = builder
                .comment("Highest Heizen damage.")
                .defineInRange("heizen_max_damage", 60.0, 0.0, 10_000.0);
        GRITZ_MAX_TICKS = builder
                .comment("Ticks Gritz binds a creature whose reiatsu is no greater than the caster's, down to a quarter of that against far stronger ones, and a quarter again on bosses.")
                .defineInRange("gritz_max_ticks", 100, 1, 1_200);
        WOLKE_TICKS = builder
                .comment("Ticks a Wolke cloud lasts.")
                .defineInRange("wolke_ticks", 200, 1, 6_000);
        WOLKE_RADIUS = builder
                .comment("Radius of a Wolke cloud in blocks.")
                .defineInRange("wolke_radius", 3.5, 0.5, 16.0);
        SPRENGER_DAMAGE_MULTIPLIER = builder
                .comment("Sprenger damage as a multiple of the caster's full-draw Heilig Pfeil damage.")
                .defineInRange("sprenger_damage_multiplier", 2.5, 0.0, 100.0);
        SPRENGER_MAX_DAMAGE = builder
                .comment("Highest Sprenger damage.")
                .defineInRange("sprenger_max_damage", 80.0, 0.0, 10_000.0);
        builder.pop();

        builder.push("vollstandig");
        VOLL_TRAINING_AFTER_RESTORE = builder
                .comment("Training a restored Quincy needs after their power returns to take Vollständig.")
                .defineInRange("training_after_restore", 500, 0, 100_000);
        VOLL_START_COST = builder
                .comment("Reishi taking Vollständig costs.")
                .defineInRange("start_cost", 20.0, 0.0, 1_000.0);
        VOLL_START_MIN_REISHI = builder
                .comment("Reishi a Quincy needs to take Vollständig.")
                .defineInRange("start_min_reishi", 30.0, 0.0, 1_000.0);
        VOLL_COOLDOWN_TICKS = builder
                .comment("Ticks after Vollständig ends before it can be taken again.")
                .defineInRange("cooldown_ticks", 600, 0, 72_000);
        VOLL_POOL_MULTIPLIER = builder
                .comment("How many times over the reishi a Quincy can hold in Vollständig; a soul that also has a Shinigami's power gets half the extra, as with Vollständig's damage and reiatsu.")
                .defineInRange("pool_multiplier", 1.5, 1.0, 100.0);
        VOLL_DAMAGE_MULTIPLIER = builder
                .comment("Heilig Pfeil damage multiplier in Vollständig.")
                .defineInRange("damage_multiplier", 1.3, 1.0, 100.0);
        VOLL_DAMAGE_CAP = builder
                .comment("Highest full-draw damage in Vollständig, never below the Heilig Bogen's own max_damage.")
                .defineInRange("damage_cap", 55.0, 0.0, 10_000.0);
        VOLL_REIATSU_MULTIPLIER = builder
                .comment("How many times over a Quincy's reiatsu counts in the reiatsu difference in Vollständig.")
                .defineInRange("reiatsu_multiplier", 1.5, 1.0, 100.0);
        VOLL_UPKEEP_PER_SECOND = builder
                .comment("Reishi Vollständig drains each second.")
                .defineInRange("upkeep_per_second", 1.0, 0.0, 1_000.0);
        VOLL_FLIGHT_UPKEEP_PER_SECOND = builder
                .comment("Extra reishi Vollständig drains each second while flying.")
                .defineInRange("flight_upkeep_per_second", 1.5, 0.0, 1_000.0);
        VOLL_HALO_BREAK_FRACTION = builder
                .comment("Share of maximum health one hit must take to shatter the halo, ending Sklaverei for the rest of the form; a hit from above the brow shatters it too.")
                .defineInRange("halo_break_fraction", 0.3, 0.0, 1.0);
        builder.pop();

        builder.push("sklaverei");
        SKLAVEREI_RADIUS = builder
                .comment("How far, in blocks, Sklaverei draws in the reishi around its user.")
                .defineInRange("radius", 8.0, 1.0, 32.0);
        SKLAVEREI_UPKEEP_PER_SECOND = builder
                .comment("Extra reishi Vollständig drains each second while Sklaverei runs.")
                .defineInRange("upkeep_per_second", 1.0, 0.0, 1_000.0);
        SKLAVEREI_HOLLOW_DAMAGE = builder
                .comment("Damage each Sklaverei pulse, twice a second, does to each Hollow in reach.")
                .defineInRange("hollow_damage", 2.0, 0.0, 1_000.0);
        SKLAVEREI_PLAYER_DRAIN = builder
                .comment("Most reishi, or else reiryoku, each pulse draws from each spiritually aware player in reach whom the user may harm.")
                .defineInRange("player_drain", 1.5, 0.0, 1_000.0);
        SKLAVEREI_PROJECTILE_GAIN = builder
                .comment("Reishi gained for each enemy Heilig Pfeil, Letzt Pfeil or Getsuga Tenshō Sklaverei unravels.")
                .defineInRange("projectile_gain", 1.5, 0.0, 1_000.0);
        SKLAVEREI_MAX_GAIN_PER_PULSE = builder
                .comment("Most reishi one Sklaverei pulse gains.")
                .defineInRange("max_gain_per_pulse", 3.0, 0.0, 1_000.0);
        SKLAVEREI_POISON_PER_HOLLOW = builder
                .comment("Hollow poison the user takes for each Hollow a pulse draws on.")
                .defineInRange("poison_per_hollow", 1.0, 0.0, 100.0);
        builder.pop();

        builder.push("npcs");
        ELDER_VISITS = builder
                .comment("Whether a Quincy Elder sometimes visits players with Quincy blood, to give lessons, teach Ransōtengai and bring a sealed power back.")
                .define("elder_visits", true);
        ELDER_COOLDOWN_MINUTES = builder
                .comment("Minutes that pass at least between two of the Elder's visits to the same soul. A new world has none until this has passed.")
                .defineInRange("elder_cooldown_minutes", 10, 1, 1_440);
        ELDER_LESSON_TRAINING = builder
                .comment("Quincy training each of the Elder's lessons gives.")
                .defineInRange("elder_lesson_training", 10, 0, 1_000);
        ELDER_MAX_LESSONS = builder
                .comment("How many lessons the Elder gives a soul in all, never more than one a day.")
                .defineInRange("elder_max_lessons", 12, 0, 1_000);
        ELDER_RESTORE_AFTER_TICKS = builder
                .comment("Ticks a Quincy's power must have been sealed before the Elder comes to bring it back.")
                .defineInRange("elder_restore_after_ticks", 24_000, 0, 10_000_000);
        ELDER_ARROW_DAMAGE = builder
                .comment("Damage of the Elder's Heilig Pfeil.")
                .defineInRange("elder_arrow_damage", 9.0, 0.0, 100.0);
        HUNTER_ENABLED = builder
                .comment("Whether a young Quincy Hunter sometimes turns up where a Garganta opens, or at a Hollow Bait, and challenges souls to a contest.")
                .define("hunter_enabled", true);
        HUNTER_CHANCE = builder
                .comment("Chance the Hunter follows a Garganta that opens near a soul of its own accord.")
                .defineInRange("hunter_chance", 0.1, 0.0, 1.0);
        HUNTER_GUARANTEE_KILLS = builder
                .comment("Hollows a soul must have purified or slain before the Hunter surely follows its next Garganta, until they have met.")
                .defineInRange("hunter_guarantee_kills", 10, 0, 10_000);
        HUNTER_COOLDOWN_MINUTES = builder
                .comment("Minutes that pass at least between two of the Hunter's arrivals near the same soul. A Hollow Bait ignores this.")
                .defineInRange("hunter_cooldown_minutes", 20, 0, 1_440);
        HUNTER_ARROW_DAMAGE = builder
                .comment("Damage of the Hunter's Heilig Pfeil.")
                .defineInRange("hunter_arrow_damage", 7.0, 0.0, 100.0);
        HUNTER_IN_BAIT_CHANCE = builder
                .comment("Chance the Hunter comes to fight a Hollow Bait's Hollows at its third wave.")
                .defineInRange("hunter_in_bait_chance", 0.25, 0.0, 1.0);
        HOLLOWS_HUNT_HUNTER = builder
                .comment("Whether Hollows hunt the Hunter as they hunt players. Nothing can hunt the Elder.")
                .define("hollows_hunt_hunter", true);
        builder.pop();

        builder.push("ransotengai");
        RANSOTENGAI_DRAIN_PERCENT = builder
                .comment("Share of maximum reiryoku Ransōtengai drains each second, in percent (2.5 = 2.5%).")
                .defineInRange("drain_percent_per_second", 2.5, 0.0, 100.0);
        RANSOTENGAI_MIN_REIRYOKU = builder
                .comment("Share of maximum reiryoku a soul needs to weave the threads.")
                .defineInRange("min_reiryoku_fraction", 0.2, 0.0, 1.0);
        RANSOTENGAI_FAILING_HEALTH = builder
                .comment("Share of maximum health at which a body counts as failing, so sneak and the Quincy Form key weave the threads. Slowness, mining fatigue and weakness count as failing too.")
                .defineInRange("failing_health_fraction", 0.25, 0.0, 1.0);
        RANSOTENGAI_COOLDOWN_SECONDS = builder
                .comment("Seconds after the threads let go before they will hold again: after every collapse, and after logging out while woven (not after death, a change of game mode, the trial or a command).")
                .defineInRange("cooldown_seconds", 180, 0, 3_600);
        RANSOTENGAI_COLLAPSE_SECONDS = builder
                .comment("Seconds of Slowness and Weakness after the threads let go, as the body gives out.")
                .defineInRange("collapse_seconds", 10, 0, 600);
        RANSOTENGAI_TRIAL_SECONDS = builder
                .comment("Seconds the Elder's Ransōtengai trial stays open, and always at least 5 more than trial_hold_seconds.")
                .defineInRange("trial_seconds", 30, 5, 600);
        RANSOTENGAI_TRIAL_HOLD_SECONDS = builder
                .comment("Seconds the threads must hold unbroken in the trial for the art to be learned. In the trial they never run dry.")
                .defineInRange("trial_hold_seconds", 5, 1, 60);
        RANSOTENGAI_HYBRID_DRAIN = builder
                .comment("How many times over the drain costs a Quincy who also has a Shinigami's power.")
                .defineInRange("hybrid_drain_multiplier", 1.5, 0.0, 10.0);
        builder.pop();
        builder.pop();

        builder.push("hollow_bait");
        BAIT_ENABLED = builder
                .comment("Whether Hollow Bait can be crushed to draw Hollows.")
                .define("enabled", true);
        BAIT_DURATION_MINUTES = builder
                .comment("Minutes a crushed bait draws Hollows, counting only the time its ground is loaded.")
                .defineInRange("duration_minutes", 20, 1, 120);
        BAIT_RADIUS = builder
                .comment("Radius of a bait site in blocks. Souls a little beyond it are hunted and see its bar too.")
                .defineInRange("radius", 64, 16, 128);
        BAIT_MAX_SITES = builder
                .comment("Most bait sites open at once in the world.")
                .defineInRange("max_sites", 4, 1, 16);
        BAIT_SITE_SPACING = builder
                .comment("No bait can be crushed this close, in blocks, to the centre of another.")
                .defineInRange("site_spacing", 128, 32, 512);
        BAIT_FINALE_ARRIVALS = builder
                .comment("Hollows that must come through before the sky above the bait cracks.")
                .defineInRange("arrivals_for_finale", 30, 5, 200);
        BAIT_FIRST_WAVE_SECONDS = builder
                .comment("Seconds after the crush before the first wave.")
                .defineInRange("first_wave_seconds", 10, 1, 300);
        BAIT_INTERVAL_START_SECONDS = builder
                .comment("Seconds between waves at first. The gap moves towards wave_interval_end_seconds as more Hollows arrive.")
                .defineInRange("wave_interval_start_seconds", 30, 5, 600);
        BAIT_INTERVAL_END_SECONDS = builder
                .comment("Seconds between waves just before the sky cracks. It may be longer than the first gap.")
                .defineInRange("wave_interval_end_seconds", 12, 5, 600);
        BAIT_MAX_ALIVE = builder
                .comment("Most of a site's Hollows about at once, counting those still to come through, while one soul is hunted.")
                .defineInRange("max_alive", 6, 1, 64);
        BAIT_ALIVE_PER_SOUL = builder
                .comment("More Hollows allowed about for each hunted soul past the first.")
                .defineInRange("max_alive_per_extra_soul", 2, 0, 16);
        BAIT_ALIVE_CAP = builder
                .comment("Most of a site's Hollows about at once, however many souls are hunted.")
                .defineInRange("max_alive_cap", 12, 1, 64);
        BAIT_LURE_START = builder
                .comment("Reiatsu the bait draws Hollows as at first, or the hunted soul's own when that is stronger.")
                .defineInRange("lure_reiatsu_start", 40.0, 0.0, 10_000.0);
        BAIT_LURE_GAIN = builder
                .comment("Reiatsu the lure gains by the time the sky cracks, so later waves bring stronger Hollows.")
                .defineInRange("lure_reiatsu_gain", 60.0, 0.0, 10_000.0);
        BAIT_FINALE_MIN_SECONDS = builder
                .comment("Fewest seconds the cracked sky holds open while the Hollows gather beneath it.")
                .defineInRange("finale_seconds_min", 15, 1, 120);
        BAIT_FINALE_MAX_SECONDS = builder
                .comment("Most seconds the cracked sky holds open.")
                .defineInRange("finale_seconds_max", 20, 1, 120);
        BAIT_ABANDON_MINUTES = builder
                .comment("Minutes a site can stay frozen, with its ground unloaded or no Hollows able to come, before it is given up (a contest is called off).")
                .defineInRange("abandon_minutes", 20, 1, 1_440);
        CONTEST_MINUTES = builder
                .comment("Minutes the Quincy Hunter's contest lasts: whoever destroys more Hollows in that time wins.")
                .defineInRange("contest_minutes", 6, 1, 60);
        builder.pop();

        builder.push("menos");
        MENOS_FINALE_GILLIAN_CHANCE = builder
                .comment("Chance that a Gillian pushes through the cracked sky at the end of a Hollow Bait.")
                .defineInRange("finale_gillian_chance", 1.0, 0.0, 1.0);
        MENOS_ARRIVAL_SHARE = builder
                .comment("Share of Garganta arrivals for strong souls that bring a Menos Grande instead.")
                .defineInRange("arrival_share", 0.05, 0.0, 1.0);
        MENOS_GILLIAN_MIN_REIATSU = builder
                .comment("Maximum reiryoku a soul needs before a Gillian may come for it.")
                .defineInRange("gillian_min_reiatsu", 300.0, 0.0, 10_000.0);
        MENOS_ADJUCHAS_MIN_REIATSU = builder
                .comment("Maximum reiryoku a soul needs before an Adjuchas pack may come for it.")
                .defineInRange("adjuchas_min_reiatsu", 500.0, 0.0, 10_000.0);
        MENOS_ADJUCHAS_SHARE = builder
                .comment("Share of Menos arrivals that are an Adjuchas pack, for souls strong enough.")
                .defineInRange("adjuchas_share", 0.35, 0.0, 1.0);
        MENOS_ADJUCHAS_PACK_SIZE = builder
                .comment("Adjuchas in a pack.")
                .defineInRange("adjuchas_pack_size", 2, 1, 6);
        MENOS_ADJUCHAS_EXTRA_AT_REIATSU = builder
                .comment("Maximum reiryoku at which a pack brings one Adjuchas more.")
                .defineInRange("adjuchas_extra_at_reiatsu", 800.0, 0.0, 10_000.0);
        MENOS_MAX_ALIVE = builder
                .comment("Most Menos alive in the world at once. 0 brings none.")
                .defineInRange("max_alive", 4, 0, 32);
        MENOS_SPACING = builder
                .comment("No Menos comes within this many blocks of another.")
                .defineInRange("spacing", 128, 16, 512);
        MENOS_LIFETIME_MINUTES = builder
                .comment("Minutes a Menos stays before it goes back through a tear.")
                .defineInRange("lifetime_minutes", 6, 1, 60);
        GILLIAN_RETREAT_HEALTH = builder
                .comment("Share of its health at which a beaten Gillian pulls back into the sky.")
                .defineInRange("gillian_retreat_health", 0.25, 0.0, 1.0);
        GILLIAN_FEED_INTERVAL_SECONDS = builder
                .comment("Seconds between the Hollows a Gillian draws up to its mouth.")
                .defineInRange("gillian_feed_interval_seconds", 5, 1, 120);
        GILLIAN_FEED_REACH = builder
                .comment("How far, in blocks, a Gillian reaches for a Hollow to eat.")
                .defineInRange("gillian_feed_reach", 10.0, 2.0, 24.0);
        GILLIAN_TRAMPLES_LEAVES = builder
                .comment("Whether a blocked Gillian tramples through leaves (mob griefing must allow it too).")
                .define("gillian_tramples_leaves", true);
        ADJUCHAS_HUNGER_HEALTH = builder
                .comment("Share of its health below which an Adjuchas hunts weaker Hollows to eat.")
                .defineInRange("adjuchas_hunger_health", 0.6, 0.0, 1.0);
        CERO_BREAKS_BLOCKS = builder
                .comment("Whether a Cero blasts soft blocks where it strikes (mob griefing must allow it too).")
                .define("cero_breaks_blocks", true);
        CERO_MAX_HARDNESS = builder
                .comment("Hardest block a Cero can blast away.")
                .defineInRange("cero_max_hardness", 0.8, 0.0, 50.0);
        CERO_BLAST_SHARE = builder
                .comment("Share of a Cero's damage dealt around where it strikes a block.")
                .defineInRange("cero_blast_share", 0.5, 0.0, 1.0);

        builder.push("gillian_cero");
        GILLIAN_CERO_DAMAGE = builder
                .comment("Damage of the beam on Normal, before the hollows damage multiplier.")
                .defineInRange("damage", 18.0, 0.0, 200.0);
        GILLIAN_CERO_CHARGE_TICKS = builder
                .comment("Ticks the Cero gathers before it fires.")
                .defineInRange("charge_ticks", 50, 5, 200);
        GILLIAN_CERO_AIM_LOCK_TICKS = builder
                .comment("Ticks before firing that the aim stops following the prey.")
                .defineInRange("aim_lock_ticks", 12, 0, 200);
        GILLIAN_CERO_RANGE = builder
                .comment("Blocks the beam reaches.")
                .defineInRange("range", 48.0, 4.0, 96.0);
        GILLIAN_CERO_MIN_RANGE = builder
                .comment("Closest, in blocks, the prey may be.")
                .defineInRange("min_range", 6.0, 0.0, 32.0);
        GILLIAN_CERO_RADIUS = builder
                .comment("Half-width of the beam in blocks.")
                .defineInRange("radius", 1.4, 0.1, 4.0);
        GILLIAN_CERO_BLAST_RADIUS = builder
                .comment("Radius of the blast where it strikes a block.")
                .defineInRange("blast_radius", 2.5, 0.0, 8.0);
        GILLIAN_CERO_MAX_BLOCKS = builder
                .comment("Most blocks one blast can break.")
                .defineInRange("max_blocks", 16, 0, 64);
        GILLIAN_CERO_COOLDOWN_SECONDS = builder
                .comment("Seconds between one Cero and the next.")
                .defineInRange("cooldown_seconds", 8, 1, 120);
        GILLIAN_CERO_KNOCKBACK = builder
                .comment("Extra knockback along the beam.")
                .defineInRange("knockback", 1.2, 0.0, 5.0);
        builder.pop();

        builder.push("adjuchas_cero");
        ADJUCHAS_CERO_DAMAGE = builder
                .comment("Damage of the beam on Normal, before the hollows damage multiplier.")
                .defineInRange("damage", 10.0, 0.0, 200.0);
        ADJUCHAS_CERO_CHARGE_TICKS = builder
                .comment("Ticks the Cero gathers before it fires.")
                .defineInRange("charge_ticks", 25, 5, 200);
        ADJUCHAS_CERO_AIM_LOCK_TICKS = builder
                .comment("Ticks before firing that the aim stops following the prey.")
                .defineInRange("aim_lock_ticks", 6, 0, 200);
        ADJUCHAS_CERO_RANGE = builder
                .comment("Blocks the beam reaches.")
                .defineInRange("range", 32.0, 4.0, 96.0);
        ADJUCHAS_CERO_MIN_RANGE = builder
                .comment("Closest, in blocks, the prey may be.")
                .defineInRange("min_range", 4.0, 0.0, 32.0);
        ADJUCHAS_CERO_RADIUS = builder
                .comment("Half-width of the beam in blocks.")
                .defineInRange("radius", 0.8, 0.1, 4.0);
        ADJUCHAS_CERO_BLAST_RADIUS = builder
                .comment("Radius of the blast where it strikes a block.")
                .defineInRange("blast_radius", 1.5, 0.0, 8.0);
        ADJUCHAS_CERO_MAX_BLOCKS = builder
                .comment("Most blocks one blast can break.")
                .defineInRange("max_blocks", 6, 0, 64);
        ADJUCHAS_CERO_COOLDOWN_SECONDS = builder
                .comment("Seconds between one Cero and the next.")
                .defineInRange("cooldown_seconds", 6, 1, 120);
        ADJUCHAS_CERO_KNOCKBACK = builder
                .comment("Extra knockback along the beam.")
                .defineInRange("knockback", 0.6, 0.0, 5.0);
        builder.pop();
        builder.pop();

        builder.push("hollow_evolution");
        GILLIAN_SOULS = builder
                .comment("Souls a Hollow player must hold within to become a Gillian.")
                .defineInRange("gillian_souls", 250, 0, 100_000);
        GILLIAN_HOLLOWS = builder
                .comment("Hollow mobs a Hollow player must have devoured in its life to become a Gillian.")
                .defineInRange("gillian_hollows", 20, 0, 10_000);
        ADJUCHAS_HOLLOWS = builder
                .comment("Hollows (mobs, Menos or Hollow players) a Gillian must devour to become an Adjuchas. Under SOFT"
                        + " regression, a Gillian that has sunk back needs this many more for each time it did.")
                .defineInRange("adjuchas_hollows", 30, 0, 10_000);
        ADJUCHAS_MENOS = builder
                .comment("How many of those must be Menos, a Gillian or an Adjuchas.")
                .defineInRange("adjuchas_menos", 2, 0, 100);
        VASTO_HOLLOWS = builder
                .comment("Hollows (mobs, Menos or Hollow players) an Adjuchas must devour to become a Vasto Lorde, if it is"
                        + " destined to.")
                .defineInRange("vasto_hollows", 40, 0, 10_000);
        VASTO_ADJUCHAS = builder
                .comment("How many of those must be Adjuchas.")
                .defineInRange("vasto_adjuchas", 3, 0, 100);
        VASTO_DESTINED_SHARE = builder
                .comment("Share of souls destined to become a Vasto Lorde, fixed for each player in each world. 1 makes every"
                        + " Adjuchas destined.")
                .defineInRange("vasto_destined_share", 0.25, 0.0, 1.0);
        VASTO_MAX = builder
                .comment("Most Vasto Lorde the server holds by devouring, counting those offline. 0 allows none. Commands"
                        + " ignore it.")
                .defineInRange("vasto_max", 10, 0, 100);
        ADJUCHAS_REGRESSION = builder
                .comment("What an Adjuchas risks when it goes without devouring a Hollow: SOFT, it sinks back into a Gillian"
                        + " and may rise again for more Hollows; CANON, it sinks back for good; OFF, nothing. The clock runs"
                        + " only where Hollows come for it: in the Overworld, off Peaceful, with mob spawning and Garganta"
                        + " arrivals on.")
                .defineEnum("regression", HollowTier.Regression.SOFT);
        REGRESS_MINUTES = builder
                .comment("Minutes of play without devouring a Hollow before an Adjuchas sinks back into a Gillian.")
                .defineInRange("regress_minutes", 30, 1, 600);
        GILLIAN_MINDLESS = builder
                .comment("Whether a Gillian is too mindless to use items, place blocks or deal with creatures (a bare hand"
                        + " still opens doors and chests).")
                .define("gillian_mindless", true);
        MENOS_RIVAL_SHARE = builder
                .comment("Share of the Garganta arrivals for a Menos Hollow player that bring a Menos Grande, at any"
                        + " reiatsu.")
                .defineInRange("menos_rival_share", 0.3, 0.0, 1.0);
        CERO_MIN_CHARGE = builder
                .comment("Share of its gather a player's Cero needs to fire when the key is let go. Less fizzles out.")
                .defineInRange("cero_min_charge", 0.5, 0.05, 1.0);
        CERO_COST_FRACTION = builder
                .comment("Share of maximum reiryoku a player's Cero costs when it fires.")
                .defineInRange("cero_cost_fraction", 0.12, 0.0, 1.0);
        CERO_MIN_COST = builder
                .comment("Least reiryoku a player's Cero costs.")
                .defineInRange("cero_min_cost", 5.0, 0.0, 10_000.0);
        CERO_COOLDOWN_SECONDS = builder
                .comment("Seconds between one Cero a player fires and the next.")
                .defineInRange("cero_cooldown_seconds", 4, 0, 120);
        CERO_DAMAGE_PER_ROOT_REIRYOKU = builder
                .comment("Damage a player's Cero gains for each square root of maximum reiryoku.")
                .defineInRange("cero_damage_per_root_reiryoku", 0.6, 0.0, 10.0);
        CERO_MAX_DAMAGE = builder
                .comment("Most damage a player's Cero deals.")
                .defineInRange("cero_max_damage", 80.0, 0.0, 1_000.0);
        CERO_RANGE = builder
                .comment("Blocks a player's Cero reaches.")
                .defineInRange("cero_range", 48.0, 4.0, 96.0);
        GILLIAN_FORM = form(builder, "gillian", "a Gillian", 6.0, 60.0, 0.7, 1.5, 1.0, 4.0, 1.0, 1.4, 0.2, 10.0, 50, 1.4);
        ADJUCHAS_FORM = form(builder, "adjuchas", "an Adjuchas", 1.5, 40.0, 1.3, 1.3, 2.0, 6.0, 1.5, 0.4, 0.4, 12.0, 25, 0.8);
        VASTO_LORDE_FORM = form(builder, "vasto_lorde", "a Vasto Lorde", 1.0, 60.0, 1.2, 1.6, 2.5, 10.0, 2.0, 0.0, 0.65, 16.0,
                15, 1.0);
        builder.pop();

        SPEC = builder.build();
    }

    private BleachConfig() {
    }

    /** One Menos form's section, {@code [hollow_evolution.<name>]}, with its defaults; {@code who} names it in comments. */
    private static MenosForm form(ModConfigSpec.Builder builder, String name, String who, double scale, double health,
                                  double speed, double claws, double clawRate, double hide, double regeneration, double step,
                                  double growth, double ceroDamage, int ceroChargeTicks, double ceroRadius) {
        builder.push(name);
        MenosForm form = new MenosForm(
                builder.comment("How many times a person's height " + who + " stands (a person is 1.8 blocks tall).")
                        .defineInRange("scale", scale, 1.0, 8.0),
                builder.comment("Maximum health " + who + " gains on top of a person's 20 (2 is a heart).")
                        .defineInRange("health_bonus", health, 0.0, 400.0),
                builder.comment("What the walking speed of " + who + " is multiplied by.")
                        .defineInRange("speed_multiplier", speed, 0.2, 3.0),
                builder.comment("What the damage the claws add is multiplied by for " + who + ".")
                        .defineInRange("claw_multiplier", claws, 0.5, 5.0),
                builder.comment("Bare-handed blows " + who + " strikes each second.")
                        .defineInRange("claw_attacks_per_second", clawRate, 0.25, 20.0),
                builder.comment("Armour points added to the hide of " + who + ", on top of a Hollow's (30 at most in all).")
                        .defineInRange("hide_bonus", hide, 0.0, 30.0),
                builder.comment("What a Hollow's regeneration is multiplied by for " + who + ".")
                        .defineInRange("regeneration_multiplier", regeneration, 0.0, 10.0),
                builder.comment("Blocks " + who + " steps up beyond a person's 0.6.")
                        .defineInRange("step_bonus", step, 0.0, 4.0),
                builder.comment("Share of maximum reiryoku added once, the first time a soul becomes " + who + ".")
                        .defineInRange("reiryoku_growth", growth, 0.0, 2.0),
                builder.comment("Damage of the Cero " + who + " fires at full gather, before the part that grows with"
                                + " maximum reiryoku.")
                        .defineInRange("cero_damage", ceroDamage, 0.0, 200.0),
                builder.comment("Ticks " + who + " takes to gather a Cero.")
                        .defineInRange("cero_charge_ticks", ceroChargeTicks, 5, 200),
                builder.comment("Half-width, in blocks, of the Cero beam " + who + " fires.")
                        .defineInRange("cero_radius", ceroRadius, 0.2, 4.0));
        builder.pop();
        return form;
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
