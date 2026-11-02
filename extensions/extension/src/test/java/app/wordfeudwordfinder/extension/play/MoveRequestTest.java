package app.wordfeudwordfinder.extension.play;

import static org.junit.Assert.assertEquals;

import app.wordfeudwordfinder.extension.solver.Move;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MoveRequestTest {

    /** MANDJE on row 8 from column 6; the A on 7,8 is already on the board. */
    static Move mandje() {
        List<int[]> placed = Arrays.asList(new int[] {6, 8}, new int[] {8, 8}, new int[] {9, 8},
                new int[] {10, 8}, new int[] {11, 8});
        return new Move("MANDJE", 6, 8, true, placed, Arrays.asList("M", "N", "D", "J", "E"), 20,
                Collections.singletonList("MANDJE"), Collections.emptyList(), true);
    }

    @Test
    public void bodyMatchesAppFormat() {
        assertEquals("{\"move\":[[6,8,\"M\"],[8,8,\"N\"],[9,8,\"D\"],[10,8,\"J\"],[11,8,\"E\"]],"
                + "\"words\":[\"MANDJE\"],\"ruleset\":2}", MoveRequest.body(mandje(), 2));
    }

    @Test
    public void bodyHasAllWordsMainFirstAndBlankFlag() {
        Move m = new Move("AB", 0, 0, true, Arrays.asList(new int[] {0, 0}, new int[] {1, 0}),
                Arrays.asList("A", "b"), 5, Arrays.asList("AB", "BC"), Collections.emptyList(), false);
        assertEquals("{\"move\":[[0,0,\"A\"],[1,0,\"B\",true]],\"words\":[\"AB\",\"BC\"],\"ruleset\":0}",
                MoveRequest.body(m, 0));
    }

    @Test
    public void blanksAreUpperCaseWithTrueAndNormalTilesUnchanged() {
        // V2 experiment (testbuild 5): blank as [x,y,"X",true]; normal tiles stay [x,y,"X"]
        Move m = new Move("ZEE", 3, 7, true, Arrays.asList(new int[] {3, 7}, new int[] {4, 7}, new int[] {5, 7}),
                Arrays.asList("z", "E", "e"), 2, Collections.singletonList("ZEE"), Collections.emptyList(), false);
        assertEquals("{\"move\":[[3,7,\"Z\",true],[4,7,\"E\"],[5,7,\"E\",true]],\"words\":[\"ZEE\"],"
                + "\"ruleset\":2}", MoveRequest.body(m, 2));
        assertEquals("{\"move\":[[6,8,\"M\"],[8,8,\"N\"],[9,8,\"D\"],[10,8,\"J\"],[11,8,\"E\"]],"
                + "\"words\":[\"MANDJE\"],\"ruleset\":2}", MoveRequest.body(mandje(), 2)); // no flag
    }

    @Test
    public void bodyEscapesLetters() {
        Move m = new Move("\"\\", 0, 0, true, Collections.singletonList(new int[] {0, 0}),
                Collections.singletonList("\"\\\n"), 0, Collections.singletonList("\u00c6"),
                Collections.emptyList(), false);
        assertEquals("{\"move\":[[0,0,\"\\\"\\\\\\n\"]],\"words\":[\"\u00c6\"],\"ruleset\":1}",
                MoveRequest.body(m, 1));
    }

    @Test
    public void urlFormat() {
        assertEquals("https://api.wordfeud.com/wf/game/4391372380/move/?game_format=log",
                MoveRequest.url(4391372380L));
    }
}
