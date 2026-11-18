package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

/**
 * The hot spring in the cavern under Sōkyoku Hill: a player standing in water there has Regeneration II, which runs out
 * soon after it steps out. The cavern is found by the plan's geometry, so the same water anywhere else heals no one.
 */
public final class HotSpring {
    /** How often each player is looked at, in its own ticks. */
    public static final int CHECK_TICKS = 10;
    /**
     * How long each top-up lasts. Topped up only once it ends within {@link #CHECK_TICKS}, it runs from 60 down past 50
     * and 25, so it heals at Regeneration II's own pace and ends at most 3 seconds after the player leaves.
     */
    public static final int EFFECT_TICKS = 60;
    /** Regeneration II. */
    public static final int AMPLIFIER = 1;

    private HotSpring() {
    }

    /** Whether this block lies in the cavern under Sōkyoku Hill: in Soul Society, inside the plan's cavern. */
    public static boolean inCavern(Level level, BlockPos pos) {
        return level.dimension() == BleachWorlds.SOUL_SOCIETY && SeireiteiPlan.inCavern(pos.getX(), pos.getY(), pos.getZ());
    }

    /** A living player's tick: every {@link #CHECK_TICKS}, one in water with its eyes in the cavern is topped up. */
    static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_TICKS != 0 || player.isSpectator() || !player.isInWater()
                || !inCavern(player.level(), BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()))) {
            return;
        }
        MobEffectInstance held = player.getEffect(MobEffects.REGENERATION);
        if (held == null || held.getAmplifier() < AMPLIFIER || held.endsWithin(CHECK_TICKS)) {
            // Ambient, with no swirl but an icon, as reiatsu's pressure is.
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_TICKS, AMPLIFIER, true, false, true));
        }
    }
}
