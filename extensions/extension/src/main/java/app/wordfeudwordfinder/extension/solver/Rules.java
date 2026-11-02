package app.wordfeudwordfinder.extension.solver;

/** Game rules used by the move generator (spec section 7). */
public final class Rules {
    private Rules() {}

    /** Bonus for using all seven rack tiles. Stays 0 until check V5 confirms the value (task 16). */
    public static final int BINGO_BONUS = 0;

    /**
     * Moves that use a blank. V2 experiment (testbuild 5): on; a blank goes out as {@code [x,y,"X",true]}
     * (play/MoveRequest), whether the server accepts that is still open.
     */
    public static final boolean BLANKS_ENABLED = true;

    /** Centre square (x = y = CENTER) that the first move must cover. */
    public static final int CENTER = 7;

    /** Board size. */
    public static final int SIZE = 15;

    /** Tiles on a full rack; using all of them earns {@link #BINGO_BONUS}. */
    public static final int RACK_SIZE = 7;

    /** True for the rack strings that stand for a blank tile ("?" and the "*" seen in real new_tiles). */
    public static boolean isBlank(String tile) {
        return "?".equals(tile) || "*".equals(tile);
    }
}
