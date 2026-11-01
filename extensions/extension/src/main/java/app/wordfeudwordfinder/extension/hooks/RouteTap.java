package app.wordfeudwordfinder.extension.hooks;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Observes Flutter platform messages (route, theme, banner state). Thread-safe; never throws. */
public final class RouteTap {
    private static volatile Long gameId;
    private static volatile boolean dark = true;
    private static volatile boolean banner;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private RouteTap() {
    }

    public static void addListener(Runnable r) {
        if (r != null) LISTENERS.add(r);
    }

    public static Long currentGameId() {
        return gameId;
    }

    public static boolean isDark() {
        return dark;
    }

    public static boolean bannerVisible() {
        return banner;
    }

    /** Hook entry point (FlutterJNI.handlePlatformMessage). Does not alter the buffer's position/limit. */
    public static void onPlatformMessage(String channel, ByteBuffer msg) {
        try {
            if (channel == null || msg == null) return;
            boolean changed = false;
            if ("flutter/navigation".equals(channel)) {
                String json = readJson(msg);
                if (json != null && json.contains("routeInformationUpdated")) {
                    Long id = PlatformMessages.gameIdFromNavigation(json);
                    Long old = gameId;
                    gameId = id;
                    changed = old == null ? id != null : !old.equals(id);
                }
            } else if ("flutter/platform".equals(channel)) {
                String json = readJson(msg);
                if (json != null && json.contains("SystemChrome.setSystemUIOverlayStyle")) {
                    Boolean d = PlatformMessages.darkFromOverlayStyle(json);
                    if (d != null && d.booleanValue() != dark) {
                        dark = d.booleanValue();
                        changed = true;
                    }
                }
            } else if (channel.endsWith(".AdApi.setBannerVisible")) {
                Boolean v = PlatformMessages.bannerVisible(readBytes(msg));
                if (v != null && v.booleanValue() != banner) {
                    banner = v.booleanValue();
                    changed = true;
                }
            }
            if (changed) {
                for (Runnable r : LISTENERS) {
                    try {
                        r.run();
                    } catch (Throwable ignored) {
                        // listener failure must not affect the app
                    }
                }
            }
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    private static byte[] readBytes(ByteBuffer msg) {
        ByteBuffer d = msg.duplicate();
        byte[] out = new byte[d.remaining()];
        d.get(out);
        return out;
    }

    private static String readJson(ByteBuffer msg) {
        byte[] b = readBytes(msg);
        return b.length == 0 ? null : new String(b, StandardCharsets.UTF_8);
    }
}
