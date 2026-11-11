package io.github.masteryoga1.bleach.world;

/** The moon's eight phases as vanilla numbers them, from full at 0 through new at 4, and Hueco Mundo's rule for them. */
public final class MoonPhases {
    public static final int COUNT = 8, FULL = 0, NEW = 4;

    private MoonPhases() {
    }

    /** The phase half the cycle away, which Hueco Mundo's sky shows: full for new, new for full. */
    public static int opposite(int phase) {
        return Math.floorMod(phase + COUNT / 2, COUNT);
    }
}
