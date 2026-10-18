package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.soul.QuincyArts;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyForms;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyProgress;
import io.github.masteryoga1.bleach.soul.SanreiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * The reiryoku bar: a thin vertical gauge just right of the hotbar, moved further out when the
 * offhand slot or the hotbar attack indicator is on that side. It gets a white outline while
 * reiatsu is released, and a wider one in the blade's colour while the player holds their own
 * released zanpakutō, with a small bar above it that fills while a technique gathers. A Plus gets a
 * second gauge beside it for the Chain of Fate, which turns red once Encroachment sets in. A
 * Quincy gets a reishi gauge beside it instead, outlined blue under Blut Vene and red under Blut
 * Arterie, with Hollow poison filling it violet from the top (flickering once the poison brings
 * pangs) and a bar above it that fills as the Cross is drawn, over the form's draw time. That bar
 * turns orange when a Letzt Stil draw is charged for a Letzt Pfeil, and pale gold while Licht Regen
 * gathers. A thin bar beside the gauge follows the Quincy arts: the day's training in the Sanrei
 * Glove (gold once mastered), the time Letzt Stil has left, the wait while the power is sealed
 * (white once the rite is open), and Vollständig (dimmed once the halo breaks) or its cooldown.
 * While the power is sealed, the reishi gauge stands empty in a grey outline, and holding the Cross
 * through the rite fills the bar above it. Quincy blood not yet awakened shows only the draw bar, in
 * grey. Placeholder look until real art exists.
 */
public final class ReiryokuHud {
    public static final ResourceLocation ID = BleachMod.id("reiryoku");

    private static final int WIDTH = 6;
    private static final int HEIGHT = 22;
    private static final int BACKGROUND = 0x90000000;
    private static final int FILL = 0xFF8FD8FF;
    private static final int FILL_LOW = 0xFF4F7FA8;
    private static final int RELEASED_OUTLINE = 0xFFFFFFFF;
    private static final int CHAIN_FILL = 0xFFC8C8D0;
    private static final int CHAIN_FILL_ENCROACHING = 0xFFB03030;
    /** The Shikai outline is this thick, just outside the white one. */
    private static final int SHIKAI_OUTLINE = 2;
    private static final int GATHER_HEIGHT = 12;
    /** The gather bar sits this far above the gauge, clear of both outlines. */
    private static final int GATHER_GAP = 5;
    private static final int GATHER_FULL = 0xFFFFFFFF;
    private static final int REISHI_FILL = 0xFFBFE6FF;
    private static final int REISHI_FILL_LOW = 0xFF6F9CC0;
    private static final int POISON_FILL = 0xC08040C0;
    private static final int VENE_OUTLINE = 0xFF3FA8FF;
    private static final int ARTERIE_OUTLINE = 0xFFE0403A;
    /** The Blut outline while a press is still waiting on the server. */
    private static final int BLUT_SETTLING_OUTLINE = 0xFF808080;
    private static final int LATENT_DRAW = 0xFF8090A0;
    /** The draw bar once a Letzt Stil draw is charged, while Licht Regen gathers, and through the rite. */
    private static final int LETZT_CHARGED_DRAW = 0xFFFFB060;
    private static final int LICHT_GATHER_DRAW = 0xFFFFE8A0;
    private static final int RITE_DRAW = 0xFFE8F4FF;
    private static final int SEALED_OUTLINE = 0xFF7A7A7A;
    /** The art bar, just right of the reishi gauge's outline. */
    private static final int ART_WIDTH = 2;
    private static final int ART_GAP = 2;
    private static final int ART_GLOVE = 0xFFFFFFFF;
    private static final int ART_GLOVE_MASTERED = 0xFFE0B040;
    private static final int ART_LETZT = 0xFFFFC890;
    private static final int ART_SEALED = 0xFF8A8A8A;
    private static final int ART_RITE_OPEN = 0xFFFFFFFF;
    private static final int ART_VOLL = 0xFFFFD86A;
    private static final int ART_VOLL_HALO_BROKEN = 0xFFA89A6A;
    private static final int ART_VOLL_COOLDOWN = 0xFF9A9A9A;
    /** Past the pang threshold the poison overlay is shown for this many ticks, then hidden as long. */
    private static final int POISON_FLICKER_TICKS = 10;

    private ReiryokuHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        SoulData data = player.getData(SoulAttachments.SOUL);
        if (!data.isInitialized() || !SoulLogic.isAware(data)) {
            return;
        }

