package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.Menos;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * A Gillian or Adjuchas player, drawn in the Menos mobs' own models and textures in place of the player
 * model: the vanilla player renderer is stopped before it draws ({@link #onRenderPlayer}) and this draws
 * instead, with the mask as a layer of its own, cracked after a blow. The player's size has already
 * scaled the model by the time {@link #scale} runs, so each form only fits its model to the player's box,
 * with the mask on the eyes: a Gillian's at nine tenths of its height, an Adjuchas's out in front of its
 * eyes, where blows to the mask are looked for, with the beast's hindquarters trailing behind the box.
 *
 * <p>Held items, armour (which Hollows cannot wear), the cape, the Hollow mask and every other layer of the
 * player model are not drawn on these bodies. A Vasto Lorde keeps the player model, with bone over it
 * (see {@link HollowBodyLayer}). The unaware never reach this: spirits are culled before any renderer
 * runs (see {@code mixin.client}). Both renderers are built again with every resource reload.
 */
public final class MenosPlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>> {
    /** Within this distance squared, as for any player, the score of a below-name objective shows under the name. */
    private static final double SCORE_LINE_DISTANCE_SQR = 100.0;
    /** How far the name is lifted above a score line: one line of text at the name tag's scale. */
    private static final float SCORE_LINE_HEIGHT = 9.0F * 1.15F * 0.025F;

    /**
     * How much bigger than its own size each form's model is drawn, and how far an Adjuchas is shifted back,
     * in blocks for each unit of the player's size. The Gillian model stands 2.5 blocks tall with its mask
     * at nine tenths of that, so at 0.72 the mask sits on a player's eyes (1.62 of 1.8); the Adjuchas mask
     * stands 1 block up, so at 1.62 it does too.
     */
    public static final float GILLIAN_FIT = 0.72F, ADJUCHAS_FIT = 1.62F, ADJUCHAS_BACK_SHIFT = 0.67F;

    @Nullable
    private static MenosPlayerRenderer gillian;
    @Nullable
    private static MenosPlayerRenderer adjuchas;

    private final ResourceLocation texture;
    private final float fit;
    private final float backShift;

    private MenosPlayerRenderer(EntityRendererProvider.Context context, EntityModel<AbstractClientPlayer> model,
                                HollowKind kind, float fit, float backShift) {
        super(context, model, 0.5F);
        this.texture = kind.texture();
        this.fit = fit;
        this.backShift = backShift;
        addLayer(new MaskLayer(this, kind));
    }

    /** Builds both bodies from this reload's models. Called from {@code EntityRenderersEvent.AddLayers}. */
    public static void build(EntityRendererProvider.Context context) {
        gillian = new MenosPlayerRenderer(context,
                new GillianModel<AbstractClientPlayer>(context.bakeLayer(BleachModelLayers.GILLIAN)),
                HollowKind.GILLIAN, GILLIAN_FIT, 0.0F);
        adjuchas = new MenosPlayerRenderer(context,
                new AdjuchasModel<AbstractClientPlayer>(context.bakeLayer(BleachModelLayers.ADJUCHAS)),
                HollowKind.ADJUCHAS, ADJUCHAS_FIT, ADJUCHAS_BACK_SHIFT);
    }

    /**
     * Draws a Gillian or Adjuchas player in its Menos body instead of the player model, which is then not
     * drawn at all. The game still draws the shadow, and the name tag is drawn as for any player, with the
     * score of a below-name objective under it (see {@link #renderNameTag}). A spectator keeps
     * the floating head vanilla gives it.
     */
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || player.isSpectator()) {
            return;
        }
        int look = ClientSouls.appearance(player).hollowLook();
        MenosPlayerRenderer renderer = (look & HollowPlayerLogic.LOOK_GILLIAN) != 0 ? gillian
                : (look & HollowPlayerLogic.LOOK_ADJUCHAS) != 0 ? adjuchas : null;
        if (renderer == null) {
            return;
        }
        event.setCanceled(true);
        float partialTick = event.getPartialTick();
        renderer.render(player, Mth.lerp(partialTick, player.yRotO, player.getYRot()), partialTick,
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    /**
     * Whether a Cero is gathering at this creature's mouth, so its model tips the face up: a Menos mob's
     * synced flag; for this client's own player, its own gathering Cero (see {@link HollowKeys#ceroProgress});
     * and for another player, what the server says of its look.
     */
    public static boolean chargingCero(LivingEntity entity) {
        if (entity instanceof Menos menos) {
            return menos.isChargingCero();
        }
        if (entity == Minecraft.getInstance().player) {
            return HollowKeys.ceroProgress(0.0F) > 0.0F;
        }
        return (ClientSouls.appearance(entity).hollowLook() & HollowPlayerLogic.LOOK_CERO_CHARGING) != 0;
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        return texture;
    }

    /**
     * The name tag as the player renderer draws it, which this one stands in for: with the score of a
     * below-name objective on a line under the name for anyone close by.
     */
    @Override
    protected void renderNameTag(AbstractClientPlayer player, Component name, PoseStack poseStack,
                                 MultiBufferSource buffer, int packedLight, float partialTick) {
        poseStack.pushPose();
        if (entityRenderDispatcher.distanceToSqr(player) < SCORE_LINE_DISTANCE_SQR) {
            Scoreboard scoreboard = player.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
            if (objective != null) {
                Component score = ReadOnlyScoreInfo.safeFormatValue(scoreboard.getPlayerScoreInfo(player, objective),
                        objective.numberFormatOrDefault(StyledFormat.NO_STYLE));
                super.renderNameTag(player, Component.empty().append(score).append(CommonComponents.SPACE)
                        .append(objective.getDisplayName()), poseStack, buffer, packedLight, partialTick);
                poseStack.translate(0.0F, SCORE_LINE_HEIGHT, 0.0F);
            }
        }
        super.renderNameTag(player, name, poseStack, buffer, packedLight, partialTick);
        poseStack.popPose();
    }

    /**
     * The model fitted to the player's box, which the player's size has already scaled. Model space is
     * upside down here, so +z is towards the back.
     */
    @Override
    protected void scale(AbstractClientPlayer player, PoseStack poseStack, float partialTick) {
        poseStack.scale(fit, fit, fit);
        poseStack.translate(0.0F, 0.0F, backShift / fit);
    }

    /** The form's bone-white mask over the body, swapped for the cracked one after a blow to it. */
    private static final class MaskLayer extends RenderLayer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>> {
        private final ResourceLocation mask;
        private final ResourceLocation cracked;

        MaskLayer(RenderLayerParent<AbstractClientPlayer, EntityModel<AbstractClientPlayer>> parent, HollowKind kind) {
            super(parent);
            this.mask = kind.maskTexture(false);
            this.cracked = kind.maskTexture(true);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (player.isInvisible()) {
                return;
            }
            boolean broken = (ClientSouls.appearance(player).hollowLook() & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0;
            renderColoredCutoutModel(getParentModel(), broken ? cracked : mask, poseStack, buffer, packedLight, player, -1);
        }
    }
}
