package app.wordfeudwordfinder.extension.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

public class RefresherTest {
    static final class FakeEnv implements Refresher.Env {
        final RefreshStrategiesTest.FakeMessenger messenger = new RefreshStrategiesTest.FakeMessenger();
        String debug;
        final List<String> log = new ArrayList<>();
        Refresher.DebugLog seenLog = null;

        @Override
        public Refresher.Messenger messenger(Refresher.DebugLog l) {
            seenLog = l;
            return messenger;
        }

        @Override
        public String debugOrder() {
            return debug;
        }

        @Override
        public void debugLog(String line) {
            log.add(line);
        }
    }

    @After
    public void reset() {
        Refresher.install(null);
    }

    @Test
    public void emptyOrderIsTheFallback() {
        assertTrue(Refresher.ORDER.isEmpty());
        assertFalse(Refresher.run(new ArrayList<>(), 42L, null));
        Refresher.refresh(42L); // no environment: never throws
        FakeEnv env = new FakeEnv();
        Refresher.install(env);
        Refresher.refresh(42L);
        assertTrue(env.messenger.sent.isEmpty()); // nothing happens; Wordfeud refreshes itself
        assertTrue(env.log.isEmpty()); // release: no logging at all
        assertEquals(null, env.seenLog);
    }

    @Test
    public void debugFileOverridesTheOrderAndLogsOnlyNames() {
        FakeEnv env = new FakeEnv();
        env.debug = "RouteBounce\nForegroundSignal";
        Refresher.install(env);
        Refresher.refresh(4391L);
        assertEquals(2, env.messenger.sent.size()); // RouteBounce worked, so ForegroundSignal did not run
        assertEquals("flutter/navigation", env.messenger.sent.get(0).channel);
        assertTrue(env.seenLog != null);
        String all = String.join("\n", env.log);
        assertTrue(all, all.contains("RouteBounce"));
        assertFalse(all, all.contains("4391")); // no game ids or message contents in the log
    }

    @Test
    public void debugFallsThroughWhenNoEngine() {
        FakeEnv env = new FakeEnv();
        env.debug = "FcmNudge,RouteBounce";
        env.messenger.available = false;
        Refresher.install(env);
        Refresher.refresh(1L);
        assertTrue(env.messenger.sent.isEmpty());
        String all = String.join("\n", env.log);
        assertTrue(all, all.contains("FcmNudge") && all.contains("RouteBounce"));
    }

    @Test
    public void runsInOrderUntilOneWorksAndSwallowsFailures() {
        List<String> calls = new ArrayList<>();
        List<Refresher.Strategy> order = Arrays.asList(
                id -> { calls.add("a" + id); throw new IllegalStateException("boom"); },
                id -> { calls.add("b" + id); return false; },
                id -> { calls.add("c" + id); return true; },
                id -> { calls.add("d" + id); return true; });
        assertTrue(Refresher.run(order, 7L, null));
        assertEquals(Arrays.asList("a7", "b7", "c7"), calls);
    }
}
