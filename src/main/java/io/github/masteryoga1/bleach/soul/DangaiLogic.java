package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The Dangai's rules, from numbers alone: the Kōryū's guard, and where and how a crossing comes out. */
public final class DangaiLogic {
    /** Ticks after the Kōryū lets a soul go before it can hold that soul again. */
    public static final int REGRAB_GUARD_TICKS = 40;
    /** How long a soul the lane's end lets out falls slowly. */
    public static final int EXIT_SLOW_FALLING_TICKS = 200;
    /** The Rukongai districts, from the first, that a lane's end may let out over. */
    public static final int EXIT_DISTRICTS = 10;
    /** Ticks a soul whose way out of the Dangai was refused waits before it is tried again. */
    public static final int RETRY_TICKS = 20;
    private static final double TICKS_PER_SECOND = 20.0;

    private DangaiLogic() {
    }

    /** Ticks in this many seconds, to the nearest. */
    public static int ticks(double seconds) {
        return (int) Math.round(seconds * TICKS_PER_SECOND);
    }

    /** A gate's bearing from the Human World's centre column: radians from +x towards +z. */
    public static float bearing(double x, double z) {
        return (float) Mth.atan2(z, x);
    }

    /** Over the middle of this Rukongai district, on this bearing from Soul Society's centre column, at y 0. */
    public static Vec3 overDistrict(int district, float bearing) {
        double r = SeireiteiPlan.districtStart(district) + SeireiteiPlan.DISTRICT_DEPTH / 2.0;
        return new Vec3(r * Math.cos(bearing), 0.0, r * Math.sin(bearing));
    }
}
