package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * What a soul currently is. Powers that can stack on top of a state are tracked separately as
 * {@link PowerPath} tiers: a Substitute Shinigami is a living Human with a Shinigami tier, who uses
 * that power in the flesh until soul form exists, and a Visored is a Shinigami with a Hollow inside.
 * A Plus sent on by Konsō becomes a {@link #RUKONGAI} soul.
 *
 * <p>New states go last: {@link SoulAppearance} sends a state by its position.
 */
public enum SoulState implements StringRepresentable {
    /** Alive, in a physical body. */
    HUMAN("human", false),
    /** A whole soul after death, bound to the living world by its Chain of Fate. */
    PLUS("plus", true),
    /** A soul with a Shinigami's power, whether trained at the Academy or a Substitute who died. */
    SHINIGAMI("shinigami", true),
    HOLLOW("hollow", true),
    /** A Hollow that removed its mask and took on Shinigami-like powers. */
    ARRANCAR("arrancar", true),
    /**
     * A soul that passed on through Konsō, free of its chain, and sent to a district of the Rukongai in Soul
     * Society, or wherever the server sends souls. Those with spiritual power can enrol at the Academy.
     */
    RUKONGAI("rukongai", true);

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
