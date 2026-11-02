package app.wordfeudwordfinder.extension.game;

import app.wordfeudwordfinder.extension.core.WfError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses responses of {@code user/games/detail/?…&game_format=log} and {@code game/<id>/move/?game_format=log}.
 *
 * <p>Only {@code opt()} plus {@code instanceof} checks are used: Android's org.json differs from the JVM jar for
 * {@code optString}/{@code opt*}-with-fallback on JSON null (it returns the string "null").
 */
public final class ResponseParser {
    private ResponseParser() {}

    public static final class Parsed {
        public final List<RawGame> games;
        public final List<Ruleset> rulesets;
        public final List<BoardLayout> boards;
        /** Games whose header could not be parsed but whose id could: id to the reason (GS-DATA). */
        public final Map<Long, WfError> failedGames;

        Parsed(List<RawGame> games, List<Ruleset> rulesets, List<BoardLayout> boards,
               Map<Long, WfError> failedGames) {
            this.games = Collections.unmodifiableList(games);
            this.rulesets = Collections.unmodifiableList(rulesets);
            this.boards = Collections.unmodifiableList(boards);
            this.failedGames = Collections.unmodifiableMap(failedGames);
        }
    }

    public static Parsed parseGamesDetail(String json) throws WfError {
        try {
            JSONObject root = new JSONObject(json);
            if (!"success".equals(root.opt("status"))) throw WfError.gsData("response status is not success");
            JSONObject content = obj(root, "content");
            List<RawGame> games = new ArrayList<>();
            JSONArray ga = arr(content, "games");
            Map<Long, WfError> failed = new LinkedHashMap<>();
            // Tolerant per game: one malformed game must not make the whole response unusable.
            for (int i = 0; i < ga.length(); i++) {
                Object go = ga.opt(i);
                try {
                    games.add(parseGame(asObj(go, "games[]")));
                } catch (WfError e) {
                    Long id = go instanceof JSONObject ? tryId((JSONObject) go) : null;
                    if (id != null) failed.put(id, e); // else: no id, nothing to attach the error to; skipped
                }
            }
            List<Ruleset> rulesets = new ArrayList<>();
            JSONArray ra = optArr(content, "rulesets");
            if (ra != null) rulesets.addAll(parseRulesets(ra));
            List<BoardLayout> boards = new ArrayList<>();
            JSONArray ba = optArr(content, "boards");
            if (ba != null) boards.addAll(parseBoards(ba));
            return new Parsed(games, rulesets, boards, failed);
        } catch (JSONException e) {
            throw WfError.gsData("json: malformed response (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Parses a move response; a non-success status throws MV-SERVER-&lt;content.type&gt;. */
    public static RawGame parseMove(String json) throws WfError {
        try {
            JSONObject root = new JSONObject(json);
            if (!"success".equals(root.opt("status"))) {
                Object content = root.opt("content");
                Object type = content instanceof JSONObject ? ((JSONObject) content).opt("type") : null;
                throw WfError.mvServer(type instanceof String ? (String) type : "unknown");
            }
            return parseGame(obj(obj(root, "content"), "game"));
        } catch (JSONException e) {
            throw WfError.gsData("json: malformed response (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Tolerant: malformed entries are skipped. */
    static List<Ruleset> parseRulesets(JSONArray ra) {
        List<Ruleset> out = new ArrayList<>();
        for (int i = 0; i < ra.length(); i++) {
            try {
                out.add(parseRuleset(asObj(ra.opt(i), "rulesets[]")));
            } catch (WfError e) {
                // skipped
            }
        }
        return out;
    }

    /** Tolerant: malformed entries are skipped. */
    static List<BoardLayout> parseBoards(JSONArray ba) {
        List<BoardLayout> out = new ArrayList<>();
        for (int i = 0; i < ba.length(); i++) {
            try {
                out.add(parseBoard(asObj(ba.opt(i), "boards[]")));
            } catch (WfError e) {
                // skipped
            }
        }
        return out;
    }

    private static Long tryId(JSONObject g) {
        try {
            return lng(g, "id");
        } catch (WfError e) {
            return null;
        }
    }

    static RawGame parseGame(JSONObject g) throws WfError {
        long id = lng(g, "id");
        int board = integer(g, "board");
        int ruleset = integer(g, "ruleset");
        int currentPlayer = integer(g, "current_player");
        int endGame = integer(g, "end_game");

        int localPosition = -1;
        JSONArray players = arr(g, "players");
        for (int i = 0; i < players.length(); i++) {
            JSONObject p = asObj(players.opt(i), "players[]");
            if (Boolean.TRUE.equals(p.opt("is_local"))) {
                if (localPosition != -1) throw WfError.gsData("two local players in game " + id);
                localPosition = integer(p, "position");
            }
        }

        List<LogEvent> log = new ArrayList<>();
        JSONArray la = arr(g, "game_log");
        for (int i = 0; i < la.length(); i++) {
            JSONObject ev = asObj(la.opt(i), "game_log[]");
            Object type = ev.opt("type");
            log.add(new LogEvent(type instanceof String ? (String) type : null, ev));
        }
        // localPosition stays -1 when no player is local; GameReplay reports that as GS-DATA.
        return new RawGame(id, board, ruleset, currentPlayer, endGame, localPosition, log);
    }

    private static Ruleset parseRuleset(JSONObject r) throws WfError {
        Object lang = r.opt("language_code");
        if (!(lang instanceof String)) throw WfError.gsData("ruleset.language_code");
        return new Ruleset(integer(r, "ruleset"), (String) lang,
                intMap(obj(r, "tile_points")), intMap(obj(r, "tile_counts")));
    }

    private static BoardLayout parseBoard(JSONObject b) throws WfError {
        JSONArray rows = arr(b, "board");
        if (rows.length() != GameState.SIZE) throw WfError.gsData("board rows");
        int[][] sq = new int[GameState.SIZE][GameState.SIZE];
        for (int y = 0; y < GameState.SIZE; y++) {
            Object ro = rows.opt(y);
            if (!(ro instanceof JSONArray) || ((JSONArray) ro).length() != GameState.SIZE) {
                throw WfError.gsData("board row " + y);
            }
            JSONArray row = (JSONArray) ro;
            for (int x = 0; x < GameState.SIZE; x++) {
                int v = toInt(row.opt(x), "board square");
                if (v < 0 || v > 4) throw WfError.gsData("board square value " + v);
                sq[y][x] = v;
            }
        }
        return new BoardLayout(integer(b, "board_id"), sq);
    }

    private static Map<String, Integer> intMap(JSONObject o) throws WfError {
        Map<String, Integer> m = new LinkedHashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            m.put(k, toInt(o.opt(k), k));
        }
        return m;
    }

    // ---- null-safe accessors (opt + instanceof only) ----

    static JSONObject obj(JSONObject o, String key) throws WfError {
        return asObj(o.opt(key), key);
    }

    static JSONObject asObj(Object v, String what) throws WfError {
        if (v instanceof JSONObject) return (JSONObject) v;
        throw WfError.gsData("expected object: " + what);
    }

    static JSONArray arr(JSONObject o, String key) throws WfError {
        Object v = o.opt(key);
        if (v instanceof JSONArray) return (JSONArray) v;
        throw WfError.gsData("expected array: " + key);
    }

    /** Null when absent or JSON null. */
    static JSONArray optArr(JSONObject o, String key) throws WfError {
        Object v = o.opt(key);
        if (v == null || JSONObject.NULL.equals(v)) return null;
        if (v instanceof JSONArray) return (JSONArray) v;
        throw WfError.gsData("expected array: " + key);
    }

    static int integer(JSONObject o, String key) throws WfError {
        return toInt(o.opt(key), key);
    }

    static int toInt(Object v, String what) throws WfError {
        long l = toLong(v, what);
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) throw WfError.gsData("out of range: " + what);
        return (int) l;
    }

    private static long lng(JSONObject o, String key) throws WfError {
        return toLong(o.opt(key), key);
    }

    private static long toLong(Object v, String what) throws WfError {
        if (v instanceof Integer || v instanceof Long) return ((Number) v).longValue();
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) return (long) d;
        }
        throw WfError.gsData("expected integer: " + what);
    }
}
