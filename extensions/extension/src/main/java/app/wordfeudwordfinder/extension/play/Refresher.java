package app.wordfeudwordfinder.extension.play;

import java.util.Collections;
import java.util.List;

/**
 * Makes Wordfeud show the new game state after a move played through the overlay (spec section 8.4).
 *
 * <p>{@link #ORDER} names the strategies to try ({@link ForegroundSignal}, {@link RouteBounce}, {@link FcmNudge}).
 * It stays empty until a strategy has been confirmed on a device (V4, docs/DEV.md "V4 — verversen"). With an empty
 * order nothing happens: the overlay closes and Wordfeud refreshes by itself (about 20 s), the fallback from spec 8.4.
 *
 * <p>Debug override: when {@code wfwf/debug_refresh_order.txt} exists, its names replace {@link #ORDER} and each step
 * is logged (strategy and channel names only). Without that file nothing is logged.
 */
public final class Refresher {
    /** One way to make Wordfeud refresh. Returns true when it was carried out. */
    public interface Strategy {
        boolean refresh(long gameId) throws Exception;
    }

    /** Sends platform messages to the Flutter engine of the current activity. */
    public interface Messenger {
        /**
         * Schedules {@code message} on {@code channel}, {@code delayMs} from now, on the main thread. Returns false
         * when no Flutter engine is reachable now (nothing scheduled).
         */
        boolean send(String channel, byte[] message, long delayMs);
    }

    /** Debug log line sink (only used when the debug file exists). */
    public interface DebugLog {
        void d(String line);
    }

    /** What the Android side provides (FlutterBridge). */
    public interface Env {
        /** The messenger; {@code log} is null in normal use and set when the debug file exists. */
        Messenger messenger(DebugLog log);

        /** The text of the debug order file, or null when it does not exist (normal use). */
        String debugOrder();

        void debugLog(String line);
    }

    /** The strategy names to try, in order. Empty: fallback (spec 8.4), Wordfeud refreshes itself. */
    static final List<String> ORDER = Collections.emptyList();

    private static volatile Env env;

    private Refresher() {}

    /** Installed by FlutterBridge for the current activity; null removes it. */
    public static void install(Env e) {
        env = e;
    }

    /** Never throws. Call it on the main thread after the overlay has closed. */
    public static void refresh(long gameId) {
        try {
            Env e = env;
            if (e == null) return;
            String debugText = e.debugOrder();
            DebugLog log = debugText == null ? null : e::debugLog;
            List<String> names = log == null ? ORDER : RefreshOrder.parse(debugText);
            if (log != null) log.d("refresh: order=" + names + " (debug file)");
            if (names.isEmpty()) return;
            run(RefreshOrder.strategies(names, e.messenger(log)), gameId, log);
        } catch (Throwable ignored) {
            // refreshing is best effort; Wordfeud catches up by itself
        }
    }

    /** Runs the strategies in order until one reports success; failures are swallowed. True when one worked. */
    static boolean run(List<Strategy> order, long gameId, DebugLog log) {
        for (Strategy s : order) {
            String name = s.getClass().getSimpleName();
            try {
                if (log != null) log.d("strategy " + name + ": start");
                boolean ok = s.refresh(gameId);
                if (log != null) log.d("strategy " + name + ": " + (ok ? "scheduled" : "not possible"));
                if (ok) return true;
            } catch (Throwable t) {
                if (log != null) log.d("strategy " + name + ": failed " + t.getClass().getSimpleName());
            }
        }
        return false;
    }
}
