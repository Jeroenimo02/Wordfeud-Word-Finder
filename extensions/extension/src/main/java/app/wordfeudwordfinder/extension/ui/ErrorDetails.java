package app.wordfeudwordfinder.extension.ui;

import app.wordfeudwordfinder.extension.core.WfError;
import java.time.Instant;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The text behind "Copy details" (spec section 10): code, detail, Wordfeud version, patch version, ruleset and time,
 * one {@code key=value} per line. The detail is the error's message (e.g. which game event failed to replay), reduced
 * to one line of at most {@link #MAX_DETAIL} characters; it is omitted when there is none. WfError messages only hold
 * event indexes and types, field names, letters and racks: never cookies, session ids, user names or response bodies
 * (the parser does not pass on org.json messages, which can quote the response).
 *
 * <p>{@link #format(WfError, String, String, int, Instant)} is the interface from the plan. {@code java.time} needs
 * API 26 and Wordfeud's minSdk is unknown, so the Android code calls the {@code long} overload, which gives the same
 * text (see {@link #isoUtc}) without loading {@code java.time}.
 */
public final class ErrorDetails {
    static final String UNKNOWN = "unknown";
    static final int MAX_DETAIL = 300;

    private ErrorDetails() {}

    public static String format(WfError e, String wfVersion, String patchVersion, int rulesetId, Instant t) {
        return format(e, wfVersion, patchVersion, rulesetId, t.toString());
    }

    /** Same as the Instant overload with {@code Instant.ofEpochMilli(epochMillis)}; safe below API 26. */
    public static String format(WfError e, String wfVersion, String patchVersion, int rulesetId, long epochMillis) {
        return format(e, wfVersion, patchVersion, rulesetId, isoUtc(epochMillis));
    }

    private static String format(WfError e, String wfVersion, String patchVersion, int rulesetId, String time) {
        return "code=" + (e == null ? UNKNOWN : e.code())
                + detailLine(e)
                + "\nwordfeud=" + orUnknown(wfVersion)
                + "\npatch=" + orUnknown(patchVersion)
                + "\nruleset=" + (rulesetId < 0 ? UNKNOWN : Integer.toString(rulesetId))
                + "\ntime=" + time;
    }

    private static String detailLine(WfError e) {
        if (e == null) return "";
        String m = e.getMessage();
        if (m == null || m.isEmpty() || m.equals(e.code())) return "";
        m = m.replace('\r', ' ').replace('\n', ' ');
        if (m.length() > MAX_DETAIL) m = m.substring(0, MAX_DETAIL);
        return "\ndetail=" + m;
    }

    /**
     * ISO-8601 in UTC, exactly as {@code Instant.ofEpochMilli(ms).toString()} prints it for years 1970-9999:
     * {@code 2026-11-01T12:34:56Z}, with {@code .SSS} only when the milliseconds are not zero.
     */
    static String isoUtc(long epochMillis) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        c.setTimeInMillis(epochMillis);
        String s = String.format(Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
        int ms = c.get(Calendar.MILLISECOND);
        return ms == 0 ? s + "Z" : s + String.format(Locale.ROOT, ".%03dZ", ms);
    }

    /**
     * Whether "Retry" makes sense: word list errors that a new attempt can fix. Not WL-NONE (there is no source),
     * GS-* (the game data does not change by retrying) or MV-* (a move is never re-sent automatically; Play is
     * re-enabled instead where that is safe).
     */
    public static boolean retryable(WfError e) {
        if (e == null) return false;
        String c = e.code();
        return c.startsWith("WL-") && !c.equals("WL-NONE");
    }

    private static String orUnknown(String s) {
        return s == null || s.trim().isEmpty() ? UNKNOWN : s;
    }
}
