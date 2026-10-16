package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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

    public static final KeyMapping FLASH_STEP_KEY = new KeyMapping("key.bleach.flash_step",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEY_CATEGORY);

    /** Whether the release key was down last tick, so holding it (and key repeat) toggles only once. */
    private static boolean releaseKeyWasDown;

    public BleachClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, BleachClientConfig.SPEC);
        modEventBus.addListener(BleachClient::registerGuiLayers);
        modEventBus.addListener(BleachClient::registerKeyMappings);
        modEventBus.addListener(BleachClient::addLayers);
        modEventBus.addListener(BleachClient::registerScreens);
        modEventBus.addListener(BleachClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(BleachClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(ReishiFootingClient::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(ReishiFootingClient::onPlayerTickPre);
        NeoForge.EVENT_BUS.addListener(BleachClient::onRenderLiving);
        if (ClientSmokeTest.isEnabled()) {
            NeoForge.EVENT_BUS.addListener(ClientSmokeTest::onClientTick);
        }
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ReiryokuHud.ID, ReiryokuHud::render);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SOUL_SCREEN_KEY);
        event.register(REIATSU_RELEASE_KEY);
        event.register(FLASH_STEP_KEY);
        event.register(ReishiFootingClient.REISHI_FOOTING_KEY);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TAILORING.get(), TailoringScreen::new);
    }

    /** The NPCs wear two of the game's default player skins until the mod has its own. */
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ResourceLocation instructor = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/kai.png");
        ResourceLocation wounded = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/noor.png");
        event.registerEntityRenderer(ModEntities.ACADEMY_INSTRUCTOR.get(),
                context -> new ShinigamiNpcRenderer<AcademyInstructor>(context, instructor));
        event.registerEntityRenderer(ModEntities.WOUNDED_SHINIGAMI.get(),
                context -> new ShinigamiNpcRenderer<WoundedShinigami>(context, wounded));
    }

    /** Adds the chain and mask layer and the sheathed blade to both player models (wide and slim arms). */
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new SoulLayer(playerRenderer, event.getEntityModels()));
                playerRenderer.addLayer(new SayaLayer<>(playerRenderer,
                        player -> ClientSouls.appearance(player).bladeSheathed()));
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
        boolean pressed = false;
        while (REIATSU_RELEASE_KEY.consumeClick()) {
            pressed = true;
        }
        if (pressed && !releaseKeyWasDown && minecraft.player != null && minecraft.getConnection() != null) {
            BleachNetwork.requestReiatsuToggle();
        }
        releaseKeyWasDown = REIATSU_RELEASE_KEY.isDown();

        // One flash step per press: the server keeps the cooldown, so extra presses just do nothing.
        boolean flashStep = false;
        while (FLASH_STEP_KEY.consumeClick()) {
            flashStep = true;
        }
        if (flashStep && minecraft.player != null && minecraft.screen == null && minecraft.getConnection() != null) {
            BleachNetwork.requestFlashStep(minecraft.player.input.forwardImpulse, minecraft.player.input.leftImpulse);
        }
    }

    /**
     * The spiritually unaware cannot see spirits. The mixins under {@code mixin.client} skip hidden
     * spirits entirely (body, name tag, shadow, fire, hitbox) and let the crosshair pass through
     * them; this is a fallback for renderers that draw entities without asking first.
     */
    private static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (ClientSouls.isHiddenFromViewer(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
