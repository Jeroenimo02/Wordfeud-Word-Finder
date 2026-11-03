package app.wordfeudwordfinder.extension.words;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import org.junit.Test;

public class WordListSourcesTest {

    private static final int MAX_ID = 12;

    @Test
    public void allEntriesPinned() {
        for (int id = 0; id <= MAX_ID; id++) {
            WordListSources.Source s = WordListSources.get(id);
            if (s == null) continue;
            assertTrue("id " + id, s.url.startsWith("https://"));
            assertTrue("id " + id + " not pinned: " + s.url, s.url.matches(".*/[0-9a-f]{40}/.*"));
            assertTrue("id " + id, !s.url.contains("/main/") && !s.url.contains("/master/"));
            assertTrue("id " + id + " sha256", s.sha256.matches("[0-9a-f]{64}"));
            assertTrue("id " + id + " version", s.version >= 1);
            assertNotNull(s.charset);
        }
    }

    @Test
    public void openListsPerRuleset() {
        // 0/5 English (ENABLE), 1 bokmål, 2 Dutch, 4/8 Swedish, 9 German, 10 nynorsk (wordlists/sources.json).
        java.util.Set<Integer> present = new java.util.HashSet<>(java.util.Arrays.asList(0, 1, 2, 4, 5, 8, 9, 10));
        for (int id = 0; id <= MAX_ID; id++) {
            if (present.contains(id)) assertNotNull("id " + id, WordListSources.get(id));
            else assertNull("id " + id, WordListSources.get(id));
        }
        WordListSources.Source nl = WordListSources.get(2);
        assertTrue(nl.url, nl.url.endsWith("/wordlists/nl/words.txt.gz"));
        assertTrue(nl.gzip());
        assertEquals("UTF-8", nl.charset);
        assertEquals(WordListSources.get(0).sha256, WordListSources.get(5).sha256);
        assertEquals(WordListSources.get(4).sha256, WordListSources.get(8).sha256);
    }

    @Test
    public void urlPointsAtThisRepositoryAndBuildCommit() {
        WordListSources.Source nl = WordListSources.get(2);
        assertEquals("https://raw.githubusercontent.com/" + WordListManifest.REPOSITORY + "/" + WordListManifest.COMMIT
                + "/wordlists/nl/words.txt.gz", nl.url);
    }

    @Test
    public void allHostsAreRawGithubusercontent() {
        for (int id = 0; id <= MAX_ID; id++) {
            WordListSources.Source s = WordListSources.get(id);
            if (s == null) continue;
            assertEquals("id " + id, "raw.githubusercontent.com", URI.create(s.url).getHost());
        }
    }
}
