package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Dangai's rules, from numbers alone: the Kōryū's guard, when and where a lane's Kōtotsu is, and where and how a
 * crossing comes out.
 */
public final class DangaiLogic {
    /** Ticks after the Kōryū lets a soul go before it can hold that soul again. */
    public static final int REGRAB_GUARD_TICKS = 40;
    /** How long a soul the lane's end lets out falls slowly. */
    public static final int EXIT_SLOW_FALLING_TICKS = 200;
    /** The Rukongai districts, from the first, that a lane's end may let out over. */
    public static final int EXIT_DISTRICTS = 10;
    /** Ticks a soul whose way out of the Dangai was refused waits before it is tried again. */
    public static final int RETRY_TICKS = 20;
    /** How high over the ground a soul the Kōtotsu throws comes out, and how long it then falls slowly and reels. */
    public static final int THROW_HEIGHT = 32, THROW_SLOW_FALLING_TICKS = 300, THROW_NAUSEA_TICKS = 100;
    /** How many places a throw tries before it lets the soul out where the lane's end would have. */
    public static final int THROW_TRIES = 8;
    private static final double TICKS_PER_SECOND = 20.0;

    private DangaiLogic() {
    }

    /** Ticks in this many seconds, to the nearest. */
    public static int ticks(double seconds) {
        return (int) Math.round(seconds * TICKS_PER_SECOND);
    }

    /** The game time a lane's Kōtotsu sets out, for a crossing whose first soul enters the lane now. */
    public static long kototsuAt(long now) {
        return now + ticks(BleachConfig.get(BleachConfig.DANGAI_KOTOTSU_DELAY_SECONDS));
    }

    /** How far down its lane the Kōtotsu's front has come by now: z 0 until it sets out, then this many blocks a second. */
    public static double front(long kototsuAt, long now, double speed) {
        return now <= kototsuAt ? 0.0 : (now - kototsuAt) * speed / TICKS_PER_SECOND;
    }

    /** {@link #front} at the configured speed. */
    public static double front(long kototsuAt, long now) {
        return front(kototsuAt, now, BleachConfig.get(BleachConfig.DANGAI_KOTOTSU_SPEED));
    }

    /** Whether a front here has reached a body whose back is at this z. */
    public static boolean caught(double front, double backZ) {
        return front > backZ;
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

    /** A spot this far across from another, at this angle (radians from +x towards +z), at its height. */
    public static Vec3 offCourse(Vec3 from, double angle, double distance) {
        return new Vec3(from.x + distance * Math.cos(angle), from.y, from.z + distance * Math.sin(angle));
    }

    /** Whether a soul may come down here in Soul Society: outside the Shakonmaku, so clear of the Seireitei and its wall. */
    public static boolean outsideWall(double x, double z) {
        return Math.sqrt(x * x + z * z) >= SeireiteiPlan.SHAKONMAKU_RADIUS;
    }
}
