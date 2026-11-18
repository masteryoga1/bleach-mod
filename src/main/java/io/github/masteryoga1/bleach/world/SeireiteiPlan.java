package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

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
    /** The Senkaimon court, where every gate from the Human World comes out: open lawn north-east of the hill. */
    public static final int SENKAIMON_COURT_X = SEIREITEI_RADIUS * 3 / 8, SENKAIMON_COURT_Z = -SEIREITEI_RADIUS * 9 / 16;
    /** The Shin'ō Academy's north-east corner, where its template is placed from; its gate faces east. */
    public static final int ACADEMY_X = -192, ACADEMY_Z = 224;
    /** The y a soul stands at on the Academy's floor, which is laid on the lawn. */
    public static final int ACADEMY_FLOOR_Y = PLATEAU_Y + 1;
    /** Where a soul sent to the Academy lands: on the path two blocks inside its gate. */
    public static final double ACADEMY_LANDING_X = ACADEMY_X - 1.5, ACADEMY_LANDING_Z = ACADEMY_Z + 7.5;

    /** Every planned site: a template set whole at its corner, turned, on the ground there, one structure for each group. */
    public static final List<Site> SITES = List.of(
            new Site("academy", ACADEMY_X, ACADEMY_Z, BleachMod.id("academy/academy"), Rotation.CLOCKWISE_90),
            // The gate houses in the order of the Rukongai's regions, each the south one turned about the centre column.
            gateHouse(SEIREITEI_RADIUS, GATE_HALF_WIDTH - 1, Rotation.COUNTERCLOCKWISE_90),
            gateHouse(-GATE_HALF_WIDTH, SEIREITEI_RADIUS, Rotation.NONE),
            gateHouse(-SEIREITEI_RADIUS - 1, -GATE_HALF_WIDTH, Rotation.CLOCKWISE_90),
            gateHouse(GATE_HALF_WIDTH - 1, -SEIREITEI_RADIUS - 1, Rotation.CLOCKWISE_180));

    private SeireiteiPlan() {
    }

    /** A gate house filling a gap of the wall, its template's corner at the gap's corner here, its front facing out. */
    private static Site gateHouse(int x, int z, Rotation rotation) {
        return new Site("gate", x, z, BleachMod.id("gate/house"), rotation);
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

    /** One planned site: its group, the corner its template is placed from, the template and its turn. */
    public record Site(String group, int x, int z, ResourceLocation template, Rotation rotation) {
        /** The chunk its structure starts in, the one holding its corner. */
        public ChunkPos chunk() {
            return new ChunkPos(x >> 4, z >> 4);
        }
    }
}
