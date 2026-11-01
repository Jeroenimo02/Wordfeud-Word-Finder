package app.wordfeudwordfinder.extension.play;

import java.util.Collections;

/**
 * Candidate 1 (spec 8.4): Wordfeud's own pigeon signal {@code FlutterProcessLifecycleApi.onForegroundStateChanged}.
 * Sends "background" ([false]) and, {@link #FOREGROUND_DELAY_MS} later, "foreground" ([true]), as when the user
 * switches back to the app, which makes Wordfeud reload its games.
 *
 * <p>Channel name from spec section 8.4. Message shape: a pigeon call encodes its arguments as a
 * StandardMessageCodec list; one bool argument gives {@code 0C 01 01/02}, the same shape RouteTap decodes on
 * Wordfeud's {@code AdApi.setBannerVisible} channel. Not verified on a device (V4).
 */
public final class ForegroundSignal implements Refresher.Strategy {
    public static final String NAME = "ForegroundSignal";
    static final String CHANNEL = "dev.flutter.pigeon.wordfeud.FlutterProcessLifecycleApi.onForegroundStateChanged";
    /** Time between "background" and "foreground", so the two are handled as separate transitions. */
    static final long FOREGROUND_DELAY_MS = 100;

    private final Refresher.Messenger messenger;

    public ForegroundSignal(Refresher.Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public boolean refresh(long gameId) {
        byte[] background = MessageCodecs.standard(Collections.singletonList(Boolean.FALSE));
        byte[] foreground = MessageCodecs.standard(Collections.singletonList(Boolean.TRUE));
        return messenger.send(CHANNEL, background, 0) && messenger.send(CHANNEL, foreground, FOREGROUND_DELAY_MS);
    }
}
