package app.wordfeudwordfinder.extension.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

public class RefreshDiagnosticTest {
    @After
    public void reset() {
        RefreshDiagnostic.clearLast();
    }

    @Test
    public void buildsOneLineFromSteps() {
        List<String> published = new ArrayList<>();
        RefreshDiagnostic d = new RefreshDiagnostic(published::add);
        d.strategy("RouteBounce");
        d.step("activity=MainActivity");
        d.step("engine=via getFlutterEngine OK");
        d.step("dartExecutor OK");
        d.step(RefreshDiagnostic.message("send", "flutter/navigation", 63) + " OK");
        assertNull(RefreshDiagnostic.last());
        d.finish();
        String expected = "RouteBounce: activity=MainActivity | engine=via getFlutterEngine OK | dartExecutor OK"
                + " | send(flutter/navigation,63B) OK";
        assertEquals(expected, RefreshDiagnostic.last());
        assertEquals(1, published.size());
        assertEquals(expected, published.get(0));
        d.step("late"); // ignored after finish
        d.finish();
        assertEquals(1, published.size());
        assertEquals(expected, d.text());
        assertTrue(d.isFinished());
    }

    @Test
    public void jniFallbackText() {
        RefreshDiagnostic d = new RefreshDiagnostic(null);
        d.strategy("RouteBounce");
        d.step("activity=MainActivity");
        d.step("engine: NoSuchMethodException getFlutterEngine on MainActivity");
        d.step("engine fields: none");
        d.step("-> FlutterJNI path");
        d.step("jni=captured, dispatchPlatformMessage via getMethod");
        d.step(RefreshDiagnostic.message("dispatch", "flutter/navigation", 63) + " OK");
        assertEquals("RouteBounce: activity=MainActivity | engine: NoSuchMethodException getFlutterEngine on "
                + "MainActivity | engine fields: none | -> FlutterJNI path | jni=captured, dispatchPlatformMessage "
                + "via getMethod | dispatch(flutter/navigation,63B) OK", d.text());
    }

    @Test
    public void textIsOneLineAndCapped() {
        RefreshDiagnostic d = new RefreshDiagnostic(null);
        d.strategy("A\nB");
        for (int i = 0; i < 100; i++) d.step("step\r\n" + i);
        String t = d.text();
        assertFalse(t.contains("\n") || t.contains("\r"));
        assertEquals(RefreshDiagnostic.MAX_LENGTH, t.length());
    }

    @Test
    public void reasonUnwrapsReflection() {
        Throwable t = new InvocationTargetException(new IllegalStateException("Not attached"));
        assertEquals("IllegalStateException Not attached", RefreshDiagnostic.reason(t));
        assertEquals("NoSuchMethodException", RefreshDiagnostic.reason(new NoSuchMethodException()));
    }

    @Test
    public void failingListenerIsSwallowed() {
        RefreshDiagnostic d = new RefreshDiagnostic(s -> { throw new IllegalStateException(); });
        d.step("x");
        d.finish();
        assertEquals("x", RefreshDiagnostic.last());
    }
}
