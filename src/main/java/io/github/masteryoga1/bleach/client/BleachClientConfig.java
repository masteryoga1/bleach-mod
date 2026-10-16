package io.github.masteryoga1.bleach.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings for this player's own client: how the controls behave. Stored in {@code bleach-client.toml}. */
public final class BleachClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REISHI_FOOTING_ON_JUMP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REISHI_FOOTING_ON_JUMP = builder
                .comment("Whether pressing jump while falling stands you on reishi footing, once you can use it.",
                        "The reishi footing key works either way.")
                .define("reishi_footing_on_jump", true);
        SPEC = builder.build();
    }

    private BleachClientConfig() {
    }

    /** The setting, or its default before the config has loaded. */
    public static boolean footingOnJump() {
        return SPEC.isLoaded() ? REISHI_FOOTING_ON_JUMP.get() : REISHI_FOOTING_ON_JUMP.getDefault();
    }
}
