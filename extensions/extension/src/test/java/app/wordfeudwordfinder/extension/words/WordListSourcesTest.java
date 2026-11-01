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
    public void onlyApprovedDutchEntryPresent() {
        for (int id = 0; id <= MAX_ID; id++) {
            if (id == 2) assertNotNull(WordListSources.get(id));
            else assertNull("id " + id, WordListSources.get(id));
        }
        WordListSources.Source nl = WordListSources.get(2);
        assertEquals("414075805ca9b76fba3cbdaab7dd5ff7cbf1f851c3580b06f1001e2fca97b287", nl.sha256);
        assertEquals(1, nl.version);
        assertEquals("UTF-8", nl.charset);
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
