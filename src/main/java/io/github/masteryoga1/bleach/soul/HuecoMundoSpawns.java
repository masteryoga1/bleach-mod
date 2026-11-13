package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Menos;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Hueco Mundo's own Hollows: where the mod's Hollows may spawn by themselves, which only Hueco Mundo's dunes and the
 * Forest of Menos list. Light never matters; the ground must be white sand or white sandstone; Hueco Mundo keeps its own
 * count of Menos. Spawners and every other way a Hollow comes are left as they were.
 */
public final class HuecoMundoSpawns {
    /** The dunes, whose spawners list the basic Hollows. */
    public static final ResourceKey<Biome> DUNES = ResourceKey.create(Registries.BIOME, BleachMod.id("hueco_mundo"));
    /** The cavern below, whose spawners list the Gillian and the Adjuchas. */
    public static final ResourceKey<Biome> FOREST_OF_MENOS =
            ResourceKey.create(Registries.BIOME, BleachMod.id("forest_of_menos"));

    private HuecoMundoSpawns() {
    }

    /** On the mod bus: each basic Hollow's ground rule, and for the Gillian and the Adjuchas Hueco Mundo's count too. */
    public static void registerPlacements(RegisterSpawnPlacementsEvent event) {
        place(event, ModEntities.SPIDER_HOLLOW.get(), HuecoMundoSpawns::mayHollowSpawn);
        place(event, ModEntities.HEXAPODUS.get(), HuecoMundoSpawns::mayHollowSpawn);
        place(event, ModEntities.HOUND_HOLLOW.get(), HuecoMundoSpawns::mayHollowSpawn);
        place(event, ModEntities.SERPENT_HOLLOW.get(), HuecoMundoSpawns::mayHollowSpawn);
        place(event, ModEntities.GILLIAN.get(), HuecoMundoSpawns::mayMenosSpawn);
        place(event, ModEntities.ADJUCHAS.get(), HuecoMundoSpawns::mayMenosSpawn);
    }

    // No placement was registered for these before, so this one sets the placement and the heightmap; the operation is
    // read only when a type already has one.
    private static <T extends Entity> void place(RegisterSpawnPlacementsEvent event, EntityType<T> type,
                                                 SpawnPlacements.SpawnPredicate<T> rule) {
        event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rule,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * Whether a Hollow may come here: a natural spawn only while Hueco Mundo's spawns are on, off Peaceful, on its ground;
     * a spawner or any other way, as before.
     */
    public static boolean mayHollowSpawn(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                         RandomSource random) {
        if (reason != MobSpawnType.NATURAL) {
            return true;
        }
        return BleachConfig.get(BleachConfig.HUECO_MUNDO_NATURAL_SPAWNS) && level.getDifficulty() != Difficulty.PEACEFUL
                && onHollowGround(level, pos);
    }

    /** As for a Hollow, and a natural one only while this world holds fewer Menos than the server allows, none near. */
    public static boolean mayMenosSpawn(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                        RandomSource random) {
        return mayHollowSpawn(type, level, reason, pos, random)
                && (reason != MobSpawnType.NATURAL || Menos.mayRiseIn(level.getLevel(), Vec3.atBottomCenterOf(pos)));
    }

    /** Whether the block under this spot is white sand or white sandstone, Hueco Mundo's own ground. */
    public static boolean onHollowGround(LevelReader level, BlockPos pos) {
        BlockState ground = level.getBlockState(pos.below());
        return ground.is(ModBlocks.WHITE_SAND.get()) || ground.is(ModBlocks.WHITE_SANDSTONE.get());
    }

    /** Whether Hueco Mundo's Hollows can come at all: its spawns on, mob spawning on, and not Peaceful. */
    public static boolean allowed(Difficulty difficulty, boolean mobSpawning) {
        return BleachConfig.get(BleachConfig.HUECO_MUNDO_NATURAL_SPAWNS) && mobSpawning && difficulty != Difficulty.PEACEFUL;
    }

    /**
     * Whether this spot lies where Hueco Mundo's Hollows spawn: on the dunes, or in the Forest of Menos while the server
     * lets any Menos rise, since only Menos are listed there.
     */
    public static boolean hollowsLiveAt(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(DUNES) || (biome.is(FOREST_OF_MENOS) && BleachConfig.get(BleachConfig.MENOS_MAX_ALIVE) > 0);
    }
}
