package app.wordfeudwordfinder.extension.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.wordfeudwordfinder.extension.core.WfError;
import java.time.Instant;
import org.junit.Test;

public class ErrorDetailsTest {
    @Test
    public void format() {
        String s = ErrorDetails.format(WfError.wlNet(), "5.7.1", "1.0.0", 2,
                Instant.parse("2026-11-01T12:34:56Z"));
        assertEquals("code=WL-NET\nwordfeud=5.7.1\npatch=1.0.0\nruleset=2\ntime=2026-11-01T12:34:56Z", s);
    }

    @Test
    public void formatKeepsMillisAndFullCode() {
        String s = ErrorDetails.format(WfError.mvServer("not_your_turn"), "5.7.1", "dev", 14,
                Instant.parse("2026-01-02T03:04:05.678Z"));
        assertEquals("code=MV-SERVER-not_your_turn\nwordfeud=5.7.1\npatch=dev\nruleset=14\n"
                + "time=2026-01-02T03:04:05.678Z", s);
    }

    @Test
    public void unknownValues() {
        String s = ErrorDetails.format(WfError.gsData("secret detail"), null, "", -1,
                Instant.parse("2026-11-01T00:00:00Z"));
        assertEquals("code=GS-DATA\nwordfeud=unknown\npatch=unknown\nruleset=unknown\n"
                + "time=2026-11-01T00:00:00Z", s);
    }

    @Test
    public void millisOverloadMatchesInstantText() {
        long[] samples = {0L, 1L, 999L, 1_000L, 1_793_449_296_000L, 1_793_449_296_005L, 1_767_323_045_678L,
                951_782_400_123L /* 2000-02-29 */};
        for (long ms : samples) {
            assertEquals(Long.toString(ms), Instant.ofEpochMilli(ms).toString(), ErrorDetails.isoUtc(ms));
            assertEquals(ErrorDetails.format(WfError.wlHash(), "a", "b", 1, Instant.ofEpochMilli(ms)),
                    ErrorDetails.format(WfError.wlHash(), "a", "b", 1, ms));
        }
    }

    @Test
    public void retryOnlyForWordListErrorsThatCanSucceed() {
        assertTrue(ErrorDetails.retryable(WfError.wlNet()));
        assertTrue(ErrorDetails.retryable(WfError.wlHttp(503)));
        assertTrue(ErrorDetails.retryable(WfError.wlHash()));
        assertTrue(ErrorDetails.retryable(WfError.wlDisk()));
        assertFalse(ErrorDetails.retryable(WfError.wlNone()));
        assertFalse(ErrorDetails.retryable(WfError.gsData("x")));
        assertFalse(ErrorDetails.retryable(WfError.gsEvent("swap")));
        assertFalse(ErrorDetails.retryable(WfError.mvSession()));
        assertFalse(ErrorDetails.retryable(WfError.mvNet()));
        assertFalse(ErrorDetails.retryable(WfError.mvServer("x")));
        assertFalse(ErrorDetails.retryable(null));
    }
}
