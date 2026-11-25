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

    /** The halls' floor: the top course of the level ground, under the dome's whole foot, of tiles ringed with sky. */
    public static final int FLOOR_Y = LEVEL_Y - 1;
    /** The dome's inner face is lined with sky from here out to it, 1 thick, so the shell's own tiles stay as laid. */
    public static final int LINING_RADIUS = DOME_INNER_RADIUS - 1;
    /** The floor's five rings of sky, 1 wide, 10 apart: no open floor cell inside the dome's inner face is darker than 7. */
    public static final int RING_SPACING = 10, RINGS = 5;
    /** The open court about the fifth tower's foot: the corridors and the meeting hall begin this far along their arm. */
    public static final int COURT = 16;
    /** The doors through the dome's foot and the corridors behind them: 8 across, open from the floor to below 108. */
    public static final int DOOR_HALF_WIDTH = 4, DOOR_TOP_Y = 108;
    /** A corridor's walls and roof, banded as the wall is under their top; piers 2 long every 6, open under the band. */
    public static final int CORRIDOR_TOP_Y = 110, PIER_PERIOD = 6, PIER_LENGTH = 2, OPENING_TOP_Y = 105;
    /** The meeting hall on the north arm, from 16 to 39 along it, 18 across with its walls, its roof's top at 114. */
    public static final int HALL_FROM = 16, HALL_TO = 39, HALL_HALF_WIDTH = 8, HALL_TOP_Y = 114;
    /** Its long table, 2 across on the arm's line from 23 to 33 along, a seat each side at every other cell from 24. */
    public static final int TABLE_FROM = 23, TABLE_TO = 33;
    /** The stair in the fifth tower's foot: its well 10 about the centre, a core 6 about, 8 flights of 6 steps. */
    public static final int WELL_HALF = 5, CORE_HALF = 3, FLIGHT = 6;
    /** The door into the tower's foot from the court, on the north side: 4 across, open from the floor to below 102. */
    public static final int TOWER_DOOR_HALF_WIDTH = 2, TOWER_DOOR_TOP_Y = 102;
    /** The throne's back, banded and capped as a tower is, its top at 154. */
    public static final int THRONE_TOP_Y = 154;
    /** Every cell the halls lay or cut: the dome's foot across, from the floor to the throne room's ceiling. */
    public static final BoundingBox HALLS_BOX = new BoundingBox(-DOME_INNER_RADIUS, FLOOR_Y, -DOME_INNER_RADIUS,
            DOME_INNER_RADIUS - 1, FIFTH_TOWER_TOP_Y - 3, DOME_INNER_RADIUS - 1);

    /**
     * What the halls lay in a cell: air cut from the shell, Las Noches' three blocks, its sky, a stair whose high side
     * faces this way, or a slab on the cell's floor.
     */
    public enum Hall {
        CUT, STONE, TILES, TRIM, SKY, STAIR_NORTH, STAIR_EAST, STAIR_SOUTH, STAIR_WEST, SLAB
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

    /**
     * Whether this cell lies under the dome or in the fifth tower's room, open or not: the air the shell shuts in until
     * the halls are laid. Whether a world's cell is still shut in is {@link LasNoches#shutIn}'s question.
     */
    public static boolean enclosed(int x, int y, int z) {
        return y >= LEVEL_Y && (fromDomeCentre(x, y, z) < DOME_INNER_RADIUS || inFifthRoom(x, y, z));
    }

    /** Whether this cell lies in the fifth tower's room, from the crown up to under its roof of two courses of tiles. */
    private static boolean inFifthRoom(int x, int y, int z) {
        return radius(x, z) < FIFTH_ROOM_RADIUS && y >= DOME_TOP_Y && y < FIFTH_TOWER_TOP_Y - 2;
    }

    /**
     * Whether a camera in this cell sees the dome's sky: under the dome or in the fifth tower's room, or in that tower's
     * column from the floor up, so the stair through the crown never leaves it. A door's cut and all outside do not.
     */
    public static boolean underSky(int x, int y, int z) {
        return enclosed(x, y, z) || radius(x, z) < FIFTH_ROOM_RADIUS && y >= LEVEL_Y && y < FIFTH_TOWER_TOP_Y - 2;
    }

    /** A course of the wall or a tower by its depth under that top: tiles, tiles, stone, trim, trim, then stone. */
    private static Shell course(int y, int top) {
        int under = top - 1 - y;
        return under < 2 ? Shell.TILES : under == 3 || under == 4 ? Shell.TRIM : Shell.STONE;
    }

    /**
     * What the halls lay in this cell over the shell, or null for what stands there. Of the shell's own cells they name
     * only the four doors' and those of the stair's way up through the crown.
     */
    @Nullable
    public static Hall hallsAt(int x, int y, int z) {
        if (!HALLS_BOX.isInside(x, y, z)) {
            return null;
        }
        if (y == FLOOR_Y) {
            if (fromDomeCentre(x, LEVEL_Y, z) >= LAS_NOCHES_RADIUS) {
                return null;
            }
            double r = radius(x, z);
            return r % RING_SPACING < 1 && r >= RING_SPACING && r < RING_SPACING * RINGS + 1 ? Hall.SKY : Hall.TILES;
        }
        if (inFifthRoom(x, y, z)) {
            return throneRoom(x, y, z);
        }
        Shell shell = shellAt(x, y, z);
        if (radius(x, z) < FIFTH_TOWER_RADIUS && y < DOME_TOP_Y) {
            return towerFoot(x, y, z, shell != null);
        }
        int along = along(x, z);
        double across = SeireiteiPlan.axisDistance(x, z);
        if (shell != null) {
            // The doors: the dome's tiles cut through at its foot, facing each gateway.
            return shell == Shell.TILES && across < DOOR_HALF_WIDTH && y < DOOR_TOP_Y && along >= COURT ? Hall.CUT : null;
        }
        if (fromDomeCentre(x, y, z) >= DOME_INNER_RADIUS) {
            return null;
        }
        boolean hallFootprint = Math.abs(x + 0.5) < Math.abs(z + 0.5) && z < 0 && along >= HALL_FROM && along <= HALL_TO
                && across < HALL_HALF_WIDTH + 1;
        if (hallFootprint && y < HALL_TOP_Y) {
            return hall(x, y, along, across);
        }
        if (!hallFootprint && along >= COURT && across < DOOR_HALF_WIDTH + 1 && y < CORRIDOR_TOP_Y) {
            if (across < DOOR_HALF_WIDTH) {
                return y < DOOR_TOP_Y ? null : Hall.TILES;
            }
            boolean pier = (along - COURT) % PIER_PERIOD < PIER_LENGTH;
            return pier || y >= OPENING_TOP_Y ? hallsCourse(y, CORRIDOR_TOP_Y) : null;
        }
        return fromDomeCentre(x, y, z) >= LINING_RADIUS ? Hall.SKY : null;
    }

    /** Whole cells out along the nearer of the two axes' arms: 0 for the four columns about the centre's corner. */
    private static int along(int x, int z) {
        return (int) (Math.max(Math.abs(x + 0.5), Math.abs(z + 0.5)) - 0.5);
    }

    /** The meeting hall, under its roof: walls banded as a tower's, a door at each end, and the table and its seats. */
    @Nullable
    private static Hall hall(int x, int y, int along, double across) {
        if (across >= HALL_HALF_WIDTH || along == HALL_FROM || along == HALL_TO) {
            boolean door = (along == HALL_FROM || along == HALL_TO) && across < DOOR_HALF_WIDTH && y < DOOR_TOP_Y;
            return door ? null : hallsCourse(y, HALL_TOP_Y);
        }
        if (y >= HALL_TOP_Y - 2) {
            return Hall.TILES;
        }
        if (y != LEVEL_Y) {
            return null;
        }
        if (across < 1 && along >= TABLE_FROM && along <= TABLE_TO) {
            return Hall.TRIM;
        }
        if (across < 1 && along == TABLE_FROM - 1) {
            // The head's seat, its back to the court.
            return Hall.STAIR_SOUTH;
        }
        if (across >= 1 && across < 2 && along > TABLE_FROM && along < TABLE_TO && (along - TABLE_FROM) % 2 == 1) {
            // A seat each side, its back to the aisle.
            return x < 0 ? Hall.STAIR_WEST : Hall.STAIR_EAST;
        }
        return null;
    }

    /**
     * The fifth tower's foot under the dome: solid stone about a stair that climbs its well to the crown, cutting the
     * crown's stone where a step or the air over one meets it, with a door from the court on the north.
     */
    @Nullable
    private static Hall towerFoot(int x, int y, int z, boolean shell) {
        double ax = Math.abs(x + 0.5), az = Math.abs(z + 0.5);
        if (ax < CORE_HALF && az < CORE_HALF) {
            // The core, lit at its four corners from the floor to the crown.
            return shell ? null : ax == CORE_HALF - 0.5 && az == CORE_HALF - 0.5 ? Hall.SKY : Hall.STONE;
        }
        if (ax < WELL_HALF && az < WELL_HALF) {
            Hall tread = stair(x, y, z);
            if (!shell || tread != null) {
                return tread;
            }
            for (int h = 1; h <= 3; h++) {
                Hall under = stair(x, y - h, z);
                if (under != null && under != Hall.TILES) {
                    return Hall.CUT;
                }
            }
            return null;
        }
        boolean door = z < 0 && ax < TOWER_DOOR_HALF_WIDTH && y < TOWER_DOOR_TOP_Y;
        return door || shell ? null : Hall.STONE;
    }

    /**
     * The stair's cell here, or null: the channel about the core runs south on its east side, west on its south, north
     * on its west and east on its north, each side a flight of 6 steps, its corners landings, twice round.
     */
    @Nullable
    private static Hall stair(int x, int y, int z) {
        boolean east = x == 3 || x == 4, south = z == 3 || z == 4, west = x == -5 || x == -4, north = z == -5 || z == -4;
        int side, step;
        Hall facing;
        if (east && z >= -3 && z <= 2) {
            side = 0;
            step = z + 3;
            facing = Hall.STAIR_SOUTH;
        } else if (south && x >= -3 && x <= 2) {
            side = 1;
            step = 2 - x;
            facing = Hall.STAIR_WEST;
        } else if (west && z >= -3 && z <= 2) {
            side = 2;
            step = 2 - z;
            facing = Hall.STAIR_NORTH;
        } else if (north && x >= -3 && x <= 2) {
            side = 3;
            step = x + 3;
            facing = Hall.STAIR_EAST;
        } else {
            // A corner: the landing after its side's flight, in both rounds but the last, whose top is the crown.
            int corner = east && south ? 0 : west && south ? 1 : west ? 2 : 3;
            for (int flight = corner; flight < 7; flight += 4) {
                if (y == LEVEL_Y + FLIGHT * flight + FLIGHT - 1) {
                    return Hall.TILES;
                }
            }
            return null;
        }
        for (int flight = side; flight < 8; flight += 4) {
            if (y == LEVEL_Y + FLIGHT * flight + step) {
                return facing;
            }
        }
        return null;
    }

    /** The throne room: lined with sky, and on the south a dais of three steps with the throne on it. */
    @Nullable
    private static Hall throneRoom(int x, int y, int z) {
        if (radius(x, z) >= FIFTH_ROOM_RADIUS - 1 || y == FIFTH_TOWER_TOP_Y - 3) {
            return Hall.SKY;
        }
        if (x < -3 || x > 2 || z < 2 || z > 6) {
            return null;
        }
        int height = Math.min(z - 1, 3);
        if (y < DOME_TOP_Y + height) {
            return y == DOME_TOP_Y + height - 1 && z <= 4 ? Hall.STAIR_SOUTH : Hall.TILES;
        }
        if (z == 5 && y == DOME_TOP_Y + 3) {
            return x == -1 || x == 0 ? Hall.STAIR_SOUTH : x == -2 || x == 1 ? Hall.SLAB : null;
        }
        return z == 6 && (x == -1 || x == 0) && y < THRONE_TOP_Y ? hallsCourse(y, THRONE_TOP_Y) : null;
    }

    /** A course of the halls' walls by its depth under that top, as the shell's courses run. */
    private static Hall hallsCourse(int y, int top) {
        return switch (course(y, top)) {
            case STONE -> Hall.STONE;
            case TILES -> Hall.TILES;
            case TRIM -> Hall.TRIM;
        };
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
