package io.github.masteryoga1.bleach.menu;

/**
 * How the Tailoring Table's garment buttons are laid out over pages: four across and two down, eight
 * to a page. Only the screen turns the pages; a button still stands for its recipe's place in the
 * menu's whole list, so the menu and the server never need to know which page is showing.
 */
public final class TailoringPages {
    public static final int COLUMNS = 4;
    public static final int ROWS = 2;
    public static final int PER_PAGE = COLUMNS * ROWS;

    private TailoringPages() {
    }

    /** How many pages this many recipes fill: always at least one, so an empty table still has a page. */
    public static int pageCount(int recipes) {
        return Math.max(1, (Math.max(0, recipes) + PER_PAGE - 1) / PER_PAGE);
    }

    /** The page brought within the pages these recipes fill, for a page that was turned too far or lost recipes. */
    public static int clampPage(int page, int recipes) {
        return Math.max(0, Math.min(page, pageCount(recipes) - 1));
    }

    /**
     * The recipe a button stands for: its place in the whole list, or -1 for a button past the last
     * recipe, on a page that does not exist, or outside the page.
     */
    public static int indexAt(int page, int slot, int recipes) {
        if (page < 0 || slot < 0 || slot >= PER_PAGE) {
            return -1;
        }
        long index = (long) page * PER_PAGE + slot;
        return index < recipes ? (int) index : -1;
    }

    /** The page that shows this recipe; the first page when none is chosen (a negative index). */
    public static int pageOf(int index) {
        return index < 0 ? 0 : index / PER_PAGE;
    }
}
