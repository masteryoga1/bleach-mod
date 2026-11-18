package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Sōkyoku Hill's works, the structure {@code bleach:seireitei_hill} at the plan's hill sites: the Senzaikyū, its bridge
 * and the Sōkyoku's stand on the hill's east end; the stair up its north cliff; the cave in its west cliff and the stair
 * down to the cavern; and the cavern's spires, floor and hot spring. Old worlds have each stamped as the gate houses are,
 * and a soul they are built over is moved out beside them.
 */
public final class HillWorks {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("seireitei_hill"));
    /** The template before its turn: the hill's end to the tower's east face, the lawn to the crown, the tower's width. */
    public static final Vec3i SIZE = new Vec3i(53, 95, 12);
    /** A block in each chunk the works cover, before the turn: the stand's posts, the bridge's rails, the tower's foot. */
    public static final List<SiteStamp.Marker> MARKERS = List.of(
            new SiteStamp.Marker(new BlockPos(50, 64, 9), () -> Blocks.STRIPPED_DARK_OAK_LOG),
            new SiteStamp.Marker(new BlockPos(50, 64, 2), () -> Blocks.STRIPPED_DARK_OAK_LOG),
            new SiteStamp.Marker(new BlockPos(39, 64, 7), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(39, 64, 4), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(23, 64, 7), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(23, 64, 4), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(6, 0, 11), ModBlocks.SEKKISEKI),
            new SiteStamp.Marker(new BlockPos(6, 0, 0), ModBlocks.SEKKISEKI));
    public static final SiteStamp STAMP = stamp(0, "The Senzaikyū and the Sōkyoku's stand", SIZE, MARKERS);
    /** A step in each chunk the stair up the north cliff covers, unturned. */
    public static final List<SiteStamp.Marker> STAIR_MARKERS = List.of(step(5, 12, 1), step(13, 4, 1));
    public static final SiteStamp STAIR = stamp(1, "Sōkyoku Hill's stair", new Vec3i(20, 67, 5), STAIR_MARKERS);
    /** A step in each chunk the cave in the west cliff and its stair down cover. */
    public static final List<SiteStamp.Marker> CAVE_MARKERS = List.of(step(9, 7, 0), step(16, 0, 0));
    public static final SiteStamp CAVE = stamp(2, "The cave under Sōkyoku Hill", new Vec3i(20, 19, 2), CAVE_MARKERS);
    /** A flagstone in the floor of each of the sixteen chunks the cavern's dressing covers. */
    public static final List<SiteStamp.Marker> CAVERN_MARKERS = IntStream.range(0, 16).mapToObj(i -> new SiteStamp.Marker(
            new BlockPos(1 + 16 * (i % 4), 1, 1 + 16 * (i / 4)), () -> Blocks.POLISHED_ANDESITE)).toList();
    public static final SiteStamp CAVERN = stamp(3, "The cavern under Sōkyoku Hill", new Vec3i(62, 54, 62),
            CAVERN_MARKERS);
    /** Every stamp of the hill's, one for each of its sites in the plan's order. */
    public static final List<SiteStamp> STAMPS = List.of(STAMP, STAIR, CAVE, CAVERN);

    private HillWorks() {
    }

    /** The stamp for the hill's site at this place in the plan's order, its parts known by these markers. */
    private static SiteStamp stamp(int site, String name, Vec3i size, List<SiteStamp.Marker> markers) {
        return new SiteStamp(BleachWorlds.SOUL_SOCIETY, STRUCTURE, name, SeireiteiPlan.sites("hill").subList(site, site + 1),
                size, markers, HillWorks::placed);
    }

    /** A step of stone stairs, at this place in its template. */
    private static SiteStamp.Marker step(int x, int y, int z) {
        return new SiteStamp.Marker(new BlockPos(x, y, z), () -> Blocks.STONE_STAIRS);
    }

    /** A chunk of the works, or one beside them, has loaded: see {@link SiteStamp#onChunkLoad}. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        STAMPS.forEach(stamp -> stamp.onChunkLoad(event));
    }

    /** The one further try a stamp's check asked for. */
    public static void onServerTick(ServerTickEvent.Post event) {
        STAMPS.forEach(stamp -> stamp.onServerTick(event));
    }

    /**
     * Builds what was never placed of the Senzaikyū's work, as {@link SiteStamp#ensureBuilt} does. Public for game tests.
     */
    public static boolean ensureBuilt(ServerLevel level) {
        return STAMP.ensureBuilt(level, 0);
    }

    /** This stamp's box, as worldgen lays it: at its site's own height, or on the lawn. */
    public static BoundingBox box(SiteStamp stamp) {
        int y = stamp.sites().get(0).y();
        return stamp.box(0, y == SeireiteiPlan.Site.GROUND ? SeireiteiPlan.PLATEAU_Y : y);
    }

    /**
     * Moves each soul or player the works now bury in this part, with what it rides, out of the work it lies in, clear of
     * it by half the width of the stack: north off the stair and west out of the cave onto the lawn, and past the nearer
     * long side of the others, onto the cavern's floor if that is in the cavern.
     */
    private static void placed(ServerLevel level, BoundingBox part) {
        for (SiteStamp stamp : STAMPS) {
            BoundingBox works = box(stamp);
            if (!part.intersects(works)) {
                continue;
            }
            AABB inside = AABB.of(part).intersect(AABB.of(works));
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, inside)) {
                if (!living.isRemoved() && !level.noCollision(living)) {
                    Entity moved = living.getRootVehicle();
                    AABB body = moved.getBoundingBox();
                    for (Entity rider : moved.getIndirectPassengers()) {
                        body = body.minmax(rider.getBoundingBox());
                    }
                    double half = Math.max(0.5, Math.max(body.getXsize(), body.getZsize()) / 2.0 + 0.01);
                    double x = stamp == CAVE ? works.minX() - half : moved.getX();
                    double z = stamp == CAVE ? moved.getZ() : stamp == STAIR || moved.getZ() < works.getCenter().getZ()
                            ? works.minZ() - half : works.maxZ() + 1 + half;
                    int column = Mth.floor(x), row = Mth.floor(z);
                    double y = SeireiteiPlan.inCavern(column, SeireiteiPlan.CAVERN_FLOOR_Y, row)
                            ? SeireiteiPlan.CAVERN_FLOOR_Y
                            : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column, row);
                    moved.teleportTo(x, y, z);
                }
            }
        }
    }
}
