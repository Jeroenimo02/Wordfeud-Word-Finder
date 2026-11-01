package app.wordfeudwordfinder.extension.hooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PlatformMessagesTest {
    @Test
    public void navigationToGame() {
        String json = "{\"method\":\"routeInformationUpdated\",\"args\":{\"uri\":\"/game/4391319402\",\"state\":null,\"replace\":false}}";
        assertEquals(Long.valueOf(4391319402L), PlatformMessages.gameIdFromNavigation(json));
    }

    @Test
    public void navigationHome() {
        String json = "{\"method\":\"routeInformationUpdated\",\"args\":{\"uri\":\"/\",\"state\":null,\"replace\":false}}";
        assertNull(PlatformMessages.gameIdFromNavigation(json));
        assertNull(PlatformMessages.gameIdFromNavigation("not json"));
        assertNull(PlatformMessages.gameIdFromNavigation(null));
    }

    @Test
    public void overlayStyleDark() {
        String json = "{\"method\":\"SystemChrome.setSystemUIOverlayStyle\",\"args\":{\"statusBarIconBrightness\":\"Brightness.light\"}}";
        assertEquals(Boolean.TRUE, PlatformMessages.darkFromOverlayStyle(json));
        String lightJson = "{\"method\":\"SystemChrome.setSystemUIOverlayStyle\",\"args\":{\"statusBarIconBrightness\":\"Brightness.dark\"}}";
        assertEquals(Boolean.FALSE, PlatformMessages.darkFromOverlayStyle(lightJson));
        assertNull(PlatformMessages.darkFromOverlayStyle("{\"method\":\"x\",\"args\":{}}"));
    }

    @Test
    public void bannerTrueFalse() {
        assertEquals(Boolean.TRUE, PlatformMessages.bannerVisible(new byte[]{0x0C, 0x01, 0x01}));
        assertEquals(Boolean.FALSE, PlatformMessages.bannerVisible(new byte[]{0x0C, 0x01, 0x02}));
        assertNull(PlatformMessages.bannerVisible(new byte[]{}));
        assertNull(PlatformMessages.bannerVisible(null));
        assertNull(PlatformMessages.bannerVisible(new byte[]{0x0C, 0x01, 0x00}));
    }
}
