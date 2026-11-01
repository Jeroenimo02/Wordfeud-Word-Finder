package app.wordfeudwordfinder.extension.core;

import app.wordfeudwordfinder.extension.game.GameStore;
import java.io.File;

/** Lazy, thread-safe singleton wiring all word finder components together. */
public final class WordFinder {
    private static volatile WordFinder instance;
    private static final Object LOCK = new Object();

    private final Storage storage;
    private final GameStore gameStore;

    /** Also used by tests: builds an instance rooted at the given directory without Android. */
    public WordFinder(File root) {
        this.storage = new Storage(root);
        this.gameStore = new GameStore(storage);
    }

    /** Returns the singleton, or null when no application context is available (yet). Never throws. */
    public static WordFinder get() {
        WordFinder w = instance;
        if (w != null) return w;
        synchronized (LOCK) {
            if (instance == null) {
                try {
                    File files = filesDir();
                    if (files != null) instance = new WordFinder(new File(files, "wfwf"));
                } catch (Throwable ignored) {
                    // no context: callers handle null
                }
            }
            return instance;
        }
    }

    private static File filesDir() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object app = at.getMethod("currentApplication").invoke(null);
        if (app == null) return null;
        Object dir = app.getClass().getMethod("getFilesDir").invoke(app);
        return dir instanceof File ? (File) dir : null;
    }

    public Storage storage() {
        return storage;
    }

    public GameStore gameStore() {
        return gameStore;
    }
}
