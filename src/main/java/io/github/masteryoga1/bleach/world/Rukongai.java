package io.github.masteryoga1.bleach.world;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The Rukongai's map, from {@link SeireiteiPlan}'s rings: which district of which region a block column lies in, by
 * where its centre lies, with Seireitei's four gates and the districts canon names. North is -z.
 */
public final class Rukongai {
    /** What {@link #district} gives inside the Seireiheki's inner face. */
    public static final int SEIREITEI = -1;
    /** What {@link #district} gives in the Seireiheki's band, its gate gaps included. */
    public static final int WALL = 0;
    /**
     * The first district number past the 80th, where the Rukongai ends and the wilds run on: past district 80, not the
     * wilds biome, which begins at district 79 ({@link SeireiteiPlan#WILDS_FROM_DISTRICT}).
     */
    public static final int WILDS = SeireiteiPlan.DISTRICTS + 1;
    /** The place of a soul outside Soul Society, which no column has (see {@link #place}). */
    public static final int NOWHERE = Integer.MIN_VALUE;

    /**
     * The districts canon names at grade B, by place code, each to its lang key's last part. The sources disagree on
     * Sabitsura's side (East at grade B, West at grade C), and East is used.
     */
    private static final Map<Integer, String> NAMES = Map.of(
            code(Region.WEST, 3), "hokutan",
            code(Region.EAST, 64), "sabitsura",
            code(Region.EAST, 76), "sakahone",
            code(Region.SOUTH, 78), "inuzuri",
            code(Region.NORTH, 79), "kusajishi",
            code(Region.NORTH, 80), "zaraki");

    private Rukongai() {
    }

    /** The Rukongai's four regions, each with its gate in the Seireiheki, on the axis it lies along. */
    public enum Region {
        EAST("east", 1, 0),
        SOUTH("south", 0, 1),
        WEST("west", -1, 0),
        NORTH("north", 0, -1);

        private final String key;
        private final BlockPos gate;

        Region(String key, int x, int z) {
            this.key = key;
            this.gate = new BlockPos(x * SeireiteiPlan.GATE_RADIUS, SeireiteiPlan.PLATEAU_Y, z * SeireiteiPlan.GATE_RADIUS);
        }

        /** The last part of the region's lang keys: east, south, west or north. */
        public String key() {
            return key;
        }

        /** The middle of the region's gate, mid-wall on its axis, at the plateau's height. */
        public BlockPos gate() {
            return gate;
        }

        /** "East", as in "District 3 of the East Rukongai". */
        public MutableComponent displayName() {
            return Component.translatable("message.bleach.place.region." + key);
        }

        /** The name of the region's gate, such as Shōryūmon in the east. */
        public MutableComponent gateName() {
            return Component.translatable("message.bleach.place.gate." + key);
        }
    }

    /**
     * Which ring this column lies in: {@link #SEIREITEI} inside the wall, {@link #WALL} in its band, 1 to 80 in the
     * Rukongai's districts, 32 blocks deep, and from {@link #WILDS} on, rings of the same depth in the wilds.
     */
    public static int district(int x, int z) {
        double r = SeireiteiPlan.radius(x, z);
        if (r < SeireiteiPlan.SEIREITEI_RADIUS) {
            return SEIREITEI;
        }
        if (r < SeireiteiPlan.WALL_OUTER_RADIUS) {
            return WALL;
        }
        return 1 + (int) Math.floor((r - SeireiteiPlan.WALL_OUTER_RADIUS) / SeireiteiPlan.DISTRICT_DEPTH);
    }

    /**
     * The region this column lies in, by the axis its centre lies nearer: East or West where |x| is at least |z| (so a
     * column on a diagonal is East or West), else South or North.
     */
    public static Region region(int x, int z) {
        double dx = x + 0.5, dz = z + 0.5;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0.0 ? Region.EAST : Region.WEST;
        }
        return dz > 0.0 ? Region.SOUTH : Region.NORTH;
    }

    /** The last part of the lang key of the name canon gives this district, such as "hokutan", or null for none. */
    @Nullable
    public static String canonName(Region region, int district) {
        return NAMES.get(code(region, district));
    }

    /**
     * One number for each place a soul is told of: {@link #SEIREITEI}, {@link #WALL}, or a district of a region, every
     * ring of a region's wilds being one place.
     */
    public static int place(int x, int z) {
        int district = district(x, z);
        return district <= WALL ? district : code(region(x, z), Math.min(district, WILDS));
    }

    /** What a soul is told of where it is: Seireitei, the Seireiheki, a district of a region, or the wilds beyond one. */
    public static MutableComponent placeName(int x, int z) {
        int district = district(x, z);
        if (district == SEIREITEI) {
            return Component.translatable("message.bleach.place.seireitei");
        }
        if (district == WALL) {
            return Component.translatable("message.bleach.place.seireiheki");
        }
        Region region = region(x, z);
        if (district >= WILDS) {
            return Component.translatable("message.bleach.place.wilds", region.displayName());
        }
        String name = canonName(region, district);
        return name == null
                ? Component.translatable("message.bleach.place.district", region.displayName(), district)
                : Component.translatable("message.bleach.place.district.named", region.displayName(), district,
                        Component.translatable("message.bleach.place.name." + name));
    }

    private static int code(Region region, int district) {
        return region.ordinal() * 1000 + district;
    }
}
