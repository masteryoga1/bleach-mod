package io.github.masteryoga1.bleach.quincy;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * The spells a Quincy casts from Gintō, silver tubes of stored reishi, and how many tubes each one
 * breaks. Choosing a spell steps through them in this order. Saved by name, but a cast's look is sent
 * by ordinal, so new spells go last.
 */
public enum GintoSpell implements StringRepresentable {
    /** Binds what it hits where it stands. */
    GRITZ("gritz", 1),
    /** A cloud that stops falls. */
    WOLKE("wolke", 1),
    /** A pillar of light that burns what stands in it. */
    HEIZEN("heizen", 4),
    /** Sets off five Seele Schneider standing in a pentagon. */
    SPRENGER("sprenger", 1);

    public static final Codec<GintoSpell> CODEC = StringRepresentable.fromEnum(GintoSpell::values);
    public static final IntFunction<GintoSpell> BY_ID =
            ByIdMap.continuous(GintoSpell::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

    private final String name;
    private final int tubes;

    GintoSpell(String name, int tubes) {
        this.name = name;
        this.tubes = tubes;
    }

    /** How many filled Gintō one cast breaks. */
    public int tubes() {
        return tubes;
    }

    public Component displayName() {
        return Component.translatable("ginto.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
