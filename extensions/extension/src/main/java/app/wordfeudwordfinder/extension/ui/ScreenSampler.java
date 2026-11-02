package app.wordfeudwordfinder.extension.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import java.lang.ref.WeakReference;

/**
 * Samples pixels of Wordfeud's Flutter surface (main thread, only while the button may show) so the button looks as
 * if it lies under Wordfeud's menus and dialogs (see {@link ScreenShade}), and to read the board colours. Our own
 * views are not in the Flutter surface, so the button never sees itself.
 *
 * <p>Idle it samples every {@link #IDLE_MS}. After a touch, and for as long as the button is dimmed or cut off, it
 * samples every frame, so menus that open or close move over the button frame by frame. Every failure leaves the
 * last result in place; never throws.
 */
final class ScreenSampler {
    interface Listener {
        /** The button's look changed; {@code cover} is never null. */
        void onCover(ScreenShade.Cover cover);
    }

    private static final long IDLE_MS = 300;
    /** Frame-by-frame sampling lasts this long after a touch (a menu opens on release, then animates). */
    private static final long FAST_AFTER_TOUCH_MS = 1500;
    /** Every n-th idle tick samples a row through the board instead. */
    private static final int BOARD_EVERY = 5;
    private static final float[] BOARD_ROWS = {0.42f, 0.5f, 0.58f};
    /** Column right of the button, in the middle of its right margin. */
    private static final float COLUMN_GAP_DP = FabPlacement.FAB_MARGIN_RIGHT_DP / 2f;

    private final Activity activity;
    private final View fab;
    private final float density;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idleTick = this::idleTick;
    private final Choreographer.FrameCallback frame = t -> frameTick();

    private WeakReference<SurfaceView> surface = new WeakReference<>(null);
    private boolean active;
    private boolean stopped;
    private boolean inFlight;
    private boolean frameScheduled;
    private long fastUntil;
    private int ticks;
    private int base = ScreenShade.PAGE_LIGHT;
    private ScreenShade.Cover cover;
    private Boolean boardDark;
    private Bitmap column;

    ScreenSampler(Activity a, View fab, float density, Listener l) {
        this.activity = a;
        this.fab = fab;
        this.density = density;
        this.listener = l;
    }

    /** The board colours last seen, or the app theme when no board was classified yet. */
    boolean boardDark(boolean appDark) {
        Boolean b = boardDark;
        return b == null ? appDark : b.booleanValue();
    }

    /** The page background behind the button for the app theme. */
    void setAppDark(boolean dark) {
        base = dark ? ScreenShade.PAGE_DARK : ScreenShade.PAGE_LIGHT;
    }

    void setActive(boolean on) {
        if (on == active || stopped) return;
        active = on;
        main.removeCallbacks(idleTick);
        if (on) {
            main.postDelayed(idleTick, IDLE_MS);
        } else {
            setCover(null);
        }
    }

    /** A touch on the screen: sample frame by frame for a while. */
    void onTouch() {
        if (!active || stopped) return;
        fastUntil = SystemClock.uptimeMillis() + FAST_AFTER_TOUCH_MS;
        scheduleFrame();
    }

    void stop() {
        stopped = true;
        active = false;
        main.removeCallbacks(idleTick);
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(frame);
        frameScheduled = false;
    }

    private boolean fast() {
        ScreenShade.Cover c = cover;
        return SystemClock.uptimeMillis() < fastUntil || (c != null && !c.untouched(fab.getHeight()));
    }

    private void scheduleFrame() {
        if (frameScheduled || !active || stopped) return;
        frameScheduled = true;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private void frameTick() {
        frameScheduled = false;
        if (!active || stopped || !fast()) return;
        scheduleFrame();
        if (!inFlight) safely(this::sampleColumn);
    }

    private void idleTick() {
        if (!active || stopped) return;
        main.postDelayed(idleTick, IDLE_MS);
        if (fast()) {
            scheduleFrame();
            return;
        }
        if (inFlight) return;
        ticks++;
        if (ticks % BOARD_EVERY == 0) safely(() -> sampleBoard(BOARD_ROWS[(ticks / BOARD_EVERY) % BOARD_ROWS.length]));
        else safely(this::sampleColumn);
    }

    private void safely(Runnable r) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            r.run();
        } catch (Throwable ignored) {
            inFlight = false;
        }
    }

    private void sampleColumn() {
        int h = fab.getHeight();
        if (h <= 0 || fab.getVisibility() != View.VISIBLE) return;
        int[] loc = new int[2];
        fab.getLocationInWindow(loc);
        int x = loc[0] + fab.getWidth() + Math.round(COLUMN_GAP_DP * density);
        if (column == null || column.getHeight() != h) column = Bitmap.createBitmap(1, h, Bitmap.Config.ARGB_8888);
        final Bitmap bmp = column;
        copy(new Rect(x, loc[1], x + 1, loc[1] + h), bmp, () -> {
            int[] px = new int[h];
            bmp.getPixels(px, 0, 1, 0, 0, 1, h);
            ScreenShade.Cover c = ScreenShade.analyse(px, base);
            if (c != null) setCover(c);
        });
    }

    private void setCover(ScreenShade.Cover c) {
        ScreenShade.Cover old = cover;
        cover = c;
        ScreenShade.Cover shown = c != null ? c : new ScreenShade.Cover(Integer.MAX_VALUE, 1f);
        if (old != null && c != null && old.visibleRows == c.visibleRows && Math.abs(old.factor - c.factor) < 0.01f) {
            return;
        }
        listener.onCover(shown);
        if (c != null && !c.untouched(fab.getHeight())) scheduleFrame();
    }

    private void sampleBoard(float row) {
        View root = activity.getWindow().getDecorView();
        int w = root.getWidth();
        int h = root.getHeight();
        ScreenShade.Cover c = cover;
        if (w <= 0 || h <= 0 || (c != null && !c.untouched(fab.getHeight()))) return;
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
        if (sv != null) {
            int[] loc = new int[2];
            sv.getLocationInWindow(loc);
            Rect r = new Rect(windowRect);
            r.offset(-loc[0], -loc[1]);
            if (r.left < 0 || r.top < 0 || r.right > sv.getWidth() || r.bottom > sv.getHeight() || r.isEmpty()) return;
            inFlight = true;
            PixelCopy.request(sv, r, dest, l, main);
        } else {
            Window w = activity.getWindow();
            inFlight = true;
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
