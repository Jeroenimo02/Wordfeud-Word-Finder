package app.wordfeudwordfinder.extension.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class ScreenShadeTest {
    private static int[] col(int n, int c) {
        int[] a = new int[n];
        Arrays.fill(a, c);
        return a;
    }

    @Test public void plainBackgroundLeavesTheButtonUntouched() {
        ScreenShade.Cover c = ScreenShade.analyse(col(40, 0xD0D5DB), ScreenShade.PAGE_LIGHT);
        assertTrue(c.untouched(40));
        c = ScreenShade.analyse(col(40, 0xCED3D9), ScreenShade.PAGE_LIGHT); // slight shade near the rack
        assertTrue(c.untouched(40));
        c = ScreenShade.analyse(col(40, 0x343B45), ScreenShade.PAGE_DARK);
        assertTrue(c.untouched(40));
    }

    @Test public void scrimDimsWithoutCutting() {
        // Measured: light page under the menu scrim.
        ScreenShade.Cover c = ScreenShade.analyse(col(40, 0x5E6265), ScreenShade.PAGE_LIGHT);
        assertEquals(40, c.visibleRows);
        assertEquals(0.46, c.factor, 0.03);
        assertFalse(c.untouched(40));
    }

    @Test public void sheetCutsFromItsTopEdge() {
        int[] a = col(40, 0x5E6265);
        for (int i = 25; i < 40; i++) a[i] = 0xE6EBEF; // measured sheet colour
        ScreenShade.Cover c = ScreenShade.analyse(a, ScreenShade.PAGE_LIGHT);
        assertEquals(25, c.visibleRows);
        assertEquals(0.46, c.factor, 0.03);
        assertEquals(0, ScreenShade.analyse(col(40, 0xE6EBEF), ScreenShade.PAGE_LIGHT).visibleRows);
    }

    @Test public void otherColoursCover() {
        assertEquals(0, ScreenShade.analyse(col(10, 0x2F7DE1), ScreenShade.PAGE_LIGHT).visibleRows);
        assertEquals(0, ScreenShade.analyse(col(10, 0x404751), ScreenShade.PAGE_DARK).visibleRows);
        assertNull(ScreenShade.analyse(new int[0], ScreenShade.PAGE_LIGHT));
        assertNull(ScreenShade.analyse(null, ScreenShade.PAGE_LIGHT));
    }

    @Test public void boardShadeFromSampledRow() {
        int[] light = col(30, 0xC6D1D7);
        light[3] = 0xF2EFEA; // tile
        light[4] = 0xB51823; // TW
        assertEquals(Boolean.FALSE, ScreenShade.boardDark(light));
        int[] dark = col(30, 0x2A2D32);
        for (int i = 0; i < 10; i++) dark[i] = 0x181A1E; // gaps
        assertEquals(Boolean.TRUE, ScreenShade.boardDark(dark));
        assertNull(ScreenShade.boardDark(col(30, 0x5A5F62)));
        assertNull(ScreenShade.boardDark(null));
    }
}
