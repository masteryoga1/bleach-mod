package io.github.masteryoga1.bleach.zanpakuto;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The kinds of zanpakutō a soul can grow towards while it bonds with its Asauchi. How the soul fights
 * builds a hidden leaning towards each, and the strongest leaning decides which spirit it meets in
 * Jinzen. Saved by name, so the order here does not matter to old saves.
 */
public enum ZanpakutoType implements StringRepresentable {
    /** A blade whose release makes it hit harder, further or in more places, as Zabimaru does. */
    POWER("power"),
    /** A blade whose release works through the elements or kidō-like arts. */
    KIDO("kido"),
    /** A blade that, once first released, stays released for good and never seals, as Zangetsu does. */
    CONSTANT_RELEASE("constant_release");

    public static final Codec<ZanpakutoType> CODEC = StringRepresentable.fromEnum(ZanpakutoType::values);

    private final String name;

    ZanpakutoType(String name) {
        this.name = name;
    }

    public Component displayName() {
        return Component.translatable("zanpakuto_type.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
