package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only entry point: HUD, screens and key bindings. */
@Mod(value = BleachMod.MODID, dist = Dist.CLIENT)
public final class BleachClient {
    public static final String KEY_CATEGORY = "key.categories.bleach";

    public static final KeyMapping SOUL_SCREEN_KEY = new KeyMapping("key.bleach.soul_screen",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY);

    public BleachClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(BleachClient::registerGuiLayers);
        modEventBus.addListener(BleachClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(BleachClient::onClientTick);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ReiryokuHud.ID, ReiryokuHud::render);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SOUL_SCREEN_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (SOUL_SCREEN_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new SoulStatsScreen());
            }
        }
    }
}
