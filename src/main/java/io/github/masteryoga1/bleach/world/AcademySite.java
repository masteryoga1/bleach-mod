package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.TrainingDummy;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Shin'ō Academy in Seireitei, the structure {@code bleach:seireitei_academy} at the plan's academy sites: its hall,
 * then its grounds, the yard of training dummies south of the hall, the dojo west of it and the Kidō range west of the
 * yard. A world whose chunks there were made before a site existed, or made with structures off, has whatever part it
 * lacks built once, when the site's chunks and those beside them are loaded; a part an operator placed by command is
 * recognised and kept, and a soul a work of the grounds buries is moved out.
 */
public final class AcademySite {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("seireitei_academy"));
    /**
     * The hall's stamp: its template, 15 by 9 by 17 before its turn, and the block each chunk's part is known by, the
     * hall's Tailoring Table and the gate's north post.
     */
    private static final SiteStamp STAMP = new SiteStamp(BleachWorlds.SOUL_SOCIETY, STRUCTURE, "The Academy in Seireitei",
            SeireiteiPlan.sites("academy").subList(0, 1), new Vec3i(15, 9, 17),
            List.of(new SiteStamp.Marker(new BlockPos(3, 1, 13), ModBlocks.TAILORING_TABLE),
                    new SiteStamp.Marker(new BlockPos(5, 1, 0), () -> Blocks.STRIPPED_DARK_OAK_LOG)),
            SiteStamp.Placed.NOTHING);
    /** The yard, by its corner lanterns' posts and a dummy's post in each of its two middle chunks. */
    public static final List<SiteStamp.Marker> YARD_MARKERS = List.of(lamp(0, 1, 1), lamp(23, 1, 1), lamp(0, 1, 15),
            lamp(23, 1, 15), post(5, 0, 5), post(5, 0, 10));
    public static final SiteStamp YARD = grounds(1, "The Academy's yard", new Vec3i(24, 5, 16), YARD_MARKERS);
    /** The dojo, known in each chunk it covers by a corner post of its walls, before its turn. */
    public static final List<SiteStamp.Marker> DOJO_MARKERS = List.of(post(1, 1, 1), post(19, 1, 1), post(1, 1, 13),
            post(19, 1, 13));
    public static final SiteStamp DOJO = grounds(2, "The Academy's dojo", new Vec3i(21, 10, 15), DOJO_MARKERS);
    /** The Kidō range, by two of its backstop's targets, its two dummies' posts and its platform's lantern posts. */
    public static final List<SiteStamp.Marker> RANGE_MARKERS = List.of(marker(1, 2, 2, Blocks.TARGET),
            marker(1, 2, 12, Blocks.TARGET), post(16, 0, 4), post(11, 0, 10), lamp(25, 0, 0), lamp(25, 0, 14));
    public static final SiteStamp RANGE = grounds(3, "The Academy's Kidō range", new Vec3i(26, 8, 15), RANGE_MARKERS);
    /** The grounds' stamps, one for each site after the hall's, in the plan's order. */
    public static final List<SiteStamp> GROUNDS = List.of(YARD, DOJO, RANGE);
    private static final List<SiteStamp> STAMPS = List.of(STAMP, YARD, DOJO, RANGE);
    /** The chunks the Academy covers (x -208 to -192, z 224 to 238), the hall's and then the gate's, where it starts. */
    public static final List<ChunkPos> COVERED = STAMP.covered(0);

    private AcademySite() {
    }

    /** The stamp for the academy site at this place in the plan's order, its parts known by these markers. */
    private static SiteStamp grounds(int site, String name, Vec3i size, List<SiteStamp.Marker> markers) {
        return new SiteStamp(BleachWorlds.SOUL_SOCIETY, STRUCTURE, name, SeireiteiPlan.sites("academy").subList(site,
                site + 1), size, markers, AcademySite::placed);
    }

    private static SiteStamp.Marker marker(int x, int y, int z, Block block) {
        return new SiteStamp.Marker(new BlockPos(x, y, z), () -> block);
    }

    /** A wall's or a dummy's post of stripped dark oak. */
    private static SiteStamp.Marker post(int x, int y, int z) {
        return marker(x, y, z, Blocks.STRIPPED_DARK_OAK_LOG);
    }

    /** A stone lantern's post of stone brick wall. */
    private static SiteStamp.Marker lamp(int x, int y, int z) {
        return marker(x, y, z, Blocks.STONE_BRICK_WALL);
    }

    /** The chunk the Academy's structure starts in, the one holding its corner: (-12, 14). */
    public static ChunkPos startChunk() {
        return STAMP.startChunk(0);
    }

    /**
     * One of Soul Society's chunks of the Academy or its grounds, or one beside them, has loaded: once the tick is done,
     * for each site whose chunks are all loaded, with the creatures saved in those it covers, whatever part of it is
     * missing is built, and if one is not, the check runs again once a tick for a while (see {@link SiteStamp#onChunkLoad}).
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        STAMPS.forEach(stamp -> stamp.onChunkLoad(event));
    }

    /** The further tries, one a tick, that a check asked for. */
    public static void onServerTick(ServerTickEvent.Post event) {
        STAMPS.forEach(stamp -> stamp.onServerTick(event));
    }

    /**
     * Builds what was never placed of the Academy's hall, as {@link SiteStamp#ensureBuilt(ServerLevel, int)} does, on the
     * server thread; its grounds wait for their own chunks. Public for Konsō's landing and the respawn (7d3), and for game
     * tests.
     *
     * @return whether any part was placed
     */
    public static boolean ensureBuilt(ServerLevel level) {
        return STAMP.ensureBuilt(level, 0);
    }

    /** This work of the grounds' box, as worldgen lays it on the lawn. */
    public static BoundingBox box(SiteStamp stamp) {
        return stamp.box(0, SeireiteiPlan.PLATEAU_Y);
    }

    /**
     * Moves each soul or creature a work of the grounds now buries in this part, with what it rides, out past the work's
     * nearer north or south side onto the lawn, clear of it by half the width of the stack; its dummies stand as set.
     * Every work is looked at, as HillWorks' hook does, so a stamp may move a soul out of a neighbouring work's footprint
     * before that work is built.
     */
    private static void placed(ServerLevel level, BoundingBox part) {
        for (SiteStamp stamp : GROUNDS) {
            BoundingBox work = box(stamp);
            if (!part.intersects(work)) {
                continue;
            }
            AABB inside = AABB.of(part).intersect(AABB.of(work));
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, inside)) {
                if (!(living instanceof TrainingDummy) && !living.isRemoved() && !level.noCollision(living)) {
                    Entity moved = living.getRootVehicle();
                    AABB body = moved.getBoundingBox();
                    for (Entity rider : moved.getIndirectPassengers()) {
                        body = body.minmax(rider.getBoundingBox());
                    }
                    double half = Math.max(0.5, Math.max(body.getXsize(), body.getZsize()) / 2.0 + 0.01);
                    double z = moved.getZ() < work.getCenter().getZ() ? work.minZ() - half : work.maxZ() + 1 + half;
                    moved.teleportTo(moved.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Mth.floor(moved.getX()), Mth.floor(z)), z);
                }
            }
        }
    }
}
