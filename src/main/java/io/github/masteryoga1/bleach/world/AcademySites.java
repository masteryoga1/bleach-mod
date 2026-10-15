package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.ArrayList;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The Academy this world was promised: if no Academy generated within reach, one is built near world
 * spawn and remembered here, so every soul that passes on has somewhere to go. Kept in the
 * Overworld's saved data.
 */
public final class AcademySites extends SavedData {
    public static final ResourceKey<Structure> ACADEMY = ResourceKey.create(Registries.STRUCTURE, BleachMod.id("academy"));
    private static final String NAME = "bleach_academy";
    /** How far from world spawn the guaranteed Academy may stand, nearest first. */
    private static final int[] RING_DISTANCES = {96, 160, 224};
    private static final int DIRECTIONS = 8;
    /** The Academy's footprint before rotation, for judging the ground. */
    private static final int WIDTH = 15;
    private static final int DEPTH = 17;
    /** Ground whose corners differ by more than this many blocks is too steep for it. */
    private static final int MAX_HEIGHT_SPREAD = 4;

    @Nullable
    private BlockPos guaranteed;

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
     * Builds the guaranteed Academy unless it already exists: on the flattest dry ground among a few
     * spots around world spawn. Loads and generates the chunks it stands in, so call it rarely and
     * only on the server thread.
     *
     * @return where it stands, or null if it could not be built anywhere
     */
    @Nullable
    public BlockPos ensureGuaranteed(ServerLevel overworld) {
        if (guaranteed != null) {
            return guaranteed;
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        List<ChunkPos> candidates = new ArrayList<>();
        for (int distance : RING_DISTANCES) {
            for (int direction = 0; direction < DIRECTIONS; direction++) {
                double angle = direction * 2.0 * Math.PI / DIRECTIONS;
                int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
                int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
                candidates.add(new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)));
            }
        }
        List<ChunkPos> ordered = new ArrayList<>(candidates.stream().filter(chunk -> isGoodGround(overworld, chunk)).toList());
        List<ChunkPos> rest = candidates.stream().filter(chunk -> !ordered.contains(chunk)).toList();
        ordered.addAll(rest);
        for (ChunkPos chunk : ordered) {
            BlockPos placed = placeAt(overworld, chunk);
            if (placed != null) {
                guaranteed = placed;
                setDirty();
                BleachMod.LOGGER.info("Built the guaranteed Academy at {}", placed);
                return placed;
            }
        }
        return null;
    }

    /** Level, dry ground for the footprint, judged from the world generator without generating anything. */
    private static boolean isGoodGround(ServerLevel level, ChunkPos chunk) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int[] corner : new int[][] {{x0, z0}, {x0 + WIDTH - 1, z0}, {x0, z0 + DEPTH - 1}, {x0 + WIDTH - 1, z0 + DEPTH - 1}}) {
            int height = generator.getBaseHeight(corner[0], corner[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
        }
        if (highest - lowest > MAX_HEIGHT_SPREAD) {
            return false;
        }
        int x = x0 + WIDTH / 2;
        int z = z0 + DEPTH / 2;
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
        Optional<Holder.Reference<Structure>> structure =
                level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ACADEMY);
        if (structure.isEmpty()) {
            return null;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart start = structure.get().value().generate(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunk, 0, level, biome -> true);
        if (!start.isValid()) {
            return null;
        }
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(each -> level.getChunk(each.x, each.z));
        ChunkPos.rangeClosed(min, max).forEach(each -> start.placeInChunk(level, level.structureManager(), generator,
                level.getRandom(), new BoundingBox(each.getMinBlockX(), level.getMinBuildHeight(), each.getMinBlockZ(),
                        each.getMaxBlockX(), level.getMaxBuildHeight(), each.getMaxBlockZ()), each));
        return new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ());
    }
}
