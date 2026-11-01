package app.wordfeudwordfinder.extension.ui;

/**
 * Every colour the overlay uses (spec section 9), as ARGB ints. No android.* imports so it stays JVM-testable.
 *
 * IMPORTANT (controller ruling R13): the approved mockup and the Wordfeud screenshots were not available
 * when these values were chosen. They approximate Wordfeud's dark UI (navy/charcoal background,
 * DL light blue, TL dark blue, DW orange/peach, TW red, cream tiles with dark letters, new tiles white
 * with a blue border and a blue score badge). They MUST be checked against the client's screenshots /
 * mockup {@code woordenboek_voorbeeldbord.jpg} (task 13 step 4) and adjusted here only.
 *
 * The light theme is the same as the dark one until check V8.
 */
public final class Theme {
    // Overlay chrome
    public final int background;
    public final int surface;
    public final int surfaceSelected;
    public final int divider;
    public final int textPrimary;
    public final int textSecondary;
    public final int accent;
    public final int onAccent;

    // Board
    public final int boardBackground;
    public final int squareEmpty;
    public final int squareDL;
    public final int squareTL;
    public final int squareDW;
    public final int squareTW;
    public final int squareLabel;

    // Tiles
    public final int tileFill;
    public final int tileText;
    public final int tileNewFill;
    public final int tileNewBorder;
    public final int tileNewText;
    /** Alpha (0..255) for a dimmed rack tile (a letter the selected word uses). */
    public final int tileDimmedAlpha;
    public final int badgeFill;
    public final int badgeText;

    private Theme(int[] c) {
        int i = 0;
        background = c[i++];
        surface = c[i++];
        surfaceSelected = c[i++];
        divider = c[i++];
        textPrimary = c[i++];
        textSecondary = c[i++];
        accent = c[i++];
        onAccent = c[i++];
        boardBackground = c[i++];
        squareEmpty = c[i++];
        squareDL = c[i++];
        squareTL = c[i++];
        squareDW = c[i++];
        squareTW = c[i++];
        squareLabel = c[i++];
        tileFill = c[i++];
        tileText = c[i++];
        tileNewFill = c[i++];
        tileNewBorder = c[i++];
        tileNewText = c[i++];
        tileDimmedAlpha = c[i++];
        badgeFill = c[i++];
        badgeText = c[i++];
        if (i != c.length) throw new IllegalStateException("theme size");
    }

    public static final Theme DARK = new Theme(new int[] {
        0xFF1B1F2A, // background: dark navy
        0xFF252B38, // surface: list rows, panels
        0xFF34425E, // surfaceSelected: selected list row
        0xFF343A48, // divider
        0xFFFFFFFF, // textPrimary
        0xFFAEB4C2, // textSecondary
        0xFF2F7DE1, // accent: Play button
        0xFFFFFFFF, // onAccent
        0xFF12151C, // boardBackground (grid lines)
        0xFF2B303B, // squareEmpty
        0xFF6FA9C9, // squareDL: light blue
        0xFF2E5A96, // squareTL: dark blue
        0xFFE29A63, // squareDW: orange/peach
        0xFFC64A43, // squareTW: red
        0xFFFFFFFF, // squareLabel
        0xFFF2E6C8, // tileFill: cream
        0xFF262626, // tileText: dark letters and points
        0xFFFFFFFF, // tileNewFill: white
        0xFF2F7DE1, // tileNewBorder: blue
        0xFF262626, // tileNewText
        90,         // tileDimmedAlpha
        0xFF2F7DE1, // badgeFill: blue score bubble
        0xFFFFFFFF, // badgeText
    });

    /** Same as {@link #DARK} until V8 provides the light colours. */
    public static final Theme LIGHT = DARK;

    /** Theme for the app's current mode (spec section 9: derived from statusBarIconBrightness). */
    public static Theme forDarkMode(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    /** Fill colour for a {@code BoardLayout} square code (0 none, 1 DL, 2 TL, 3 DW, 4 TW). */
    public int bonusColor(int code) {
        switch (code) {
            case 1: return squareDL;
            case 2: return squareTL;
            case 3: return squareDW;
            case 4: return squareTW;
            default: return squareEmpty;
        }
    }
}
