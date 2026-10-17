package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.zanpakuto.JinzenLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The inner worlds behind the Jinzen screen, built from plain rectangles in the spirit's tint so no
 * art is needed: an empty dark for a blade with nothing to show, drifting mist, a city of towers
 * lying on their sides under a pale sky, or a jungle of trunks and swaying vines. Every shape comes
 * from a fixed hash, so a world looks the same each time it opens; only the drift and the sway move.
 */
public final class JinzenBackdrops {
    private static final int WHITE = 0xFFFFFF;

    private JinzenBackdrops() {
    }

    /** Fills the whole screen with this inner world. {@code time} is in ticks, with the partial tick. */
    @SuppressWarnings("deprecation") // drawManaged sends the hundreds of rectangles as one batch, not one each
    static void draw(GuiGraphics graphics, JinzenLook look, int width, int height, float time) {
        int tint = look.tint();
        graphics.drawManaged(() -> {
            switch (look.backdrop()) {
                case MIST -> mist(graphics, tint, width, height, time);
                case SIDEWAYS_CITY -> sidewaysCity(graphics, tint, width, height, time);
                case JUNGLE -> jungle(graphics, tint, width, height, time);
                default -> emptyDark(graphics, tint, width, height, time);
            }
        });
    }

    /** A dark fill fading downwards, with a few faint motes rising through it. */
    private static void emptyDark(GuiGraphics graphics, int tint, int width, int height, float time) {
        graphics.fillGradient(0, 0, width, height, opaque(shade(tint, 0.7F)), opaque(shade(tint, 0.3F)));
        int mote = mix(tint, WHITE, 0.6F);
        for (int i = 0; i < 28; i++) {
            int x = Math.round(hash(i, 1) * width);
            float speed = 0.15F + 0.35F * hash(i, 2);
            int y = height - Math.round(wrap(time * speed + hash(i, 3) * height, height));
            graphics.fill(x, y, x + 1, y + 1, argb(40 + Math.round(60 * hash(i, 4)), mote));
        }
    }

    /** Pale at the top and darker below, with soft bands of mist drifting sideways across it. */
    private static void mist(GuiGraphics graphics, int tint, int width, int height, float time) {
        graphics.fillGradient(0, 0, width, height, opaque(mix(tint, WHITE, 0.35F)), opaque(shade(tint, 0.45F)));
        int band = mix(tint, WHITE, 0.6F);
        for (int i = 0; i < 9; i++) {
            int bandWidth = Math.round(width * (0.4F + 0.5F * hash(i, 11)));
            int bandHeight = 6 + Math.round(18 * hash(i, 12));
            int y = Math.round(height * (0.08F + 0.84F * hash(i, 13)));
            float speed = 0.2F + 0.6F * hash(i, 14);
            int span = width + bandWidth;
            int x = Math.round(wrap(time * speed + hash(i, 15) * span, span)) - bandWidth;
            graphics.fill(x, y, x + bandWidth, y + bandHeight, argb(30 + Math.round(40 * hash(i, 16)), band));
        }
    }

    /**
     * Standing on the side of a tower: the city lies on its side, so the other towers reach in
     * sideways from the edges under a pale sky, the near ones dark with rows of windows, the far ones
     * faded. Clouds drift slowly across.
     */
    private static void sidewaysCity(GuiGraphics graphics, int tint, int width, int height, float time) {
        graphics.fillGradient(0, 0, width, height, opaque(mix(tint, WHITE, 0.8F)), opaque(mix(tint, WHITE, 0.55F)));
        int cloud = mix(tint, WHITE, 0.92F);
        for (int i = 0; i < 6; i++) {
            int cloudWidth = 40 + Math.round(80 * hash(i, 21));
            int y = Math.round(height * hash(i, 22));
            int span = width + cloudWidth;
            int x = width - Math.round(wrap(time * (0.1F + 0.2F * hash(i, 23)) + hash(i, 24) * span, span));
            graphics.fill(x, y, x + cloudWidth, y + 4 + Math.round(6 * hash(i, 25)), argb(110, cloud));
        }
        towers(graphics, tint, width, height, false);
        towers(graphics, tint, width, height, true);
    }

    /** One rank of towers lying on their sides: the near rank from the left edge, the far one from the right. */
    private static void towers(GuiGraphics graphics, int tint, int width, int height, boolean near) {
        int salt = near ? 30 : 40;
        int y = near ? -6 : 4;
        for (int i = 0; y < height; i++) {
            int thick = near ? 18 + Math.round(22 * hash(i, salt)) : 10 + Math.round(14 * hash(i, salt));
            int gap = near ? 10 + Math.round(26 * hash(i, salt + 1)) : 6 + Math.round(16 * hash(i, salt + 1));
            float share = near ? 0.2F + 0.45F * hash(i, salt + 2) : 0.15F + 0.35F * hash(i, salt + 2);
            int reach = Math.round(width * share);
            int colour = near ? shade(tint, 0.5F + 0.3F * hash(i, salt + 3))
                    : mix(tint, WHITE, 0.3F + 0.15F * hash(i, salt + 3));
            int x0 = near ? 0 : width - reach;
            int x1 = near ? reach : width;
            graphics.fill(x0, y, x1, y + thick, opaque(colour));
            if (near) {
                windows(graphics, x0, y, x1, thick, colour);
            }
            y += thick + gap;
        }
    }

