package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only entry point: HUD, screens, key bindings and how souls are drawn. */
@Mod(value = BleachMod.MODID, dist = Dist.CLIENT)
public final class BleachClient {
    public static final String KEY_CATEGORY = "key.categories.bleach";

    public static final KeyMapping SOUL_SCREEN_KEY = new KeyMapping("key.bleach.soul_screen",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY);
    public static final KeyMapping REIATSU_RELEASE_KEY = new KeyMapping("key.bleach.reiatsu_release",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY);

    public BleachClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(BleachClient::registerGuiLayers);
        modEventBus.addListener(BleachClient::registerKeyMappings);
        modEventBus.addListener(BleachClient::addLayers);
        NeoForge.EVENT_BUS.addListener(BleachClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BleachClient::onRenderLiving);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ReiryokuHud.ID, ReiryokuHud::render);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SOUL_SCREEN_KEY);
        event.register(REIATSU_RELEASE_KEY);
    }

    /** Adds the chain and mask layer to both player models (wide and slim arms). */
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new SoulLayer(playerRenderer, event.getEntityModels()));
            }
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (SOUL_SCREEN_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new SoulStatsScreen());
            }
        }
        while (REIATSU_RELEASE_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null) {
                BleachNetwork.requestReiatsuToggle();
            }
        }
    }

    /**
     * The spiritually unaware cannot see spirits. Cancelling skips the body, its layers and its name
     * tag; the shadow still shows, which needs a mixin to hide and is left for later.
     */
    private static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (ClientSouls.isHiddenFromViewer(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
