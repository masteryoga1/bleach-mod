package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * One structure's planned sites in one world, stamped into chunks made before a site existed, or made with structures
 * off: once the chunks a site covers and those beside them are loaded, each that never placed its part places it then.
 * A part an operator placed by command is known by its marker blocks and kept.
 */
public final class SiteStamp {
    private static final int NO_RETRY = -1;

    private final ResourceKey<Level> world;
    private final ResourceKey<Structure> structure;
    private final String name;
    private final List<SeireiteiPlan.Site> sites;
    private final Vec3i size;
    private final List<Marker> markers;
    private final Placed placed;
    private final List<List<ChunkPos>> covered = new ArrayList<>(), around = new ArrayList<>();
    /**
     * For each site, the server tick after which a check that found a chunk it needs not yet loaded runs once more, or
     * {@link #NO_RETRY}. Server thread only. One left over from a world closed since costs a check that loads nothing.
     */
    private final int[] retryAfter;

    /**
     * The stamp for this structure's sites in this world, each a template of this size before its turn, its parts known
     * by these markers; {@code name} begins the log's lines, and {@code placed} hears of each part placed.
     */
    public SiteStamp(ResourceKey<Level> world, ResourceKey<Structure> structure, String name, List<SeireiteiPlan.Site> sites,
                     Vec3i size, List<Marker> markers, Placed placed) {
        this.world = world;
        this.structure = structure;
        this.name = name;
        this.sites = List.copyOf(sites);
        this.size = size;
        this.markers = List.copyOf(markers);
        this.placed = placed;
        for (int i = 0; i < this.sites.size(); i++) {
            BoundingBox box = box(i, 0);
            // Along x, a row of z at a time, as ChunkPos.rangeClosed runs.
            List<ChunkPos> chunks = ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                    new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList();
            covered.add(chunks);
            around.add(chunks.stream().flatMap(pos -> ChunkPos.rangeClosed(pos, 1)).distinct().toList());
        }
        retryAfter = new int[this.sites.size()];
        Arrays.fill(retryAfter, NO_RETRY);
    }

    /** The sites, in the plan's order. */
    public List<SeireiteiPlan.Site> sites() {
        return sites;
    }

    /** The chunks this site covers, across x and then z. */
    public List<ChunkPos> covered(int index) {
        return covered.get(index);
    }

    /** The chunk this site's structure starts in, the one holding its corner. */
    public ChunkPos startChunk(int index) {
        return sites.get(index).chunk();
    }

