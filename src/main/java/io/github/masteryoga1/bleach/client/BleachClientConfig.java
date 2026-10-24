package io.github.masteryoga1.bleach.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings for this player's own client: how the controls behave and what the screen shows. Stored in
 * {@code bleach-client.toml}.
 */
public final class BleachClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REISHI_FOOTING_ON_JUMP;
    public static final ModConfigSpec.BooleanValue HOLLOW_MASK_OVERLAY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REISHI_FOOTING_ON_JUMP = builder
                .comment("Whether pressing jump while falling stands you on reishi footing, once you can use it.",
                        "The reishi footing key works either way.")
                .define("reishi_footing_on_jump", true);
        HOLLOW_MASK_OVERLAY = builder
                .comment("Whether a Hollow sees the edge of its mask around the screen in first person.")
                .define("hollow_mask_overlay", true);
        SPEC = builder.build();
    }

    private BleachClientConfig() {
    }

    /** The setting, or its default before the config has loaded. */
    public static boolean footingOnJump() {
        return SPEC.isLoaded() ? REISHI_FOOTING_ON_JUMP.get() : REISHI_FOOTING_ON_JUMP.getDefault();
    }

    /** Whether to draw the mask's edge in first person, or the default before the config has loaded. */
    public static boolean maskOverlay() {
        return SPEC.isLoaded() ? HOLLOW_MASK_OVERLAY.get() : HOLLOW_MASK_OVERLAY.getDefault();
    }
}
