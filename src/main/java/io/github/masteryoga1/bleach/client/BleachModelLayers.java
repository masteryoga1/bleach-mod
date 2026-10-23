package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The mod's own entity model layers. The Spider Hollow and the Hexapodus borrow the vanilla spider's
 * layer and the Plus the villager's, so only the Hollows with models of their own are here, with
 * what the Quincy arts put on a player: the Sanrei Glove, Vollständig's wings and halo, and the
 * shell Blut Vene's veins are drawn on over armour.
 */
public final class BleachModelLayers {
    public static final ModelLayerLocation HOUND_HOLLOW = layer("hound_hollow");
    public static final ModelLayerLocation SERPENT_HOLLOW = layer("serpent_hollow");
    public static final ModelLayerLocation FISHBONE_D = layer("fishbone_d");
    public static final ModelLayerLocation SANREI_GLOVE = layer("sanrei_glove");
    public static final ModelLayerLocation VOLLSTANDIG = layer("vollstandig");
    /** Blut Vene's veins over armour: the outer armour layer, a little larger, on the skin's layout. */
    public static final ModelLayerLocation VEIN_SHELL = layer("vein_shell");
    private static final CubeDeformation VEIN_SHELL_SIZE = new CubeDeformation(1.25F);

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
        event.registerLayerDefinition(VEIN_SHELL,
                () -> LayerDefinition.create(HumanoidArmorModel.createBodyLayer(VEIN_SHELL_SIZE), 64, 64));
    }
}
