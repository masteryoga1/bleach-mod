package io.github.masteryoga1.bleach.world;

/**
 * Soul Society's plan: every size and height that Java needs and the world's JSON must agree with. x and z are blocks
 * from the centre column, north is -z, and a height is the y a player stands at, one above the top block.
 */
public final class SeireiteiPlan {
    /** Seireitei's ground, the gate gaps' and the Rukongai's base. */
    public static final int PLATEAU_Y = 72;
    /** The Seireiheki's inner face: Seireitei is every block whose centre lies nearer the centre column than this. */
    public static final int SEIREITEI_RADIUS = 512;
    public static final int WALL_THICKNESS = 8;
    /** The wall's outer face, where the Rukongai's first district begins. */
    public static final int WALL_OUTER_RADIUS = SEIREITEI_RADIUS + WALL_THICKNESS;
    public static final int WALL_TOP_Y = 112;
    /** A gate's gap: wall blocks whose centre lies nearer an axis than this stay open, 16 blocks across. */
    public static final int GATE_HALF_WIDTH = 8;
    /** How far along its axis each gate house stands: mid-wall. */
    public static final int GATE_RADIUS = SEIREITEI_RADIUS + WALL_THICKNESS / 2;
    /** Sōkyoku Hill: a sheer mesa. */
    public static final int MESA_RADIUS = 64, MESA_TOP_Y = 136;
    /** The cavern inside the hill: air from its floor up to, not including, its ceiling. */
    public static final int CAVERN_RADIUS = 44, CAVERN_FLOOR_Y = 56, CAVERN_CEILING_Y = 120;
    public static final int DISTRICT_DEPTH = 32, DISTRICTS = 80;
    /** Where the 80th district ends and the wilds begin. */
    public static final int RUKONGAI_END_RADIUS = WALL_OUTER_RADIUS + DISTRICT_DEPTH * DISTRICTS;
    /** The lowest any Rukongai or wilds column stands, for any seed: the noise settings hold the ground up to here. */
    public static final int RUKONGAI_FLOOR_Y = 24;
    /** The Shakonmaku: a sphere about (0, PLATEAU_Y, 0) just clear of the wall's outer face. */
    public static final int SHAKONMAKU_RADIUS = WALL_OUTER_RADIUS + 8;
    /** Blocks from the centre for each unit of the router's temperature, which chooses the biome. */
    public static final double CLIMATE_SCALE = 2000.0;
    /** The hill's biome ends short of the mesa's edge, and the wall's spans the wall, both allowing for biome blur. */
    public static final int HILL_BIOME_RADIUS = 52, WALL_BIOME_INNER = 504, WALL_BIOME_OUTER = 528;
    /** The first districts of the rough and of the wilds' biome. */
    public static final int ROUGH_FROM_DISTRICT = 31, WILDS_FROM_DISTRICT = 79;

    private SeireiteiPlan() {
    }

    /** How far this block's centre lies from the centre column. */
    public static double radius(int x, int z) {
        double dx = x + 0.5, dz = z + 0.5;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** How far this block's centre lies from the nearer of the lines x = 0 and z = 0, on which the gates stand. */
    public static double axisDistance(int x, int z) {
        return Math.min(Math.abs(x + 0.5), Math.abs(z + 0.5));
    }

    /** Whether this column is part of the Seireiheki, not one of its gate gaps. */
    public static boolean inWall(int x, int z) {
        double r = radius(x, z);
        return r >= SEIREITEI_RADIUS && r < WALL_OUTER_RADIUS && axisDistance(x, z) >= GATE_HALF_WIDTH;
    }

    /** The radius where this Rukongai district, 1 to 80, begins. */
    public static int districtStart(int district) {
        return WALL_OUTER_RADIUS + DISTRICT_DEPTH * (district - 1);
    }
}
