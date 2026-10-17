package io.github.masteryoga1.bleach;

import com.mojang.logging.LogUtils;
import io.github.masteryoga1.bleach.advancement.BleachTriggers;
import io.github.masteryoga1.bleach.command.BleachCommand;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModArmorMaterials;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModCreativeTabs;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModMenus;
import io.github.masteryoga1.bleach.registry.ModRecipes;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BleachMod.MODID)
public final class BleachMod {
    public static final String MODID = "bleach";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BleachMod(IEventBus modEventBus, ModContainer modContainer) {
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        SoulAttachments.ATTACHMENT_TYPES.register(modEventBus);
        BleachTriggers.TRIGGERS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, BleachConfig.SPEC);
        modEventBus.addListener(BleachNetwork::register);
        modEventBus.addListener(ModEntities::onAttributes);
        modEventBus.addListener(ZanpakutoSpirits::register);

        SoulEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(BleachCommand::register);
    }

    /** Builds a resource location in this mod's namespace, e.g. {@code bleach:asauchi}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
