package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The Academy this world was promised: if no Academy generated within reach, one is built near world
 * spawn and remembered here, so every soul that passes on has somewhere to go. In a new world it is
 * built at once; in one already played, only on ground nobody has spent time on. Kept in the
 * Overworld's saved data.
 */
public final class AcademySites extends SavedData {
    public static final ResourceKey<Structure> ACADEMY = ResourceKey.create(Registries.STRUCTURE, BleachMod.id("academy"));
    private static final String NAME = "bleach_academy";
    /**
     * How far from world spawn the guaranteed Academy may stand, nearest first. The far rings are for
     * worlds whose players have already built all around spawn.
     */
    private static final int[] RING_DISTANCES = {96, 160, 224, 320, 448, 640, 896, 1280};
    private static final int DIRECTIONS = 8;
    /** Ground whose corners differ by more than this many blocks is too steep for it. */
    private static final int MAX_HEIGHT_SPREAD = 4;
    /** After finding nowhere to build, how long before looking again: 5 minutes. */
    private static final long RETRY_TICKS = 6000L;

    @Nullable
    private BlockPos guaranteed;
    /** When the last search for somewhere to build found nowhere. Not saved. */
    private long failedAt = Long.MIN_VALUE;

    public static SavedData.Factory<AcademySites> factory() {
        return new SavedData.Factory<>(AcademySites::new, AcademySites::load, null);
    }

    public static AcademySites get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** Where the guaranteed Academy stands: the corner of the chunk it was built from, at y 0. */
    public Optional<BlockPos> guaranteed() {
        return Optional.ofNullable(guaranteed);
    }

    public static AcademySites load(CompoundTag tag, HolderLookup.Provider registries) {
        AcademySites sites = new AcademySites();
        sites.guaranteed = NbtUtils.readBlockPos(tag, "guaranteed").orElse(null);
        return sites;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (guaranteed != null) {
            tag.put("guaranteed", NbtUtils.writeBlockPos(guaranteed));
        }
        return tag;
    }

    /**
     * Builds the guaranteed Academy unless it already exists: on the nearest flat dry ground around
     * world spawn, or failing that the nearest at all, but never where anyone has spent time, so it
     * never lands on what players built. Loads chunks and builds the Academy in them, so call it
     * rarely and only on the server thread.
     *
     * @return where it stands, or null if there was nowhere to build it
     */
    @Nullable
    public BlockPos ensureGuaranteed(ServerLevel overworld) {
        long now = overworld.getGameTime();
        if (guaranteed != null || now - failedAt < RETRY_TICKS) {
            return guaranteed;
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        StructureStart fallback = null;
        ChunkPos fallbackChunk = null;
        for (int distance : RING_DISTANCES) {
            for (int direction = 0; direction < DIRECTIONS; direction++) {
                double angle = direction * 2.0 * Math.PI / DIRECTIONS;
                int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
                int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
                ChunkPos chunk = new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
                StructureStart start = generate(overworld, chunk);
                if (start == null || !isUntouched(overworld, start.getBoundingBox())) {
                    continue;
                }
                if (isGoodGround(overworld, start.getBoundingBox())) {
                    return record(overworld, start, chunk);
                }
                if (fallback == null) {
                    fallback = start;
                    fallbackChunk = chunk;
                }
            }
        }
        if (fallback != null) {
            return record(overworld, fallback, fallbackChunk);
        }
        failedAt = now;
        BleachMod.LOGGER.warn("Found no untouched ground near world spawn for the guaranteed Academy");
        return null;
    }

    private BlockPos record(ServerLevel overworld, StructureStart start, ChunkPos chunk) {
        guaranteed = place(overworld, start, chunk);
        setDirty();
        BleachMod.LOGGER.info("Built the guaranteed Academy at {}", guaranteed);
        return guaranteed;
    }

    /**
     * Whether no player has spent time where the Academy would stand, and it is inside the world
     * border. Reads the chunks without generating them.
     */
    private static boolean isUntouched(ServerLevel level, BoundingBox box) {
        if (!level.getWorldBorder().isWithinBounds(box.minX(), box.minZ())
                || !level.getWorldBorder().isWithinBounds(box.maxX(), box.maxZ())) {
            return false;
        }
        for (ChunkPos each : chunksUnder(box)) {
            ChunkAccess chunk = level.getChunk(each.x, each.z, ChunkStatus.EMPTY, true);
            long inhabited = chunk instanceof ImposterProtoChunk imposter
                    ? imposter.getWrapped().getInhabitedTime() : chunk.getInhabitedTime();
            if (inhabited > 0L) {
                return false;
            }
        }
        return true;
    }

    /** Level, dry ground under the Academy's box, judged from the world generator without generating anything. */
    private static boolean isGoodGround(ServerLevel level, BoundingBox box) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int[] corner : new int[][] {{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.minX(), box.maxZ()},
                {box.maxX(), box.maxZ()}}) {
            int height = generator.getBaseHeight(corner[0], corner[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
        }
        if (highest - lowest > MAX_HEIGHT_SPREAD) {
            return false;
        }
        int x = (box.minX() + box.maxX()) / 2;
        int z = (box.minZ() + box.maxZ()) / 2;
        int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
        NoiseColumn column = generator.getBaseColumn(x, z, level, random);
        return column.getBlock(top - 1).getFluidState().isEmpty();
    }

    /**
     * Builds an Academy starting in this chunk, the way {@code /place structure} does, whatever the
     * biome. It is not recorded as a generated structure, so locating it relies on {@link #guaranteed}.
     *
     * @return the corner of the chunk at y 0, as a structure search reports it, or null if it could not be built
     */
    @Nullable
    public static BlockPos placeAt(ServerLevel level, ChunkPos chunk) {
        StructureStart start = generate(level, chunk);
        return start != null ? place(level, start, chunk) : null;
    }

    /** Lays out an Academy starting in this chunk, rotated as the world seed says, without building it. */
    @Nullable
    private static StructureStart generate(ServerLevel level, ChunkPos chunk) {
        Optional<Holder.Reference<Structure>> structure =
                level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ACADEMY);
        if (structure.isEmpty()) {
            return null;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart start = structure.get().value().generate(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunk, 0, level, biome -> true);
        return start.isValid() ? start : null;
    }

    private static BlockPos place(ServerLevel level, StructureStart start, ChunkPos chunk) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        List<ChunkPos> chunks = chunksUnder(start.getBoundingBox());
        chunks.forEach(each -> level.getChunk(each.x, each.z));
        chunks.forEach(each -> start.placeInChunk(level, level.structureManager(), generator,
                level.getRandom(), new BoundingBox(each.getMinBlockX(), level.getMinBuildHeight(), each.getMinBlockZ(),
                        each.getMaxBlockX(), level.getMaxBuildHeight(), each.getMaxBlockZ()), each));
        return new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ());
    }

    private static List<ChunkPos> chunksUnder(BoundingBox box) {
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        return ChunkPos.rangeClosed(min, max).toList();
    }
}
