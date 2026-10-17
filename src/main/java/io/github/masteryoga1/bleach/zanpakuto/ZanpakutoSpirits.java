package io.github.masteryoga1.bleach.zanpakuto;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The zanpakutō spirits a world knows, read from data packs at
 * {@code data/<namespace>/bleach/zanpakuto_spirit/<id>.json} when the world loads (not on
 * {@code /reload}). The registry stays on the server and is never sent to clients, so joining needs
 * no matching data and nothing on the client may look a spirit up: there, every lookup finds nothing.
 */
public final class ZanpakutoSpirits {
    public static final ResourceKey<Registry<ZanpakutoSpirit>> REGISTRY =
            ResourceKey.createRegistryKey(BleachMod.id("zanpakuto_spirit"));

    /** Ichigo's: a constant release whose technique is Getsuga Tenshō. */
    public static final ResourceKey<ZanpakutoSpirit> ZANGETSU = ResourceKey.create(REGISTRY, BleachMod.id("zangetsu"));
    /** Renji's: a power type whose blade lashes out in segments. */
    public static final ResourceKey<ZanpakutoSpirit> ZABIMARU = ResourceKey.create(REGISTRY, BleachMod.id("zabimaru"));

    private ZanpakutoSpirits() {
    }

    /** Registers the spirits as a data pack registry, with no network codec, so it is never synced. */
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY, ZanpakutoSpirit.CODEC);
    }

    /** The world's spirits; empty on a client, which never receives them. */
    public static Optional<Registry<ZanpakutoSpirit>> registry(Level level) {
        return level.registryAccess().registry(REGISTRY);
    }

    /** The spirit with this id, if the world has it. */
    public static Optional<ZanpakutoSpirit> get(Level level, ResourceLocation id) {
        return registry(level).flatMap(spirits -> spirits.getOptional(id));
    }

    /** The spirit with this id, if there is an id and the world has it. */
    public static Optional<ZanpakutoSpirit> get(Level level, Optional<ResourceLocation> id) {
        return id.flatMap(found -> get(level, found));
    }
}
