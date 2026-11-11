package io.github.masteryoga1.bleach.soul;

/** Where Konsō sends a soul that passes on outside Soul Society: the server's {@code [konso] sends_to}. */
public enum KonsoTarget {
    /** To a district of the Rukongai drawn by chance, the inner ones likelier (see {@code RukongaiEvents}). */
    RUKONGAI,
    /** Straight to the gates of the Academy in Seireitei, the shortcut that stood in for the Rukongai. */
    ACADEMY,
    /** Nowhere: the soul stays where it stands, free of its chain. */
    NOWHERE
}