        // Vanilla draws the offhand slot on the right for left-handed players, and otherwise puts the
        // hotbar attack indicator (when that option is on) on the right.
        int offset = 6;
        if (player.getMainArm() == HumanoidArm.LEFT) {
            offset += 30;
        } else if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            offset += 20;
        }
        int x = graphics.guiWidth() / 2 + 91 + offset;
        int y = graphics.guiHeight() - HEIGHT;

        float fraction = data.reiryokuFraction();
        drawGauge(graphics, x, y, fraction, fraction < 0.2F ? FILL_LOW : FILL);
        if (data.isReiatsuReleased()) {
            graphics.renderOutline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, RELEASED_OUTLINE);
        }
        ItemStack held = player.getMainHandItem();
        ZanpakutoBlade blade = ZanpakutoItem.blade(held);
        if (blade.released() && ZanpakutoLogic.isOwnBlade(held, player)) {
            int colour = 0xFF000000 | blade.colour();
            for (int ring = 2; ring < 2 + SHIKAI_OUTLINE; ring++) {
                graphics.renderOutline(x - ring, y - ring, WIDTH + 2 * ring, HEIGHT + 2 * ring, colour);
            }
            float gather = ZanpakutoKeys.gatherProgress(deltaTracker.getGameTimeDeltaPartialTick(false));
            if (gather > 0.0F) {
                drawGather(graphics, x, y - GATHER_GAP - GATHER_HEIGHT, gather, gather >= 1.0F ? GATHER_FULL : colour);
            }
        }

        boolean chainShown = data.state() == SoulState.PLUS && data.chainMaxTicks() > 0;
        if (chainShown) {
            drawGauge(graphics, x + WIDTH + 2, y, data.chainFraction(),
                    SoulLogic.isEncroaching(data) ? CHAIN_FILL_ENCROACHING : CHAIN_FILL);
        }

        // The reishi gauge goes beyond the chain one, should a soul ever have both.
        int rx = chainShown ? x + 2 * WIDTH + 8 : x + WIDTH + 6;
        if (QuincyLogic.canUsePowers(data)) {
            renderReishi(graphics, player, data, rx, y);
        } else if (QuincyArtsLogic.isSealed(data)) {
            renderSealed(graphics, player, data, rx, y);
        } else if (QuincyLogic.isLatent(data)) {
            float draw = QuincyClient.drawPower(player);
            if (draw > 0.0F) {
                drawGather(graphics, rx, y - GATHER_GAP - GATHER_HEIGHT, draw, LATENT_DRAW);
            }
        }
    }

    /**
     * A Quincy's reishi: the gauge, the Hollow poison over it, the Blut outline (grey while a press
     * waits on the server), the art bar beside it, and the draw bar above it while the Cross is drawn.
     */
    private static void renderReishi(GuiGraphics graphics, LocalPlayer player, SoulData data, int x, int y) {
        float max = QuincyLogic.maxReishi(data);
        float fraction = max > 0.0F ? Mth.clamp(QuincyLogic.reishi(data) / max, 0.0F, 1.0F) : 0.0F;
        drawGauge(graphics, x, y, fraction, fraction < 0.2F ? REISHI_FILL_LOW : REISHI_FILL);

        long gameTime = player.level().getGameTime();
        float poison = data.quincy().poison();
        boolean flickerOff = poison >= QuincyLogic.POISON_PANG && (gameTime / POISON_FLICKER_TICKS) % 2 != 0;
        int poisoned = Mth.ceil((HEIGHT - 2) * Mth.clamp(poison / QuincyProgress.MAX_POISON, 0.0F, 1.0F));
        if (poisoned > 0 && !flickerOff) {
            graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 1 + poisoned, POISON_FILL);
        }

        BlutMode blut = QuincyLogic.activeBlut(data);
        if (QuincyClient.blutSettling(gameTime)) {
            graphics.renderOutline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, BLUT_SETTLING_OUTLINE);
        } else if (blut != BlutMode.OFF) {
            graphics.renderOutline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2,
                    blut == BlutMode.VENE ? VENE_OUTLINE : ARTERIE_OUTLINE);
        }

        drawArtBar(graphics, data, x + WIDTH + ART_GAP, y, gameTime);

        int drawY = y - GATHER_GAP - GATHER_HEIGHT;
        if (QuincyClient.lichtGathering(player)) {
            drawGather(graphics, x, drawY, QuincyClient.lichtGatherProgress(player), LICHT_GATHER_DRAW);
        } else if (QuincyClient.letztChargeReady(player)) {
            drawGather(graphics, x, drawY, 1.0F, LETZT_CHARGED_DRAW);
        } else {
            float draw = QuincyClient.drawPower(player);
            if (draw > 0.0F) {
                drawGather(graphics, x, drawY, draw, draw >= 1.0F ? GATHER_FULL : REISHI_FILL);
            }
        }
    }

    /**
     * A sealed Quincy: the reishi gauge stands empty in a grey outline, the art bar beside it counts
     * down the wait, and the bar above it fills while the Cross is held through the rite.
     */
    private static void renderSealed(GuiGraphics graphics, LocalPlayer player, SoulData data, int x, int y) {
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        graphics.renderOutline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, SEALED_OUTLINE);
        long gameTime = player.level().getGameTime();
        drawArtBar(graphics, data, x + WIDTH + ART_GAP, y, gameTime);
        int held = QuincyClient.useTicks(player);
        if (held > 0) {
            int riteTicks = Math.max(1, BleachConfig.get(BleachConfig.RESTORE_RITE_TICKS));
            drawGather(graphics, x, y - GATHER_GAP - GATHER_HEIGHT, Math.min(1.0F, held / (float) riteTicks), RITE_DRAW);
        }
    }

    /**
     * The thin bar beside the reishi gauge for the Quincy arts, filling upwards, in the one state that
     * applies: Vollständig or its cooldown, Letzt Stil's time left, the wait while sealed, or the
     * Sanrei Glove's day. Nothing for a Quincy who has none of these.
     */
    private static void drawArtBar(GuiGraphics graphics, SoulData data, int x, int y, long now) {
        QuincyArts arts = QuincyArtsLogic.arts(data);
        QuincyForms forms = arts.forms();
        SanreiProgress glove = arts.glove();
        float fraction;
        int colour;
        if (QuincyArtsLogic.inVollstandig(data)) {
            fraction = 1.0F;
            colour = forms.haloBroken() ? ART_VOLL_HALO_BROKEN : ART_VOLL;
        } else if (QuincyArtsLogic.isRestored(data) && now < forms.vollReadyAt()) {
            fraction = 1.0F - share(forms.vollReadyAt() - now, BleachConfig.get(BleachConfig.VOLL_COOLDOWN_TICKS));
            colour = ART_VOLL_COOLDOWN;
        } else if (QuincyArtsLogic.inLetzt(data)) {
            fraction = share(arts.letztEndsAt() - now, BleachConfig.get(BleachConfig.LETZT_DURATION_TICKS));
            colour = ART_LETZT;
        } else if (QuincyArtsLogic.isSealed(data)) {
            fraction = share(now - arts.stageSince(), QuincyArtsLogic.sealedTicksNeeded(false));
            colour = QuincyArtsLogic.riteOpen(data, now) ? ART_RITE_OPEN : ART_SEALED;
        } else if (QuincyArtsLogic.gloveMastered(data)) {
            fraction = 1.0F;
            colour = ART_GLOVE_MASTERED;
        } else if (QuincyArtsLogic.handicapped(data)) {
            fraction = share(glove.today(), BleachConfig.get(BleachConfig.SANREI_POINTS_PER_DAY));
            colour = ART_GLOVE;
        } else {
            return;
        }
        graphics.fill(x, y, x + ART_WIDTH, y + HEIGHT, BACKGROUND);
        int filled = Mth.ceil((HEIGHT - 2) * fraction);
        if (filled > 0) {
            int bottom = y + HEIGHT - 1;
            graphics.fill(x, bottom - filled, x + ART_WIDTH, bottom, colour);
        }
    }

    /** {@code part} of {@code whole}, from 0 to 1; all of it when the whole is nothing. */
    private static float share(long part, long whole) {
        return whole <= 0L ? 1.0F : Mth.clamp((float) part / (float) whole, 0.0F, 1.0F);
    }

    /** The gather bar: the same width as the gauge, filling upwards, and white once full. */
    private static void drawGather(GuiGraphics graphics, int x, int y, float fraction, int color) {
        graphics.fill(x, y, x + WIDTH, y + GATHER_HEIGHT, BACKGROUND);
        int inner = GATHER_HEIGHT - 2;
        int filled = Mth.ceil(inner * fraction);
        if (filled > 0) {
            int bottom = y + GATHER_HEIGHT - 1;
            graphics.fill(x + 1, bottom - filled, x + WIDTH - 1, bottom, color);
        }
    }

    private static void drawGauge(GuiGraphics graphics, int x, int y, float fraction, int color) {
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        int inner = HEIGHT - 2;
        int filled = Mth.ceil(inner * fraction);
        if (filled > 0) {
            int bottom = y + HEIGHT - 1;
            graphics.fill(x + 1, bottom - filled, x + WIDTH - 1, bottom, color);
        }
    }
}
