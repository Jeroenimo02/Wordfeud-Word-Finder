package app.wordfeudwordfinder.extension.play;

import app.wordfeudwordfinder.extension.solver.Move;
import java.util.List;
import java.util.Locale;

/** Builds the move request exactly as the app sends it (spec section 8.1). */
public final class MoveRequest {
    private static final String BASE = "https://api.wordfeud.com/wf/";

    private MoveRequest() {
    }

    public static String url(long gameId) {
        return BASE + "game/" + gameId + "/move/?game_format=log";
    }

    /**
     * {@code {"move":[[x,y,"L"],...],"words":[...],"ruleset":n}} without spaces: tiles in the order of
     * {@link Move#placed} (a blank is its letter in lower case), {@code words} = {@link Move#allWords} (main word
     * first). Built by hand so the key order is fixed.
     */
    public static String body(Move m, int rulesetId) {
        StringBuilder sb = new StringBuilder("{\"move\":[");
        for (int i = 0; i < m.placed.size(); i++) {
            if (i > 0) sb.append(',');
            int[] xy = m.placed.get(i);
            sb.append('[').append(xy[0]).append(',').append(xy[1]).append(',');
            quote(sb, m.placedLetters.get(i));
            sb.append(']');
        }
        sb.append("],\"words\":[");
        List<String> words = m.allWords;
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append(',');
            quote(sb, words.get(i));
        }
        return sb.append("],\"ruleset\":").append(rulesetId).append('}').toString();
    }

    /** JSON string literal; non-ASCII letters are kept as-is (the body is sent as UTF-8). */
    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
