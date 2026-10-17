package io.github.masteryoga1.bleach.entity;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What a Shinigami NPC says to a visitor, chosen from the visitor's soul. Each view has its own text,
 * {@code screen.bleach.dialogue.<npc>.<view>}, and the actions in {@link NpcAction} it allows.
 * Sent by ordinal, so new views go last.
 */
public enum DialogueView {
    /** A living Human with no power of their own. */
    LIVING,
    SUBSTITUTE,
    PLUS,
    /** A Rukongai soul not yet at the Academy. */
    SOUL,
    STUDENT,
    SHINIGAMI,
    /** A Hollow or an Arrancar. */
    HOLLOW,
    /** The wounded Shinigami asks a Human who can see her to take her power. */
    OFFER_POWER,
    /** The wounded Shinigami has nothing to ask of this visitor. */
    RESTING,
    /** A Quincy, whose kind the Shinigami distrust. */
    QUINCY;

    private static final IntFunction<DialogueView> BY_ID =
            ByIdMap.continuous(DialogueView::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, DialogueView> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, DialogueView::ordinal);

    /** The last part of the view's translation key. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
