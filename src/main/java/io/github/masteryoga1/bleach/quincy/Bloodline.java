package io.github.masteryoga1.bleach.quincy;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Whether Quincy blood runs in a soul, and how pure it is. Quincy are born, not made: the blood is
 * settled once, and only a soul that has it can awaken the power through a Quincy Cross. Saved by
 * name, so the order here does not matter to old saves.
 */
public enum Bloodline implements StringRepresentable {
    /** No Quincy blood: an ordinary Human. */
    NONE("none"),
    /** Mixed blood, from one Quincy parent. Blut comes a tier later. */
    GEMISCHT("gemischt"),
    /** Pure blood, Quincy on both sides. */
    ECHT("echt");

    public static final Codec<Bloodline> CODEC = StringRepresentable.fromEnum(Bloodline::values);

    private final String name;

    Bloodline(String name) {
        this.name = name;
    }

    public Component displayName() {
        return Component.translatable("bloodline.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
