package app.wordfeudwordfinder.extension.ui;

/**
 * Decisions on pixels sampled from Wordfeud's own screen (see {@link ScreenSampler}). No android.* imports so it
 * stays JVM-testable.
 *
 * <p>Under: Flutter draws the board and its menus into one surface, so the button cannot sit between them. It is
 * made to look as if it lies under them instead: a pixel column of Wordfeud's screen just right of the button (the
 * page background, #D0D5DB light / #343B45 dark) is classified row by row. The scrim of a menu or dialog is
 * measured on the app bar (no menu comes there) and dims the button by the same amount; a row that is not the
 * background darkened by exactly that scrim (a menu sheet) covers the button, which is cut off from that row down.
 *
 * <p>Board: Wordfeud has a "dark board" option in light mode, so the board colours are classified from a pixel row
 * through the board (empty squares and grid gaps of either palette).
 */
public final class ScreenShade {
    // Stateful only for the app bar baseline (see scrim()); everything else is static.
    /** Page background behind the button, light and dark theme. */
    public static final int PAGE_LIGHT = 0xD0D5DB;
    public static final int PAGE_DARK = 0x343B45;
    /** Max per-channel distance for a pixel to count as the (darkened) background. */
    static final int SCALED_TOLERANCE = 10;
    /** Max per-channel distance for a pixel to count as a board colour. */
    static final int TOLERANCE = 14;
    static final int MIN_VOTES = 6;

    private static final int[] LIGHT_BOARD = {0xC6D1D7, 0xACC0C7};
    private static final int[] DARK_BOARD = {0x2A2D32, 0x171A1F};

    /** Below this brightness (0..255) the app bar is too dark to measure a scrim on. */
    static final double MIN_BAR = 12;

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
     * Scrim strength (0..1 multiplier) from the app bar, where no menu ever comes: the sample against the brightest
     * app bar value seen on this screen. Call {@link #resetScrim()} for another game or theme.
     */
    private double brightestBar = -1;

    public void resetScrim() {
        brightestBar = -1;
    }

    public double scrim(int barRgb) {
        double b = brightness(barRgb & 0xFFFFFF);
        if (b > brightestBar) brightestBar = b;
        if (brightestBar < MIN_BAR) return 1.0;
        return Math.min(1.0, b / brightestBar);
    }

    /**
     * Classifies the column beside the button (one pixel per button row, top to bottom): a row that is the page
     * background {@code base} darkened by exactly the scrim {@code s} is free, anything else covers the button from
     * that row down. (A darkened-background test alone is not enough: the dark theme's menu sheet #2C3137 is close
     * to #343B45 at 82%.) Null when nothing is known.
     */
    public static Cover analyse(int[] column, int base, double s) {
        if (column == null || column.length == 0) return null;
        float factor = (float) Math.max(0.0, Math.min(1.0, s));
        for (int i = 0; i < column.length; i++) {
            if (!scaledFrom(column[i] & 0xFFFFFF, base, s)) return new Cover(i, factor);
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
