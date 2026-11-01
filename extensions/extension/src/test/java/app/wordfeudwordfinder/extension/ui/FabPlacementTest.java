package app.wordfeudwordfinder.extension.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FabPlacementTest {

    @Test
    public void constantsMatchSpec() {
        assertEquals(40, FabPlacement.FAB_SIZE_DP);
        assertEquals(10, FabPlacement.FAB_MARGIN_RIGHT_DP);
        assertEquals(165, FabPlacement.RACK_TOP_FROM_BOTTOM_DP);
        assertEquals(10, FabPlacement.FAB_GAP_ABOVE_RACK_DP);
    }

    @Test
    public void bottomMarginWithoutBanner() {
        // (165 + 10) dp at density 2 = 350 px, plus a 48 px navigation bar inset.
        assertEquals(398, FabPlacement.bottomMarginPx(2f, 48, false));
        assertEquals(175, FabPlacement.bottomMarginPx(1f, 0, false));
    }

    @Test
    public void bannerShiftsTheButtonUp() {
        int without = FabPlacement.bottomMarginPx(2f, 48, false);
        int with = FabPlacement.bottomMarginPx(2f, 48, true);
        assertEquals(FabPlacement.BANNER_SHIFT_DP * 2, with - without);
    }

    @Test
    public void negativeInsetCountsAsZero() {
        assertEquals(175, FabPlacement.bottomMarginPx(1f, -5, false));
    }

    @Test
    public void dpRoundsToPixels() {
        assertEquals(105, FabPlacement.dp(2.625f, 40));
        assertEquals(26, FabPlacement.dp(2.625f, 10));
        assertEquals(0, FabPlacement.dp(2f, 0));
    }

    @Test
    public void effectiveBottomInsetOnlyCountsOverlapWithTheContent() {
        // Edge-to-edge: content reaches the window bottom, so the whole inset overlaps.
        assertEquals(48, FabPlacement.effectiveBottomInset(48, 2000, 2000));
        // Content already ends above the navigation bar: nothing overlaps.
        assertEquals(0, FabPlacement.effectiveBottomInset(48, 2000, 1952));
        // Partial overlap.
        assertEquals(18, FabPlacement.effectiveBottomInset(48, 2000, 1970));
        assertEquals(0, FabPlacement.effectiveBottomInset(0, 2000, 2000));
    }

    @Test
    public void effectiveTopInsetOnlyCountsOverlapWithTheContent() {
        assertEquals(60, FabPlacement.effectiveTopInset(60, 0));
        assertEquals(0, FabPlacement.effectiveTopInset(60, 60));
        assertEquals(20, FabPlacement.effectiveTopInset(60, 40));
        assertEquals(0, FabPlacement.effectiveTopInset(60, 100));
    }
}
