package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Shin'ō Academy in Seireitei, the structure {@code bleach:seireitei_academy} at the plan's academy site. A world
 * whose chunks there were made before the site existed, or made with structures off, has whatever part it lacks built
 * once, when both of the Academy's chunks and those beside them are loaded: each that never placed its part places it
 * then. A part an operator placed by command is recognised and kept.
 */
public final class AcademySite {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("seireitei_academy"));
    /** The chunks the Academy covers (x -208 to -192, z 224 to 238), the hall's and then the gate's, where it starts. */
    public static final List<ChunkPos> COVERED = List.of(new ChunkPos(-13, 14), new ChunkPos(-12, 14));
    /** Those chunks and every one beside them: placing the Academy's edges reads the blocks just outside it. */
    private static final List<ChunkPos> AROUND =
            COVERED.stream().flatMap(pos -> ChunkPos.rangeClosed(pos, 1)).distinct().toList();
    private static final int NO_RETRY = -1;

    /**
     * The server tick after which a check that found a chunk it needs not yet loaded runs once more, or
     * {@link #NO_RETRY}. Server thread only. One left over from a world closed since costs a check that loads nothing.
     */
    private static int retryAfter = NO_RETRY;

    private AcademySite() {
    }

    /** The chunk the Academy's structure starts in, the one holding its corner: (-12, 14). */
    public static ChunkPos startChunk() {
        return new ChunkPos(SeireiteiPlan.ACADEMY_X >> 4, SeireiteiPlan.ACADEMY_Z >> 4);
    }

    /**
     * One of Soul Society's Academy chunks, or one beside them, has loaded: once the tick is done, if all of them are
     * loaded, whatever part of the Academy is missing is built, and if one is not, the check runs once more after the
     * next tick. Nothing is loaded or made for it, so whichever chunk loads last completes it.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == BleachWorlds.SOUL_SOCIETY
                && AROUND.contains(event.getChunk().getPos())) {
            queueCheck(level, true);
        }
    }

    /** The one further try a check asked for, queued in a tick after the one it ran in. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (retryAfter != NO_RETRY && server.getTickCount() > retryAfter) {
            retryAfter = NO_RETRY;
            ServerLevel level = server.getLevel(BleachWorlds.SOUL_SOCIETY);
            if (level != null) {
                queueCheck(level, false);
            }
        }
    }

    /** Checks once the server's current work is done: a chunk's load event comes inside the load itself. */
    private static void queueCheck(ServerLevel level, boolean mayRetry) {
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> ensureBuiltIfLoaded(level, mayRetry)));
    }

    /**
     * As {@link #ensureBuilt}, but only when every chunk the Academy covers, and every one beside them, is loaded
     * already; else, once, later. A block placed at its edge would make a neighbour that is not loaded load.
     */
    private static void ensureBuiltIfLoaded(ServerLevel level, boolean mayRetry) {
        for (ChunkPos pos : AROUND) {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                if (mayRetry) {
                    retryAfter = level.getServer().getTickCount();
                }
                return;
            }
        }
        ensureBuilt(level);
    }

    /**
     * Builds what was never placed of the Academy: its start, if its chunk has none, and the part of each chunk it covers
     * that holds no reference to it, which then gains one. A part that stands already, placed by command, only gains the
     * reference. Those chunks are loaded, and made, first, unless the Academy is whole already. Server thread only.
     * Public for Konsō's landing and the respawn (7d3), and for game tests.
     *
     * @return whether any part was placed
     */
    public static boolean ensureBuilt(ServerLevel level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        if (structure == null || whole(level, structure)) {
            return false;
        }
        ChunkPos home = startChunk();
        // Made before the start is set, a covered chunk cannot gain its reference, and place its part, on its own.
        List<LevelChunk> chunks = COVERED.stream().map(each -> level.getChunk(each.x, each.z)).toList();
        ChunkAccess homeChunk = level.getChunk(home.x, home.z);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart start = homeChunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), home, 0, level,
                    biome -> true);
            if (!start.isValid()) {
                BleachMod.LOGGER.warn("The Academy in Seireitei could not be laid out in chunk {}; it was not built", home);
                return false;
            }
            homeChunk.setStartForStructure(structure, start);
        }
        boolean placed = false;
        for (ChunkAccess chunk : chunks) {
            ChunkPos pos = chunk.getPos();
            if (chunk.getReferencesForStructure(structure).contains(home.toLong())) {
                continue;
            }
            // A /place sets no reference: building again would stack a second instructor on the first.
            if (!partStands(chunk)) {
                if (chunk.getInhabitedTime() > 0) {
                    BleachMod.LOGGER.warn("The Academy in Seireitei is built over chunk {}, where souls have spent {} ticks",
                            pos, chunk.getInhabitedTime());
                }
                start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                        new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                                pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ()), pos);
                placed = true;
            }
            chunk.addReferenceForStructure(structure, home.toLong());
        }
        return placed;
    }

    /** Whether every chunk it covers is loaded and holds its reference, and its start is valid: nothing to do. */
    private static boolean whole(ServerLevel level, Structure structure) {
        ChunkPos home = startChunk();
        for (ChunkPos pos : COVERED) {
            ChunkAccess chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null || !chunk.getReferencesForStructure(structure).contains(home.toLong())) {
                return false;
            }
        }
        StructureStart start = level.getChunkSource().getChunkNow(home.x, home.z).getStartForStructure(structure);
        return start != null && start.isValid();
    }

    /**
     * Whether this chunk's part of the Academy stands already, by a block only that part puts there: the hall's Tailoring
     * Table, template (3, 1, 13), and the gate's north post, template (5, 1, 0), turned clockwise about the corner. A
     * block comes with its chunk, where the instructor, loaded after it, might not be there yet.
     */
    private static boolean partStands(ChunkAccess chunk) {
        int x = SeireiteiPlan.ACADEMY_X, y = SeireiteiPlan.ACADEMY_FLOOR_Y, z = SeireiteiPlan.ACADEMY_Z;
        BlockPos table = new BlockPos(x - 13, y, z + 3), post = new BlockPos(x, y, z + 5);
        ChunkPos pos = chunk.getPos();
        return new ChunkPos(table).equals(pos) ? chunk.getBlockState(table).is(ModBlocks.TAILORING_TABLE.get())
                : new ChunkPos(post).equals(pos) && chunk.getBlockState(post).is(Blocks.STRIPPED_DARK_OAK_LOG);
    }
}
