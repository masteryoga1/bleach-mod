package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.ArrayList;
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
 * off: once the chunks a site covers and those beside them are loaded, and the creatures saved in those it covers, each
 * that never placed its part places it then. A part an operator placed by command is known by its marker blocks and kept.
 * A chunkwise stamp, for one structure too wide to wait on whole, lays each chunk it covers alone once that chunk and
 * those beside it are loaded and its guard allows it, and keeps no start, so its reference alone says a chunk was laid.
 */
public final class SiteStamp {
    /** How many further checks, one a tick, a chunk's load gives a site while its chunks load: 5 seconds. */
    private static final int LOAD_TRIES = 100;
    /** How many chunks a chunkwise stamp lays in one tick at most; the rest wait for the ticks after. */
    private static final int LAYS_PER_TICK = 4;

    private final ResourceKey<Level> world;
    private final ResourceKey<Structure> structure;
    private final String name;
    private final List<SeireiteiPlan.Site> sites;
    private final Vec3i size;
    private final List<Marker> markers;
    private final Placed placed;
    private final Guard guard;
    private final List<List<ChunkPos>> covered = new ArrayList<>(), around = new ArrayList<>();
    /** A chunkwise stamp's start chunk, where its structure is set out from afresh for each chunk; else null. */
    @Nullable
    private final ChunkPos chunkwiseHome;
    /**
     * For each site, how many further checks, one a tick, are still to run since a check found what it needs not yet
     * loaded; 0 for none. Server thread only. Some left over from a world closed since cost checks that load nothing.
     */
    private final int[] triesLeft;
    /** The server tick a chunkwise stamp last counted its lays in, and how many it has laid in it. Server thread only. */
    private int layTick = -1, laidThisTick;

    /**
     * The stamp for this structure's sites in this world, each a template of this size before its turn, its parts known
     * by these markers; {@code name} begins the log's lines, and {@code placed} hears of each part placed.
     */
    public SiteStamp(ResourceKey<Level> world, ResourceKey<Structure> structure, String name, List<SeireiteiPlan.Site> sites,
                     Vec3i size, List<Marker> markers, Placed placed) {
        this(world, structure, name, sites, size, markers, placed, Guard.ANY);
    }

    /** As the stamp above, which builds over a site's bare chunks only when {@code guard} allows it. */
    public SiteStamp(ResourceKey<Level> world, ResourceKey<Structure> structure, String name, List<SeireiteiPlan.Site> sites,
                     Vec3i size, List<Marker> markers, Placed placed, Guard guard) {
        this.world = world;
        this.structure = structure;
        this.name = name;
        this.sites = List.copyOf(sites);
        this.size = size;
        this.markers = List.copyOf(markers);
        this.placed = placed;
        this.guard = guard;
        this.chunkwiseHome = null;
        for (int i = 0; i < this.sites.size(); i++) {
            BoundingBox box = box(i, 0);
            // Along x, a row of z at a time, as ChunkPos.rangeClosed runs.
            List<ChunkPos> chunks = ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                    new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList();
            covered.add(chunks);
            around.add(chunks.stream().flatMap(pos -> ChunkPos.rangeClosed(pos, 1)).distinct().toList());
        }
        triesLeft = new int[this.sites.size()];
    }

