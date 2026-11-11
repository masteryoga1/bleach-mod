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
import io.github.masteryoga1.bleach.world.MoonPhases;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;

/**
 * Hueco Mundo's sky: black, with no clouds or stars, and the moon overhead at the phase opposite the Overworld's. The
 * black is the biomes' fog colours, all but black, which the sky's background takes.
 */
public final class HuecoMundoEffects extends DimensionSpecialEffects {
    /** The id Hueco Mundo's dimension type names as its effects. */
    public static final ResourceLocation ID = BleachMod.id("hueco_mundo");
    /** Vanilla's sheet of the moon's eight phases, four across and two down. */
    private static final ResourceLocation MOON =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    /** The moon's half-width and its distance from the eye, as vanilla draws its own. */
    private static final float MOON_HALF = 20.0F, MOON_DISTANCE = 100.0F;

    public HuecoMundoEffects() {
        // No cloud height, so no clouds; no vanilla sky, since renderSky draws the moon alone.
        super(Float.NaN, true, SkyType.NONE, false, false);
    }

    /** Registers Hueco Mundo's sky; on the mod bus, on the client only. */
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new HuecoMundoEffects());
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
     * falls here; nothing when fog, lava, powder snow, blindness or darkness hides the sky, as in vanilla.
     */
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
            Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        FogType fluid = camera.getFluidInCamera();
        if (isFoggy || fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA || skyHidden(camera)) {
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

    /** Whether blindness or darkness hides the sky from the one this camera follows, as vanilla's own sky asks. */
    private static boolean skyHidden(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }
}
