package app.wordfeudwordfinder.extension.hooks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Pure decoding helpers for Flutter platform messages. Never throw; return null when not decodable. */
public final class PlatformMessages {
    private static final Pattern GAME_URI = Pattern.compile("^/game/(\\d{1,18})(?:[/?#].*)?$");

    private PlatformMessages() {
    }

    public static Long gameIdFromNavigation(String json) {
        try {
            if (json == null) return null;
            JSONObject args = new JSONObject(json).optJSONObject("args");
            if (args == null) return null;
            Object u = args.opt("uri");
            if (!(u instanceof String)) return null;
            String uri = (String) u;
            Matcher m = GAME_URI.matcher(uri);
            return m.matches() ? Long.valueOf(Long.parseLong(m.group(1))) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Boolean darkFromOverlayStyle(String json) {
        try {
            if (json == null) return null;
            JSONObject args = new JSONObject(json).optJSONObject("args");
            if (args == null) return null;
            Object v = args.opt("statusBarIconBrightness");
            if (!(v instanceof String)) return null;
            String b = (String) v;
            return Boolean.valueOf("Brightness.light".equals(b));
        } catch (Exception e) {
            return null;
        }
    }

    /** StandardMessageCodec list (0x0C) of size 1 holding TRUE (0x01) or FALSE (0x02). */
    public static Boolean bannerVisible(byte[] b) {
        if (b == null || b.length != 3 || b[0] != 0x0C || b[1] != 0x01) return null;
        if (b[2] == 0x01) return Boolean.TRUE;
        if (b[2] == 0x02) return Boolean.FALSE;
        return null;
    }
}
