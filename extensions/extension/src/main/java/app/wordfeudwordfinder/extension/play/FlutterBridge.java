package app.wordfeudwordfinder.extension.play;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import app.wordfeudwordfinder.extension.hooks.RouteTap;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Android side of {@link Refresher}: sends platform messages to the Flutter engine of Wordfeud's MainActivity and
 * reads the debug opt-in file. Flutter classes are reached by reflection only (the extension cannot link them):
 * {@code activity.getFlutterEngine()} (FlutterActivity / FlutterFragmentActivity, protected, so searched up the class
 * hierarchy; fallback: any field holding the engine or an object with {@code getFlutterEngine()}, such as
 * FlutterFragmentActivity's FlutterFragment), then {@code engine.getDartExecutor()} (a BinaryMessenger), then
 * {@code send(String, ByteBuffer)}. Flutter's own ProGuard rules keep {@code io.flutter.**}, so these names survive.
 *
 * <p>Messages are sent on the main (platform) thread, as Flutter requires, in direct ByteBuffers whose position is
 * the message size (DartMessenger passes {@code message.position()} as the length to FlutterJNI).
 *
 * <p>Debug: the file {@code debug_refresh_order.txt} in {@code <filesDir>/wfwf/} or
 * {@code <externalFilesDir>/wfwf/} is the opt-in (there is no host BuildConfig to read). Only while it exists does
 * anything get logged (tag {@value #TAG}), and only strategy and channel names, never message contents.
 */
public final class FlutterBridge implements Refresher.Env {
    static final String TAG = "WFWF-Refresh";

    private static volatile Handler main;

    private final WeakReference<Activity> activity;
    private final List<File> debugFiles;

    private FlutterBridge(Activity a, List<File> debugFiles) {
        this.activity = new WeakReference<>(a);
        this.debugFiles = debugFiles;
    }

    /** Main thread, once the activity's onCreate has run. Never throws. */
    public static void install(Activity a) {
        try {
            List<File> files = new ArrayList<>();
            files.add(new File(new File(a.getFilesDir(), "wfwf"), RefreshOrder.DEBUG_FILE));
            try {
                File ext = a.getExternalFilesDir(null);
                if (ext != null) files.add(new File(new File(ext, "wfwf"), RefreshOrder.DEBUG_FILE));
            } catch (Throwable ignored) {
                // no external storage: internal file only
            }
            FlutterBridge b = new FlutterBridge(a, files);
            Refresher.install(b);
            if (b.debugOrder() != null) {
                Log.d(TAG, "debug file present: logging channel names");
                RouteTap.setChannelLogger(channel -> Log.d(TAG, "channel seen: " + channel));
            } else {
                RouteTap.setChannelLogger(null);
            }
        } catch (Throwable ignored) {
            // refreshing stays off: Wordfeud refreshes itself
        }
    }

    @Override
    public String debugOrder() {
        return RefreshOrder.readDebugFile(debugFiles);
    }

    @Override
    public void debugLog(String line) {
        Log.d(TAG, line);
    }

    @Override
    public Refresher.Messenger messenger(Refresher.DebugLog log) {
        return (channel, message, delayMs) -> send(channel, message, delayMs, log);
    }

    private boolean send(String channel, byte[] message, long delayMs, Refresher.DebugLog log) {
        Activity a = activity.get();
        if (a == null || a.isFinishing() || a.isDestroyed()) {
            if (log != null) log.d("no activity for " + channel);
            return false;
        }
        Object messenger;
        try {
            messenger = findMessenger(a);
        } catch (Throwable t) {
            messenger = null;
        }
        if (messenger == null) {
            if (log != null) log.d("no Flutter engine for " + channel);
            return false;
        }
        Runnable r = () -> {
            try {
                Activity now = activity.get();
                if (now == null || now.isDestroyed()) return;
                Object m = findMessenger(now);
                if (m == null) return;
                sendNow(m, channel, message);
                if (log != null) log.d("sent " + channel + " (" + message.length + " bytes)");
            } catch (Throwable t) {
                if (log != null) log.d("send failed on " + channel + ": " + t.getClass().getSimpleName());
            }
        };
        if (log != null) log.d("schedule " + channel + " in " + delayMs + " ms");
        return main().postDelayed(r, Math.max(0L, delayMs));
    }

    private static Handler main() {
        Handler h = main;
        if (h == null) {
            h = new Handler(Looper.getMainLooper());
            main = h;
        }
        return h;
    }

    /** The engine's DartExecutor (a BinaryMessenger), or null. */
    static Object findMessenger(Activity a) throws Exception {
        Object engine = call(a, "getFlutterEngine");
        if (engine == null) engine = engineFromFields(a);
        if (engine == null) return null;
        return call(engine, "getDartExecutor");
    }

    private static Object engineFromFields(Object owner) throws Exception {
        for (Class<?> c = owner.getClass(); c != null && c != Activity.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object v = f.get(owner);
                if (v == null) continue;
                if (v.getClass().getName().equals("io.flutter.embedding.engine.FlutterEngine")) return v;
                Object e = call(v, "getFlutterEngine");
                if (e != null) return e;
            }
        }
        return null;
    }

    /** Calls a no-argument method found anywhere up the class hierarchy; null when absent or when it returns null. */
    private static Object call(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // look in the super class
            }
        }
        return null;
    }

    private static void sendNow(Object messenger, String channel, byte[] message) throws Exception {
        ByteBuffer buf = ByteBuffer.allocateDirect(message.length);
        buf.put(message); // position == size, as Flutter's codecs leave it
        Method send = messenger.getClass().getMethod("send", String.class, ByteBuffer.class);
        send.invoke(messenger, channel, buf);
    }
}
