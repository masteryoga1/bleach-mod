package io.github.masteryoga1.bleach.soul;

/**
 * Whether a player is standing on hardened reishi, and at what height. The server decides; the
 * client keeps its own copy, set from what the server says and from its own prediction, so the
 * player's movement collides with the footing the same way on both sides. Never saved: footing ends
 * on death, logging out and changing dimension.
 */
public final class FootingState {
    /** Whether the reishi under this player's feet holds them. */
    public boolean active;
    /** The block height the footing hardens at; its top is one block higher. */
    public int anchorY;
    /** Client only: until which tick of the player the fall is held at the footing's top while the footing has not arrived. */
    public int clampUntilTick = -1;
    /** Server only: where the player was last tick, to lay footing ahead of them. */
    double lastX;
    double lastZ;
    boolean hasLast;
}
