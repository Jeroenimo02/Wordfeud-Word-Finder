package app.wordfeudwordfinder.extension.ui;

/**
 * Decisions on pixels sampled from Wordfeud's own screen (see {@link ScreenSampler}). No android.* imports so it
 * stays JVM-testable.
 *
 * <p>Under: Flutter draws the board and its menus into one surface, so the button cannot sit between them. It is
 * made to look as if it lies under them instead: a pixel column of Wordfeud's screen just right of the button (the
 * page background, #D0D5DB light / #343B45 dark) is classified row by row. A row that is the background darkened
 * (a menu or dialog scrim, #D0D5DB becomes #5E6265) dims the button by the same amount; a row that is not (the
 * sheet of the "..." menu, #E6EBEF) covers it, and the button is cut off from the first such row down.
 *
 * <p>Board: Wordfeud has a "dark board" option in light mode, so the board colours are classified from a pixel row
 * through the board (empty squares and grid gaps of either palette).
 */
public final class ScreenShade {
    /** Page background behind the button, light and dark theme. */
    public static final int PAGE_LIGHT = 0xD0D5DB;
    public static final int PAGE_DARK = 0x343B45;
    /** Max per-channel distance for a pixel to count as the (darkened) background. */
    static final int SCALED_TOLERANCE = 10;
    /** A row brighter than the background by more than this share is something drawn over it. */
    static final double BRIGHTER = 1.04;
    /** Max per-channel distance for a pixel to count as a board colour. */
    static final int TOLERANCE = 14;
    static final int MIN_VOTES = 6;

    private static final int[] LIGHT_BOARD = {0xC6D1D7, 0xACC0C7};
    private static final int[] DARK_BOARD = {0x2A2D32, 0x171A1F};

    private ScreenShade() {}

    /** How the button should look: rows [0, visibleRows) shown, multiplied by {@code factor} (1 = untouched). */
    public static final class Cover {
        public final int visibleRows;
        public final float factor;

        Cover(int visibleRows, float factor) {
            this.visibleRows = visibleRows;
            this.factor = factor;
        }

        public boolean untouched(int rows) {
            return visibleRows >= rows && factor >= 0.98f;
        }
    }

    /**
     * Classifies the column beside the button (one pixel per button row, top to bottom) against the page background
     * {@code base}. Null when nothing is known (no pixels), meaning: leave the button as it is.
     */
    public static Cover analyse(int[] column, int base) {
        if (column == null || column.length == 0) return null;
        double baseB = brightness(base);
        if (baseB <= 0) return null;
        float factor = 1f;
        for (int i = 0; i < column.length; i++) {
            int p = column[i] & 0xFFFFFF;
            double r = brightness(p) / baseB;
            if (r > BRIGHTER || !scaledFrom(p, base, r)) return new Cover(i, i == 0 ? 1f : factor);
            if (i == 0) factor = (float) Math.min(1.0, r);
        }
        return new Cover(column.length, factor);
    }

    private static boolean scaledFrom(int p, int base, double r) {
        for (int shift = 0; shift <= 16; shift += 8) {
            double expect = ((base >> shift) & 0xFF) * r;
            if (Math.abs(((p >> shift) & 0xFF) - expect) > SCALED_TOLERANCE) return false;
        }
        return true;
    }

    static double brightness(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * bl;
    }

    /** TRUE dark board, FALSE light board, null undecided (no board under the samples, or covered). */
    public static Boolean boardDark(int[] pixels) {
        if (pixels == null) return null;
        int dark = 0;
        int light = 0;
        for (int p : pixels) {
            if (matches(p, DARK_BOARD)) dark++;
            else if (matches(p, LIGHT_BOARD)) light++;
        }
        if (dark >= MIN_VOTES && dark > 2 * light) return Boolean.TRUE;
        if (light >= MIN_VOTES && light > 2 * dark) return Boolean.FALSE;
        return null;
    }

    private static boolean matches(int p, int[] palette) {
        for (int c : palette) {
            if (Math.abs(((p >> 16) & 0xFF) - ((c >> 16) & 0xFF)) <= TOLERANCE
                    && Math.abs(((p >> 8) & 0xFF) - ((c >> 8) & 0xFF)) <= TOLERANCE
                    && Math.abs((p & 0xFF) - (c & 0xFF)) <= TOLERANCE) {
                return true;
            }
        }
        return false;
    }
}
