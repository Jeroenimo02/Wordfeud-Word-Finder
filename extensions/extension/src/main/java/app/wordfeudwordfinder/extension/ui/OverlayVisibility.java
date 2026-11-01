package app.wordfeudwordfinder.extension.ui;

import app.wordfeudwordfinder.extension.game.GameState;

/** Visibility rules for the floating button (spec section 2). */
public final class OverlayVisibility {
    private OverlayVisibility() {}

    public static boolean fabVisible(Long routeGameId, GameState st) {
        if (routeGameId == null || st == null) return false;
        // Ended games hide the button even when unreadable; an unreadable running game shows it (with the error).
        return !st.ended();
    }
}
