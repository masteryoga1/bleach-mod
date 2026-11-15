package io.github.masteryoga1.bleach.world;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Shakonmaku, the membrane over Seireitei: a sphere of {@link SeireiteiPlan#SHAKONMAKU_RADIUS} about the plateau's
 * middle that, with the Seireiheki below it, parts Soul Society into three sides. It is no block: every block's cell
 * lies on one side, and a line crosses it where it steps from an inside cell straight to an outside one, or back.
 */
public final class Shakonmaku {
    /** Where a cell lies: inside the wall or under the membrane above it, in a gate's gap, or anywhere else. */
    public enum Side {
        INSIDE, GATEWAY, OUTSIDE
    }

    /** The sphere's centre height, the plateau's. */
    private static final double CENTRE_Y = SeireiteiPlan.PLATEAU_Y;
    /** The sphere's radius, squared. */
    private static final double RADIUS_SQR = square(SeireiteiPlan.SHAKONMAKU_RADIUS);
    /** How far any point can lie from its cell's centre, rounded up: half a block's diagonal is under a block. */
    private static final double SLACK = 1.0;
    /** Points this far within the inner face and the sphere lie in inside cells, and so does every line between two. */
    private static final double CORE_RADIUS_SQR = square(SeireiteiPlan.SEIREITEI_RADIUS - SLACK);
    private static final double CORE_SPHERE_SQR = square(SeireiteiPlan.SHAKONMAKU_RADIUS - SLACK);
    /** No cell this far from the centre is inside, so no line crosses there: the far sphere, squared. */
    private static final double FAR_SQR = square(SeireiteiPlan.SHAKONMAKU_RADIUS + SLACK);
    /** The most cells a line within the far sphere can enter: one a face, and no axis spans more than its diameter. */
    private static final int MOST_CELLS = Mth.ceil(2.0 * (SeireiteiPlan.SHAKONMAKU_RADIUS + SLACK) * Math.sqrt(3.0)) + 3;

    private Shakonmaku() {
    }

    /** Whether this level is Soul Society, the only world the membrane stands in. */
    public static boolean in(Level level) {
        return level.dimension() == BleachWorlds.SOUL_SOCIETY;
    }

    /**
     * The side this block's cell lies on: in a gate's gap below the wall's top, inside if its centre is under the
     * sphere and nearer the centre column than the inner face or at the wall's top or above, else outside.
     */
    public static Side side(int x, int y, int z) {
        double r = SeireiteiPlan.radius(x, z);
        if (y < SeireiteiPlan.WALL_TOP_Y && r >= SeireiteiPlan.SEIREITEI_RADIUS && r < SeireiteiPlan.WALL_OUTER_RADIUS
                && SeireiteiPlan.axisDistance(x, z) < SeireiteiPlan.GATE_HALF_WIDTH) {
            return Side.GATEWAY;
        }
        double up = y + 0.5 - CENTRE_Y;
        boolean under = r * r + up * up < RADIUS_SQR;
        return under && (r < SeireiteiPlan.SEIREITEI_RADIUS || y >= SeireiteiPlan.WALL_TOP_Y) ? Side.INSIDE : Side.OUTSIDE;
    }

    /** The side of the cell this point is in. */
    public static Side side(Vec3 at) {
        return side(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
    }

    /**
     * Where the line from {@code from} to {@code to} first steps straight from an inside cell to an outside one or back,
     * a gate's gap between them never counting: the point and face where it enters the first cell on the far side, or
     * null. A point on a face lies in the cell on its positive side, and only the line's part within the far sphere is
     * walked (none if an end is not finite).
     */
    @Nullable
    public static BlockHitResult crossing(Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.equals(to) || core(from) && core(to)) {
            return null;
        }
        // Where the line is within the far sphere: the roots of |from + t * way - centre|^2 = FAR_SQR, kept to [0, 1].
        Vec3 way = to.subtract(from);
        double a = way.lengthSqr();
        double b = from.x * way.x + (from.y - CENTRE_Y) * way.y + from.z * way.z;
        double root = Math.sqrt(b * b - a * (distanceSqr(from) - FAR_SQR));
        double enter = Math.max(0.0, (-b - root) / a);
        double leave = Math.min(1.0, (-b + root) / a);
        if (!(enter < leave)) {
            return null;
        }
        Vec3 start = enter > 0.0 ? from.add(way.scale(enter)) : from;
        Vec3 end = leave < 1.0 ? from.add(way.scale(leave)) : to;
        return walk(start, end);
    }

    /** Whether the line from {@code from} to {@code to} crosses the membrane anywhere. */
    public static boolean crosses(Vec3 from, Vec3 to) {
        return crossing(from, to) != null;
    }

    /** The cell walk of {@link #crossing}, along a line that lies within the far sphere. */
    @Nullable
    private static BlockHitResult walk(Vec3 from, Vec3 to) {
        Vec3 way = to.subtract(from);
        BlockPos.MutableBlockPos cell = BlockPos.containing(from).mutable();
        Side before = side(cell);
        for (int entered = 0; entered < MOST_CELLS; entered++) {
            // The face the line passes next: one met heading the positive way is passed there, the other way past it.
            Direction ahead = null;
            double soonest = Double.POSITIVE_INFINITY;
            for (Direction.Axis axis : Direction.Axis.VALUES) {
                double step = way.get(axis);
                double at = step > 0.0 ? (cell.get(axis) + 1 - from.get(axis)) / step
                        : step < 0.0 ? (cell.get(axis) - from.get(axis)) / step : Double.POSITIVE_INFINITY;
                if (at < soonest && (at < 1.0 || at == 1.0 && step > 0.0)) {
                    soonest = at;
                    ahead = Direction.fromAxisAndDirection(axis,
                            step > 0.0 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
                }
            }
            if (ahead == null) {
                return null;
            }
            Side here = side(cell.move(ahead));
            if (before != here && before != Side.GATEWAY && here != Side.GATEWAY) {
                return new BlockHitResult(from.add(way.scale(soonest)), ahead.getOpposite(), cell.immutable(), false);
            }
            before = here;
        }
        return null;
    }

    /** The side this cell lies on. */
    private static Side side(BlockPos cell) {
        return side(cell.getX(), cell.getY(), cell.getZ());
    }

    /** Within the inner face and the sphere by a cell's slack: a set whose every line between two points stays inside. */
    private static boolean core(Vec3 at) {
        return at.x * at.x + at.z * at.z < CORE_RADIUS_SQR && distanceSqr(at) < CORE_SPHERE_SQR;
    }

    /** The square of this point's distance from the sphere's centre. */
    private static double distanceSqr(Vec3 at) {
        double up = at.y - CENTRE_Y;
        return at.x * at.x + up * up + at.z * at.z;
    }

    /** Whether each of this point's coordinates is a finite number. */
    private static boolean finite(Vec3 at) {
        return Double.isFinite(at.x) && Double.isFinite(at.y) && Double.isFinite(at.z);
    }

    /** This value times itself. */
    private static double square(double value) {
        return value * value;
    }
}
