package app.wordfeudwordfinder.extension.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import java.lang.ref.WeakReference;

/**
 * Samples a few pixels of Wordfeud's Flutter surface (main thread, only while the button may show) to see whether a
 * menu or dialog scrim covers the screen, and which board colours the app uses. Our own views are not in the Flutter
 * surface, so the overlay never sees itself. Every failure leaves the last result in place; never throws.
 */
final class ScreenSampler {
    interface Listener {
        void onChanged();
    }

    private static final long INTERVAL_MS = 300;
    /** Every n-th tick also samples a row through the board. */
    private static final int BOARD_EVERY = 5;
    private static final float[] BOARD_ROWS = {0.42f, 0.5f, 0.58f};
    private static final int APP_BAR_X_DP = 6;
    private static final int APP_BAR_Y_DP = 10;

    private final Activity activity;
    private final float density;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScreenShade shade = new ScreenShade();
    private final Runnable tick = this::tick;

    private WeakReference<SurfaceView> surface = new WeakReference<>(null);
    private boolean active;
    private boolean stopped;
    private boolean inFlight;
    private int ticks;
    private int topInset;
    private boolean covered;
    private Boolean boardDark;
    private Bitmap barBitmap;

    ScreenSampler(Activity a, float density, Listener l) {
        this.activity = a;
        this.density = density;
        this.listener = l;
    }

    /** Whether a scrim covers the screen (false while inactive). */
    boolean covered() {
        return active && covered;
    }

    /** The board colours last seen, or the app theme when no board was classified yet. */
    boolean boardDark(boolean appDark) {
        Boolean b = boardDark;
        return b == null ? appDark : b.booleanValue();
    }

    /** Another game, route or theme: forget the app bar baseline. */
    void reset() {
        shade.reset();
        covered = false;
    }

    void setActive(boolean on, int topInsetPx) {
        topInset = topInsetPx;
        if (on == active || stopped) return;
        active = on;
        covered = false;
        main.removeCallbacks(tick);
        if (on) main.postDelayed(tick, INTERVAL_MS);
    }

    void stop() {
        stopped = true;
        active = false;
        main.removeCallbacks(tick);
    }

    private void tick() {
        if (!active || stopped) return;
        main.postDelayed(tick, INTERVAL_MS);
        if (inFlight || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            ticks++;
            if (ticks % BOARD_EVERY == 0) sampleBoard(BOARD_ROWS[(ticks / BOARD_EVERY) % BOARD_ROWS.length]);
            else sampleAppBar();
        } catch (Throwable ignored) {
            inFlight = false;
        }
    }

    private void sampleAppBar() {
        int x = Math.round(APP_BAR_X_DP * density);
        int y = topInset + Math.round(APP_BAR_Y_DP * density);
        if (barBitmap == null) barBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        final Bitmap bmp = barBitmap;
        copy(new Rect(x, y, x + 2, y + 2), bmp, () -> {
            boolean c = shade.update(bmp.getPixel(0, 0) & 0xFFFFFF);
            if (c != covered) {
                covered = c;
                listener.onChanged();
            }
        });
    }

    private void sampleBoard(float row) {
        View root = activity.getWindow().getDecorView();
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0 || covered) return;
        int y = Math.round(h * row);
        int n = Math.max(15, w / 3);
        final Bitmap bmp = Bitmap.createBitmap(n, 1, Bitmap.Config.ARGB_8888);
        copy(new Rect(0, y, w, y + 1), bmp, () -> {
            int[] px = new int[n];
            bmp.getPixels(px, 0, n, 0, 0, n, 1);
            for (int i = 0; i < n; i++) px[i] &= 0xFFFFFF;
            Boolean d = ScreenShade.boardDark(px);
            if (d != null) boardDark = d;
            bmp.recycle();
        });
    }

    /** Copies {@code windowRect} (window coordinates) into {@code dest}; {@code done} runs on success only. */
    private void copy(Rect windowRect, Bitmap dest, Runnable done) {
        SurfaceView sv = findSurface();
        PixelCopy.OnPixelCopyFinishedListener l = result -> {
            inFlight = false;
            if (result != PixelCopy.SUCCESS || !active || stopped) return;
            try {
                done.run();
            } catch (Throwable ignored) {
                // keep the last result
            }
        };
        inFlight = true;
        if (sv != null) {
            int[] loc = new int[2];
            sv.getLocationInWindow(loc);
            Rect r = new Rect(windowRect);
            r.offset(-loc[0], -loc[1]);
            if (!r.intersect(0, 0, sv.getWidth(), sv.getHeight()) || r.isEmpty()) {
                inFlight = false;
                return;
            }
            PixelCopy.request(sv, r, dest, l, main);
        } else {
            Window w = activity.getWindow();
            PixelCopy.request(w, windowRect, dest, l, main);
        }
    }

    /** Flutter's FlutterSurfaceView (the largest visible SurfaceView), or null when Flutter draws into the window. */
    private SurfaceView findSurface() {
        SurfaceView cached = surface.get();
        if (cached != null && cached.isAttachedToWindow() && cached.getHolder().getSurface().isValid()) return cached;
        SurfaceView best = largest(activity.getWindow().getDecorView(), null);
        surface = new WeakReference<>(best);
        return best;
    }

    private static SurfaceView largest(View v, SurfaceView best) {
        if (v.getVisibility() != View.VISIBLE) return best;
        if (v instanceof SurfaceView && ((SurfaceView) v).getHolder().getSurface().isValid()) {
            long area = (long) v.getWidth() * v.getHeight();
            if (best == null || area > (long) best.getWidth() * best.getHeight()) best = (SurfaceView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) best = largest(g.getChildAt(i), best);
        }
        return best;
    }
}
