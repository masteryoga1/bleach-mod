package io.github.masteryoga1.bleach.world;

import javax.annotation.Nullable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
     * DOME_TOP_Y. They hold the dome, whose outer face lies LAS_NOCHES_RADIUS from the cavern's ceiling at the centre.
     */
    public static final int LAS_NOCHES_RADIUS = 64, DOME_TOP_Y = 144;
    /** The las_noches_grounds biome's outer edge: the cells sampled from here out are the dunes', which alone grow trees. */
    public static final int GROUNDS_RADIUS = 216;
    /** A Garganta into Hueco Mundo sets a soul down about this far out at least: past Las Noches' wall, r 120 to 124. */
    public static final int ARRIVAL_RADIUS = 136;
    /** The furthest a quartz tree's branch reaches sideways from its trunk. */
    public static final int TREE_REACH = 5;
    /** Blocks from the centre for each unit of the router's temperature, which chooses the biome, as in Soul Society. */
    public static final double CLIMATE_SCALE = 2000.0;
    /** A cavern column is quartz trunk where the trunk noise, smoothed over 4-block cells, is above this. */
    public static final double TRUNK_THRESHOLD = 0.45;
    /** Las Noches' outer wall: the columns from 120 out to, not including, 124, standing from the ground to 116. */
    public static final int WALL_INNER_RADIUS = 120, WALL_OUTER_RADIUS = 124, WALL_TOP_Y = 116;
    /** Its four gateways, on the lines x = 0 and z = 0: 12 columns across, open from the ground to below y 111. */
    public static final int GATEWAY_HALF_WIDTH = 6, GATEWAY_TOP_Y = 111;
    /** The dome's inner face, its shell 2 thick inside LAS_NOCHES_RADIUS about the cavern's ceiling at the centre. */
    public static final int DOME_INNER_RADIUS = 62;
    /** The fifth tower on the dome's crown: 10 about the centre column, its room within 8 from DOME_TOP_Y, its top 208. */
    public static final int FIFTH_TOWER_RADIUS = 10, FIFTH_ROOM_RADIUS = 8, FIFTH_TOWER_TOP_Y = 208;
    /** The four towers on the dome's shoulders, about (±28, ±28): solid, 5 about, their tops at 176. */
    public static final int DOME_TOWER_OFFSET = 28, DOME_TOWER_RADIUS = 5, DOME_TOWER_TOP_Y = 176;
    /** The four great towers between the dome and the wall, about (±65, ±65): solid, 8 about, their tops at 184. */
    public static final int GREAT_TOWER_OFFSET = 65, GREAT_TOWER_RADIUS = 8, GREAT_TOWER_TOP_Y = 184;
    /** Every cell Las Noches lays: the wall's outer edge across, from the ground to under the fifth tower's top. */
    public static final BoundingBox SHELL_BOX = new BoundingBox(-WALL_OUTER_RADIUS, LEVEL_Y, -WALL_OUTER_RADIUS,
            WALL_OUTER_RADIUS - 1, FIFTH_TOWER_TOP_Y - 1, WALL_OUTER_RADIUS - 1);

    /** What Las Noches lays in a cell: its white stone, its white tiles or its dark band. */
    public enum Shell {
        STONE, TILES, TRIM
    }

    private HuecoMundoPlan() {
    }

    /** How far this block's centre lies from the centre column, as {@code bleach:radial} measures it. */
    public static double radius(int x, int z) {
        return SeireiteiPlan.radius(x, z);
    }

    /**
     * What Las Noches lays in this cell, or null: the first of a great tower, the fifth tower, a shoulder tower, the dome
     * and the wall that holds it. The towers on the dome stand on its inner face, so their stone replaces its tiles.
     */
    @Nullable
    public static Shell shellAt(int x, int y, int z) {
        if (y < LEVEL_Y || y >= FIFTH_TOWER_TOP_Y) {
            return null;
        }
        if (fromTower(x, z, GREAT_TOWER_OFFSET) < GREAT_TOWER_RADIUS) {
            return y < GREAT_TOWER_TOP_Y ? course(y, GREAT_TOWER_TOP_Y) : null;
        }
        double dome = fromDomeCentre(x, y, z), r = radius(x, z);
        if (r < FIFTH_TOWER_RADIUS) {
            return dome < DOME_INNER_RADIUS || inFifthRoom(x, y, z) ? null : course(y, FIFTH_TOWER_TOP_Y);
        }
        if (fromTower(x, z, DOME_TOWER_OFFSET) < DOME_TOWER_RADIUS) {
            return dome < DOME_INNER_RADIUS || y >= DOME_TOWER_TOP_Y ? null : course(y, DOME_TOWER_TOP_Y);
        }
        if (dome >= DOME_INNER_RADIUS && dome < LAS_NOCHES_RADIUS) {
            return Shell.TILES;
        }
        if (r < WALL_INNER_RADIUS || r >= WALL_OUTER_RADIUS || y >= WALL_TOP_Y) {
            return null;
        }
        return SeireiteiPlan.axisDistance(x, z) < GATEWAY_HALF_WIDTH && y < GATEWAY_TOP_Y ? null : course(y, WALL_TOP_Y);
    }

    /** Whether this cell is air the shell shuts in: under the dome, or in the fifth tower's room. */
    public static boolean enclosed(int x, int y, int z) {
        return y >= LEVEL_Y && (fromDomeCentre(x, y, z) < DOME_INNER_RADIUS || inFifthRoom(x, y, z));
    }

    /** Whether this cell lies in the fifth tower's room, from the crown up to under its roof of two courses of tiles. */
    private static boolean inFifthRoom(int x, int y, int z) {
        return radius(x, z) < FIFTH_ROOM_RADIUS && y >= DOME_TOP_Y && y < FIFTH_TOWER_TOP_Y - 2;
    }

    /** A course of the wall or a tower by its depth under that top: tiles, tiles, stone, trim, trim, then stone. */
    private static Shell course(int y, int top) {
        int under = top - 1 - y;
        return under < 2 ? Shell.TILES : under == 3 || under == 4 ? Shell.TRIM : Shell.STONE;
    }

    /** How far this column's centre lies from the nearest of four tower axes at (±offset, ±offset). */
    private static double fromTower(int x, int z, int offset) {
        double dx = Math.abs(x + 0.5) - offset, dz = Math.abs(z + 0.5) - offset;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** How far this block's centre lies from the dome's centre, the centre column's corner at the cavern's ceiling. */
    private static double fromDomeCentre(int x, int y, int z) {
        double dx = x + 0.5, dy = y + 0.5 - CAVERN_CEILING_Y, dz = z + 0.5;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
