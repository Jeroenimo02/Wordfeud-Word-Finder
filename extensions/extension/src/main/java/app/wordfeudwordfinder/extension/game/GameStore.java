package app.wordfeudwordfinder.extension.game;

import app.wordfeudwordfinder.extension.core.Storage;
import app.wordfeudwordfinder.extension.core.WfError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Holds parsed games and their replayed state; caches rulesets and board layouts on disk because the app omits
 * known ones from games/detail (spec section 5). Thread-safe; listeners run outside the monitor.
 */
public final class GameStore {
    private static final String RULESETS_FILE = "cache/rulesets.json";
    private static final String BOARDS_FILE = "cache/boards.json";

    private final Storage storage;
    private final Map<Long, RawGame> games = new LinkedHashMap<>();
    private final Map<Long, GameState> states = new LinkedHashMap<>();
    private final Map<Integer, Ruleset> rulesets = new LinkedHashMap<>();
    private final Map<Integer, BoardLayout> boards = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    /** Number of games/detail responses processed (see {@link #detailGeneration}). */
    private long detailGeneration;

    public GameStore(Storage s) {
        this.storage = s;
        loadCache();
    }

    /**
     * Merges a games/detail response: games are upserted by id, new rulesets/boards are merged into memory and the
     * disk cache. A response that is not parseable as a whole leaves the store unchanged and does not notify.
     */
    public void onGamesDetail(String json) {
        ResponseParser.Parsed p;
        try {
            p = ResponseParser.parseGamesDetail(json);
        } catch (WfError e) {
            return;
        }
        List<Runnable> toRun;
        synchronized (this) {
            mergeCache(p.rulesets, p.boards);
            for (RawGame g : p.games) put(g);
            for (Map.Entry<Long, WfError> f : p.failedGames.entrySet()) {
                games.remove(f.getKey());
                states.put(f.getKey(), unreadable(f.getKey(), f.getValue(), false, false));
            }
            detailGeneration++;
            toRun = new ArrayList<>(listeners);
        }
        notifyAll(toRun);
    }

    /** Replaces the game from a move response; throws MV-SERVER-... and changes nothing on a server error. */
    public void onMoveResponse(String json) throws WfError {
        RawGame g = ResponseParser.parseMove(json);
        List<Runnable> toRun;
        synchronized (this) {
            put(g);
            toRun = new ArrayList<>(listeners);
        }
        notifyAll(toRun);
    }

    /** Null when the game is unknown. */
    public synchronized GameState state(long gameId) {
        return states.get(gameId);
    }

    /** The parsed game (ruleset/board ids), or null when unknown or its header was unparseable. */
    public synchronized RawGame game(long gameId) {
        return games.get(gameId);
    }

    public synchronized Ruleset ruleset(int id) {
        return rulesets.get(id);
    }

    public synchronized BoardLayout board(int id) {
        return boards.get(id);
    }

    /** Ruleset ids of the games currently in the store. */
    public synchronized Set<Integer> seenRulesets() {
        Set<Integer> out = new HashSet<>();
        for (RawGame g : games.values()) out.add(g.getRulesetId());
        return out;
    }

    /**
     * How many games/detail responses have been processed (a move response does not count). Listeners fire after
     * both kinds; a caller that must wait for a games/detail compares this count instead (Play after MV-NET).
     */
    public synchronized long detailGeneration() {
        return detailGeneration;
    }

    public synchronized void addListener(Runnable r) {
        listeners.add(r);
    }

    // ---- internals (call with the monitor held) ----

    private void put(RawGame g) {
        games.put(g.getId(), g);
        if (!rulesets.containsKey(g.getRulesetId())) {
            states.put(g.getId(), unreadable(g.getId(), WfError.gsData("ruleset " + g.getRulesetId() + " unknown"),
                    g));
        } else if (!boards.containsKey(g.getBoardId())) {
            states.put(g.getId(), unreadable(g.getId(), WfError.gsData("board " + g.getBoardId() + " unknown"), g));
        } else {
            states.put(g.getId(), GameReplay.replay(g));
        }
    }

    private static GameState unreadable(long id, WfError e, RawGame g) {
        boolean ended = g.getEndGame() != 0;
        boolean myTurn = g.getLocalPosition() >= 0 && g.getCurrentPlayer() == g.getLocalPosition() && !ended;
        return unreadable(id, e, myTurn, ended);
    }

    private static GameState unreadable(long id, WfError e, boolean myTurn, boolean ended) {
        return new GameState(id, new char[GameState.SIZE][GameState.SIZE], new ArrayList<String>(), myTurn, ended, e);
    }

    private void mergeCache(List<Ruleset> newRulesets, List<BoardLayout> newBoards) {
        for (Ruleset r : newRulesets) rulesets.put(r.getId(), r);
        for (BoardLayout b : newBoards) boards.put(b.getId(), b);
        try {
            if (!newRulesets.isEmpty()) storage.writeTextAtomic(RULESETS_FILE, rulesetsJson().toString());
            if (!newBoards.isEmpty()) storage.writeTextAtomic(BOARDS_FILE, boardsJson().toString());
        } catch (org.json.JSONException e) {
            // best effort: the in-memory copy is still valid for this session
        } catch (IOException | RuntimeException e) {
            // best effort: the in-memory copy is still valid for this session
        }
    }

    private JSONArray rulesetsJson() throws org.json.JSONException {
        JSONArray a = new JSONArray();
        for (Ruleset r : rulesets.values()) {
            JSONObject o = new JSONObject();
            o.put("ruleset", r.getId());
            o.put("language_code", r.getLanguageCode());
            o.put("tile_points", new JSONObject(r.getTilePoints()));
            o.put("tile_counts", new JSONObject(r.getTileCounts()));
            a.put(o);
        }
        return a;
    }

    private JSONArray boardsJson() throws org.json.JSONException {
        JSONArray a = new JSONArray();
        for (BoardLayout b : boards.values()) {
            JSONObject o = new JSONObject();
            o.put("board_id", b.getId());
            JSONArray rows = new JSONArray();
            for (int[] row : b.getSquares()) {
                JSONArray r = new JSONArray();
                for (int v : row) r.put(v);
                rows.put(r);
            }
            o.put("board", rows);
            a.put(o);
        }
        return a;
    }

    /** Reads the cache files; a missing or corrupt file counts as empty, corrupt entries are skipped. */
    private void loadCache() {
        List<Ruleset> rs = new ArrayList<>();
        List<BoardLayout> bs = new ArrayList<>();
        try {
            String t = storage.readText(RULESETS_FILE);
            if (t != null) {
                Object v = new org.json.JSONTokener(t).nextValue();
                if (v instanceof JSONArray) rs = ResponseParser.parseRulesets((JSONArray) v);
            }
        } catch (Exception e) {
            rs = new ArrayList<>();
        }
        try {
            String t = storage.readText(BOARDS_FILE);
            if (t != null) {
                Object v = new org.json.JSONTokener(t).nextValue();
                if (v instanceof JSONArray) bs = ResponseParser.parseBoards((JSONArray) v);
            }
        } catch (Exception e) {
            bs = new ArrayList<>();
        }
        for (Ruleset r : rs) rulesets.put(r.getId(), r);
        for (BoardLayout b : bs) boards.put(b.getId(), b);
    }

    private static void notifyAll(List<Runnable> toRun) {
        for (Runnable r : toRun) {
            try {
                r.run();
            } catch (Throwable t) {
                // listener failures never affect the store or the app
            }
        }
    }
}
