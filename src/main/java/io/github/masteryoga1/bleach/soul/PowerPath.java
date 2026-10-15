package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The kinds of power a soul can hold at the same time, each with its own tier. Tier 0 means the
 * power has not awakened.
 */
public enum PowerPath implements StringRepresentable {
    SHINIGAMI("shinigami"),
    HOLLOW("hollow"),
    QUINCY("quincy"),
    FULLBRING("fullbring");

    public static final Codec<PowerPath> CODEC = StringRepresentable.fromEnum(PowerPath::values);

    private final String name;

    PowerPath(String name) {
        this.name = name;
    }

    public Component displayName() {
        return Component.translatable("power_path.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
