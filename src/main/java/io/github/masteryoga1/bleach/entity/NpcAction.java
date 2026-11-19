package io.github.masteryoga1.bleach.entity;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * What a visitor can ask an NPC for, and in which {@link DialogueView}s. The server checks
 * the view again before doing anything, so a client cannot ask for what it was never offered.
 * Sent by ordinal, so new actions go last.
 */
public enum NpcAction {
    KONSO(DialogueView.PLUS),
    ENROLL(DialogueView.SOUL),
    GRADUATE(DialogueView.STUDENT),
    ASAUCHI(DialogueView.SUBSTITUTE, DialogueView.STUDENT, DialogueView.STUDENT_TRIAL, DialogueView.SHINIGAMI),
    ACCEPT_POWER(DialogueView.OFFER_POWER),
    /** A lesson once a day: the Quincy Elder's, or the Academy instructor's Kidō lesson for a student. */
    LESSON(DialogueView.ELDER_GREETING, DialogueView.ELDER_LESSON, DialogueView.STUDENT, DialogueView.STUDENT_TRIAL),
    /** The Elder's Ransōtengai trial. */
    RANSOTENGAI(DialogueView.ELDER_TEACH),
    /** The Elder's arrow beside the heart, which brings a sealed power back. */
    RESTORE(DialogueView.ELDER_RESTORE),
    /** The Quincy Hunter's contest: whoever destroys more Hollows. */
    CONTEST(DialogueView.HUNTER_CHALLENGE, DialogueView.HUNTER_RIVALRY),
    /** The instructor's blade through a Hollow player's mask. */
    PURIFY(DialogueView.HOLLOW_PURIFY),
    /** The instructor's Jigokuchō, for a Shinigami or a Substitute that has lost its own. */
    BUTTERFLY(DialogueView.SUBSTITUTE, DialogueView.SHINIGAMI),
    /** The instructor's field trial: his Senkaimon from Soul Society, or in the Human World a hunt where one stands. */
    FIELD_TRIAL(DialogueView.STUDENT_TRIAL),
    /** Kisuke Urahara's wares on the merchant screen, in the views of the souls he may sell something to. */
    TRADE(DialogueView.SHOP_AWARE, DialogueView.SHOP_PLUS, DialogueView.SHOP_SOUL, DialogueView.SHOP_STUDENT,
            DialogueView.SHOP_SHINIGAMI, DialogueView.SHOP_QUINCY, DialogueView.SHOP_VISORED),
    /**
     * Kisuke Urahara's Senkaimon in the room under his shop, for a soul whose home it leads to or that has a gate of its
     * own back: a Rukongai soul, a student, a Shinigami or Substitute, a Visored.
     */
    SENKAIMON(DialogueView.SHOP_SOUL, DialogueView.SHOP_STUDENT, DialogueView.SHOP_SHINIGAMI, DialogueView.SHOP_VISORED),
    /** His Garganta between the Keikaigi's posts to Hueco Mundo, whose far end brings souls home: for all he sells to. */
    GARGANTA(DialogueView.SHOP_AWARE, DialogueView.SHOP_PLUS, DialogueView.SHOP_SOUL, DialogueView.SHOP_STUDENT,
            DialogueView.SHOP_SHINIGAMI, DialogueView.SHOP_QUINCY, DialogueView.SHOP_VISORED);

    private static final IntFunction<NpcAction> BY_ID =
            ByIdMap.continuous(NpcAction::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, NpcAction> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, NpcAction::ordinal);

    private final Set<DialogueView> views;

    NpcAction(DialogueView first, DialogueView... rest) {
        this.views = EnumSet.of(first, rest);
    }

    public boolean allowedIn(DialogueView view) {
        return views.contains(view);
    }

    /** The actions offered in a view, in button order. */
    public static List<NpcAction> offeredIn(DialogueView view) {
        return Arrays.stream(values()).filter(action -> action.allowedIn(view)).toList();
    }

    /** The last part of the action's button key, {@code screen.bleach.dialogue.action.<key>}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
