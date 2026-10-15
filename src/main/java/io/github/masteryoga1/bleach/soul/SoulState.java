package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * What a soul currently is. Powers that can stack on top of a state (a Substitute Shinigami is
 * still a living Human, a Visored is a Shinigami with a Hollow inside) are tracked separately as
 * {@link PowerPath} tiers.
 */
public enum SoulState implements StringRepresentable {
    /** Alive, in a physical body. */
    HUMAN("human", false),
    /** A whole soul after death, bound to the living world by its Chain of Fate. */
    PLUS("plus", true),
    SHINIGAMI("shinigami", true),
    HOLLOW("hollow", true),
    /** A Hollow that removed its mask and took on Shinigami-like powers. */
    ARRANCAR("arrancar", true);

    public static final Codec<SoulState> CODEC = StringRepresentable.fromEnum(SoulState::values);

    private final String name;
    private final boolean spirit;

    SoulState(String name, boolean spirit) {
        this.name = name;
        this.spirit = spirit;
    }

    /** Whether this soul has no physical body, so only the spiritually aware can see it. */
    public boolean isSpirit() {
        return spirit;
    }

    public Component displayName() {
        return Component.translatable("soul_state.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
