package app.wordfeudwordfinder.extension.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RefresherTest {
    @Test
    public void emptyOrderIsTheFallback() {
        assertTrue(Refresher.ORDER.isEmpty());
        assertFalse(Refresher.run(Refresher.ORDER, 42L)); // nothing happens; Wordfeud refreshes itself
        Refresher.refresh(42L); // never throws
    }

    @Test
    public void runsInOrderUntilOneWorksAndSwallowsFailures() {
        List<String> calls = new ArrayList<>();
        List<Refresher.Strategy> order = Arrays.asList(
                id -> { calls.add("a" + id); throw new IllegalStateException("boom"); },
                id -> { calls.add("b" + id); return false; },
                id -> { calls.add("c" + id); return true; },
                id -> { calls.add("d" + id); return true; });
        assertTrue(Refresher.run(order, 7L));
        assertEquals(Arrays.asList("a7", "b7", "c7"), calls);
    }
}
