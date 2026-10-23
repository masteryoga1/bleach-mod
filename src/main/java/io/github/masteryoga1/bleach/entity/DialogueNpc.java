package io.github.masteryoga1.bleach.entity;

import net.minecraft.world.entity.player.Player;

/**
 * A creature that talks: using it opens a dialogue whose text is
 * {@code screen.bleach.dialogue.<key>.<view>} and whose buttons come back as {@link NpcAction}s. The
 * Shinigami of the Academy and the Quincy of the Human World both answer through here, so one screen
 * and one payload serve them all. Only living entities implement it.
 */
public interface DialogueNpc {
    /** The middle part of the speech keys: {@code screen.bleach.dialogue.<key>.<view>}. */
    String dialogueKey();

    /**
     * Server side, from {@code NpcActionPayload}: does what the visitor asked for, if they may still
     * have it now. The NPC checks everything again, so a client cannot ask for what it was never offered.
     */
    void perform(Player visitor, NpcAction action);
}
