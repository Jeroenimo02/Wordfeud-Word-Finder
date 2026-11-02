package app.wordfeudwordfinder.extension.hooks;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * V2 diagnosis: remembers the last move Wordfeud itself sends, so the format of a blank tile in a move can be read
 * from the device (toast and "Copy details"). Request bodies reach Cronet through the static factories of
 * {@code org.chromium.net.UploadDataProviders} ({@code create(byte[])}, {@code create(byte[], int, int)},
 * {@code create(ByteBuffer)}); uploadTapPatch calls {@link #onUpload(Object)} / {@link #onUpload(byte[], int, int)} at
 * their start.
 *
 * <p>Only a body that parses as a JSON object with a {@code "move"} array is kept (a Wordfeud move: tiles, words,
 * ruleset); every other body is ignored and never stored or shown. At most {@link #MAX_BYTES} bytes are copied; a
 * longer body does not parse and is ignored. A ByteBuffer is read through {@code duplicate()}, so its position and
 * limit stay as they were. Thread-safe; never throws.
 */
// TODO(V2): remove this tap (and uploadTapPatch) once the blank format in a move is known
public final class UploadTap {
    /** Shows each captured move as a toast "WFWF move: &lt;body&gt;". */
    // TODO(V2): remove the diagnostic toast
    public static final boolean DIAGNOSTIC_TOAST = true;
    static final int MAX_BYTES = 4096;
    /**
     * create(byte[]) usually calls create(byte[], int, int) (and Dart may wrap the same bytes again), so one move can
     * pass several hooks: the same body within this time is shown once.
     */
    static final long DUPLICATE_MS = 2000;

    private static volatile String lastMove;
    private static volatile Toaster toaster;
    private static final Object LOCK = new Object();
    private static String lastShown;
    private static long lastShownAt;

    /** Shows a short text on the main thread (FlutterBridge). */
    public interface Toaster {
        void show(String text);
    }

    private UploadTap() {
    }

    /** Set by FlutterBridge once the activity is there; null switches the toast off. */
    public static void setToaster(Toaster t) {
        toaster = t;
    }

    /** The last move body Wordfeud sent (compact JSON), or null when none was seen yet. */
    public static String lastMove() {
        return lastMove;
    }

    /** Hook for {@code create(byte[])} and {@code create(ByteBuffer)}. */
    public static void onUpload(Object data) {
        try {
            if (data instanceof byte[]) {
                byte[] b = (byte[]) data;
                capture(b, 0, b.length);
            } else if (data instanceof ByteBuffer) {
                byte[] b = copy((ByteBuffer) data);
                if (b != null) capture(b, 0, b.length);
            }
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    /** Hook for {@code create(byte[], int, int)}. */
    public static void onUpload(byte[] data, int offset, int length) {
        try {
            if (data == null || offset < 0 || length < 0 || offset > data.length - length) return;
            capture(data, offset, length);
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    private static void capture(byte[] b, int offset, int length) {
        String body = moveBody(b, offset, length);
        if (body == null) return;
        lastMove = body;
        if (!DIAGNOSTIC_TOAST) return;
        Toaster t = toaster;
        if (t != null && shouldShow(body, System.currentTimeMillis())) t.show("move: " + body);
    }

    /** False for the same body again within {@link #DUPLICATE_MS}. */
    static boolean shouldShow(String body, long now) {
        synchronized (LOCK) {
            if (body.equals(lastShown) && now - lastShownAt >= 0 && now - lastShownAt < DUPLICATE_MS) return false;
            lastShown = body;
            lastShownAt = now;
            return true;
        }
    }

    /** The remaining bytes of {@code buf} (at most {@link #MAX_BYTES}), read from a duplicate: buf is not changed. */
    static byte[] copy(ByteBuffer buf) {
        if (buf == null) return null;
        ByteBuffer d = buf.duplicate();
        byte[] out = new byte[Math.min(d.remaining(), MAX_BYTES)];
        d.get(out);
        return out;
    }

    /**
     * The body as compact JSON (whitespace outside strings removed, otherwise unchanged) when the first
     * {@link #MAX_BYTES} bytes are a JSON object with a {@code "move"} array; null for anything else.
     */
    static String moveBody(byte[] b, int offset, int length) {
        if (b == null) return null;
        int n = Math.min(length, MAX_BYTES);
        int i = offset;
        int end = offset + n;
        while (i < end && isSpace(b[i])) i++;
        if (i >= end || b[i] != '{') return null; // cheap filter before parsing
        String s = new String(b, offset, n, StandardCharsets.UTF_8);
        try {
            JSONObject o = new JSONObject(s);
            if (o.optJSONArray("move") == null) return null;
        } catch (Throwable notJson) {
            return null;
        }
        return compact(s);
    }

    private static boolean isSpace(byte c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }

    /** Removes whitespace outside JSON strings. */
    static String compact(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                sb.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
                sb.append(c);
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Tests only. */
    static void reset() {
        lastMove = null;
        toaster = null;
        synchronized (LOCK) {
            lastShown = null;
            lastShownAt = 0;
        }
    }
}
