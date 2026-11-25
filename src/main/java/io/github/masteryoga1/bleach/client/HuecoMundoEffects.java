package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import io.github.masteryoga1.bleach.world.MoonPhases;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hueco Mundo's sky: black, with no clouds or stars, and the moon overhead at the phase opposite the Overworld's, the black
 * being the biomes' fog colours, which the sky's background takes. Under Las Noches' dome the fog and the background turn
 * over a second to the lining's blue and the light to a bright, white one, so the lit lining reads as the sky.
 */
public final class HuecoMundoEffects extends DimensionSpecialEffects {
    /** The id Hueco Mundo's dimension type names as its effects. */
    public static final ResourceLocation ID = BleachMod.id("hueco_mundo");
    /** Vanilla's sheet of the moon's eight phases, four across and two down. */
    private static final ResourceLocation MOON =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    /** The moon's half-width and its distance from the eye, as vanilla draws its own. */
    private static final float MOON_HALF = 20.0F, MOON_DISTANCE = 100.0F;
    /** The dome's sky, the lining texture's mean colour (128, 176, 232), which the fog and the background take under it. */
    private static final float SKY_RED = 128 / 255.0F, SKY_GREEN = 176 / 255.0F, SKY_BLUE = 232 / 255.0F;
    /** How far the tint moves in a client tick: across in a second. */
    private static final float FADE_STEP = 1.0F / 20.0F;
    /** The grey vanilla eases every cell of the lightmap toward, a twenty-fifth of the way. */
    private static final Vector3f GREY = new Vector3f(0.75F);
    /** How far the dome's sky had come at the last client tick and at the one before: 0 outside it, 1 under it. */
    private static float tintNow, tintBefore;
    /** The world the tint was last worked out in, on the client thread: in a new one it is set at once. */
    @Nullable
    private static ClientLevel tintLevel;
    /** The body the camera followed when the tint was last worked out: behind a new one it is set at once. */
    @Nullable
    private static Entity tintBody;

    public HuecoMundoEffects() {
        // No cloud height, so no clouds; no vanilla sky, since renderSky draws the moon alone.
        super(Float.NaN, true, SkyType.NONE, false, false);
    }

    /** Registers Hueco Mundo's sky; on the mod bus, on the client only. */
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new HuecoMundoEffects());
    }

    /** Registers the tint's tick and the fog's colour under the dome; on the game bus, on the client only. */
    public static void registerTint(IEventBus bus) {
        bus.addListener(HuecoMundoEffects::onClientTick);
        bus.addListener(HuecoMundoEffects::onComputeFogColor);
    }

    /** The biome's fog colour as it is, at any hour, as in the Nether. */
    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    /**
     * Draws the moon where and as vanilla draws its own, at the opposite phase and never veiled by rain, which never
     * falls here; nothing when fog, lava, powder snow, blindness or darkness hides the sky, as in vanilla, nor once the
     * dome's sky has more than half come.
     */
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
            Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        FogType fluid = camera.getFluidInCamera();
        if (isFoggy || fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA || skyHidden(camera)
                || tint(partialTick) > 0.5F) {
            return true;
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(modelViewMatrix);
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360.0F));
        Matrix4f matrix = pose.last().pose();
        int phase = MoonPhases.opposite(level.getMoonPhase());
        float u0 = phase % 4 / 4.0F, v0 = phase / 4 % 2 / 2.0F, u1 = u0 + 0.25F, v1 = v0 + 0.5F;
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);
        BufferBuilder moon = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        moon.addVertex(matrix, -MOON_HALF, -MOON_DISTANCE, MOON_HALF).setUv(u1, v1);
        moon.addVertex(matrix, MOON_HALF, -MOON_DISTANCE, MOON_HALF).setUv(u0, v1);
        moon.addVertex(matrix, MOON_HALF, -MOON_DISTANCE, -MOON_HALF).setUv(u0, v0);
        moon.addVertex(matrix, -MOON_HALF, -MOON_DISTANCE, -MOON_HALF).setUv(u1, v0);
        BufferUploader.drawWithShader(moon.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        return true;
    }

    /**
     * Under the dome a cell the sky's light does not reach takes a day's light, white and steady, as if the sky stood at
     * noon: as far as the tint has come and the cell lacks sky light, so the dunes seen through a door keep their night.
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
            float skyLight, int pixelX, int pixelY, Vector3f colors) {
        float share = tint(partialTicks) * (15 - pixelY) / 15.0F;
        if (share <= 0.0F) {
            return;
        }
        // Vanilla's sum at noon, with the block light white and still: night vision, darkness and the gamma follow.
        Vector3f day = new Vector3f(LightTexture.getBrightness(level.dimensionType(), pixelX) * 1.5F
                + LightTexture.getBrightness(level.dimensionType(), pixelY)).lerp(GREY, 0.04F);
        float darken = Minecraft.getInstance().gameRenderer.getDarkenWorldAmount(partialTicks);
        if (darken > 0.0F) {
            day.lerp(new Vector3f(day).mul(0.7F, 0.6F, 0.6F), darken);
        }
        colors.lerp(day, share);
    }

    /**
     * Each client tick the tint moves a twentieth of the way toward whether the camera is under the dome's sky; in a new
     * world or behind a new body it is set at once, so normally a soul that wakes there sees no second of night.
     */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity body = minecraft.getCameraEntity();
        float target = level != null && body != null && level.dimension() == BleachWorlds.HUECO_MUNDO
                && cameraUnderSky(minecraft, body) ? 1.0F : 0.0F;
        if (level != tintLevel || body != tintBody) {
            tintLevel = level;
            tintBody = body;
            tintNow = tintBefore = target;
            return;
        }
        tintBefore = tintNow;
        tintNow = target > tintNow ? Math.min(target, tintNow + FADE_STEP) : Math.max(target, tintNow - FADE_STEP);
    }

    /** Whether the camera, or the body's eyes until the camera has followed it, is under the dome's sky. */
    private static boolean cameraUnderSky(Minecraft minecraft, Entity body) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        BlockPos at = camera.isInitialized() && camera.getEntity() == body ? camera.getBlockPosition()
                : BlockPos.containing(body.getEyePosition());
        return HuecoMundoPlan.underSky(at.getX(), at.getY(), at.getZ());
    }

    /** How far the dome's sky has come at this point between client ticks: 0 in any world but the one it was worked for. */
    static float tint(float partialTick) {
        return tintLevel == null || Minecraft.getInstance().level != tintLevel ? 0.0F
                : Mth.lerp(partialTick, tintBefore, tintNow);
    }

    /**
     * Under the dome the fog and the background, after every vanilla change to them, turn to the sky's blue as far as the
     * tint has come, but not in water, lava or powder snow, or under blindness or darkness; the fog's reach stays vanilla's.
     */
    private static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        float weight = tint((float) event.getPartialTick());
        Camera camera = event.getCamera();
        if (weight <= 0.0F || camera.getFluidInCamera() != FogType.NONE || skyHidden(camera)) {
            return;
        }
        event.setRed(Mth.lerp(weight, event.getRed(), SKY_RED));
        event.setGreen(Mth.lerp(weight, event.getGreen(), SKY_GREEN));
        event.setBlue(Mth.lerp(weight, event.getBlue(), SKY_BLUE));
    }

    /** Whether blindness or darkness hides the sky from the one this camera follows, as vanilla's own sky asks. */
    private static boolean skyHidden(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }
}
