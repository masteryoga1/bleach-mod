package io.github.masteryoga1.bleach.soul;

import java.util.UUID;

/**
 * Something one player has offered another and is waiting on them to accept: Konsō for a Plus, or a
 * Shinigami's power for a Human. Kept only while the server runs.
 *
 * @param kind      what is offered
 * @param from      the player who offered it
 * @param expiresAt game time after which it can no longer be accepted
 */
public record SoulOffer(Kind kind, UUID from, long expiresAt) {
    public enum Kind {
        KONSO,
        POWER
    }

    public boolean isFrom(UUID player, long now) {
        return from.equals(player) && now <= expiresAt;
    }
}
