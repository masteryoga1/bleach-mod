package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The training room under the Urahara Shop, {@code bleach:urahara_training_room}: a hall of rock whose floor lies 22 blocks
 * under the shop's, reached by a hatch behind the counter and a ladder. No structure set lists it, so worldgen never lays
 * it; laid at the shop's chunk, as {@code /place structure} lays it, it turns as the shop does and falls exactly under it.
 */
public final class UraharaRoom {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("urahara_training_room"));
    public static final ResourceLocation TEMPLATE = BleachMod.id("urahara_training_room");
    /**
     * The room's rock and air before the shop's turn, from the shop's corner on its floor: from its floor 22 below the
     * shop's to its roof 9 below, over the shop's footprint grown alike on each side. The template's corner is the first.
     */
    public static final BlockPos ROOM_MIN = new BlockPos(-10, -22, -8), ROOM_MAX = new BlockPos(22, -9, 23);
    /** The shaft's rock round the ladder, from over the room's roof to under the shop's floor. */
    public static final BlockPos SHAFT_MIN = new BlockPos(1, -8, 8), SHAFT_MAX = new BlockPos(3, -1, 10);
    /** The hatch in the shop's floor behind the counter's west end, which the ladder runs down from. */
    public static final BlockPos HATCH = new BlockPos(2, 0, 9);

    private UraharaRoom() {
    }

    /** The room as its structure lays it out under the shop at this chunk, as {@code /place structure} does; or null. */
    @Nullable
    public static StructureStart startAt(ServerLevel overworld, ChunkPos chunk) {
        Structure laid = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        if (laid == null) {
            return null;
        }
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        StructureStart start = laid.generate(overworld.registryAccess(), generator, generator.getBiomeSource(),
                overworld.getChunkSource().randomState(), overworld.getStructureManager(), overworld.getSeed(), chunk, 0,
                overworld, biome -> true);
        return start.isValid() && start.getPieces().get(0) instanceof PoolElementStructurePiece ? start : null;
    }
}
