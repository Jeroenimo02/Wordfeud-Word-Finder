package app.wordfeudwordfinder.extension.words;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import app.wordfeudwordfinder.extension.core.Storage;
import app.wordfeudwordfinder.extension.core.WfError;
import app.wordfeudwordfinder.extension.game.Ruleset;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WordListManagerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final int ID = 7;
    private static final byte[] LIST = "aap\r\nNoot\nmies\n".getBytes(StandardCharsets.UTF_8);
    private static final Executor SYNC = Runnable::run;

    private Storage storage;
    private final Map<Integer, WordListSources.Source> table = new HashMap<>();
    private final Function<Integer, WordListSources.Source> sources = table::get;

    /** Fetcher that counts calls and either returns fixed bytes or throws. */
    private static final class FakeFetcher implements HttpFetcher {
        final List<String> urls = new ArrayList<>();
        byte[] body;
        WfError error;

        @Override
        public byte[] fetch(String url, ProgressListener progress) throws WfError {
            urls.add(url);
            if (error != null) throw error;
            if (progress != null) {
                progress.onProgress(body.length / 2, body.length);
                progress.onProgress(body.length, body.length);
            }
            return body;
        }
    }

    /** Records callback invocations. */
    private static final class Rec implements WordListManager.Callback {
        final List<Integer> progress = new ArrayList<>();
        WordGraph ready;
        WfError error;
        int calls;

        @Override public void onProgress(int pct) { progress.add(pct); }
        @Override public void onReady(WordGraph g) { ready = g; calls++; }
        @Override public void onError(WfError e) { error = e; calls++; }
    }

    private static String sha256(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static Ruleset ruleset() {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (char c = 'A'; c <= 'Z'; c++) points.put(String.valueOf(c), 1);
        points.put("?", 0);
        return new Ruleset(ID, "nl", points, Collections.emptyMap());
    }

    private static Function<Integer, Ruleset> rulesets() {
        Ruleset rs = ruleset();
        return id -> id == ID ? rs : null;
    }

    private FakeFetcher fetcher;

    @Before
    public void setUp() throws Exception {
        storage = new Storage(tmp.getRoot());
        fetcher = new FakeFetcher();
        fetcher.body = LIST;
        table.put(ID, new WordListSources.Source("https://example.org/nl.txt", sha256(LIST), 2, "UTF-8"));
    }

    private WordListManager manager() {
        return new WordListManager(storage, fetcher, SYNC, sources);
    }

    private File listDir() {
        return new File(tmp.getRoot(), "wordlists");
    }

    private List<String> listFiles() {
        String[] names = listDir().list();
        List<String> out = names == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(names));
        Collections.sort(out);
        return out;
    }

    @Test
    public void backgroundTriesOncePerSession() {
        fetcher.error = WfError.wlNet();
        WordListManager m = manager();
        m.onRulesetsSeen(Collections.singleton(ID), rulesets());
        m.onRulesetsSeen(Collections.singleton(ID), rulesets());
        assertEquals(1, fetcher.urls.size());
        assertEquals("https://example.org/nl.txt", fetcher.urls.get(0));
    }

    @Test
    public void overlayRetriesAndReportsCode() {
        fetcher.error = WfError.wlNet();
        WordListManager m = manager();
        m.onRulesetsSeen(Collections.singleton(ID), rulesets());
        Rec rec = new Rec();
        m.ensureForOverlay(ID, ruleset(), rec);
        assertEquals(2, fetcher.urls.size());
        assertEquals(1, rec.calls);
        assertNotNull(rec.error);
        assertEquals("WL-NET", rec.error.code());
    }

    @Test
    public void hashMismatch() {
        fetcher.body = "something else\n".getBytes(StandardCharsets.UTF_8);
        Rec rec = new Rec();
        manager().ensureForOverlay(ID, ruleset(), rec);
        assertEquals("WL-HASH", rec.error.code());
        assertNull(rec.ready);
        assertTrue(listFiles().isEmpty());
    }

    @Test
    public void newVersionRedownloads() throws Exception {
        File old = new File(listDir(), ID + "-v1.graph");
        listDir().mkdirs();
        try (OutputStream out = new FileOutputStream(old)) {
            WordGraph.build(Arrays.asList("OUD")).write(out);
        }
        Rec rec = new Rec();
        WordListManager m = manager();
        m.ensureForOverlay(ID, ruleset(), rec);
        assertEquals(1, fetcher.urls.size());
        assertNotNull(rec.ready);
        assertTrue(rec.ready.contains("NOOT"));
        assertFalse(rec.ready.contains("OUD"));
        assertEquals(Arrays.asList(ID + "-v2.graph"), listFiles());
    }

    @Test
    public void unknownRulesetIsNone() {
        Rec rec = new Rec();
        WordListManager m = manager();
        m.onRulesetsSeen(Collections.singleton(99), rulesets());
        m.ensureForOverlay(99, ruleset(), rec);
        assertEquals(0, fetcher.urls.size());
        assertEquals("WL-NONE", rec.error.code());
        assertNull(m.getOrNull(99));
    }

    @Test
    public void downloadStoresGraphAndReportsProgress() {
        Rec rec = new Rec();
        WordListManager m = manager();
        m.ensureForOverlay(ID, ruleset(), rec);
        assertNull(rec.error);
        assertTrue(rec.ready.contains("AAP"));
        assertTrue(rec.ready.contains("MIES"));
        assertFalse(rec.ready.contains("aap"));
        assertEquals(Arrays.asList(ID + "-v2.graph"), listFiles());
        assertFalse(rec.progress.isEmpty());
        for (int i = 1; i < rec.progress.size(); i++) {
            assertTrue(rec.progress.get(i) >= rec.progress.get(i - 1));
        }
        assertTrue(rec.progress.get(rec.progress.size() - 1) <= 100);
        assertSame(rec.ready, m.getOrNull(ID));
    }

    @Test
    public void currentFileIsLoadedFromDiskWithoutFetch() {
        manager().ensureForOverlay(ID, ruleset(), new Rec());
        WordListManager fresh = manager();
        WordGraph g = fresh.getOrNull(ID);
        assertNotNull(g);
        assertTrue(g.contains("NOOT"));
        Rec rec = new Rec();
        fresh.ensureForOverlay(ID, ruleset(), rec);
        fresh.onRulesetsSeen(Collections.singleton(ID), rulesets());
        assertSame(g, rec.ready);
        assertEquals(1, fetcher.urls.size());
    }

    @Test
    public void backgroundSkipsWhenCurrentFileExists() {
        manager().ensureForOverlay(ID, ruleset(), new Rec());
        manager().onRulesetsSeen(Collections.singleton(ID), rulesets());
        assertEquals(1, fetcher.urls.size());
    }

    @Test
    public void corruptFileIsDeletedAndRedownloadedByOverlay() throws IOException {
        listDir().mkdirs();
        File f = new File(listDir(), ID + "-v2.graph");
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(new byte[] {1, 2, 3, 4, 5});
        }
        WordListManager m = manager();
        assertNull(m.getOrNull(ID));
        assertFalse(f.exists());
        Rec rec = new Rec();
        m.ensureForOverlay(ID, ruleset(), rec);
        assertEquals(1, fetcher.urls.size());
        assertTrue(rec.ready.contains("AAP"));
    }

    @Test
    public void overlayAttachesToRunningDownload() {
        List<Runnable> queued = new ArrayList<>();
        WordListManager m = new WordListManager(storage, fetcher, queued::add, sources);
        m.onRulesetsSeen(Collections.singleton(ID), rulesets());
        Rec rec = new Rec();
        m.ensureForOverlay(ID, ruleset(), rec);
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(1, fetcher.urls.size());
        assertEquals(1, rec.calls);
        assertTrue(rec.ready.contains("NOOT"));
    }

    @Test
    public void diskFailureIsWlDisk() throws IOException {
        // A plain file where the directory should be makes every write fail.
        assertTrue(listDir().createNewFile());
        Rec rec = new Rec();
        manager().ensureForOverlay(ID, ruleset(), rec);
        assertEquals("WL-DISK", rec.error.code());
    }

    @Test
    public void missingRulesetIsGsData() {
        Rec rec = new Rec();
        manager().ensureForOverlay(ID, null, rec);
        assertEquals("GS-DATA", rec.error.code());
        assertEquals(0, fetcher.urls.size());
    }

    @Test
    public void callbackExceptionsAreSwallowed() {
        WordListManager m = manager();
        m.ensureForOverlay(ID, ruleset(), new WordListManager.Callback() {
            @Override public void onProgress(int pct) { throw new RuntimeException("boom"); }
            @Override public void onReady(WordGraph g) { throw new RuntimeException("boom"); }
            @Override public void onError(WfError e) { throw new RuntimeException("boom"); }
        });
        assertNotNull(m.getOrNull(ID));
    }

    @Test
    public void defaultTableHasNoEntriesYet() {
        assertNull(WordListSources.get(1));
        assertNull(WordListSources.get(ID));
    }
}
