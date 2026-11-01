package app.wordfeudwordfinder.extension.words;

import app.wordfeudwordfinder.extension.core.Storage;
import app.wordfeudwordfinder.extension.core.WfError;
import app.wordfeudwordfinder.extension.game.Ruleset;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads, verifies, converts and stores word lists (spec section 6.2), and hands out the loaded graphs.
 *
 * <p>Files: {@code wordlists/<id>-v<version>.graph}. A ruleset has a valid local list when the file for the
 * version in the sources table exists; a lower version is re-downloaded and other versions are deleted after a
 * successful download.
 *
 * <p>Attempts: {@link #onRulesetsSeen} starts at most one background attempt per ruleset per session (a failure is
 * not retried automatically); {@link #ensureForOverlay} always makes a new attempt unless a download for that
 * ruleset is already running, in which case its callback is attached to that download. At most one download per
 * ruleset runs at a time.
 *
 * <p>Threading: all disk and network work runs on the injected executor; {@link Callback} methods are invoked on
 * the executor thread (the UI layer posts them to the main thread). Exceptions thrown by callbacks are swallowed.
 * {@link #getOrNull} may read from disk and should not be called on the main thread.
 */
public final class WordListManager {

    /** Result of {@link #ensureForOverlay}: any number of progress calls, then exactly one onReady or onError. */
    public interface Callback {
        /** Best-effort progress, 0..100, non-decreasing. */
        void onProgress(int pct);

        void onReady(WordGraph g);

        void onError(WfError e);
    }

    static final String DIR = "wordlists";
    private static final Pattern FILE = Pattern.compile("(\\d+)-v(\\d+)\\.graph(\\.tmp)?");

    // Progress stages (percent). The download itself fills 0..DOWNLOADED when the length is known.
    private static final int DOWNLOADED = 80;
    private static final int VERIFIED = 85;
    private static final int NORMALIZED = 90;
    private static final int BUILT = 95;

    private final Storage storage;
    private final HttpFetcher fetcher;
    private final Executor executor;
    private final Function<Integer, WordListSources.Source> sources;

    // Guarded by this.
    private final Map<Integer, Loaded> loaded = new HashMap<>();
    private final Set<Integer> backgroundTried = new HashSet<>();
    private final Map<Integer, Job> running = new HashMap<>();

    private static final class Loaded {
        final int version;
        final WordGraph graph;

        Loaded(int version, WordGraph graph) {
            this.version = version;
            this.graph = graph;
        }
    }

    /** One running download; callbacks attach to it. Fields guarded by the manager's monitor. */
    private static final class Job {
        final List<Callback> callbacks = new ArrayList<>();
        int lastPct = -1;
    }

    public WordListManager(Storage storage, HttpFetcher fetcher, Executor executor) {
        this(storage, fetcher, executor, WordListSources::get);
    }

    /** With an injectable sources table (tests). */
    public WordListManager(Storage storage, HttpFetcher fetcher, Executor executor,
                           Function<Integer, WordListSources.Source> sources) {
        this.storage = storage;
        this.fetcher = fetcher;
        this.executor = executor;
        this.sources = sources;
    }

    /**
     * Starts a background download for every ruleset in {@code ids} that has a source, has no valid local list,
     * and has not had a background attempt in this session yet. Cheap: does no IO on the calling thread.
     */
    public void onRulesetsSeen(Set<Integer> ids, Function<Integer, Ruleset> rulesets) {
        if (ids == null) return;
        for (Integer id : ids) {
            if (id == null) continue;
            WordListSources.Source src = sources.apply(id);
            Job job;
            synchronized (this) {
                if (!backgroundTried.add(id)) continue;
                if (src == null || running.containsKey(id)) continue;
                Loaded l = loaded.get(id);
                if (l != null && l.version == src.version) continue;
                job = new Job();
                running.put(id, job);
            }
            Ruleset rs = rulesets == null ? null : rulesets.apply(id);
            submit(id, rs, src, job);
        }
    }

    /**
     * Makes the list for {@code id} available for the overlay: reports onReady with the graph (loaded from memory
     * or disk, or freshly downloaded), or onError with WL-NONE / WL-NET / WL-HTTP-n / WL-HASH / WL-DISK, or
     * GS-DATA when the ruleset is unknown so no alphabet is available.
     */
    public void ensureForOverlay(int id, Ruleset rs, Callback cb) {
        WordListSources.Source src = sources.apply(id);
        if (src == null) {
            executor.execute(() -> deliverError(cb, WfError.wlNone()));
            return;
        }
        Job job;
        synchronized (this) {
            Loaded l = loaded.get(id);
            if (l != null && l.version == src.version) {
                WordGraph g = l.graph;
                executor.execute(() -> deliverReady(cb, g));
                return;
            }
            Job existing = running.get(id);
            if (existing != null) {
                existing.callbacks.add(cb);
                return;
            }
            job = new Job();
            job.callbacks.add(cb);
            running.put(id, job);
            backgroundTried.add(id); // an overlay attempt also counts as this session's background attempt
        }
        submit(id, rs, src, job);
    }

    /**
     * The graph for {@code id} when the current version is in memory or on disk, else null. A file that cannot be
     * read is deleted (it will be downloaded again). May read from disk.
     */
    public WordGraph getOrNull(int id) {
        WordListSources.Source src = sources.apply(id);
        if (src == null) return null;
        synchronized (this) {
            Loaded l = loaded.get(id);
            if (l != null && l.version == src.version) return l.graph;
        }
        File f = file(id, src.version);
        if (!f.isFile()) return null;
        WordGraph g;
        try (InputStream in = new FileInputStream(f)) {
            g = WordGraph.read(in, f.length());
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            f.delete();
            return null;
        }
        synchronized (this) {
            Loaded l = loaded.get(id);
            if (l != null && l.version == src.version) return l.graph; // a concurrent load won
            loaded.put(id, new Loaded(src.version, g));
        }
        return g;
    }

    // ------------------------------------------------------------------ job

    private void submit(int id, Ruleset rs, WordListSources.Source src, Job job) {
        try {
            executor.execute(() -> run(id, rs, src, job));
        } catch (RuntimeException e) {
            // e.g. a rejected execution: never leave the job registered
            finish(id, job, null, WfError.wlNet());
        }
    }

    private void run(int id, Ruleset rs, WordListSources.Source src, Job job) {
        WordGraph g = null;
        WfError err = null;
        try {
            if (file(id, src.version).isFile()) {
                g = getOrNull(id); // null when the file was corrupt (and is now deleted)
            }
            if (g == null) g = download(id, rs, src, job);
        } catch (WfError e) {
            err = e;
        } catch (RuntimeException | OutOfMemoryError e) {
            // Unexpected failure while preparing the list locally (e.g. too little memory for the graph).
            err = WfError.wlDisk();
        }
        finish(id, job, g, err);
    }

    private WordGraph download(int id, Ruleset rs, WordListSources.Source src, Job job) throws WfError {
        if (rs == null) throw WfError.gsData("ruleset " + id + " unknown");
        Charset cs;
        try {
            cs = Charset.forName(src.charset);
        } catch (RuntimeException e) {
            throw WfError.wlNone(); // broken table entry: no usable list
        }
        progress(job, 0);
        byte[] raw = fetcher.fetch(src.url, (read, total) -> {
            if (total > 0) progress(job, (int) Math.min(DOWNLOADED, read * DOWNLOADED / total));
        });
        progress(job, DOWNLOADED);
        if (raw == null || !sha256Hex(raw).equals(src.sha256.toLowerCase(Locale.ROOT))) throw WfError.wlHash();
        progress(job, VERIFIED);
        List<String> words;
        try {
            words = WordListNormalizer.normalize(new ByteArrayInputStream(raw), cs, alphabet(rs));
        } catch (IOException e) {
            throw WfError.wlDisk(); // cannot happen for an in-memory stream
        }
        raw = null; // let the download be collected before building
        progress(job, NORMALIZED);
        WordGraph g = WordGraph.build(words);
        words = null;
        progress(job, BUILT);
        store(id, src.version, g);
        synchronized (this) {
            loaded.put(id, new Loaded(src.version, g));
        }
        deleteOtherVersions(id, src.version);
        return g;
    }

    private void finish(int id, Job job, WordGraph g, WfError err) {
        List<Callback> cbs;
        synchronized (this) {
            if (running.get(id) == job) running.remove(id);
            cbs = new ArrayList<>(job.callbacks);
        }
        for (Callback cb : cbs) {
            if (err != null) deliverError(cb, err);
            else deliverReady(cb, g);
        }
    }

    private void progress(Job job, int pct) {
        List<Callback> cbs;
        synchronized (this) {
            if (pct <= job.lastPct) return;
            job.lastPct = pct;
            cbs = new ArrayList<>(job.callbacks);
        }
        for (Callback cb : cbs) {
            try {
                cb.onProgress(pct);
            } catch (RuntimeException ignored) {
                // callbacks never break the download
            }
        }
    }

    private static void deliverReady(Callback cb, WordGraph g) {
        try {
            cb.onReady(g);
        } catch (RuntimeException ignored) {
            // callbacks never break the manager
        }
    }

    private static void deliverError(Callback cb, WfError e) {
        try {
            cb.onError(e);
        } catch (RuntimeException ignored) {
            // callbacks never break the manager
        }
    }

    // ------------------------------------------------------------------ disk

    private File dir() {
        return storage.dir(DIR);
    }

    private File file(int id, int version) {
        return new File(dir(), id + "-v" + version + ".graph");
    }

    /** Writes {@code <file>.tmp} and renames it over the target; on any failure no file is left: WL-DISK. */
    private void store(int id, int version, WordGraph g) throws WfError {
        File target = file(id, version);
        File tmp = new File(target.getPath() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                g.write(out);
                out.getFD().sync();
            }
            if (!tmp.renameTo(target)) {
                target.delete();
                if (!tmp.renameTo(target)) throw new IOException("rename failed");
            }
        } catch (IOException | RuntimeException e) {
            tmp.delete();
            throw WfError.wlDisk();
        }
    }

    /** Deletes the files (and leftover temp files) of every other version of {@code id}. */
    private void deleteOtherVersions(int id, int version) {
        File[] files = dir().listFiles();
        if (files == null) return;
        for (File f : files) {
            Matcher m = FILE.matcher(f.getName());
            if (!m.matches()) continue;
            try {
                if (Integer.parseInt(m.group(1)) != id) continue;
                if (Integer.parseInt(m.group(2)) == version && m.group(3) == null) continue;
            } catch (NumberFormatException e) {
                continue;
            }
            f.delete();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The ruleset's tile letters, upper case, without the blank keys ("?" / "*"). */
    static Set<String> alphabet(Ruleset rs) {
        Set<String> out = new HashSet<>();
        for (String k : rs.getTilePoints().keySet()) {
            if (k == null || k.isEmpty() || "?".equals(k) || "*".equals(k)) continue;
            out.add(k.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private static String sha256Hex(byte[] b) {
        byte[] d;
        try {
            d = MessageDigest.getInstance("SHA-256").digest(b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte x : d) sb.append(hex[(x >> 4) & 15]).append(hex[x & 15]);
        return sb.toString();
    }
}
