package app.wordfeudwordfinder.extension.ui;

import static org.junit.Assert.*;

import app.wordfeudwordfinder.extension.game.Ruleset;
import app.wordfeudwordfinder.extension.solver.Move;
import app.wordfeudwordfinder.extension.ui.Strings.Key;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OverlayFormatTest {
    private static Ruleset rs() {
        Map<String, Integer> p = new HashMap<>();
        p.put("A", 1);
        p.put("E", 1);
        p.put("N", 2);
        p.put("Q", 10);
        return new Ruleset(1, "nl", p, Collections.<String, Integer>emptyMap());
    }

    private static Move move(boolean horizontal, List<String> letters, List<String> bonuses, int score) {
        List<int[]> placed = new java.util.ArrayList<>();
        for (int i = 0; i < letters.size(); i++) placed.add(new int[] {i, 0});
        return new Move("WORD", 0, 0, horizontal, placed, letters, score,
                Collections.singletonList("WORD"), bonuses, false);
    }

    @Test public void bonusLabels() {
        assertNull(OverlayFormat.bonusLabel(0));
        assertEquals("DL", OverlayFormat.bonusLabel(1));
        assertEquals("TL", OverlayFormat.bonusLabel(2));
        assertEquals("DW", OverlayFormat.bonusLabel(3));
        assertEquals("TW", OverlayFormat.bonusLabel(4));
        assertNull(OverlayFormat.bonusLabel(5));
        assertNull(OverlayFormat.bonusLabel(-1));
    }

    @Test public void letterPoints() {
        assertEquals(1, OverlayFormat.letterPoints(rs(), 'A'));
        assertEquals(10, OverlayFormat.letterPoints(rs(), 'Q'));
        assertEquals(0, OverlayFormat.letterPoints(rs(), 'q'));   // blank
        assertEquals(0, OverlayFormat.letterPoints(rs(), 'Z'));   // unknown
        assertEquals(0, OverlayFormat.letterPoints(null, 'A'));
        assertEquals(0, OverlayFormat.letterPoints(rs(), '\0'));
    }

    @Test public void blankDetection() {
        assertTrue(OverlayFormat.isBlank(rs(), 'q'));
        assertFalse(OverlayFormat.isBlank(rs(), 'Q'));
        assertFalse(OverlayFormat.isBlank(rs(), '\0'));
        assertEquals("Q", OverlayFormat.displayLetter('q'));
        assertEquals("", OverlayFormat.displayLetter('\0'));
    }

    @Test public void bonusSummaryKeepsOrderAndDropsDuplicates() {
        Move m = move(true, Arrays.asList("A", "N", "E"), Arrays.asList("TW:A", "DL:N", "TW:E"), 30);
        assertEquals("TW DL", OverlayFormat.bonusSummary(m));
        assertEquals("", OverlayFormat.bonusSummary(move(true, Arrays.asList("A"),
                Collections.<String>emptyList(), 2)));
    }

    @Test public void detailLine() {
        Move m = move(false, Arrays.asList("A", "n"), Collections.singletonList("DW:A"), 12);
        assertEquals("Down · Uses: A ? · DW", OverlayFormat.detail("en", m));
        Move h = move(true, Arrays.asList("E"), Collections.<String>emptyList(), 2);
        assertEquals("Horizontaal · Gebruikt: E", OverlayFormat.detail("nl", h));
    }

    @Test public void scoreText() {
        assertEquals("12 pts", OverlayFormat.scoreText("en", 12));
        assertEquals(String.format(Strings.get("de", Key.PTS), 7), OverlayFormat.scoreText("de", 7));
    }

    @Test public void usedRackIndicesHandlesDuplicatesAndBlanks() {
        List<String> rack = Arrays.asList("E", "A", "E", "?", "N");
        Move m = move(true, Arrays.asList("E", "x", "N"), Collections.<String>emptyList(), 5);
        boolean[] used = OverlayFormat.usedRackIndices(rack, m);
        assertArrayEquals(new boolean[] {true, false, false, true, true}, used);
    }

    @Test public void usedRackIndicesTwoOfTheSameLetter() {
        List<String> rack = Arrays.asList("E", "A", "E");
        Move m = move(true, Arrays.asList("E", "E"), Collections.<String>emptyList(), 2);
        assertArrayEquals(new boolean[] {true, false, true}, OverlayFormat.usedRackIndices(rack, m));
    }

    @Test public void usedRackIndicesNullSafe() {
        assertEquals(0, OverlayFormat.usedRackIndices(null, null).length);
        assertArrayEquals(new boolean[] {false}, OverlayFormat.usedRackIndices(Arrays.asList("A"), null));
    }

    @Test public void themeBonusColoursAreDistinctAndLightEqualsDarkForNow() {
        Theme t = Theme.forDarkMode(true);
        assertEquals(t.squareEmpty, t.bonusColor(0));
        assertEquals(t.squareEmpty, t.bonusColor(9));
        java.util.Set<Integer> s = new java.util.HashSet<>();
        for (int c = 0; c <= 4; c++) s.add(t.bonusColor(c));
        assertEquals(5, s.size());
        assertSame(Theme.DARK, Theme.forDarkMode(true));
        assertEquals(Theme.DARK.background, Theme.forDarkMode(false).background);
        assertEquals(Theme.DARK.tileNewBorder, Theme.forDarkMode(false).tileNewBorder);
    }
}
