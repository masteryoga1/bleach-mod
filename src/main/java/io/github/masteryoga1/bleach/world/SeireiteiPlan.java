package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Soul Society's plan: every size and height that Java needs and the world's JSON must agree with, and the places where
 * its buildings stand. x and z are blocks from the centre column, north is -z, and a height is the y a player stands at,
 * one above the top block.
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
    /** A gate house's doorway: its gap's columns nearer the axis than this, open from the plateau up to its head. */
    public static final int GATE_DOOR_HALF_WIDTH = 5, GATE_DOOR_TOP_Y = PLATEAU_Y + 25;
    /**
     * The great gate's row in its house, counted from the wall's inner face (the outer of the two mid-wall rows), and how
     * many rows it rises by, into the slot left open over the doorway in that row.
     */
    public static final int GATE_ROW = WALL_THICKNESS / 2, GATE_LIFT = 12;
    /** The wicket's opening: this many rows from the ground through the right-hand pier, coming in from the Rukongai. */
    public static final int GATE_WICKET_ROWS = 2;
    /** A gap cell this far out or more lies on the Rukongai's half, beyond the great gate's outer face. */
    public static final int GATE_FACE_RADIUS = GATE_RADIUS + 1;
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
    /** The Senkaimon court, where every gate from the Human World comes out: a paved square north-east of the hill. */
    public static final int SENKAIMON_COURT_X = SEIREITEI_RADIUS * 3 / 8, SENKAIMON_COURT_Z = -SEIREITEI_RADIUS * 9 / 16;
    /** The Shin'ō Academy's north-east corner, where its template is placed from; its gate faces east. */
    public static final int ACADEMY_X = -192, ACADEMY_Z = 224;
    /** The y a soul stands at on the Academy's floor, which is laid on the lawn. */
    public static final int ACADEMY_FLOOR_Y = PLATEAU_Y + 1;
    /** Where a soul sent to the Academy lands: on the path two blocks inside its gate. */
    public static final double ACADEMY_LANDING_X = ACADEMY_X - 1.5, ACADEMY_LANDING_Z = ACADEMY_Z + 7.5;
    /** Sōkyoku Hill's reserve: every column nearer the centre column than this, kept for the hill's own works. */
    public static final int HILL_RESERVE_RADIUS = 140;
    /** The Senkaimon court's reserve: this many columns each side of the block its gates come out on. */
    public static final int COURT_RESERVE_HALF = 24;
    /** A gate's forecourt: this many columns each side of its axis, this many deep in from the wall's inner face. */
    public static final int FORECOURT_HALF_WIDTH = 20, FORECOURT_DEPTH = 48;
    /** How many columns about each site's own are kept for it too. */
    public static final int SITE_MARGIN = 4;
    /** A barracks compound's side: two chunks, its corners on chunk lines. */
    public static final int BARRACKS_SIZE = 32;
    /** A barracks gateway's first column and width in its template's north wall, as make_structures.py's GATEWAY. */
    public static final int BARRACKS_GATEWAY_X = 14, BARRACKS_GATEWAY_WIDTH = 4;
    /** An avenue's columns each side of its gate's axis: its doorway's. The two ring roads' width. */
    public static final int AVENUE_HALF_WIDTH = GATE_DOOR_HALF_WIDTH, RING_WIDTH = 6;
    /** The outer ring road's inner edge: past the Academy grounds' far corner, short of the diagonal compounds'. */
    public static final double OUTER_RING_RADIUS = 418.5;
    /** City blocks repeat every two chunks, a lane this many columns each side of each chunk-pair line. */
    public static final int BLOCK_PITCH = 32, LANE_HALF_WIDTH = 2;
    /** The unwalled lane along the Seireiheki's inner face, and a lane wall's height: plinth, plaster and its cap. */
    public static final int WALL_LANE = 4, LANE_WALL_HEIGHT = 4;
    /** The court square's lanterns: on its diagonals, this many columns out from its arrival block. */
    public static final int COURT_LANTERN_NEAR = 6, COURT_LANTERN_FAR = 18;

    /** Every planned site: a template set whole at its corner, turned, on the ground there, one structure for each group. */
    public static final List<Site> SITES = List.of(
            new Site("academy", ACADEMY_X, ACADEMY_Z, BleachMod.id("academy/academy"), Rotation.CLOCKWISE_90, 15, 17),
            // The gate houses in the order of the Rukongai's regions, each the south one turned about the centre column.
            gateHouse(SEIREITEI_RADIUS, GATE_HALF_WIDTH - 1, Rotation.COUNTERCLOCKWISE_90),
            gateHouse(-GATE_HALF_WIDTH, SEIREITEI_RADIUS, Rotation.NONE),
            gateHouse(-SEIREITEI_RADIUS - 1, -GATE_HALF_WIDTH, Rotation.CLOCKWISE_90),
            gateHouse(GATE_HALF_WIDTH - 1, -SEIREITEI_RADIUS - 1, Rotation.CLOCKWISE_180),
            // The Gotei 13's barracks, each on chunk lines: the First's at the north gate's way in by the hill, its gate up
            // that way; the rest clockwise round a ring about 460 out from 15°, each gate facing the centre column.
            barracks("first", -16, -192, Rotation.NONE), barracks("second", 127, -433, Rotation.CLOCKWISE_180),
            barracks("third", 335, -305, Rotation.CLOCKWISE_180), barracks("fourth", 432, -97, Rotation.COUNTERCLOCKWISE_90),
            barracks("fifth", 432, 127, Rotation.COUNTERCLOCKWISE_90), barracks("sixth", 304, 304, Rotation.NONE),
            barracks("seventh", 96, 432, Rotation.NONE), barracks("eighth", -128, 432, Rotation.NONE),
            barracks("ninth", -336, 304, Rotation.NONE), barracks("tenth", -433, 96, Rotation.CLOCKWISE_90),
            barracks("eleventh", -433, -128, Rotation.CLOCKWISE_90), barracks("twelfth", -305, -305, Rotation.CLOCKWISE_180),
            barracks("thirteenth", -97, -433, Rotation.CLOCKWISE_180));
    /**
     * The plan's reserves, which no later street, wall or site may enter but its own group's: Sōkyoku Hill, the Academy's
     * grounds, the Senkaimon court and each gate's forecourt, then every site with its margin, in the plan's order.
     */
    public static final List<Reserve> RESERVES = reserves();
    /** The north avenue's last row: the one before the First's reserve, short of its gate. */
    private static final int NORTH_AVENUE_END_Z = sites("barracks").get(0).reserve().minZ() - 1;
    /** Each barracks gate's paved path, in {@code sites("barracks")}' order: see {@link #gatePaths()}. */
    private static final List<BoundingBox> GATE_PATHS = makeGatePaths();

    private SeireiteiPlan() {
    }

    /** A gate house filling a gap of the wall, its template's corner at the gap's corner here, its front facing out. */
    private static Site gateHouse(int x, int z, Rotation rotation) {
        return new Site("gate", x, z, BleachMod.id("gate/house"), rotation, 2 * GATE_HALF_WIDTH, WALL_THICKNESS);
    }

    /** A division's barracks, its template named as the division's banner is, its corner on a chunk's. */
    private static Site barracks(String division, int x, int z, Rotation rotation) {
        return new Site("barracks", x, z, BleachMod.id("barracks/" + division), rotation, BARRACKS_SIZE, BARRACKS_SIZE);
    }

    /** The named reserves, then each site's. */
    private static List<Reserve> reserves() {
        int hill = HILL_RESERVE_RADIUS, court = COURT_RESERVE_HALF, half = FORECOURT_HALF_WIDTH;
        int near = SEIREITEI_RADIUS - FORECOURT_DEPTH, far = SEIREITEI_RADIUS - 1;
        List<Reserve> rows = new ArrayList<>(List.of(new Reserve("hill", -hill, -hill, hill - 1, hill - 1, true),
                // The Academy's hall in its north-east corner, a forecourt before its gate, and room west and south.
                new Reserve("academy", -288, 208, -177, 303, false),
                new Reserve("court", SENKAIMON_COURT_X - court, SENKAIMON_COURT_Z - court, SENKAIMON_COURT_X + court,
                        SENKAIMON_COURT_Z + court, false),
                // The forecourts in the gate sites' order: east, south, west, north.
                new Reserve("gate", near, -half, far, half - 1, false),
                new Reserve("gate", -half, near, half - 1, far, false),
                new Reserve("gate", -far - 1, -half, -near - 1, half - 1, false),
                new Reserve("gate", -half, -far - 1, half - 1, -near - 1, false)));
        SITES.forEach(site -> rows.add(site.reserve()));
        return List.copyOf(rows);
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

    /** Whether this block lies in a gate's gap, below the wall's top: the gate house's cells and the ground under them. */
    public static boolean inGap(int x, int y, int z) {
        double r = radius(x, z);
        return y < WALL_TOP_Y && r >= SEIREITEI_RADIUS && r < WALL_OUTER_RADIUS && axisDistance(x, z) < GATE_HALF_WIDTH;
    }

    /**
     * How far this block's centre lies across its gate's gap from the gap's middle, signed so that every wicket's way
     * lies at the same positive distance.
     */
    public static double across(int x, int z) {
        double dx = x + 0.5, dz = z + 0.5;
        return Math.abs(dx) < Math.abs(dz) ? Math.signum(dz) * dx : -Math.signum(dx) * dz;
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

    /** The group's sites, in the plan's order. */
    public static List<Site> sites(String group) {
        return SITES.stream().filter(site -> site.group().equals(group)).toList();
    }

    /** The first reserve that keeps this column, the named ones before the sites', or null if none does. */
    @Nullable
    public static Reserve reservedBy(int x, int z) {
        for (Reserve reserve : RESERVES) {
            if (reserve.contains(x, z)) {
                return reserve;
            }
        }
        return null;
    }

    /** What Seireitei's streets lay on this column, from the column alone: nothing, paving, the court's, or a lane wall. */
    public static Street street(int x, int z) {
        if (radius(x, z) >= SEIREITEI_RADIUS) {
            return Street.NONE;
        }
        Reserve kept = reservedBy(x, z);
        String group = kept == null ? "" : kept.group();
        if (group.equals("court")) {
            int dx = Math.abs(x - SENKAIMON_COURT_X), dz = Math.abs(z - SENKAIMON_COURT_Z);
            return dx + dz == 0 ? Street.MARK
                    : dx == dz && (dx == COURT_LANTERN_NEAR || dx == COURT_LANTERN_FAR) ? Street.LANTERN : Street.PAVING;
        }
        if (avenue(x, z) || ring(x, z) || onGatePath(x, z)) {
            // Paving crosses a gate's forecourt, and a compound's margin only on its own gate's path.
            return kept == null || group.equals("gate") || group.equals("barracks") && onGatePath(x, z)
                    ? Street.PAVING : Street.NONE;
        }
        if (kept != null || lane(x) || lane(z) || lane(x + BLOCK_PITCH / 2) || lane(z + BLOCK_PITCH / 2)
                || radius(x, z) >= SEIREITEI_RADIUS - WALL_LANE) {
            return Street.NONE;
        }
        return bordersWay(x + 1, z) || bordersWay(x - 1, z) || bordersWay(x, z + 1) || bordersWay(x, z - 1)
                ? Street.WALL : Street.NONE;
    }

    /** Whether this column lies on an avenue, the north one ending before the First's reserve. */
    public static boolean avenue(int x, int z) {
        boolean north = z < 0 && Math.abs(x + 0.5) < AVENUE_HALF_WIDTH;
        return axisDistance(x, z) < AVENUE_HALF_WIDTH && !(north && z > NORTH_AVENUE_END_Z);
    }

    /** Whether this column lies on a ring road: the inner one about the hill's reserve, or the outer one. */
    public static boolean ring(int x, int z) {
        double r = radius(x, z);
        return r >= HILL_RESERVE_RADIUS && r < HILL_RESERVE_RADIUS + RING_WIDTH
                || r >= OUTER_RING_RADIUS && r < OUTER_RING_RADIUS + RING_WIDTH;
    }

    /** Whether this x or z lies in a lane; a block side's opening is the lane half a pitch on. */
    private static boolean lane(int c) {
        return Math.floorMod(c + LANE_HALF_WIDTH, BLOCK_PITCH) < 2 * LANE_HALF_WIDTH;
    }

    /** Whether a block's edge beside this column is walled: a lane, an avenue or a gate's path, off the wall's lane. */
    private static boolean bordersWay(int x, int z) {
        return radius(x, z) < SEIREITEI_RADIUS - WALL_LANE && (lane(x) || lane(z) || avenue(x, z) || onGatePath(x, z));
    }

    /** Each barracks gate's paved path, in {@code sites("barracks")}' order: copies, so no caller moves the plan's own. */
    public static List<BoundingBox> gatePaths() {
        return GATE_PATHS.stream().map(path -> path.moved(0, 0, 0)).toList();
    }

    /** Whether this column lies on a barracks gate's paved path. */
    public static boolean onGatePath(int x, int z) {
        for (BoundingBox path : GATE_PATHS) {
            if (path.isInside(x, PLATEAU_Y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Each barracks gate's path: its gateway's columns, straight out from its step until both edges meet a road. */
    private static List<BoundingBox> makeGatePaths() {
        List<BoundingBox> paths = new ArrayList<>();
        for (Site site : sites("barracks")) {
            BlockPos corner = new BlockPos(site.x(), PLATEAU_Y, site.z());
            BlockPos left = corner.offset(new BlockPos(BARRACKS_GATEWAY_X, 0, -1).rotate(site.rotation()));
            BlockPos right = left.offset(new BlockPos(BARRACKS_GATEWAY_WIDTH - 1, 0, 0).rotate(site.rotation()));
            Direction out = site.rotation().rotate(Direction.NORTH);
            int rows = 1;
            while (!road(left.relative(out, rows)) || !road(right.relative(out, rows))) {
                if (++rows > BARRACKS_SIZE) {
                    throw new IllegalStateException(site.template() + "'s gate meets no road");
                }
            }
            paths.add(BoundingBox.fromCorners(left, right.relative(out, rows - 1)));
        }
        return List.copyOf(paths);
    }

    /** Whether this column lies on an avenue or a ring road, which a gate's path runs out to. */
    private static boolean road(BlockPos pos) {
        return avenue(pos.getX(), pos.getZ()) || ring(pos.getX(), pos.getZ());
    }

    /** The group's site whose structure starts in this chunk, or null. */
    @Nullable
    public static Site siteAt(String group, int chunkX, int chunkZ) {
        for (Site site : SITES) {
            if (site.group().equals(group) && site.x() >> 4 == chunkX && site.z() >> 4 == chunkZ) {
                return site;
            }
        }
        return null;
    }

    /**
     * One planned site: its group, the corner its template is placed from, the template, its turn, and the template's
     * width and depth (its x and z) before the turn.
     */
    public record Site(String group, int x, int z, ResourceLocation template, Rotation rotation, int width, int depth) {
        /** The chunk its structure starts in, the one holding its corner. */
        public ChunkPos chunk() {
            return new ChunkPos(x >> 4, z >> 4);
        }

        /** The columns its template covers, turned about the corner, as one layer on the plateau. */
        public BoundingBox footprint() {
            BlockPos corner = new BlockPos(x, PLATEAU_Y, z);
            return BoundingBox.fromCorners(corner, corner.offset(new BlockPos(width - 1, 0, depth - 1).rotate(rotation)));
        }

        /** Its footprint and the margin about it: its own reserve. */
        public Reserve reserve() {
            BoundingBox box = footprint();
            return new Reserve(group, box.minX() - SITE_MARGIN, box.minZ() - SITE_MARGIN, box.maxX() + SITE_MARGIN,
                    box.maxZ() + SITE_MARGIN, false);
        }
    }

    /**
     * Columns kept for a group's own works, which no other's street, wall or site may enter: those between two corners,
     * both included, or, for a round one, those whose centres lie inside the disc that square bounds.
     */
    public record Reserve(String group, int minX, int minZ, int maxX, int maxZ, boolean round) {
        /** Whether this column is kept. */
        public boolean contains(int x, int z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                return false;
            }
            double half = (maxX - minX + 1) / 2.0, dx = x + 0.5 - minX - half, dz = z + 0.5 - minZ - half;
            return !round || dx * dx + dz * dz < half * half;
        }
    }

    /** What the streets lay on a column: nothing, paving, the court's marked arrival or a lantern, or a lane wall. */
    public enum Street { NONE, PAVING, MARK, LANTERN, WALL }
}