    /** This site's box, its template turned about its corner, standing on y. */
    public BoundingBox box(int index, int y) {
        SeireiteiPlan.Site site = sites.get(index);
        BlockPos corner = new BlockPos(site.x(), y, site.z());
        return BoundingBox.fromCorners(corner,
                corner.offset(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1).rotate(site.rotation())));
    }

    /**
     * A chunk of the world, one a site covers or one beside them, has loaded: once the tick is done, if all of that
     * site's are loaded, whatever part of it is missing is built, and if one is not, the check runs once more after the
     * next tick. Nothing is loaded or made for it, so whichever chunk loads last completes it.
     */
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == world) {
            for (int i = 0; i < sites.size(); i++) {
                if (around.get(i).contains(event.getChunk().getPos())) {
                    queueCheck(level, i, true);
                }
            }
        }
    }

    /** The one further try a check asked for, queued in a tick after the one it ran in. */
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (int i = 0; i < sites.size(); i++) {
            if (retryAfter[i] != NO_RETRY && server.getTickCount() > retryAfter[i]) {
                retryAfter[i] = NO_RETRY;
                ServerLevel level = server.getLevel(world);
                if (level != null) {
                    queueCheck(level, i, false);
                }
            }
        }
    }

    /** Checks once the server's current work is done: a chunk's load event comes inside the load itself. */
    private void queueCheck(ServerLevel level, int index, boolean mayRetry) {
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> ensureBuiltIfLoaded(level, index, mayRetry)));
    }

    /**
     * As {@link #ensureBuilt(ServerLevel, int)}, but only when every chunk the site covers, and every one beside them,
     * is loaded already; else, once, later. A block placed at its edge would make a neighbour that is not loaded load.
     */
    private void ensureBuiltIfLoaded(ServerLevel level, int index, boolean mayRetry) {
        for (ChunkPos pos : around.get(index)) {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                if (mayRetry) {
                    retryAfter[index] = level.getServer().getTickCount();
                }
                return;
            }
        }
        ensureBuilt(level, index);
    }

    /**
     * Builds what was never placed of this site: its start, if its chunk has none, and the part of each chunk it covers
     * that holds no reference to it, which then gains one. A part that stands already, placed by command, only gains the
     * reference. Those chunks are loaded, and made, first, unless the site is whole already. Server thread only.
     *
     * @return whether any part was placed
     */
    public boolean ensureBuilt(ServerLevel level, int index) {
        Structure laid = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structure);
        if (laid == null || whole(level, laid, index)) {
            return false;
        }
        ChunkPos home = startChunk(index);
        // Made before the start is set, a covered chunk cannot gain its reference, and place its part, on its own.
        List<LevelChunk> chunks = covered.get(index).stream().map(each -> level.getChunk(each.x, each.z)).toList();
        ChunkAccess homeChunk = level.getChunk(home.x, home.z);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart start = homeChunk.getStartForStructure(laid);
        if (start == null || !start.isValid()) {
            start = laid.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), home, 0, level,
                    biome -> true);
            if (!start.isValid()) {
                BleachMod.LOGGER.warn("{} could not be laid out in chunk {}; it was not built", name, home);
                return false;
            }
            homeChunk.setStartForStructure(laid, start);
        }
        BlockPos corner = start.getPieces().get(0) instanceof TemplateStructurePiece piece ? piece.templatePosition() : null;
        boolean built = false;
        for (ChunkAccess chunk : chunks) {
            ChunkPos pos = chunk.getPos();
            if (chunk.getReferencesForStructure(laid).contains(home.toLong())) {
                continue;
            }
            // A /place sets no reference: building again would stack a second of what it holds on the first.
            if (!partStands(chunk, index, corner)) {
                if (chunk.getInhabitedTime() > 0) {
                    BleachMod.LOGGER.warn("{} is built over chunk {}, where souls have spent {} ticks", name, pos,
                            chunk.getInhabitedTime());
                }
                BoundingBox column = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                        pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ());
                start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), column, pos);
                placed.placed(level, column);
                built = true;
            }
            chunk.addReferenceForStructure(laid, home.toLong());
        }
        return built;
    }

    /** Whether every chunk the site covers is loaded and holds its reference, and its start is valid: nothing to do. */
    private boolean whole(ServerLevel level, Structure laid, int index) {
        ChunkPos home = startChunk(index);
        for (ChunkPos pos : covered.get(index)) {
            ChunkAccess chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null || !chunk.getReferencesForStructure(laid).contains(home.toLong())) {
                return false;
            }
        }
        StructureStart start = level.getChunkSource().getChunkNow(home.x, home.z).getStartForStructure(laid);
        return start != null && start.isValid();
    }

    /**
     * Whether this chunk's part stands already, by the markers in it, turned with the template about its corner, every
     * one of them in place. A block comes with its chunk, where an entity, loaded after it, might not be there yet.
     */
    private boolean partStands(ChunkAccess chunk, int index, @Nullable BlockPos corner) {
        if (corner == null) {
            return false;
        }
        boolean any = false;
        for (Marker marker : markers) {
            BlockPos at = markerAt(index, marker, corner);
            if (new ChunkPos(at).equals(chunk.getPos())) {
                if (!chunk.getBlockState(at).is(marker.block().get())) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    /**
     * Whether this site stands in every chunk it covers, each loaded already: the chunk holds its reference, as worldgen
     * and the stamp leave it, or its markers stand about this corner, as a /place leaves them. Nothing is loaded for it.
     */
    public boolean stands(ServerLevel level, int index, BlockPos corner) {
        Structure laid = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structure);
        long home = startChunk(index).toLong();
        for (ChunkPos pos : covered.get(index)) {
            ChunkAccess chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null || !(laid != null && chunk.getReferencesForStructure(laid).contains(home))
                    && !partStands(chunk, index, corner)) {
                return false;
            }
        }
        return true;
    }

    /** Where this marker stands in this site once its template is set at this corner and turned with the site. */
    public BlockPos markerAt(int index, Marker marker, BlockPos corner) {
        return turned(index, marker.at(), corner);
    }

    /** Where this place of the template stands in this site once it is set at this corner and turned with the site. */
    public BlockPos turned(int index, BlockPos place, BlockPos corner) {
        return corner.offset(place.rotate(sites.get(index).rotation()));
    }

    /** A block that only a site's own part puts here, in template coordinates before the turn. */
    public record Marker(BlockPos at, Supplier<? extends Block> block) {
    }

    /** Told of each part a stamp has just placed, by the chunk's column. */
    @FunctionalInterface
    public interface Placed {
        Placed NOTHING = (level, part) -> {
        };

        void placed(ServerLevel level, BoundingBox part);
    }
}
