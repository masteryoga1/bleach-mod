package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The mod's own entity model layers. The Spider Hollow and the Hexapodus borrow the vanilla spider's
 * layer and the Plus the villager's, so only the Hollows with models of their own are here, with
 * what the Quincy arts put on a player: the Sanrei Glove and Vollständig's wings and halo.
 */
public final class BleachModelLayers {
    public static final ModelLayerLocation HOUND_HOLLOW = layer("hound_hollow");
    public static final ModelLayerLocation SERPENT_HOLLOW = layer("serpent_hollow");
    public static final ModelLayerLocation FISHBONE_D = layer("fishbone_d");
    public static final ModelLayerLocation SANREI_GLOVE = layer("sanrei_glove");
    public static final ModelLayerLocation VOLLSTANDIG = layer("vollstandig");

    private BleachModelLayers() {
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(BleachMod.id(name), "main");
    }

    /** Tells the game how to build each layer's mesh. On the mod bus. */
    static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HOUND_HOLLOW, HoundHollowModel::createBodyLayer);
        event.registerLayerDefinition(SERPENT_HOLLOW, SerpentHollowModel::createBodyLayer);
        event.registerLayerDefinition(FISHBONE_D, FishboneDModel::createBodyLayer);
        event.registerLayerDefinition(SANREI_GLOVE, SanreiGloveModel::createLayer);
        event.registerLayerDefinition(VOLLSTANDIG, VollstandigModel::createLayer);
    }
}
