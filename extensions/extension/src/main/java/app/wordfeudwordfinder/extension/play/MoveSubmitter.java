package app.wordfeudwordfinder.extension.play;

import app.wordfeudwordfinder.extension.core.WfError;
import app.wordfeudwordfinder.extension.game.GameStore;
import app.wordfeudwordfinder.extension.hooks.UserAgentTap;
import app.wordfeudwordfinder.extension.solver.Move;
import app.wordfeudwordfinder.extension.words.RejectedWords;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sends a move to the Wordfeud API (spec sections 8.1-8.3). Blocking: call it off the main thread. At most one
 * request runs at a time; a call while another is running returns ERROR(MV-NET) at once without sending anything.
 * There is never an automatic retry.
 *
 * <p>Outcome mapping:
 * <ul>
 *   <li>no valid session: ERROR(MV-SESSION), nothing is sent;</li>
 *   <li>connection problem / time-out / any unexpected exception while sending: ERROR(MV-NET);</li>
 *   <li>a response body (2xx, or the error body of another status) is handed to
 *       {@link GameStore#onMoveResponse}: {@code status: success} gives SUCCESS (the store is updated);
 *       a reply whose root {@code status} string is not "success" is a server error;
 *       {@code MV-SERVER-illegal_word} gives REJECTED with the words the server names in {@code content.words}
 *       (array) or {@code content.word} (string) when present, else the main word (format unconfirmed, V6), and
 *       those words go to {@link RejectedWords}; any other {@code MV-SERVER-*} gives ERROR(that code);</li>
 *   <li>a non-2xx status whose body is missing or not a Wordfeud response: ERROR(MV-SERVER-http_&lt;status&gt;);</li>
 *   <li>a 2xx response that is empty or cannot be interpreted (also an unexpected exception): ERROR(MV-NET),
 *       outcome unknown; but when its root {@code status} is "success" and only the game cannot be read (or a
 *       store listener fails): SUCCESS, the refresh / next games/detail delivers the state.</li>
 * </ul>
 * Nothing about the cookie, the request or the response is logged.
 */
public final class MoveSubmitter {
    private static final String ILLEGAL_WORD = "MV-SERVER-illegal_word";

    /** Outcome of {@link #submit}. */
    public static final class Result {
        public enum Kind { SUCCESS, REJECTED, ERROR }

        public final Kind kind;
        /** For REJECTED: the rejected words (upper case); empty otherwise. */
        public final List<String> words;
        /** For ERROR: the error; null otherwise. */
        public final WfError error;

        private Result(Kind kind, List<String> words, WfError error) {
            this.kind = kind;
            this.words = Collections.unmodifiableList(new ArrayList<>(words));
            this.error = error;
        }

        static Result success() {
            return new Result(Kind.SUCCESS, Collections.emptyList(), null);
        }

        static Result rejected(List<String> words) {
            return new Result(Kind.REJECTED, words, null);
        }

        static Result error(WfError e) {
            return new Result(Kind.ERROR, Collections.emptyList(), e);
        }

        @Override
        public String toString() {
            return kind == Kind.ERROR ? "ERROR(" + error.code() + ")"
                    : kind == Kind.REJECTED ? "REJECTED(" + words + ")" : "SUCCESS";
        }
    }

    private final SessionReader session;
    private final Poster poster;
    private final GameStore store;
    private final RejectedWords rejected;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public MoveSubmitter(SessionReader session, Poster poster, GameStore store, RejectedWords rejected) {
        this.session = session;
        this.poster = poster;
        this.store = store;
        this.rejected = rejected;
    }

    /** True while a move is being sent (the Play button stays disabled). */
    public boolean isBusy() {
        return busy.get();
    }

    public Result submit(long gameId, int rulesetId, Move m) {
        if (!busy.compareAndSet(false, true)) return Result.error(WfError.mvNet());
        try {
            return doSubmit(gameId, rulesetId, m);
        } catch (RuntimeException e) {
            // never throws: an unexpected failure means the outcome is unknown
            return Result.error(WfError.mvNet());
        } finally {
            busy.set(false);
        }
    }

    private Result doSubmit(long gameId, int rulesetId, Move m) {
        String sessionId;
        try {
            sessionId = session.sessionId();
        } catch (WfError e) {
            return Result.error(e);
        } catch (RuntimeException e) {
            return Result.error(WfError.mvSession());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cookie", "sessionid=" + sessionId);
        String ua;
        try {
            ua = UserAgentTap.get();
        } catch (RuntimeException e) {
            ua = null;
        }
        if (ua != null) headers.put("User-Agent", ua);
        headers.put("Content-Type", "application/json");

        String response;
        Integer httpError = null;
        try {
            response = poster.post(MoveRequest.url(gameId), MoveRequest.body(m, rulesetId), headers);
        } catch (Poster.HttpStatusException e) {
            response = e.body;
            httpError = e.status;
        } catch (IOException | RuntimeException e) {
            return Result.error(WfError.mvNet());
        }
        if (response == null || response.trim().isEmpty()) return unknownOutcome(httpError);
        try {
            // Also for a non-2xx body: the server's own JSON decides. A non-2xx with status "success" is
            // therefore treated as SUCCESS (the server says the move was played).
            store.onMoveResponse(response);
            return Result.success();
        } catch (WfError e) {
            if (ILLEGAL_WORD.equals(e.code())) {
                List<String> words = serverNamedWords(response);
                if (words.isEmpty()) words = Collections.singletonList(m.word.toUpperCase(Locale.ROOT));
                try {
                    rejected.add(rulesetId, words);
                } catch (RuntimeException ignored) {
                    // best effort: the result still reports the words
                }
                return Result.rejected(words);
            }
            String status = statusOf(response);
            // MV-SERVER-* only for a real Wordfeud error reply (a root "status" string); JSON without it is not one
            if (e.code().startsWith("MV-SERVER-") && status != null && !"success".equals(status)) {
                return Result.error(e);
            }
            return httpError == null && "success".equals(status) ? Result.success() : unknownOutcome(httpError);
        } catch (RuntimeException e) {
            // e.g. a GameStore listener failing after the game was stored
            return httpError == null && "success".equals(statusOf(response)) ? Result.success()
                    : unknownOutcome(httpError);
        }
    }

    /**
     * A response that cannot be interpreted: for 2xx the move may or may not have been played, so MV-NET
     * ("outcome unknown"; the Play button stays off until the next games/detail); for another status
     * MV-SERVER-http_&lt;status&gt;.
     */
    private static Result unknownOutcome(Integer httpError) {
        return Result.error(httpError != null ? WfError.mvServer("http_" + httpError) : WfError.mvNet());
    }

    /**
     * The root {@code status} string, or null when the body is not a JSON object with one. "success" with a game
     * that cannot be read still means the move was played: the refresh / next games/detail delivers the state.
     */
    static String statusOf(String json) {
        try {
            Object st = new JSONObject(json).opt("status");
            return st instanceof String ? (String) st : null;
        } catch (JSONException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Words in {@code content.words} (array of strings) or {@code content.word} (string); empty when absent. */
    static List<String> serverNamedWords(String json) {
        List<String> out = new ArrayList<>();
        try {
            Object content = new JSONObject(json).opt("content");
            if (!(content instanceof JSONObject)) return out;
            JSONObject c = (JSONObject) content;
            Object ws = c.opt("words");
            if (ws instanceof JSONArray) {
                JSONArray a = (JSONArray) ws;
                for (int i = 0; i < a.length(); i++) add(out, a.opt(i));
            } else {
                add(out, ws);
            }
            if (out.isEmpty()) add(out, c.opt("word"));
        } catch (JSONException e) {
            out.clear(); // separate catches: checked on Android, unchecked on the JVM test jar
        } catch (RuntimeException e) {
            out.clear();
        }
        return out;
    }

    private static void add(List<String> out, Object w) {
        if (!(w instanceof String)) return;
        String s = ((String) w).trim().toUpperCase(Locale.ROOT);
        if (!s.isEmpty() && !out.contains(s)) out.add(s);
    }
}
