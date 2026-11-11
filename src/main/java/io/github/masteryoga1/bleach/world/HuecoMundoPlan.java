package io.github.masteryoga1.bleach.world;

/**
 * Hueco Mundo's plan: every height and radius that Java needs and the world's JSON must agree with. x and z are blocks
 * from the centre column, a height is the y a player stands at, one above the top block, and each holds for any seed.
 */
public final class HuecoMundoPlan {
    /** The world's floor and height: y 0 up to, not including, 256. */
    public static final int MIN_Y = 0, HEIGHT = 256;
    /** Bedrock lies at y 0 and thins out upward, none from this y. */
    public static final int BEDROCK_TOP_Y = 5;
    /** The Forest of Menos: air or quartz trunk from its floor up to, not including, its ceiling, under the whole world. */
    public static final int CAVERN_FLOOR_Y = 24, CAVERN_CEILING_Y = 80;
    /** No white sand lies below this y, so the white sandstone from the cavern's ceiling up to here holds all the sand. */
    public static final int SAND_FROM_Y = 84;
    /** Every column stands between these, the dunes' lowest and highest. */
    public static final int DUNE_LOW_Y = 88, DUNE_HIGH_Y = 108;
    /** Las Noches' level ground: every column whose centre lies within LEVEL_RADIUS of the centre column stands here. */
    public static final int LEVEL_Y = 96, LEVEL_RADIUS = 200;
    /** Where the slope up or down from the level ground meets the dunes in full. */
    public static final int SLOPE_END_RADIUS = 232;
    /**
     * The las_noches biome: the cells sampled nearer the centre column than LAS_NOCHES_RADIUS, from y 80 to 140, below
     * DOME_TOP_Y.
     */
    public static final int LAS_NOCHES_RADIUS = 64, DOME_TOP_Y = 144;
    /** The las_noches_grounds biome's outer edge: the cells sampled from here out are the dunes', which alone grow trees. */
    public static final int GROUNDS_RADIUS = 216;
    /** The furthest a quartz tree's branch reaches sideways from its trunk. */
    public static final int TREE_REACH = 5;
    /** Blocks from the centre for each unit of the router's temperature, which chooses the biome, as in Soul Society. */
    public static final double CLIMATE_SCALE = 2000.0;
    /** A cavern column is quartz trunk where the trunk noise, smoothed over 4-block cells, is above this. */
    public static final double TRUNK_THRESHOLD = 0.45;

    private HuecoMundoPlan() {
    }

    /** How far this block's centre lies from the centre column, as {@code bleach:radial} measures it. */
    public static double radius(int x, int z) {
        return SeireiteiPlan.radius(x, z);
    }
}
