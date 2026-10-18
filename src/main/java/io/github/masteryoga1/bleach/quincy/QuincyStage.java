package io.github.masteryoga1.bleach.quincy;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How far a Quincy's power has come through the burning out and the return: untouched, burning in
 * Letzt Stil, sealed after it, or restored as Ginrei Kojaku. A soul goes through them once, in this
 * order. Saved by name, so new stages go last.
 */
public enum QuincyStage implements StringRepresentable {
    /** The power as it grew: the Heilig Bogen, the Sanrei Glove, and Letzt Stil still to come. */
    UNTOUCHED("untouched"),
    /** The glove torn off: a short while of overwhelming power before it burns out. */
    LETZT_STIL("letzt_stil"),
    /** The power burnt out. Only Gintō filled before still work, until the power returns. */
    SEALED("sealed"),
    /** The power won back, changed: Ginrei Kojaku, Seele Schneider and, later, Vollständig. */
    RESTORED("restored");

    public static final Codec<QuincyStage> CODEC = StringRepresentable.fromEnum(QuincyStage::values);

    private final String name;

    QuincyStage(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