    /** Rows of windows along a tower, some lit and most dark. */
    private static void windows(GuiGraphics graphics, int x0, int y, int x1, int thick, int colour) {
        int lit = opaque(mix(colour, WHITE, 0.45F));
        int dark = opaque(shade(colour, 0.7F));
        for (int row = y + 3; row + 3 <= y + thick - 3; row += 6) {
            for (int column = x0 + 4; column + 4 <= x1 - 3; column += 8) {
                boolean on = hash(column * 31 + row, 7) < 0.25F;
                graphics.fill(column, row, column + 4, row + 3, on ? lit : dark);
            }
        }
    }

    /**
     * A jungle: thin far trunks and a few broad near ones, a canopy along the top and vines hanging
     * from it that sway, the tips furthest.
     */
    private static void jungle(GuiGraphics graphics, int tint, int width, int height, float time) {
        graphics.fillGradient(0, 0, width, height, opaque(shade(tint, 0.6F)), opaque(shade(tint, 0.18F)));
        for (int i = 0; i < 10; i++) {
            int x = Math.round(hash(i, 51) * width);
            int trunk = 4 + Math.round(6 * hash(i, 52));
            graphics.fill(x, 0, x + trunk, height, opaque(shade(tint, 0.32F + 0.08F * hash(i, 53))));
        }
        for (int i = 0; i < 4; i++) {
            int x = Math.round((i + hash(i, 54)) * width / 4.0F) - 10;
            int trunk = 16 + Math.round(18 * hash(i, 55));
            graphics.fill(x, 0, x + trunk, height, opaque(shade(tint, 0.16F)));
            int bark = opaque(shade(tint, 0.1F));
            graphics.fill(x + trunk / 3, 0, x + trunk / 3 + 1, height, bark);
            graphics.fill(x + 2 * trunk / 3, 0, x + 2 * trunk / 3 + 1, height, bark);
        }
        for (int x = -6; x < width; x += 10) {
            int i = x + 6;
            int clump = 8 + Math.round(18 * hash(i, 56));
            int dx = Math.round(6 * hash(i, 57));
            graphics.fill(x + dx, 0, x + dx + 14, clump, opaque(shade(tint, 0.35F + 0.3F * hash(i, 58))));
        }
        int vine = opaque(shade(tint, 1.1F));
        int leaf = opaque(shade(tint, 1.35F));
        for (int i = 0; i < 14; i++) {
            float baseX = hash(i, 61) * width;
            float length = height * (0.3F + 0.45F * hash(i, 62));
            float swing = (4.0F + 6.0F * hash(i, 63)) * Mth.sin(time * 0.05F + hash(i, 64) * Mth.TWO_PI);
            for (int s = 0; s < length; s += 4) {
                float depth = s / length;
                int x = Math.round(baseX + swing * depth * depth);
                graphics.fill(x - 1, s, x + 1, s + 4, vine);
                if (s % 12 == 0) {
                    boolean right = (s / 12) % 2 == 0;
                    graphics.fill(right ? x + 1 : x - 4, s + 1, right ? x + 4 : x - 1, s + 3, leaf);
                }
            }
        }
    }

    // ---- Colours, shared with the screen and the overlay ----

    /** An RGB colour at full opacity. */
    static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** An RGB colour with this alpha, from 0 to 255. */
    static int argb(int alpha, int rgb) {
        return Mth.clamp(alpha, 0, 255) << 24 | (rgb & 0xFFFFFF);
    }

    /** An RGB colour made darker (below 1) or lighter (above 1), each channel kept in range. */
    static int shade(int rgb, float factor) {
        int r = Mth.clamp(Math.round((rgb >> 16 & 0xFF) * factor), 0, 255);
        int g = Mth.clamp(Math.round((rgb >> 8 & 0xFF) * factor), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 0xFF) * factor), 0, 255);
        return r << 16 | g << 8 | b;
    }

    /** The RGB colour {@code t} of the way from one colour to another. */
    static int mix(int from, int to, float t) {
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    /** A fixed pseudo-random number from 0 (inclusive) to 1 (exclusive) for this index and salt. */
    static float hash(int index, int salt) {
        int h = index * 0x27D4EB2D + salt * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 8) / (float) (1 << 24);
    }

    /** {@code value} wrapped into 0 (inclusive) to {@code span} (exclusive). */
    private static float wrap(float value, float span) {
        return (value % span + span) % span;
    }
}
