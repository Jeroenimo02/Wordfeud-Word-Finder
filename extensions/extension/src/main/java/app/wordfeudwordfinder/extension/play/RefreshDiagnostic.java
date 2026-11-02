package app.wordfeudwordfinder.extension.play;

/**
 * V4 test builds only: what happened while refreshing, as one short line, e.g.
 * {@code RouteBounce: activity=MainActivity | engine=via getFlutterEngine OK | dartExecutor OK |
 * send(flutter/navigation,63B) OK}. Shown as a toast ({@link Refresher#DIAGNOSTIC_TOAST}) and appended to the
 * error details ({@code refresh=}). Steps hold only class/method names, outcomes, channel names and byte counts:
 * never game ids or message contents.
 *
 * <p>Steps come from the main thread and (for the strategy itself) the caller; methods are synchronized.
 * {@link #finish()} publishes the text once ({@link #last()}) and calls the listener.
 */
// TODO(V4): remove with the diagnostic toast after the refresh strategy is confirmed
public final class RefreshDiagnostic {
    static final int MAX_LENGTH = 400;
    static final String SEP = " | ";

    private static volatile String last;

    private final Refresher.DebugLog onFinish;
    private final StringBuilder text = new StringBuilder();
    private boolean segmentOpen; // a strategy name was written and has no step yet
    private boolean finished;

    RefreshDiagnostic(Refresher.DebugLog onFinish) {
        this.onFinish = onFinish;
    }

    /** The text of the last finished refresh, or null. */
    public static String last() {
        return last;
    }

    static void clearLast() {
        last = null;
    }

    /** Starts the part of one strategy: {@code "<name>: "}. */
    public synchronized void strategy(String name) {
        if (finished) return;
        if (text.length() > 0) text.append(" || ");
        text.append(clean(name)).append(": ");
        segmentOpen = true;
    }

    /** Adds one step outcome. */
    public synchronized void step(String outcome) {
        if (finished) return;
        if (text.length() > 0 && !segmentOpen) text.append(SEP);
        text.append(clean(outcome));
        segmentOpen = false;
    }

    /** {@code send(<channel>,<n>B)}: the label of one message, without its contents. */
    public static String message(String verb, String channel, int bytes) {
        return verb + "(" + channel + "," + bytes + "B)";
    }

    /** A short reason for a failure: the exception's simple name and message (cause for reflection wrappers). */
    public static String reason(Throwable t) {
        if (t == null) return "?";
        Throwable c = t;
        if (c instanceof java.lang.reflect.InvocationTargetException && c.getCause() != null) c = c.getCause();
        String m = c.getMessage();
        return c.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : " " + m);
    }

    public synchronized String text() {
        String s = text.toString();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH - 1) + "…" : s;
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    /** Publishes the text once; later calls and steps are ignored. Never throws. */
    public void finish() {
        String t;
        synchronized (this) {
            if (finished) return;
            finished = true;
            t = text();
        }
        last = t;
        try {
            if (onFinish != null) onFinish.d(t);
        } catch (Throwable ignored) {
            // diagnostics never affect the app
        }
    }

    static String clean(String s) {
        if (s == null) return "null";
        s = s.replace('\r', ' ').replace('\n', ' ');
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
