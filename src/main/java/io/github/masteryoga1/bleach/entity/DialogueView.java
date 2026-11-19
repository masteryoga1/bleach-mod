package io.github.masteryoga1.bleach.entity;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What an NPC says to a visitor, chosen from the visitor's soul. Each view has its own text,
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
    /** A Shinigami NPC away from its work, the wounded one or one on duty, has nothing to ask of this visitor. */
    RESTING,
    /** A Quincy, whose kind the Shinigami distrust. */
    QUINCY,
    /** The Quincy Elder meets one of his blood for the first time, and offers a first lesson. */
    ELDER_GREETING,
    /** The Elder has a lesson to give today. */
    ELDER_LESSON,
    /** The Elder offers a Quincy of the third tier the Ransōtengai trial. */
    ELDER_TEACH,
    /** The trial is open: the visitor must weave the threads. */
    ELDER_TRIAL,
    /** The visitor's power is sealed, and the Elder can bring it back with an arrow. */
    ELDER_RESTORE,
    /** The Elder has nothing more to give this visitor today. */
    ELDER_IDLE,
    /** The visitor has no Quincy blood. */
    ELDER_STRANGER,
    /** A Hollow or an Arrancar, whom the Elder will not speak with. */
    ELDER_HOLLOW,
    /** The young Quincy Hunter challenges a Shinigami to a contest. */
    HUNTER_CHALLENGE,
    /** The Hunter challenges another Quincy to a contest. */
    HUNTER_RIVALRY,
    /** The Hunter's contest with this visitor is running. */
    HUNTER_CONTEST,
    /** The Hunter has had his contest for this visit. */
    HUNTER_RESTING,
    /** Anyone else, the unaware included, whom the Hunter sends home. */
    HUNTER_PLAIN,
    /** A Hollow or an Arrancar, whom the Hunter warns off. */
    HUNTER_HOLLOW,
    /** The instructor offers to cleanse a Hollow player with his blade. */
    HOLLOW_PURIFY,
    /** A gatekeeper lets this soul pass by the gates' rule: his wicket opens to its hand. */
    KEEPER_OPEN,
    /** A gatekeeper bars this soul, the reason in a note, and says that whoever beats him may pass. */
    KEEPER_BARRED,
    /** A gatekeeper greets a soul holding his gate's pass, which it won from him. */
    KEEPER_WON,
    /** A gatekeeper is beaten and fights no one, from his yield until he is whole. */
    KEEPER_BEATEN,
    /** A gatekeeper fights this soul, which may pass, for its blow, until it leaves his ground. */
    KEEPER_FOE;

    private static final IntFunction<DialogueView> BY_ID =
            ByIdMap.continuous(DialogueView::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, DialogueView> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, DialogueView::ordinal);

    /** The last part of the view's translation key. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
