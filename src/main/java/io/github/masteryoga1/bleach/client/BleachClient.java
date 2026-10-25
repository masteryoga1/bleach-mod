package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.Hexapodus;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HoundHollow;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.QuincyElder;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.SerpentHollow;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
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

/**
 * Client-only entry point: HUD, screens, key bindings, and how souls, zanpakutō techniques, the
 * Quincy's reishi and a Hollow's body are drawn.
 */
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
        modEventBus.addListener(BleachModelLayers::register);
        QuincyClient.register(modEventBus);
        HollowPlayerClient.register(NeoForge.EVENT_BUS);
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
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, JinzenOverlay.ID, JinzenOverlay::render);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, QuincyClient.POISON_LAYER_ID, QuincyClient::renderPoison);
        // Registered last, so it goes straight above vanilla's camera overlays, beneath Jinzen and the poison.
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, HollowPlayerClient.MASK_EDGE_LAYER_ID,
                HollowPlayerClient::renderMaskEdge);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SOUL_SCREEN_KEY);
        event.register(REIATSU_RELEASE_KEY);
        event.register(FLASH_STEP_KEY);
        event.register(ReishiFootingClient.REISHI_FOOTING_KEY);
        event.register(ZanpakutoKeys.RELEASE);
        event.register(ZanpakutoKeys.SEAL);
        event.register(ZanpakutoKeys.TECHNIQUE);
        event.register(QuincyClient.BLUT_KEY);
        event.register(QuincyClient.FORM_KEY);
        event.register(HollowKeys.DEVOUR);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TAILORING.get(), TailoringScreen::new);
    }

    /**
     * The NPCs wear four of the game's default player skins until the mod has its own, no two alike:
     * the Academy's instructor, the wounded Shinigami, the Quincy Elder and the Quincy Hunter. Hollows
     * use the vanilla spider and wolf models or simple ones of the mod's, and a Plus looks like a villager.
     * Zanpakutō techniques and Gintō spells are drawn from plain shapes and the mod's placeholder
     * textures, and the Heilig Pfeil, the fired Seele Schneider and the Letzt Pfeil as vanilla arrows
     * in the mod's own textures.
     * The type arguments are spelled out rather than left for the compiler to infer through the lambdas.
     */
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ResourceLocation instructor = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/kai.png");
        ResourceLocation wounded = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/noor.png");
        event.registerEntityRenderer(ModEntities.ACADEMY_INSTRUCTOR.get(),
                context -> new ShinigamiNpcRenderer<AcademyInstructor>(context, instructor));
        event.registerEntityRenderer(ModEntities.WOUNDED_SHINIGAMI.get(),
                context -> new ShinigamiNpcRenderer<WoundedShinigami>(context, wounded));
        ResourceLocation elder = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/zuri.png");
        ResourceLocation hunter = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/efe.png");
        event.registerEntityRenderer(ModEntities.QUINCY_ELDER.get(),
                context -> new QuincyNpcRenderer<QuincyElder>(context, elder));
        event.registerEntityRenderer(ModEntities.QUINCY_HUNTER.get(),
                context -> new QuincyNpcRenderer<QuincyHunter>(context, hunter));
        // Spiders end on their backs when they die; the rest fall on their sides.
        event.registerEntityRenderer(ModEntities.SPIDER_HOLLOW.get(),
                context -> new HollowRenderer<SpiderHollow, SpiderModel<SpiderHollow>>(context,
                        new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)),
                        HollowKind.SPIDER, 180.0F));
        event.registerEntityRenderer(ModEntities.HEXAPODUS.get(),
                context -> new HollowRenderer<Hexapodus, HexapodusModel>(context,
                        new HexapodusModel(context.bakeLayer(ModelLayers.SPIDER)),
                        HollowKind.HEXAPODUS, 180.0F));
        event.registerEntityRenderer(ModEntities.HOUND_HOLLOW.get(),
                context -> new HollowRenderer<HoundHollow, HoundHollowModel>(context,
                        new HoundHollowModel(context.bakeLayer(BleachModelLayers.HOUND_HOLLOW)),
                        HollowKind.HOUND, 90.0F));
        event.registerEntityRenderer(ModEntities.SERPENT_HOLLOW.get(),
                context -> new HollowRenderer<SerpentHollow, SerpentHollowModel>(context,
                        new SerpentHollowModel(context.bakeLayer(BleachModelLayers.SERPENT_HOLLOW)),
                        HollowKind.SERPENT, 90.0F));
        event.registerEntityRenderer(ModEntities.FISHBONE_D.get(),
                context -> new HollowRenderer<FishboneD, FishboneDModel>(context,
                        new FishboneDModel(context.bakeLayer(BleachModelLayers.FISHBONE_D)),
                        HollowKind.FISHBONE_D, 90.0F));
        event.registerEntityRenderer(ModEntities.PLUS.get(), PlusRenderer::new);
        event.registerEntityRenderer(ModEntities.GARGANTA.get(), GargantaRenderer::new);
        event.registerEntityRenderer(ModEntities.GETSUGA_TENSHO.get(), GetsugaTenshoRenderer::new);
        event.registerEntityRenderer(ModEntities.ZABIMARU_LASH.get(), ZabimaruLashRenderer::new);
        event.registerEntityRenderer(ModEntities.HEILIG_PFEIL.get(), HeiligPfeilRenderer::new);
        // A Seele Schneider is the same size however it was drawn; a Letzt Pfeil is always huge.
        event.registerEntityRenderer(ModEntities.SEELE_SCHNEIDER.get(),
                context -> new HeiligPfeilRenderer<SeeleSchneiderPfeil>(context, HeiligPfeilRenderer.SEELE_TEXTURE,
                        1.0F, 0.0F));
        event.registerEntityRenderer(ModEntities.LETZT_PFEIL.get(),
                context -> new HeiligPfeilRenderer<LetztPfeil>(context, HeiligPfeilRenderer.LETZT_TEXTURE,
                        2.5F, 0.0F));
        event.registerEntityRenderer(ModEntities.GINTO_CAST.get(), GintoCastRenderer::new);
    }

    /**
     * Adds a Hollow's body (the hole, dark forearms and claws), the chain and mask layer, the sheathed
     * blade, Blut Vene's veins and the Quincy arts (the Sanrei Glove, Letzt Stil's burning lines, and
     * Vollständig's wings and halo) to both player models (wide and slim arms). The body goes under the
     * mask. The sheath takes the look of the blade the player carries, and the arts the look of the
     * Quincy, as their appearance tells.
     */
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new HollowBodyLayer(playerRenderer));
                playerRenderer.addLayer(new SoulLayer(playerRenderer, event.getEntityModels()));
                playerRenderer.addLayer(new SayaLayer<>(playerRenderer,
                        player -> SheathLook.forModel(ClientSouls.appearance(player).sheathModel())));
                playerRenderer.addLayer(new BlutLayer(playerRenderer, event.getEntityModels()));
                playerRenderer.addLayer(new SanreiGloveLayer(playerRenderer, event.getEntityModels()));
                playerRenderer.addLayer(new LetztStilLayer(playerRenderer));
                playerRenderer.addLayer(new VollstandigLayer(playerRenderer, event.getEntityModels()));
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

        ZanpakutoKeys.tick(minecraft);
        QuincyClient.tick(minecraft);
        HollowKeys.tick(minecraft);

        if (minecraft.level != null) {
            SpiritShimmer.tick(minecraft);
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
