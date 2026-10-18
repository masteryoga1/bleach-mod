package io.github.masteryoga1.bleach.soul;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Five Seele Schneider standing in a pentagon, the shape Sprenger sets off: corners spread evenly
 * enough round their centre (no gap under 36 or over 108 degrees), between 1.5 and 10 blocks out
 * and no corner more than twice as far as another, at most 3 blocks apart in height, and strictly
 * convex. Pure geometry, so game tests can try shapes without blades. What it covers runs from a
 * block below the lowest corner to five above the highest.
 *
 * @param corners    the five corners in order round the centre (by angle in the xz plane)
 * @param centreX    the corners' mean x
 * @param centreZ    the corners' mean z
 * @param minY       the lowest corner's y
 * @param maxY       the highest corner's y
 * @param meanRadius the corners' mean distance from the centre in the xz plane
 */
public record SprengerShape(List<Vec3> corners, double centreX, double centreZ, double minY, double maxY,
                            double meanRadius) {
    public static final int CORNERS = 5;

    private static final double MIN_GAP = Math.toRadians(36.0);
    private static final double MAX_GAP = Math.toRadians(108.0);
    private static final double MIN_RADIUS = 1.5, MAX_RADIUS = 10.0, MAX_RADIUS_RATIO = 2.0;
    private static final double MAX_Y_SPREAD = 3.0;
    private static final double BELOW = 1.0, ABOVE = 5.0;
    /** Slack for corners exactly on a limit, so a shape built to the limit is not lost to rounding. */
    private static final double EPSILON = 1.0E-9;

    public SprengerShape {
        corners = List.copyOf(corners);
    }

    /** The shape these five points make, or null if they are not five points that make a valid one. */
    @Nullable
    public static SprengerShape of(List<Vec3> points) {
        if (points == null || points.size() != CORNERS) {
            return null;
        }
        double sumX = 0.0, sumZ = 0.0;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Vec3 point : points) {
            if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
                return null;
            }
            sumX += point.x;
            sumZ += point.z;
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        if (maxY - minY > MAX_Y_SPREAD + EPSILON) {
            return null;
        }
        double centreX = sumX / CORNERS;
        double centreZ = sumZ / CORNERS;
        List<Vec3> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(point -> Math.atan2(point.z - centreZ, point.x - centreX)));
        double[] angles = new double[CORNERS];
        double minRadius = Double.POSITIVE_INFINITY, maxRadius = 0.0, sumRadius = 0.0;
        for (int i = 0; i < CORNERS; i++) {
            Vec3 corner = sorted.get(i);
            double dx = corner.x - centreX;
            double dz = corner.z - centreZ;
            double radius = Math.sqrt(dx * dx + dz * dz);
            angles[i] = Math.atan2(dz, dx);
            minRadius = Math.min(minRadius, radius);
            maxRadius = Math.max(maxRadius, radius);
            sumRadius += radius;
        }
        if (minRadius < MIN_RADIUS - EPSILON || maxRadius > MAX_RADIUS + EPSILON
                || maxRadius > minRadius * MAX_RADIUS_RATIO + EPSILON) {
            return null;
        }
        for (int i = 0; i < CORNERS; i++) {
            // The last gap wraps round from the last corner to the first.
            double gap = i < CORNERS - 1 ? angles[i + 1] - angles[i] : angles[0] + 2.0 * Math.PI - angles[i];
            if (gap < MIN_GAP - EPSILON || gap > MAX_GAP + EPSILON) {
                return null;
            }
        }
        int turn = 0;
        for (int i = 0; i < CORNERS; i++) {
            Vec3 a = sorted.get(i);
            Vec3 b = sorted.get((i + 1) % CORNERS);
            Vec3 c = sorted.get((i + 2) % CORNERS);
            double cross = (b.x - a.x) * (c.z - b.z) - (b.z - a.z) * (c.x - b.x);
            if (Math.abs(cross) <= EPSILON) {
                return null;
            }
            int sign = cross > 0.0 ? 1 : -1;
            if (turn != 0 && sign != turn) {
                return null;
            }
            turn = sign;
        }
        return new SprengerShape(sorted, centreX, centreZ, minY, maxY, sumRadius / CORNERS);
    }

    /** Whether this point stands inside the pentagon (an even-odd ray test in the xz plane) and within its height. */
    public boolean contains(Vec3 point) {
        if (!(point.y >= minY - BELOW && point.y <= maxY + ABOVE)) {
            return false;
        }
        boolean inside = false;
        int count = corners.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Vec3 a = corners.get(i);
            Vec3 b = corners.get(j);
            if ((a.z > point.z) != (b.z > point.z)
                    && point.x < (b.x - a.x) * (point.z - a.z) / (b.z - a.z) + a.x) {
                inside = !inside;
            }
        }
        return inside;
    }
}
