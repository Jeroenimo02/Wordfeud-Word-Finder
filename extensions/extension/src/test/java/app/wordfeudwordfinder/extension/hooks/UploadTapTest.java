package app.wordfeudwordfinder.extension.hooks;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UploadTapTest {
    private static final String MOVE = "{\"move\": [[7, 7, \"E\"], [8, 7, \"N\", true]],\n \"words\": [\"EEN\"],"
            + " \"ruleset\": 2}";
    private static final String COMPACT = "{\"move\":[[7,7,\"E\"],[8,7,\"N\",true]],\"words\":[\"EEN\"],\"ruleset\":2}";

    private final List<String> toasts = new ArrayList<>();

    @Before
    public void setUp() {
        UploadTap.reset();
        UploadTap.setToaster(toasts::add);
    }

    @After
    public void tearDown() {
        UploadTap.reset();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void diagnosticToastOnForV2() {
        assertTrue(UploadTap.DIAGNOSTIC_TOAST);
    }

    @Test
    public void keepsAMoveBodyCompactAndShowsIt() {
        UploadTap.onUpload((Object) utf8(MOVE));
        assertEquals(COMPACT, UploadTap.lastMove());
        assertEquals(1, toasts.size());
        assertEquals("move: " + COMPACT, toasts.get(0));
    }

    @Test
    public void whitespaceInsideStringsIsKept() {
        assertEquals("{\"move\":[],\"x\":\"a b\\\" c\"}", UploadTap.compact("{ \"move\": [], \"x\": \"a b\\\" c\" }"));
    }

    @Test
    public void ignoresOtherBodies() {
        String[] others = {
            "{\"username\":\"a\",\"password\":\"secret\"}", // other JSON: never kept
            "{\"move\":\"not an array\"}",
            "{\"moves\":[]}",
            "[{\"move\":[]}]",
            "move=[[1,2,\"A\"]]",
            "{\"move\":[[1,2,\"A\"]]", // broken JSON
            "",
        };
        for (String s : others) {
            UploadTap.onUpload((Object) utf8(s));
            UploadTap.onUpload(ByteBuffer.wrap(utf8(s)));
            UploadTap.onUpload(utf8(s), 0, utf8(s).length);
        }
        UploadTap.onUpload(new byte[] {(byte) 0x1f, (byte) 0x8b, 0, 1}); // gzip / binary
        UploadTap.onUpload((Object) null);
        UploadTap.onUpload("{\"move\":[]}"); // a String is not a body the hooks pass
        UploadTap.onUpload(null, 0, 0);
        assertNull(UploadTap.lastMove());
        assertTrue(toasts.isEmpty());
    }

    @Test
    public void otherBodyAfterAMoveKeepsTheMove() {
        UploadTap.onUpload((Object) utf8(MOVE));
        UploadTap.onUpload((Object) utf8("{\"token\":\"abc\"}"));
        assertEquals(COMPACT, UploadTap.lastMove());
    }

    @Test
    public void offsetAndLengthOverload() {
        byte[] padded = utf8("xx" + MOVE + "yy");
        UploadTap.onUpload(padded, 2, utf8(MOVE).length);
        assertEquals(COMPACT, UploadTap.lastMove());
        UploadTap.reset();
        UploadTap.onUpload(padded, 2, padded.length); // out of range: ignored, no exception
        UploadTap.onUpload(padded, -1, 3);
        assertNull(UploadTap.lastMove());
    }

    @Test
    public void bodiesOverTheCapAreIgnored() {
        StringBuilder sb = new StringBuilder("{\"move\":[[7,7,\"E\"]],\"words\":[\"");
        while (sb.length() < UploadTap.MAX_BYTES + 10) sb.append('A');
        String big = sb.append("\"]}").toString();
        UploadTap.onUpload((Object) utf8(big));
        UploadTap.onUpload(ByteBuffer.wrap(utf8(big)));
        assertNull(UploadTap.lastMove()); // only MAX_BYTES copied: does not parse
        assertEquals(UploadTap.MAX_BYTES, UploadTap.copy(ByteBuffer.wrap(utf8(big))).length);
    }

    @Test
    public void byteBufferIsLeftUntouched() {
        byte[] bytes = utf8("ab" + MOVE + "cd");
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        buf.put(bytes);
        buf.position(2).limit(2 + utf8(MOVE).length);
        buf.mark();
        UploadTap.onUpload(buf);
        assertEquals(COMPACT, UploadTap.lastMove());
        assertEquals(2, buf.position());
        assertEquals(2 + utf8(MOVE).length, buf.limit());
        buf.reset(); // mark kept
        assertEquals(2, buf.position());
        assertArrayEquals(utf8(MOVE), UploadTap.copy(buf));
        assertEquals(2, buf.position());

        ByteBuffer ro = ByteBuffer.wrap(utf8(MOVE)).asReadOnlyBuffer();
        UploadTap.reset();
        UploadTap.onUpload(ro);
        assertEquals(COMPACT, UploadTap.lastMove());
        assertEquals(0, ro.position());
    }

    @Test
    public void sameMoveThroughSeveralHooksIsShownOnce() {
        byte[] b = utf8(MOVE);
        UploadTap.onUpload((Object) b); // create(byte[])
        UploadTap.onUpload(b, 0, b.length); // ... which calls create(byte[], int, int)
        assertEquals(1, toasts.size());
        assertTrue(UploadTap.shouldShow("x", 0));
        assertFalse(UploadTap.shouldShow("x", UploadTap.DUPLICATE_MS - 1));
        assertTrue(UploadTap.shouldShow("x", UploadTap.DUPLICATE_MS * 3));
        assertTrue(UploadTap.shouldShow("y", UploadTap.DUPLICATE_MS * 3));
    }

    @Test
    public void noToasterIsFine() {
        UploadTap.setToaster(null);
        UploadTap.onUpload((Object) utf8(MOVE));
        assertEquals(COMPACT, UploadTap.lastMove());
        UploadTap.setToaster(t -> {
            throw new IllegalStateException("toast failed");
        });
        UploadTap.onUpload((Object) utf8(MOVE.replace("EEN", "NEE"))); // must not throw
    }
}
