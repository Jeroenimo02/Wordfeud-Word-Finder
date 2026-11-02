package app.wordfeudwordfinder.extension.ui;

/**
 * Decisions on pixels sampled from Wordfeud's own screen (see {@link ScreenSampler}). No android.* imports so it
 * stays JVM-testable.
 *
 * <p>Covered: Flutter darkens the whole screen with a scrim while a bottom sheet (the "..." game menu) or a dialog is
 * open; the button must then hide, as it otherwise floats over the menu. The app bar colour is sampled and compared
 * with the brightest value seen on this screen: light #DEE3E7 becomes #67686A under the scrim.
 *
 * <p>Board: Wordfeud has a "dark board" option in light mode, so the board colours are classified from a pixel row
 * through the board (empty squares and grid gaps of either palette).
 */
public final class ScreenShade {
    /** Covered when the sampled brightness falls below this share of the brightest value seen. */
    static final double COVERED_RATIO = 0.75;
    /** Below this brightness (0..255) a darker sample is noise, not a scrim (a nearly black app bar). */
    static final double MIN_BASELINE = 20;
    /** Max per-channel distance for a pixel to count as a board colour. */
    static final int TOLERANCE = 14;
    static final int MIN_VOTES = 6;

    private static final int[] LIGHT_BOARD = {0xC6D1D7, 0xACC0C7};
    private static final int[] DARK_BOARD = {0x2A2D32, 0x171A1F};

    private double brightest = -1;

    /** Forget the baseline (another game, route or app theme). */
    public void reset() {
        brightest = -1;
    }

    /** Feeds one app bar sample; returns whether the screen is covered by a scrim now. */
    public boolean update(int rgb) {
        double b = brightness(rgb);
        if (b > brightest) brightest = b;
        return brightest >= MIN_BASELINE && b < brightest * COVERED_RATIO;
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
