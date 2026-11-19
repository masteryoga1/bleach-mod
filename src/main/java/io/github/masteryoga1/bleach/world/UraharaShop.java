package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/**
 * The Urahara Shop, {@code bleach:urahara_shop}: one in each Overworld made with structures on, which worldgen builds at
 * the one spot its ring of one gives, roughly 170 to 870 blocks from the world's centre and never on it, on dry, gentle
 * land where the world has any near that spot. A default flat world has no ring, so no shop.
 */
public final class UraharaShop {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("urahara_shop"));
    public static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("urahara_shop"));
    public static final ResourceLocation TEMPLATE = BleachMod.id("urahara_shop");
    /** The template before its turn: 13 wide, 13 high and 16 deep, its open front at z 0. */
    public static final Vec3i SIZE = new Vec3i(13, 13, 16);
    /** Where Kisuke Urahara stands in the template before its turn: behind the counter, facing the door. */
    public static final BlockPos KEEPER = new BlockPos(4, 1, 9);

    private UraharaShop() {
    }

    /** The ring's one spot for the shop in this world, or null where its set is not placed, as in a default flat world. */
    @Nullable
    public static ChunkPos spot(ServerLevel overworld, ChunkGeneratorStructureState state) {
        StructureSet set = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET);
        // Rings are kept by their placement itself, so it must be the registry's own.
        if (set == null || !(set.placement() instanceof ConcentricRingsStructurePlacement rings)) {
            return null;
        }
        List<ChunkPos> spots = state.getRingPositionsFor(rings);
        return spots == null || spots.isEmpty() ? null : spots.get(0);
    }

    /**
     * The shop's site at this spot, as worldgen lays it out there: at the chunk's corner, turned and set at its height;
     * null if none lays out.
     */
    @Nullable
    public static SeireiteiPlan.Site site(ServerLevel overworld, ChunkPos spot) {
        Structure laid = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        if (laid == null) {
            return null;
        }
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        StructureStart start = laid.generate(overworld.registryAccess(), generator, generator.getBiomeSource(),
                overworld.getChunkSource().randomState(), overworld.getStructureManager(), overworld.getSeed(), spot, 0,
                overworld, biome -> true);
        if (!start.isValid() || !(start.getPieces().get(0) instanceof PoolElementStructurePiece piece)) {
            return null;
        }
        BlockPos corner = piece.getPosition();
        return new SeireiteiPlan.Site("urahara_shop", corner.getX(), corner.getZ(), TEMPLATE, piece.getRotation(),
                SIZE.getX(), SIZE.getZ(), corner.getY());
    }
}
