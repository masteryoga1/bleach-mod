package io.github.masteryoga1.bleach;

import com.mojang.logging.LogUtils;
import io.github.masteryoga1.bleach.registry.ModCreativeTabs;
import io.github.masteryoga1.bleach.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BleachMod.MODID)
public final class BleachMod {
    public static final String MODID = "bleach";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BleachMod(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
    }

    /** Builds a resource location in this mod's namespace, e.g. {@code bleach:asauchi}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
