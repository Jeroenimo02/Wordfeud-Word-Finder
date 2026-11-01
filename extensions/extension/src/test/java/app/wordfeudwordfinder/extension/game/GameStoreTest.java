package app.wordfeudwordfinder.extension.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import app.wordfeudwordfinder.extension.core.Storage;
import app.wordfeudwordfinder.extension.core.WfError;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GameStoreTest {
    private static final long GAME = 4391319402L;
    private static final long OTHER = 4391325091L;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static String fixture(String n) { return GameReplayTest.fixture(n); }

    private String withoutRulesetsAndBoards() throws Exception {
        JSONObject root = new JSONObject(fixture("detail_neigen.json"));
        root.getJSONObject("content").remove("rulesets");
        root.getJSONObject("content").remove("boards");
        return root.toString();
    }

    @Test
    public void storesGamesAndCaches() throws Exception {
        Storage s = new Storage(tmp.getRoot());
        GameStore store = new GameStore(s);
        store.onGamesDetail(fixture("detail_neigen.json"));
        GameState st = store.state(GAME);
        assertNotNull(st);
        assertNull(st.unreadable());
        assertTrue(s.readText("cache/rulesets.json").contains("\"ruleset\":2"));
        assertNotNull(s.readText("cache/boards.json"));
        assertNotNull(store.ruleset(2));
        assertNotNull(store.board(0));
        assertNotNull(store.game(GAME));
        assertTrue(store.seenRulesets().contains(2));
        assertNull(store.state(1L));
    }

    @Test
    public void usesCachedBoardWhenOmitted() throws Exception {
        new GameStore(new Storage(tmp.getRoot())).onGamesDetail(fixture("detail_neigen.json"));
        GameStore second = new GameStore(new Storage(tmp.getRoot()));
        second.onGamesDetail(withoutRulesetsAndBoards());
        assertNull(second.state(GAME).unreadable());
    }

    @Test
    public void missingRulesetOrBoardGivesGsData() throws Exception {
        GameStore store = new GameStore(new Storage(tmp.getRoot()));
        store.onGamesDetail(withoutRulesetsAndBoards());
        GameState st = store.state(GAME);
        assertNotNull(st);
        assertEquals("GS-DATA", st.unreadable().code());
    }

    @Test
    public void corruptCacheIsTreatedAsEmptyAndMergeKeepsOldEntries() throws Exception {
        Storage s = new Storage(tmp.getRoot());
        s.writeTextAtomic("cache/rulesets.json", "{not json");
        s.writeTextAtomic("cache/boards.json", "[1,2");
        GameStore store = new GameStore(s);
        assertNull(store.ruleset(2));
        store.onGamesDetail(fixture("detail_neigen.json"));
        assertNull(store.state(GAME).unreadable());
        // merge: a later response with only another ruleset keeps ruleset 2 on disk
        JSONObject root = new JSONObject(fixture("detail_neigen.json"));
        JSONObject c = root.getJSONObject("content");
        JSONObject r = new JSONObject(c.getJSONArray("rulesets").getJSONObject(0).toString());
        r.put("ruleset", 9);
        c.getJSONArray("rulesets").put(0, r);
        store.onGamesDetail(root.toString());
        GameStore again = new GameStore(s);
        assertNotNull(again.ruleset(9));
        assertNotNull(again.ruleset(2));
    }

    @Test
    public void moveResponseReplacesGame() throws Exception {
        GameStore store = new GameStore(new Storage(tmp.getRoot()));
        store.onGamesDetail(fixture("detail_neigen.json"));
        int before = countTiles(store.state(GAME));
        store.onMoveResponse(fixture("move_hoort.json"));
        GameState after = store.state(GAME);
        assertNull(after.unreadable());
        assertFalse(after.myTurn());
        assertTrue(countTiles(after) > before);
    }

    @Test
    public void moveResponseWorksWithoutDetail() throws Exception {
        // ruleset/board come from the disk cache of an earlier session
        new GameStore(new Storage(tmp.getRoot())).onGamesDetail(fixture("detail_neigen.json"));
        GameStore fresh = new GameStore(new Storage(tmp.getRoot()));
        fresh.onMoveResponse(fixture("move_hoort.json"));
        assertNull(fresh.state(GAME).unreadable());
    }

    @Test
    public void moveErrorThrowsAndLeavesStoreUnchanged() throws Exception {
        GameStore store = new GameStore(new Storage(tmp.getRoot()));
        store.onGamesDetail(fixture("detail_neigen.json"));
        GameState before = store.state(GAME);
        final AtomicInteger calls = new AtomicInteger();
        store.addListener(new Runnable() { public void run() { calls.incrementAndGet(); } });
        try {
            store.onMoveResponse("{\"status\":\"error\",\"content\":{\"type\":\"illegal_word\"}}");
            fail();
        } catch (WfError e) {
            assertEquals("MV-SERVER-illegal_word", e.code());
        }
        assertTrue(before == store.state(GAME));
        assertEquals(0, calls.get());
    }

    @Test
    public void listenerCalled() throws Exception {
        GameStore store = new GameStore(new Storage(tmp.getRoot()));
        final AtomicInteger calls = new AtomicInteger();
        store.addListener(new Runnable() { public void run() { calls.incrementAndGet(); } });
        store.addListener(new Runnable() { public void run() { throw new IllegalStateException("boom"); } });
        store.onGamesDetail(fixture("detail_neigen.json"));
        assertEquals(1, calls.get());
        store.onMoveResponse(fixture("move_hoort.json"));
        assertEquals(2, calls.get());
    }

    @Test
    public void listenerMayCallBackIntoStore() throws Exception {
        final GameStore store = new GameStore(new Storage(tmp.getRoot()));
        final AtomicInteger seen = new AtomicInteger();
        store.addListener(new Runnable() {
            public void run() {
                Thread t = new Thread(new Runnable() { public void run() { seen.set(store.seenRulesets().size()); } });
                t.start();
                try { t.join(2000); } catch (InterruptedException e) { /* ignore */ }
            }
        });
        store.onGamesDetail(fixture("detail_neigen.json")); // would deadlock if invoked under the monitor
        assertEquals(1, seen.get());
    }

    @Test
    public void malformedGameDoesNotSpoilOthers() throws Exception {
        JSONObject root = new JSONObject(fixture("detail_neigen.json"));
        root.getJSONObject("content").getJSONArray("games").getJSONObject(1).remove("board");
        GameStore store = new GameStore(new Storage(tmp.getRoot()));
        store.onGamesDetail(root.toString());
        assertNull(store.state(GAME).unreadable());
        GameState bad = store.state(OTHER);
        assertNotNull(bad);
        assertEquals("GS-DATA", bad.unreadable().code());
        assertNull(store.game(OTHER));
    }

    @Test
    public void detailGenerationCountsOnlyProcessedGamesDetail() throws Exception {
        GameStore store = new GameStore(new Storage(tmp.getRoot()));
        assertEquals(0, store.detailGeneration());
        store.onGamesDetail(fixture("detail_neigen.json"));
        assertEquals(1, store.detailGeneration());
        store.onMoveResponse(fixture("move_hoort.json"));
        assertEquals("a move response is not a games/detail", 1, store.detailGeneration());
        store.onGamesDetail("not json");
        assertEquals("an unparseable response is not processed", 1, store.detailGeneration());
        final long[] seen = {-1};
        store.addListener(new Runnable() { public void run() { seen[0] = store.detailGeneration(); } });
        store.onGamesDetail(fixture("detail_neigen.json"));
        assertEquals(2, store.detailGeneration());
        assertEquals("listeners already see the new count", 2, seen[0]);
    }

    @Test
    public void replayRuntimeExceptionMakesGameUnreadable() throws Exception {
        GameStore store = new GameStore(new Storage(tmp.getRoot()), g -> {
            throw new IllegalStateException("boom");
        });
        final int[] notified = {0};
        store.addListener(() -> notified[0]++);
        store.onGamesDetail(fixture("detail_neigen.json"));
        GameState st = store.state(GAME);
        assertNotNull(st);
        assertNotNull(st.unreadable());
        assertEquals("GS-DATA", st.unreadable().code());
        assertEquals(1, notified[0]);
        assertEquals(1, store.detailGeneration());
        assertNotNull("raw game still stored", store.game(GAME));

        store.onMoveResponse(fixture("move_hoort.json"));
        assertEquals("GS-DATA", store.state(GAME).unreadable().code());
        assertEquals(2, notified[0]);
        assertEquals("a move does not count as a detail", 1, store.detailGeneration());
    }

    private static int countTiles(GameState s) {
        int n = 0;
        for (char[] row : s.board()) for (char c : row) if (c != '\0') n++;
        return n;
    }
}
