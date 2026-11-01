package app.wordfeudwordfinder.extension.hooks;

import app.wordfeudwordfinder.extension.core.WordFinder;
import app.wordfeudwordfinder.extension.game.GameStore;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/** Entry points called from the Cronet callback proxy. Never throws. */
public final class NetworkTap {
    private static final ResponseAssembler ASSEMBLER = new ResponseAssembler();

    private NetworkTap() {
    }

    public static void onStarted(Object req, Object info) {
        try {
            ASSEMBLER.started(req, urlOf(info));
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    public static void onRead(Object req, Object info, ByteBuffer b) {
        try {
            ASSEMBLER.read(req, b);
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    public static void onSucceeded(Object req, Object info) {
        try {
            ResponseAssembler.Completed c = ASSEMBLER.succeeded(req);
            if (c == null) return;
            WordFinder wf = WordFinder.get();
            if (wf == null) return;
            GameStore store = wf.gameStore();
            if (c.url.contains("user/games/detail/")) store.onGamesDetail(c.body);
            else if (c.url.contains("/move/")) store.onMoveResponse(c.body);
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    public static void onDropped(Object req) {
        try {
            ASSEMBLER.dropped(req);
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    private static String urlOf(Object info) throws Exception {
        if (info == null) return null;
        Method m = info.getClass().getMethod("getUrl");
        try {
            m.setAccessible(true);
        } catch (Throwable ignored) {
            // public method; accessibility tweak is best effort
        }
        Object u = m.invoke(info);
        return u instanceof String ? (String) u : null;
    }
}
