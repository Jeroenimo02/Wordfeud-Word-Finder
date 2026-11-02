package app.wordfeudwordfinder.extension.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScreenShadeTest {
    @Test public void lightAppBarUnderScrimIsCovered() {
        ScreenShade s = new ScreenShade();
        assertFalse(s.update(0xDEE3E7));
        assertTrue(s.update(0x67686A));
        assertFalse(s.update(0xDEE3E7));
    }

    @Test public void darkAppBarUnderScrimIsCovered() {
        ScreenShade s = new ScreenShade();
        assertFalse(s.update(0x343B45));
        assertTrue(s.update(0x181B20));
    }

    @Test public void smallChangesAndBlackBarsAreNotCovered() {
        ScreenShade s = new ScreenShade();
        assertFalse(s.update(0xDEE3E7));
        assertFalse(s.update(0xD0D5DB));
        ScreenShade black = new ScreenShade();
        assertFalse(black.update(0x0A0A0A));
        assertFalse(black.update(0x000000));
    }

    @Test public void resetForgetsTheBaseline() {
        ScreenShade s = new ScreenShade();
        s.update(0xDEE3E7);
        s.reset();
        assertFalse(s.update(0x343B45));
    }

    @Test public void boardShadeFromSampledRow() {
        int[] light = new int[30];
        java.util.Arrays.fill(light, 0xC6D1D7);
        light[3] = 0xF2EFEA; // tile
        light[4] = 0xB51823; // TW
        assertEquals(Boolean.FALSE, ScreenShade.boardDark(light));
        int[] dark = new int[30];
        java.util.Arrays.fill(dark, 0x2A2D32);
        for (int i = 0; i < 10; i++) dark[i] = 0x181A1E; // gaps
        assertEquals(Boolean.TRUE, ScreenShade.boardDark(dark));
        int[] scrim = new int[30];
        java.util.Arrays.fill(scrim, 0x5A5F62);
        assertNull(ScreenShade.boardDark(scrim));
        assertNull(ScreenShade.boardDark(null));
    }
}