    /** A chunkwise stamp (see {@link #chunkwise}). */
    private SiteStamp(ResourceKey<Level> world, ResourceKey<Structure> structure, String name, ChunkPos home,
                      BoundingBox box, Placed placed, Guard guard) {
        this.world = world;
        this.structure = structure;
        this.name = name;
        this.sites = List.of();
        this.size = Vec3i.ZERO;
        this.markers = List.of();
        this.placed = placed;
        this.guard = guard;
        this.chunkwiseHome = home;
        ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4))
                .forEach(pos -> {
                    covered.add(List.of(pos));
                    around.add(ChunkPos.rangeClosed(pos, 1).toList());
                });
        triesLeft = new int[covered.size()];
    }

    /**
     * The stamp for one structure in this world, set out from {@code home} and covering {@code box}, with no template or
     * markers: each chunk it covers is a site of its own, across x and then z, laid alone (see {@link #ensureBuilt}).
     */
    public static SiteStamp chunkwise(ResourceKey<Level> world, ResourceKey<Structure> structure, String name,
                                      ChunkPos home, BoundingBox box, Placed placed) {
        return new SiteStamp(world, structure, name, home, box, placed, Guard.ANY);
    }

    /**
     * As the chunkwise stamp above, which lays a chunk only when {@code guard} allows it, asked with that one chunk; one
     * held back waits for its next check, which a chunk's load beside it or {@link #recheck} brings.
     */
    public static SiteStamp chunkwise(ResourceKey<Level> world, ResourceKey<Structure> structure, String name,
                                      ChunkPos home, BoundingBox box, Placed placed, Guard guard) {
        return new SiteStamp(world, structure, name, home, box, placed, guard);
    }

    /** The sites, in the plan's order; none for a chunkwise stamp. */
    public List<SeireiteiPlan.Site> sites() {
        return sites;
    }

    /** The chunks this site covers, across x and then z. */
    public List<ChunkPos> covered(int index) {
        return covered.get(index);
    }

    /** The chunk this site's structure starts in: the one holding its corner, or a chunkwise stamp's home. */
    public ChunkPos startChunk(int index) {
        return chunkwiseHome != null ? chunkwiseHome : sites.get(index).chunk();
    }

    /** This site's box, its template turned about its corner, standing on y. Not for a chunkwise stamp. */
    public BoundingBox box(int index, int y) {
        templateSites();
        SeireiteiPlan.Site site = sites.get(index);
        BlockPos corner = new BlockPos(site.x(), y, site.z());
        return BoundingBox.fromCorners(corner,
                corner.offset(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1).rotate(site.rotation())));
    }

    /**
     * A chunk of the world, one a site covers or one beside them, has loaded: once the tick is done, that site is checked,
     * and if its chunks are not all loaded yet it is checked again once a tick, up to {@link #LOAD_TRIES} times, and while
     * only the creatures saved in those it covers are still to join, once a tick until they have. Nothing is loaded or
     * made for it, so whichever loads last completes it.
     */
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == world) {
            for (int i = 0; i < covered.size(); i++) {
                if (around.get(i).contains(event.getChunk().getPos())) {
                    queueCheck(level, i, true);
                }
            }
        }
    }

    /** Checks again, once the tick is done, each site this chunk is covered by or lies beside, as its load would. */
    public void recheck(ServerLevel level, ChunkPos pos) {
        if (level.dimension() == world) {
            for (int i = 0; i < covered.size(); i++) {
                if (around.get(i).contains(pos)) {
                    queueCheck(level, i, false);
                }
            }
        }
    }

    /** Queues this tick's further check of each site a check left tries, counting one off. */
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (int i = 0; i < covered.size(); i++) {
            if (triesLeft[i] > 0) {
                triesLeft[i]--;
                ServerLevel level = server.getLevel(world);
                if (level != null) {
                    queueCheck(level, i, false);
                }
            }
        }
    }

    /** Checks once the server's current work is done: a chunk's load event comes inside the load itself. */
    private void queueCheck(ServerLevel level, int index, boolean fromLoad) {
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> ensureBuiltIfLoaded(level, index, fromLoad)));
    }

    /**
     * As {@link #ensureBuilt(ServerLevel, int)}, but only when every chunk the site covers, and every one beside them, is
     * loaded, and the creatures saved in those it covers have joined, so that {@code placed} finds each it buries. A chunk
     * still to load gets the tries a chunk's load gives; creatures still to join, the tries renewed while the chunks stay.
     */
    private void ensureBuiltIfLoaded(ServerLevel level, int index, boolean fromLoad) {
        if (!around.get(index).stream().allMatch(pos -> level.getChunkSource().getChunkNow(pos.x, pos.z) != null)) {
            // A block placed at its edge would make a neighbour that is not loaded load; that one's own load checks again.
            if (fromLoad) {
                triesLeft[index] = LOAD_TRIES;
            }
            return;
        }
        if (!covered.get(index).stream().allMatch(pos -> level.areEntitiesLoaded(pos.toLong()))) {
            // Read off the thread, saved creatures may join many ticks after their chunk, and no event says when.
            triesLeft[index] = LOAD_TRIES;
            return;
        }
        triesLeft[index] = 0;
        ensureBuilt(level, index);
    }

    /**
     * Builds what was never placed of this site: its start, if its chunk has none, and the part of each chunk it covers
     * that holds no reference to it, which then gains one. A part that stands already, placed by command, only gains the
     * reference. Those chunks are loaded, and made, first, unless the site is whole already. Server thread only.
     * Its guard is asked before anything is set, with the chunks still bare; on a no, nothing is set or placed.
     * A chunkwise stamp's site is its one chunk, laid alone unless it holds the reference or its guard says no, from a
     * start never kept.
     *
     * @return whether any part was placed
     */
    public boolean ensureBuilt(ServerLevel level, int index) {
        Structure laid = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structure);
        if (laid == null) {
            return false;
        }
        if (chunkwiseHome != null) {
            return layAlone(level, laid, index, false);
        }
        if (whole(level, laid, index)) {
            // With no chunk bare, the guard learns the site stands whole, as worldgen or a stamp left it.
            guard.mayBuild(level, List.of());
            return false;
        }
        ChunkPos home = startChunk(index);
        // Made before the start is set, a covered chunk cannot gain its reference, and place its part, on its own.
        List<LevelChunk> chunks = covered.get(index).stream().map(each -> level.getChunk(each.x, each.z)).toList();
        ChunkAccess homeChunk = level.getChunk(home.x, home.z);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart start = homeChunk.getStartForStructure(laid);
        boolean fresh = start == null || !start.isValid();
        if (fresh) {
            start = laid.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), home, 0, level,
                    biome -> true);
            if (!start.isValid()) {
                BleachMod.LOGGER.warn("{} could not be laid out in chunk {}; it was not built", name, home);
                return false;
            }
        }
        BlockPos corner = start.getPieces().get(0) instanceof TemplateStructurePiece piece ? piece.templatePosition() : null;
        List<LevelChunk> unreferenced = chunks.stream()
                .filter(chunk -> !chunk.getReferencesForStructure(laid).contains(home.toLong())).toList();
        // A /place sets no reference: building again would stack a second of what it holds on the first.
        List<LevelChunk> bare = unreferenced.stream().filter(chunk -> !partStands(chunk, index, corner)).toList();
        // Asked before anything is set, so a site refused leaves no start for /locate to report.
        if (!guard.mayBuild(level, bare)) {
            return false;
        }
        if (fresh) {
            homeChunk.setStartForStructure(laid, start);
            // Tells /locate, as a chunk's load does; Soul Society's stamps gain only a cache refresh no /locate reads.
            level.onStructureStartsAvailable(homeChunk);
        }
        for (LevelChunk chunk : bare) {
            lay(level, start, generator, chunk);
        }
        unreferenced.forEach(chunk -> chunk.addReferenceForStructure(laid, home.toLong()));
        return !bare.isEmpty();
    }

    /**
     * Lays this chunkwise stamp's site for this chunk now, in its own world, if it covers the chunk, the chunk is bare and
     * its guard agrees: past this tick's share, though counted in it, for a soul that cannot wait. Server thread only.
     *
     * @return whether it was laid
     */
    public boolean layNow(ServerLevel level, ChunkPos pos) {
        if (level.dimension() != world || chunkwiseHome == null) {
            return false;
        }
        Structure laid = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structure);
        int index = covered.indexOf(List.of(pos));
        return laid != null && index >= 0 && layAlone(level, laid, index, true);
    }

    /**
     * A chunkwise stamp's site: its one chunk, unless it holds the reference, laid from a start set out afresh and never
     * kept, then given the reference. Past this tick's share it is left for the next tick's check, unless laid now.
     */
    private boolean layAlone(ServerLevel level, Structure laid, int index, boolean now) {
        ChunkPos pos = covered.get(index).get(0), home = chunkwiseHome;
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        if (chunk.getReferencesForStructure(laid).contains(home.toLong())) {
            return false;
        }
        // Asked before this tick's share is counted, so a chunk held back costs none of it, and keeps no try.
        if (!guard.mayBuild(level, List.of(chunk))) {
            return false;
        }
        int tick = level.getServer().getTickCount();
        if (tick != layTick) {
            layTick = tick;
            laidThisTick = 0;
        }
        if (laidThisTick >= LAYS_PER_TICK && !now) {
            triesLeft[index] = Math.max(triesLeft[index], 1);
            return false;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        // Kept in the home chunk, a start would give chunks made later a reference, though with structures off no part.
        StructureStart start = laid.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), home, 0, level,
                biome -> true);
        if (!start.isValid()) {
            BleachMod.LOGGER.warn("{} could not be laid out from chunk {}; chunk {} was not built", name, home, pos);
            return false;
        }
        laidThisTick++;
        lay(level, start, generator, chunk);
        chunk.addReferenceForStructure(laid, home.toLong());
        return true;
    }

    /** Places this start's part in this chunk's full-height column, after a warning if souls have spent time there. */
    private void lay(ServerLevel level, StructureStart start, ChunkGenerator generator, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (chunk.getInhabitedTime() > 0) {
            BleachMod.LOGGER.warn("{} is built over chunk {}, where souls have spent {} ticks", name, pos,
                    chunk.getInhabitedTime());
        }
        BoundingBox column = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ());
        start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), column, pos);
        placed.placed(level, column);
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
     * Not for a chunkwise stamp.
     */
    public boolean stands(ServerLevel level, int index, BlockPos corner) {
        templateSites();
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

    /**
     * Where this marker stands in this site once its template is set at this corner and turned with the site. Not for a
     * chunkwise stamp.
     */
    public BlockPos markerAt(int index, Marker marker, BlockPos corner) {
        return turned(index, marker.at(), corner);
    }

    /**
     * Where this place of the template stands in this site once it is set at this corner and turned with the site. Not for
     * a chunkwise stamp.
     */
    public BlockPos turned(int index, BlockPos place, BlockPos corner) {
        templateSites();
        return corner.offset(place.rotate(sites.get(index).rotation()));
    }

    /** Refuses a template site's question to a chunkwise stamp, whose sites are chunks with no template, corner or turn. */
    private void templateSites() {
        if (chunkwiseHome != null) {
            throw new UnsupportedOperationException(name + " lays chunks alone and has no template sites");
        }
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

    /**
     * Asked at each check, before anything is set or built, with the site's chunks still bare, or with none once it
     * stands whole; a chunkwise stamp asks with its one chunk, still bare. Nothing is set or placed on a no.
     */
    @FunctionalInterface
    public interface Guard {
        Guard ANY = (level, bare) -> true;

        boolean mayBuild(ServerLevel level, List<? extends ChunkAccess> bare);
    }
}
