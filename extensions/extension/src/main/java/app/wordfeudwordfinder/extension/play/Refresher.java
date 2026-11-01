package app.wordfeudwordfinder.extension.play;

import java.util.Collections;
import java.util.List;

/**
 * Makes Wordfeud show the new game state after a move played through the overlay (spec section 8.4).
 *
 * <p>{@link #ORDER} is empty until Task 15 has tested the candidates on a device (V4). With an empty order nothing
 * happens: the overlay closes and Wordfeud refreshes by itself (about 20 s). That is the fallback from spec 8.4.
 */
public final class Refresher {
    /** One way to make Wordfeud refresh. Returns true when it was carried out. */
    public interface Strategy {
        boolean refresh(long gameId) throws Exception;
    }

    /** The strategies to try, in order. Empty: fallback (spec 8.4), Wordfeud refreshes itself. Filled in Task 15. */
    static final List<Strategy> ORDER = Collections.emptyList();

    private Refresher() {}

    /** Never throws. Call it after the overlay has closed. */
    public static void refresh(long gameId) {
        try {
            run(ORDER, gameId);
        } catch (Throwable ignored) {
            // refreshing is best effort; Wordfeud catches up by itself
        }
    }

    /** Runs the strategies in order until one reports success; failures are swallowed. True when one worked. */
    static boolean run(List<Strategy> order, long gameId) {
        for (Strategy s : order) {
            try {
                if (s.refresh(gameId)) return true;
            } catch (Throwable ignored) {
                // try the next one
            }
        }
        return false;
    }
}
