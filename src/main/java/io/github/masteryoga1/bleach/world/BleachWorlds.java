package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/** The mod's three worlds, and the one place travel code looks a world up, which a game test can point elsewhere. */
public final class BleachWorlds {
    public static final ResourceKey<Level> SOUL_SOCIETY = world("soul_society");
    /** Hueco Mundo's key; its world comes in a later step. */
    public static final ResourceKey<Level> HUECO_MUNDO = world("hueco_mundo");
    /** The Dangai's key: the corridor between the worlds, which the Senkaimon's unguided cross. */
    public static final ResourceKey<Level> DANGAI = world("dangai");
    public static final ResourceKey<DimensionType> SOUL_SOCIETY_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, BleachMod.id("soul_society"));
    public static final ResourceKey<NoiseGeneratorSettings> SOUL_SOCIETY_NOISE =
            ResourceKey.create(Registries.NOISE_SETTINGS, BleachMod.id("soul_society"));

    private static final Map<ResourceKey<Level>, ResourceKey<Level>> STAND_INS = new ConcurrentHashMap<>();

    private BleachWorlds() {
    }

    /** The server's world for this key, or for its stand-in while a test has set one; null if it has neither. */
    @Nullable
    public static ServerLevel level(MinecraftServer server, ResourceKey<Level> world) {
        return server.getLevel(STAND_INS.getOrDefault(world, world));
    }

    /** For game tests only: looks up {@code standIn} wherever {@code world} is asked for, or stops when it is null. */
    public static void standIn(ResourceKey<Level> world, @Nullable ResourceKey<Level> standIn) {
        if (standIn == null) {
            STAND_INS.remove(world);
        } else {
            STAND_INS.put(world, standIn);
        }
    }

    /** Whether this is one of the mod's spirit worlds: Soul Society, Hueco Mundo or the Dangai. */
    public static boolean isSpiritWorld(ResourceKey<Level> world) {
        return world == SOUL_SOCIETY || world == HUECO_MUNDO || world == DANGAI;
    }

    private static ResourceKey<Level> world(String name) {
        return ResourceKey.create(Registries.DIMENSION, BleachMod.id(name));
    }
}
