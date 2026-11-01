package app.wordfeudwordfinder.extension.game;

import app.wordfeudwordfinder.extension.core.WfError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Replays a game_log into board and local rack (spec section 5). */
public final class GameReplay {
    private GameReplay() {}

    /** Exact field sets observed in real logs; any other field makes the game unreadable (GS-DATA). */
    private static final Set<String> START_FIELDS =
            new HashSet<>(Arrays.asList("type", "tiles", "timestamp"));
    private static final Set<String> PACKED_MOVE_FIELDS = new HashSet<>(Arrays.asList(
            "type", "player", "x", "y", "horizontal", "tiles", "new_tiles", "timestamp"));
    // V10: swap and resign shapes observed in real logs (detail_mandje.json). pass and timeout are still unobserved.
    private static final Set<String> SWAP_FIELDS =
            new HashSet<>(Arrays.asList("type", "player", "tile_count", "tiles", "new_tiles", "timestamp"));
    private static final Set<String> RESIGN_FIELDS =
            new HashSet<>(Arrays.asList("type", "player", "timestamp"));

    public static GameState replay(RawGame g) {
        int lp = g.getLocalPosition();
        boolean ended = g.getEndGame() != 0;
        boolean myTurn = g.getCurrentPlayer() == lp && g.getEndGame() == 0;
        char[][] board = new char[GameState.SIZE][GameState.SIZE];
        List<String> rack = new ArrayList<>();
        try {
            if (lp < 0) throw WfError.gsData("no local player");
            for (LogEvent ev : g.getLog()) {
                String type = ev.getType();
                if (type == null) throw WfError.gsData("event without type");
                switch (type) {
                    case "start":
                        checkFields(ev.getRaw(), START_FIELDS);
                        applyStart(ev.getRaw(), lp, rack);
                        break;
                    case "packed_move":
                        checkFields(ev.getRaw(), PACKED_MOVE_FIELDS);
                        applyPackedMove(ev.getRaw(), lp, board, rack);
                        break;
                    case "swap":
                        checkFields(ev.getRaw(), SWAP_FIELDS);
                        applySwap(ev.getRaw(), lp, rack);
                        break;
                    case "resign":
                        checkFields(ev.getRaw(), RESIGN_FIELDS);
                        ResponseParser.integer(ev.getRaw(), "player"); // no board or rack change
                        break;
                    default:
                        throw WfError.gsEvent(type);
                }
            }
        } catch (WfError e) {
            return new GameState(g.getId(), new char[GameState.SIZE][GameState.SIZE], new ArrayList<String>(),
                    myTurn, ended, e);
        }
        return new GameState(g.getId(), board, rack, myTurn, ended, null);
    }

    private static void checkFields(JSONObject raw, Set<String> allowed) throws WfError {
        Iterator<String> keys = raw.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (!allowed.contains(k)) throw WfError.gsData("unknown field " + k);
        }
    }

    private static void applyStart(JSONObject raw, int lp, List<String> rack) throws WfError {
        JSONArray tiles = ResponseParser.arr(raw, "tiles");
        Object mine = tiles.opt(lp);
        if (!(mine instanceof JSONArray)) throw WfError.gsData("start without local rack");
        rack.clear();
        rack.addAll(letters((JSONArray) mine));
    }

    private static void applyPackedMove(JSONObject raw, int lp, char[][] board, List<String> rack) throws WfError {
        int player = ResponseParser.integer(raw, "player");
        int x = ResponseParser.integer(raw, "x");
        int y = ResponseParser.integer(raw, "y");
        Object h = raw.opt("horizontal");
        if (!(h instanceof Boolean)) throw WfError.gsData("horizontal");
        boolean horizontal = (Boolean) h;
        int dx = horizontal ? 1 : 0;
        int dy = horizontal ? 0 : 1;
        List<String> placed = letters(ResponseParser.arr(raw, "tiles"));
        if (placed.isEmpty()) throw WfError.gsData("move without tiles");

        for (String letter : placed) {
            while (inside(x, y) && board[y][x] != '\0') { // skip occupied squares
                x += dx;
                y += dy;
            }
            if (!inside(x, y)) throw WfError.gsData("move leaves the board");
            board[y][x] = letter.charAt(0);
            x += dx;
            y += dy;
        }

        if (player == lp) {
            for (String letter : placed) {
                if (!rack.remove(letter)) throw WfError.gsData("letter " + letter + " not on rack");
            }
            JSONArray newTiles = ResponseParser.optArr(raw, "new_tiles");
            if (newTiles != null) rack.addAll(letters(newTiles));
        }
    }

    /**
     * Swap: only the local player's own swap changes the rack. It must reveal both the swapped and the new tiles;
     * otherwise the rack cannot be reconstructed and the game is unreadable (GS-DATA). The opponent's swap
     * (whatever it reveals) does not touch the local rack.
     */
    private static void applySwap(JSONObject raw, int lp, List<String> rack) throws WfError {
        int player = ResponseParser.integer(raw, "player");
        if (player != lp) return;
        List<String> swapped = letters(ResponseParser.arr(raw, "tiles"));
        JSONArray newTiles = ResponseParser.optArr(raw, "new_tiles");
        if (newTiles == null) throw WfError.gsData("own swap without new tiles");
        List<String> fresh = letters(newTiles);
        if (swapped.isEmpty() || fresh.size() != swapped.size()) throw WfError.gsData("swap tile count");
        for (String letter : swapped) {
            if (!rack.remove(letter)) throw WfError.gsData("letter " + letter + " not on rack");
        }
        rack.addAll(fresh);
    }

    private static List<String> letters(JSONArray a) throws WfError {
        List<String> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            if (!(v instanceof String) || ((String) v).length() != 1) throw WfError.gsData("bad tile");
            out.add((String) v);
        }
        return out;
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < GameState.SIZE && y < GameState.SIZE;
    }
}
