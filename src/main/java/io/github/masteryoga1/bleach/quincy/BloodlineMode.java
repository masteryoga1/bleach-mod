package io.github.masteryoga1.bleach.quincy;

/**
 * How a server settles the bloodline of a new living Human without Shinigami power (the server
 * config's {@code quincy.bloodline_mode}).
 */
public enum BloodlineMode {
    /** Each player is asked once, and answers Human, Quincy or "let fate decide". */
    CHOICE,
    /** Each player's blood is rolled for them, as if they had let fate decide, without asking. */
    RANDOM,
    /** Nobody is asked or rolled for: Quincy blood comes only by command. */
    OFF
}
