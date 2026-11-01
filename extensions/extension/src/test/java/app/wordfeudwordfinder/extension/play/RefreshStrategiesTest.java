package app.wordfeudwordfinder.extension.play;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public class RefreshStrategiesTest {
    static final class Sent {
        final String channel;
        final byte[] message;
        final long delay;

        Sent(String channel, byte[] message, long delay) {
            this.channel = channel;
            this.message = message;
            this.delay = delay;
        }
    }

    static final class FakeMessenger implements Refresher.Messenger {
        final List<Sent> sent = new ArrayList<>();
        boolean available = true;

        final List<Integer> calls = new ArrayList<>();

        /** Records message i of a sequence with delay i * gap (when the bridge would send it). */
        @Override
        public boolean send(String channel, List<byte[]> messages, long gapMs) {
            if (!available) return false;
            calls.add(messages.size());
            for (int i = 0; i < messages.size(); i++) sent.add(new Sent(channel, messages.get(i), i * gapMs));
            return true;
        }
    }

    @Test
    public void foregroundSignalSendsBackgroundThenForeground() throws Exception {
        FakeMessenger m = new FakeMessenger();
        assertTrue(new ForegroundSignal(m).refresh(42L));
        assertEquals(java.util.Collections.singletonList(2), m.calls); // one sequence: [true] always follows [false]
        assertEquals(2, m.sent.size());
        String ch = "dev.flutter.pigeon.wordfeud.FlutterProcessLifecycleApi.onForegroundStateChanged";
        assertEquals(ch, m.sent.get(0).channel);
        assertEquals(ch, m.sent.get(1).channel);
        assertArrayEquals(new byte[] {0x0C, 0x01, 0x02}, m.sent.get(0).message); // [false]
        assertArrayEquals(new byte[] {0x0C, 0x01, 0x01}, m.sent.get(1).message); // [true]
        assertEquals(0L, m.sent.get(0).delay);
        assertEquals(ForegroundSignal.FOREGROUND_DELAY_MS, m.sent.get(1).delay);
    }

    @Test
    public void routeBounceGoesHomeThenBackToTheGame() throws Exception {
        FakeMessenger m = new FakeMessenger();
        assertTrue(new RouteBounce(m).refresh(4391L));
        assertEquals(java.util.Collections.singletonList(2), m.calls); // "/" is always followed by the game
        assertEquals(2, m.sent.size());
        assertEquals("flutter/navigation", m.sent.get(0).channel);
        assertEquals("flutter/navigation", m.sent.get(1).channel);
        JSONObject home = new JSONObject(new String(m.sent.get(0).message, StandardCharsets.UTF_8));
        JSONObject game = new JSONObject(new String(m.sent.get(1).message, StandardCharsets.UTF_8));
        assertEquals("pushRouteInformation", home.getString("method"));
        assertEquals("/", home.getJSONObject("args").getString("location"));
        assertEquals("/", home.getJSONObject("args").getString("uri"));
        assertTrue(home.getJSONObject("args").isNull("state"));
        assertEquals("pushRouteInformation", game.getString("method"));
        assertEquals("/game/4391", game.getJSONObject("args").getString("location"));
        assertEquals("/game/4391", game.getJSONObject("args").getString("uri"));
        assertEquals(0L, m.sent.get(0).delay);
        assertEquals(RouteBounce.BOUNCE_DELAY_MS, m.sent.get(1).delay);
        assertTrue(RouteBounce.BOUNCE_DELAY_MS >= 50 && RouteBounce.BOUNCE_DELAY_MS <= 100);
    }

    @Test
    public void fcmNudgeIsAMessagingOnMessageCall() throws Exception {
        FakeMessenger m = new FakeMessenger();
        assertTrue(new FcmNudge(m, () -> 1000L).refresh(77L));
        assertEquals(1, m.sent.size());
        assertEquals("plugins.flutter.io/firebase_messaging", m.sent.get(0).channel);
        byte[] msg = m.sent.get(0).message;
        byte[] method = MessageCodecs.standard("Messaging#onMessage");
        byte[] head = java.util.Arrays.copyOf(msg, method.length);
        assertArrayEquals(method, head);
        assertEquals(0x0D, msg[method.length]); // arguments: a map (RemoteMessage)
        String text = new String(msg, StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("data"));
        assertTrue(text.contains("game_id"));
        assertTrue(text.contains("77"));
        assertTrue(text.contains("sentTime"));
    }

    @Test
    public void strategiesHaveTheirNames() {
        FakeMessenger m = new FakeMessenger();
        assertEquals("ForegroundSignal", new ForegroundSignal(m).name());
        assertEquals("RouteBounce", new RouteBounce(m).name());
        assertEquals("FcmNudge", new FcmNudge(m).name());
    }

    @Test
    public void strategiesReportFalseWithoutAnEngine() throws Exception {
        FakeMessenger m = new FakeMessenger();
        m.available = false;
        assertFalse(new ForegroundSignal(m).refresh(1L));
        assertFalse(new RouteBounce(m).refresh(1L));
        assertFalse(new FcmNudge(m, () -> 0L).refresh(1L));
    }
}
