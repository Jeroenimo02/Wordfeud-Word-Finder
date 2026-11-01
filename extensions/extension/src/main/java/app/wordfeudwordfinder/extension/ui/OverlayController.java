package app.wordfeudwordfinder.extension.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import app.wordfeudwordfinder.extension.core.WfError;
import app.wordfeudwordfinder.extension.core.WordFinder;
import app.wordfeudwordfinder.extension.game.BoardLayout;
import app.wordfeudwordfinder.extension.game.GameState;
import app.wordfeudwordfinder.extension.game.RawGame;
import app.wordfeudwordfinder.extension.game.Ruleset;
import app.wordfeudwordfinder.extension.hooks.RouteTap;
import app.wordfeudwordfinder.extension.play.MoveSubmitter;
import app.wordfeudwordfinder.extension.solver.Move;
import app.wordfeudwordfinder.extension.solver.Rules;
import app.wordfeudwordfinder.extension.solver.SortMode;
import app.wordfeudwordfinder.extension.ui.Strings.Key;
import app.wordfeudwordfinder.extension.words.WordGraph;
import app.wordfeudwordfinder.extension.words.WordListManager;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The floating button and the overlay (spec section 9), hook H3. {@link #attach} is called at the start of
 * {@code MainActivity.onCreate} and defers all work to the main looper. One controller per activity; it is dropped when that activity is destroyed.
 *
 * <p>Threading: every view is touched on the main thread only. Listener calls from {@code GameStore},
 * {@code RouteTap} and the word list manager arrive on other threads and are posted to the main thread. Move
 * generation runs on one background executor; a new computation cancels the previous one through
 * {@link Computations}. Nothing in here may throw into the app: every entry point catches Throwable.
 */
public final class OverlayController {

    // ---- layout constants (dp / sp) ----
    private static final int PANEL_MARGIN_DP = 8;
    private static final int PANEL_PADDING_DP = 12;
    private static final int PANEL_RADIUS_DP = 16;
    private static final int HEADER_HEIGHT_DP = 44;
    private static final int RACK_TILE_DP = 36;
    private static final int RACK_GAP_DP = 3;
    private static final int SECTION_GAP_DP = 8;
    private static final int BUTTON_HEIGHT_DP = 36;
    private static final int FAB_ELEVATION_DP = 6;
    /** The preview board takes at most this share of the panel height, so the list always keeps some room. */
    private static final float BOARD_MAX_HEIGHT_FRACTION = 0.5f;
    private static final int SCRIM = 0x99000000;

    /**
     * Created lazily (not in the static initializer): attach() runs inside the app's onCreate, and an error thrown
     * while initialising this class would escape attach()'s try/catch.
     */
    private static volatile Handler main;
    private static final AtomicBoolean LISTENERS_ADDED = new AtomicBoolean(false);
    private static volatile ExecutorService executor;
    /** The controller of the live MainActivity; null when none. Main thread writes only. */
    private static volatile OverlayController current;

    // ---- per activity ----
    private Activity activity;
    private final WordFinder wf;
    private final float density;
    private final Computations computations = new Computations();
    private final Application.ActivityLifecycleCallbacks lifecycle;
    private FrameLayout layer;
    private View fab;
    private final View.OnLayoutChangeListener fabRelayout = (v, l, t, r, b, ol, ot, or, ob) -> positionFab();

    // ---- open overlay session (main thread) ----
    private boolean open;
    /** Incremented on every open and close; late callbacks from an earlier session compare against it. */
    private int session;
    private long gameId;
    private GameState state;
    private RawGame game;
    private Ruleset ruleset;
    private BoardLayout layout;
    private String lang = "en";
    private Theme theme = Theme.DARK;
    private SortMode sort = SortMode.SCORE;
    /** The sorted list shown, or null while it is (re)computed. */
    private List<Move> moves;
    private Move selected;
    private WordGraph readyGraph;
    private int readyGraphRuleset = -1;
    private boolean wordListRequested;
    /** The word list error of this open overlay; it stays shown (no automatic retry) until the overlay reopens. */
    private WfError wordListError;
    private boolean playing;
    private Object backCallback;
    private View focusBeforeOpen;

    // ---- overlay views (null while closed) ----
    private FrameLayout overlayRoot;
    private LinearLayout panel;
    private LinearLayout rackRow;
    private BoardPreviewView board;
    private TextView status;
    private LinearLayout infoRow;
    private TextView infoWord;
    private TextView infoDetail;
    private TextView infoScore;
    private TextView playButton;
    private TextView notYourTurn;
    private LinearLayout sortRow;
    private final TextView[] sortButtons = new TextView[SortMode.values().length];
    private ListView list;
    private MoveListAdapter adapter;

    private OverlayController(Activity a, WordFinder wf) {
        this.activity = a;
        this.wf = wf;
        this.density = a.getResources().getDisplayMetrics().density;
        this.lifecycle = new Lifecycle(this);
    }

    // =====================================================================================================
    // Entry point
    // =====================================================================================================

    /**
     * Hook H3, called at the START of {@code MainActivity.onCreate} (before super.onCreate). It must only post:
     * nothing here may touch the window or the content view synchronously. The posted work runs after onCreate
     * has returned. Never throws; on any failure the app is left as it is.
     */
    public static void attach(Activity a) {
        try {
            if (a == null) return;
            // Called before super.onCreate: build the views later, once onCreate has returned and the content
            // view is in place.
            main().post(() -> {
                try {
                    attachNow(a);
                } catch (Throwable ignored) {
                    // the app keeps working without the word finder
                }
            });
        } catch (Throwable ignored) {
            // hooks never change app behaviour
        }
    }

    private static void attachNow(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        OverlayController old = current;
        if (old != null) {
            if (old.activity == a) return;
            old.detach();
        }
        WordFinder wf = WordFinder.get();
        if (wf == null) return;
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;
        OverlayController c = new OverlayController(a, wf);
        try {
            c.build(content);
            a.getApplication().registerActivityLifecycleCallbacks(c.lifecycle);
        } catch (Throwable t) {
            c.detach();
            return;
        }
        current = c;
        if (LISTENERS_ADDED.compareAndSet(false, true)) {
            // GameStore and RouteTap have no removeListener, so these are added once and forward to `current`.
            Runnable r = OverlayController::postRefreshCurrent;
            wf.gameStore().addListener(r);
            RouteTap.addListener(r);
        }
        c.refresh();
    }

    private static void postRefreshCurrent() {
        main().post(() -> {
            OverlayController c = current;
            if (c != null) c.refresh();
        });
    }

    private static Handler main() {
        Handler h = main;
        if (h == null) {
            h = new Handler(Looper.getMainLooper());
            main = h;
        }
        return h;
    }

    private static ExecutorService executor() {
        ExecutorService ex = executor;
        if (ex == null) {
            synchronized (OverlayController.class) {
                if (executor == null) {
                    ThreadPoolExecutor t = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(), r -> {
                                Thread th = new Thread(r, "wfwf-overlay");
                                th.setDaemon(true);
                                return th;
                            });
                    t.allowCoreThreadTimeOut(true);
                    executor = t;
                }
                ex = executor;
            }
        }
        return ex;
    }

    /** Runs {@code r} on the background executor, swallowing everything it throws. */
    private static void background(Runnable r) {
        executor().execute(() -> {
            try {
                r.run();
            } catch (Throwable ignored) {
                // a failed computation only leaves the overlay without a list
            }
        });
    }

    /** Runs {@code r} on the main thread if this controller is still attached, swallowing everything it throws. */
    private void ui(Runnable r) {
        main().post(() -> {
            if (activity == null) return;
            try {
                r.run();
            } catch (Throwable ignored) {
                // UI failures never reach the app
            }
        });
    }

    // =====================================================================================================
    // Layer and floating button
    // =====================================================================================================

    private void build(ViewGroup content) {
        Context ctx = activity;
        layer = new FrameLayout(ctx);
        // The layer itself never consumes touches, so the game underneath stays usable while the overlay is closed.
        layer.setClickable(false);
        layer.setFocusable(false);
        content.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int size = FabPlacement.dp(density, FabPlacement.FAB_SIZE_DP);
        View b = new View(ctx);
        b.setBackground(new FabDrawable(theme));
        b.setElevation(dp(FAB_ELEVATION_DP));
        b.setContentDescription(Strings.get(language(), Key.TITLE));
        b.setClickable(true);
        b.setOnClickListener(v -> {
            try {
                openOverlay();
            } catch (Throwable ignored) {
                // a broken overlay must not crash the game
            }
        });
        b.setVisibility(View.GONE);
        fab = b;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | Gravity.END);
        layer.addView(fab, lp);
        layer.addOnLayoutChangeListener(fabRelayout);
        layer.setOnApplyWindowInsetsListener((v, insets) -> {
            ui(this::positionFab);
            return insets;
        });
    }

    /** Main thread: button visibility and position, and closes the overlay when its game is no longer shown. */
    private void refresh() {
        if (activity == null || layer == null) return;
        Long id = RouteTap.currentGameId();
        GameState st = id == null ? null : wf.gameStore().state(id);
        boolean visible = OverlayVisibility.fabVisible(id, st);
        if (open) {
            if (!visible || id == null || id != gameId) {
                close();
            } else if (!MovePipeline.sameInput(state, st)) {
                state = st;
                loadGame();
            } else {
                state = st;
                updatePlayEnabled();
            }
        }
        fab.setVisibility(visible && !open ? View.VISIBLE : View.GONE);
        positionFab();
    }

    private void positionFab() {
        if (fab == null || layer == null) return;
        Insets in = insets();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fab.getLayoutParams();
        int right = FabPlacement.dp(density, FabPlacement.FAB_MARGIN_RIGHT_DP);
        int bottom = FabPlacement.bottomMarginPx(density, in.bottom, RouteTap.bannerVisible());
        if (lp.rightMargin != right || lp.bottomMargin != bottom) {
            lp.rightMargin = right;
            lp.bottomMargin = bottom;
            fab.setLayoutParams(lp);
        }
    }

    /** The parts of the system bar insets that lie over the layer (zero when the app is not drawn edge to edge). */
    private Insets insets() {
        int top = 0;
        int bottom = 0;
        WindowInsets wi = Build.VERSION.SDK_INT >= 23 ? layer.getRootWindowInsets() : null;
        if (wi != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets sb = wi.getInsets(WindowInsets.Type.systemBars());
                top = sb.top;
                bottom = sb.bottom;
            } else {
                top = legacyTop(wi);
                bottom = legacyBottom(wi);
            }
        }
        int[] loc = new int[2];
        layer.getLocationInWindow(loc);
        View root = layer.getRootView();
        int windowHeight = root == null ? 0 : root.getHeight();
        Insets out = new Insets();
        if (windowHeight > 0 && layer.getHeight() > 0) {
            out.bottom = FabPlacement.effectiveBottomInset(bottom, windowHeight, loc[1] + layer.getHeight());
            out.top = FabPlacement.effectiveTopInset(top, loc[1]);
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private static int legacyTop(WindowInsets wi) {
        return wi.getSystemWindowInsetTop();
    }

    @SuppressWarnings("deprecation")
    private static int legacyBottom(WindowInsets wi) {
        return wi.getSystemWindowInsetBottom();
    }

    private static final class Insets {
        int top;
        int bottom;
    }

    // =====================================================================================================
    // Open / close
    // =====================================================================================================

    private void openOverlay() {
        if (open || activity == null) return;
        Long id = RouteTap.currentGameId();
        GameState st = id == null ? null : wf.gameStore().state(id);
        if (!OverlayVisibility.fabVisible(id, st)) return;
        open = true;
        session++;
        gameId = id;
        state = st;
        sort = SortMode.SCORE;
        moves = null;
        selected = null;
        readyGraph = null;
        readyGraphRuleset = -1;
        wordListRequested = false;
        wordListError = null;
        playing = false;
        lang = language();
        theme = Theme.forDarkMode(RouteTap.isDark());
        focusBeforeOpen = activity.getCurrentFocus();
        buildOverlay();
        layer.addView(overlayRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlayRoot.requestFocus();
        registerBack();
        fab.setVisibility(View.GONE);
        loadGame();
    }

    /** Closes the overlay (X, swipe, back, success, or its game left the screen). Main thread; idempotent. */
    private void close() {
        if (!open) return;
        open = false;
        session++;
        computations.cancel();
        unregisterBack();
        if (overlayRoot != null && layer != null) layer.removeView(overlayRoot);
        clearOverlayViews();
        moves = null;
        selected = null;
        state = null;
        game = null;
        readyGraph = null;
        View f = focusBeforeOpen;
        focusBeforeOpen = null;
        if (f != null && f.isAttachedToWindow()) f.requestFocus();
        refresh();
    }

    private void detach() {
        try {
            close();
        } catch (Throwable ignored) {
            // continue tearing down
        }
        try {
            if (layer != null) {
                layer.removeOnLayoutChangeListener(fabRelayout);
                layer.setOnApplyWindowInsetsListener(null);
                ViewGroup parent = (ViewGroup) layer.getParent();
                if (parent != null) parent.removeView(layer);
            }
        } catch (Throwable ignored) {
            // the activity is going away anyway
        }
        try {
            if (activity != null) activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);
        } catch (Throwable ignored) {
            // nothing more to release
        }
        if (current == this) current = null;
        layer = null;
        fab = null;
        activity = null;
    }

    /** Detaches when its activity is destroyed, so the activity is never leaked. */
    private static final class Lifecycle implements Application.ActivityLifecycleCallbacks {
        private final OverlayController c;

        Lifecycle(OverlayController c) {
            this.c = c;
        }

        @Override
        public void onActivityDestroyed(Activity a) {
            try {
                if (a == c.activity) c.detach();
            } catch (Throwable ignored) {
                // never affects the app
            }
        }

        @Override
        public void onActivityCreated(Activity a, Bundle b) {}

        @Override
        public void onActivityStarted(Activity a) {}

        @Override
        public void onActivityResumed(Activity a) {}

        @Override
        public void onActivityPaused(Activity a) {}

        @Override
        public void onActivityStopped(Activity a) {}

        @Override
        public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    }

    // ---- back button ----

    private void registerBack() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback == null) {
            try {
                backCallback = Back33.register(activity, () -> ui(this::close));
            } catch (Throwable ignored) {
                backCallback = null; // the key event path below still works
            }
        }
    }

    private void unregisterBack() {
        Object cb = backCallback;
        backCallback = null;
        if (cb != null && activity != null && Build.VERSION.SDK_INT >= 33) {
            try {
                Back33.unregister(activity, cb);
            } catch (Throwable ignored) {
                // already gone
            }
        }
    }

    /**
     * API 33+ back handling, in its own class so older devices never load {@code OnBackInvokedCallback}. When the
     * app has opted in to predictive back the system calls this callback and sends no KEYCODE_BACK; otherwise
     * the key event reaches the focused overlay view instead ({@link OverlayRoot#dispatchKeyEvent}).
     */
    private static final class Back33 {
        static Object register(Activity a, Runnable onBack) {
            OnBackInvokedCallback cb = onBack::run;
            a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb);
            return cb;
        }

        static void unregister(Activity a, Object cb) {
            a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) cb);
        }
    }

    /** Full-screen scrim that holds the panel, swallows touches and closes on KEYCODE_BACK while focused. */
    private final class OverlayRoot extends FrameLayout {
        OverlayRoot(Context ctx) {
            super(ctx);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            try {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) close();
                    return true;
                }
            } catch (Throwable ignored) {
                // fall through to the default handling
            }
            return super.dispatchKeyEvent(e);
        }
    }

    // =====================================================================================================
    // Overlay views
    // =====================================================================================================

    private void buildOverlay() {
        Context ctx = activity;
        overlayRoot = new OverlayRoot(ctx);
        overlayRoot.setBackgroundColor(SCRIM);
        overlayRoot.setClickable(true); // touches outside the panel do not reach the game
        overlayRoot.setFocusable(true);
        overlayRoot.setFocusableInTouchMode(true);

        panel = new LinearLayout(ctx) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                sizeBoard(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(PANEL_PADDING_DP);
        panel.setPadding(pad, 0, pad, pad);
        panel.setBackground(rounded(theme.background, dp(PANEL_RADIUS_DP)));
        panel.setClickable(true);
        Insets in = insets();
        int m = dp(PANEL_MARGIN_DP);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        plp.setMargins(m, m + in.top, m, m + in.bottom);
        overlayRoot.addView(panel, plp);

        panel.addView(header(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(HEADER_HEIGHT_DP)));

        rackRow = new LinearLayout(ctx);
        rackRow.setOrientation(LinearLayout.HORIZONTAL);
        rackRow.setGravity(Gravity.CENTER);
        rackRow.setContentDescription(Strings.get(lang, Key.YOUR_TILES));
        panel.addView(rackRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(RACK_TILE_DP)));

        board = new BoardPreviewView(ctx, theme);
        board.setLanguage(lang);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, 0);
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        blp.topMargin = dp(SECTION_GAP_DP);
        panel.addView(board, blp);

        status = text(ctx, 14, theme.textSecondary, Fonts.inter(ctx));
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = matchWrap();
        slp.topMargin = dp(SECTION_GAP_DP);
        panel.addView(status, slp);

        infoRow = infoRow(ctx);
        LinearLayout.LayoutParams ilp = matchWrap();
        ilp.topMargin = dp(SECTION_GAP_DP);
        panel.addView(infoRow, ilp);

        sortRow = sortRow(ctx);
        LinearLayout.LayoutParams srlp = matchWrap();
        srlp.topMargin = dp(SECTION_GAP_DP);
        panel.addView(sortRow, srlp);

        adapter = new MoveListAdapter(ctx, theme, lang);
        list = new ListView(ctx);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setBackgroundColor(theme.surface);
        list.setOnItemClickListener((parent, view, position, id) -> {
            try {
                select(position, false);
            } catch (Throwable ignored) {
                // keep the overlay alive
            }
        });
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        llp.topMargin = dp(SECTION_GAP_DP);
        panel.addView(list, llp);
    }

    /** Square board, at most the panel's inner width and {@link #BOARD_MAX_HEIGHT_FRACTION} of its height. */
    private void sizeBoard(int panelWidth, int panelHeight) {
        if (board == null) return;
        int inner = panelWidth - panel.getPaddingLeft() - panel.getPaddingRight();
        int size = Math.max(0, Math.min(inner, (int) (panelHeight * BOARD_MAX_HEIGHT_FRACTION)));
        ViewGroup.LayoutParams lp = board.getLayoutParams();
        if (lp != null) {
            lp.width = size;
            lp.height = size;
        }
    }

    private View header(Context ctx) {
        LinearLayout h = new LinearLayout(ctx);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(ctx, 18, theme.textPrimary, Fonts.interBold(ctx));
        title.setText(Strings.get(lang, Key.TITLE));
        h.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = text(ctx, 26, theme.textPrimary, Fonts.inter(ctx));
        x.setText("×");
        x.setGravity(Gravity.CENTER);
        x.setContentDescription(Strings.get(lang, Key.CLOSE));
        x.setClickable(true);
        x.setOnClickListener(v -> close());
        h.addView(x, new LinearLayout.LayoutParams(dp(HEADER_HEIGHT_DP), dp(HEADER_HEIGHT_DP)));
        h.setOnTouchListener(new SwipeListener());
        return h;
    }

    /** Swipe down on the header: the panel follows the finger and closes on a fling or a long enough drag. */
    private final class SwipeListener implements View.OnTouchListener {
        private float downY;
        private VelocityTracker tracker;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            try {
                return handle(e);
            } catch (Throwable t) {
                return false;
            }
        }

        private boolean handle(MotionEvent e) {
            if (panel == null) return false;
            ViewConfiguration vc = ViewConfiguration.get(activity);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = e.getRawY();
                    recycle();
                    tracker = VelocityTracker.obtain();
                    tracker.addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (tracker != null) tracker.addMovement(e);
                    float dy = e.getRawY() - downY;
                    if (dy > vc.getScaledTouchSlop()) panel.setTranslationY(SwipeDismiss.dragOffset(dy));
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float vy = 0f;
                    if (tracker != null) {
                        tracker.addMovement(e);
                        tracker.computeCurrentVelocity(1000);
                        vy = tracker.getYVelocity();
                    }
                    recycle();
                    float dy = e.getRawY() - downY;
                    if (SwipeDismiss.shouldDismiss(dy, vy, vc.getScaledTouchSlop(), panel.getHeight(),
                            vc.getScaledMinimumFlingVelocity())) {
                        final int s = session;
                        panel.animate().translationY(panel.getHeight()).setDuration(150)
                                .withEndAction(() -> {
                                    if (open && session == s) close();
                                }).start();
                    } else {
                        panel.animate().translationY(0f).setDuration(150).start();
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    recycle();
                    panel.animate().translationY(0f).setDuration(150).start();
                    return true;
                default:
                    return true;
            }
        }

        private void recycle() {
            if (tracker != null) {
                tracker.recycle();
                tracker = null;
            }
        }
    }

    private LinearLayout infoRow(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        infoWord = text(ctx, 18, theme.textPrimary, Fonts.mono(ctx));
        infoWord.setTypeface(Fonts.mono(ctx), android.graphics.Typeface.BOLD);
        infoDetail = text(ctx, 13, theme.textSecondary, Fonts.inter(ctx));
        texts.addView(infoWord);
        texts.addView(infoDetail);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        infoScore = text(ctx, 16, theme.textPrimary, Fonts.interBold(ctx));
        LinearLayout.LayoutParams sclp = wrap();
        sclp.rightMargin = dp(SECTION_GAP_DP);
        row.addView(infoScore, sclp);

        playButton = button(ctx, Strings.get(lang, Key.PLAY), true);
        playButton.setOnClickListener(v -> {
            try {
                onPlayClicked();
            } catch (Throwable ignored) {
                // keep the overlay alive
            }
        });
        row.addView(playButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(BUTTON_HEIGHT_DP)));

        notYourTurn = text(ctx, 14, theme.textSecondary, Fonts.inter(ctx));
        notYourTurn.setText(Strings.get(lang, Key.NOT_YOUR_TURN));
        notYourTurn.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        notYourTurn.setMaxWidth(dp(160));
        row.addView(notYourTurn, wrap());
        return row;
    }

    private LinearLayout sortRow(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Key[] keys = {Key.SORT_SCORE, Key.SORT_LENGTH, Key.SORT_ALPHA};
        SortMode[] modes = {SortMode.SCORE, SortMode.LENGTH, SortMode.ALPHA};
        for (int i = 0; i < modes.length; i++) {
            final SortMode mode = modes[i];
            TextView b = button(ctx, Strings.get(lang, keys[i]), false);
            b.setOnClickListener(v -> {
                try {
                    onSort(mode);
                } catch (Throwable ignored) {
                    // keep the overlay alive
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(BUTTON_HEIGHT_DP), 1f);
            if (i > 0) lp.leftMargin = dp(SECTION_GAP_DP);
            row.addView(b, lp);
            sortButtons[mode.ordinal()] = b;
        }
        updateSortButtons();
        return row;
    }

    private void clearOverlayViews() {
        overlayRoot = null;
        panel = null;
        rackRow = null;
        board = null;
        status = null;
        infoRow = null;
        infoWord = null;
        infoDetail = null;
        infoScore = null;
        playButton = null;
        notYourTurn = null;
        sortRow = null;
        java.util.Arrays.fill(sortButtons, null);
        list = null;
        adapter = null;
    }

    // =====================================================================================================
    // Data flow
    // =====================================================================================================

    /** Main thread: shows the open game (new open, or new data for it) and starts computing its moves. */
    private void loadGame() {
        if (!open || panel == null) return;
        computations.cancel();
        moves = null;
        selected = null;
        adapter.setMoves(Collections.<Move>emptyList());
        GameState st = state;
        WfError err = st == null ? WfError.gsData("no state") : st.unreadable();
        game = wf.gameStore().game(gameId);
        ruleset = game == null ? null : wf.gameStore().ruleset(game.getRulesetId());
        layout = game == null ? null : wf.gameStore().board(game.getBoardId());
        if (err == null && (game == null || ruleset == null || layout == null)) {
            err = WfError.gsData("game data missing");
        }
        if (err != null) {
            // TODO(Task 14): full ErrorPanel with "Copy details".
            showContent(false);
            showStatus(errorText(err));
            return;
        }
        showContent(true);
        hideStatus();
        board.setBoard(st.board(), layout, ruleset);
        board.setMove(null);
        updateRack();
        updateInfo();
        compute();
    }

    /** Main thread: generates the move list in the background (cancelling any running computation). */
    private void compute() {
        if (!open || state == null || game == null) return;
        final AtomicBoolean token = computations.begin();
        final int s = session;
        final GameState st = state;
        final BoardLayout bl = layout;
        final Ruleset rs = ruleset;
        final int rulesetId = game.getRulesetId();
        final SortMode mode = sort;
        final WordGraph known = readyGraphRuleset == rulesetId ? readyGraph : null;
        background(() -> {
            if (token.get()) return;
            WordGraph wg = known;
            if (wg == null) {
                try {
                    wg = wf.wordLists().getOrNull(rulesetId);
                } catch (Throwable t) {
                    wg = null;
                }
            }
            if (wg == null) {
                ui(() -> {
                    if (open && session == s && computations.isCurrent(token)) requestWordList(rulesetId, rs);
                });
                return;
            }
            final List<Move> out = MovePipeline.compute(st, bl, rs, wg,
                    l -> wf.rejected().filter(rulesetId, l), mode, token);
            if (out == null) return;
            ui(() -> {
                if (open && session == s && computations.isCurrent(token)) showMoves(out, false);
            });
        });
    }

    /** Main thread: the word list is missing; download it once per open overlay and show a progress text. */
    private void requestWordList(int rulesetId, Ruleset rs) {
        // TODO(Task 14): progress bar, error panel with "Retry" and "Copy details".
        if (wordListError != null) {
            showStatus(errorText(wordListError));
            return;
        }
        showStatus(Strings.get(lang, Key.DOWNLOADING));
        if (wordListRequested) return;
        wordListRequested = true;
        final int s = session;
        wf.wordLists().ensureForOverlay(rulesetId, rs, new WordListManager.Callback() {
            @Override
            public void onProgress(int pct) {
                ui(() -> {
                    if (open && session == s && moves == null && wordListError == null && status != null
                            && status.getVisibility() == View.VISIBLE) {
                        showStatus(Strings.get(lang, Key.DOWNLOADING) + " " + pct + "%");
                    }
                });
            }

            @Override
            public void onReady(WordGraph g) {
                ui(() -> {
                    if (!open || session != s) return;
                    readyGraph = g;
                    readyGraphRuleset = rulesetId;
                    hideStatus();
                    compute();
                });
            }

            @Override
            public void onError(WfError e) {
                ui(() -> {
                    if (!open || session != s) return;
                    wordListError = e;
                    showStatus(errorText(e));
                });
            }
        });
    }

    /** Main thread: a new sorted list. Keeps the selected move after a re-sort; else selects the best move. */
    private void showMoves(List<Move> out, boolean keepSelection) {
        if (adapter == null) return;
        Move keep = selected;
        moves = out;
        adapter.setMoves(out); // resets the adapter's selection
        int idx = keepSelection ? MovePipeline.indexOf(out, keep) : -1;
        if (idx < 0) idx = MovePipeline.bestIndex(out);
        select(idx, true);
    }

    /** Main thread: highlights row {@code idx} (-1 for none) and updates board, rack and info line at once. */
    private void select(int idx, boolean scrollTo) {
        if (adapter == null || moves == null) return;
        selected = idx >= 0 && idx < moves.size() ? moves.get(idx) : null;
        adapter.setSelected(selected == null ? -1 : idx);
        board.setMove(selected);
        updateRack();
        updateInfo();
        if (scrollTo && selected != null) list.setSelection(idx);
    }

    private void onSort(SortMode mode) {
        if (!open || mode == sort) return;
        sort = mode;
        updateSortButtons();
        final List<Move> current = moves;
        if (current == null) {
            // Still computing (or waiting for the word list): restart with the new order.
            if (!wordListRequested || readyGraph != null) compute();
            return;
        }
        final AtomicBoolean token = computations.begin();
        final int s = session;
        background(() -> {
            List<Move> sorted = MovePipeline.sort(current, mode, token);
            if (sorted == null) return;
            ui(() -> {
                if (open && session == s && computations.isCurrent(token)) showMoves(sorted, true);
            });
        });
    }

    // =====================================================================================================
    // Play (seam for Task 14)
    // =====================================================================================================

    /**
     * Sends the selected move. This task handles only SUCCESS (close the overlay; the store already holds the new
     * game state). TODO(Task 14): REJECTED message + updated list, MV-* error panel with "Copy details", Play
     * staying disabled after MV-NET until the next games/detail (spec 8.3).
     */
    private void onPlayClicked() {
        if (!open || selected == null || playing || state == null || !state.myTurn() || game == null) return;
        MoveSubmitter submitter = wf.submitter();
        if (submitter.isBusy()) return;
        playing = true;
        updatePlayEnabled();
        computations.cancel(); // free the executor for the request
        final Move m = selected;
        final long id = gameId;
        final int rulesetId = game.getRulesetId();
        final int s = session;
        background(() -> {
            MoveSubmitter.Result r = submitter.submit(id, rulesetId, m);
            ui(() -> {
                if (open && session == s) {
                    onPlayResult(r);
                } else if (open) {
                    updatePlayEnabled(); // a later overlay session waited for this request to finish
                }
            });
        });
    }

    private void onPlayResult(MoveSubmitter.Result r) {
        playing = false;
        if (r.kind == MoveSubmitter.Result.Kind.SUCCESS) {
            close();
            return;
        }
        // TODO(Task 14): proper messages and error panel. For now: a plain text line and a fresh list.
        if (r.kind == MoveSubmitter.Result.Kind.REJECTED) {
            showStatus(Strings.get(lang, Key.REJECTED));
            moves = null;
            compute(); // RejectedWords now holds the words, so the move disappears from the list
        } else if (r.error != null) {
            showStatus(errorText(r.error));
        }
        updatePlayEnabled();
    }

    // =====================================================================================================
    // View updates (main thread)
    // =====================================================================================================

    private void showContent(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        rackRow.setVisibility(v);
        board.setVisibility(v);
        infoRow.setVisibility(v);
        sortRow.setVisibility(v);
        list.setVisibility(v);
    }

    private void showStatus(String text) {
        if (status == null) return;
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        if (status != null) status.setVisibility(View.GONE);
    }

    private String errorText(WfError e) {
        return Strings.get(lang, Strings.errorKey(e)) + " (" + e.code() + ")";
    }

    private void updateRack() {
        if (rackRow == null || state == null) return;
        List<String> rack = state.rack();
        if (rackRow.getChildCount() != rack.size()) {
            rackRow.removeAllViews();
            for (int i = 0; i < rack.size(); i++) {
                View t = new View(activity);
                t.setBackground(new TileDrawable(theme, Fonts.interBold(activity), Fonts.inter(activity)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(RACK_TILE_DP), dp(RACK_TILE_DP));
                if (i > 0) lp.leftMargin = dp(RACK_GAP_DP);
                rackRow.addView(t, lp);
            }
        }
        boolean[] used = OverlayFormat.usedRackIndices(rack, selected);
        for (int i = 0; i < rack.size(); i++) {
            String t = rack.get(i);
            boolean blank = t == null || t.isEmpty() || Rules.isBlank(t);
            String letter = blank ? "" : t.toUpperCase(Locale.ROOT);
            int points = blank ? 0 : OverlayFormat.letterPoints(ruleset, t.charAt(0));
            TileDrawable d = (TileDrawable) rackRow.getChildAt(i).getBackground();
            d.set(letter, points, used[i] ? TileDrawable.State.DIMMED : TileDrawable.State.NORMAL, null);
        }
    }

    private void updateInfo() {
        if (infoRow == null) return;
        Move m = selected;
        infoWord.setText(m == null ? "" : m.word);
        infoDetail.setText(m == null ? "" : OverlayFormat.detail(lang, m));
        infoDetail.setVisibility(m == null ? View.GONE : View.VISIBLE);
        infoScore.setText(m == null ? "" : OverlayFormat.scoreText(lang, m.score));
        boolean myTurn = state != null && state.myTurn();
        playButton.setVisibility(myTurn ? View.VISIBLE : View.GONE);
        notYourTurn.setVisibility(myTurn ? View.GONE : View.VISIBLE);
        updatePlayEnabled();
    }

    private void updatePlayEnabled() {
        if (playButton == null) return;
        boolean enabled = selected != null && !playing && !wf.submitter().isBusy();
        playButton.setEnabled(enabled);
        playButton.setAlpha(enabled ? 1f : 0.4f);
    }

    private void updateSortButtons() {
        for (SortMode mode : SortMode.values()) {
            TextView b = sortButtons[mode.ordinal()];
            if (b == null) continue;
            boolean on = mode == sort;
            b.setBackground(rounded(on ? theme.accent : theme.surface, dp(BUTTON_HEIGHT_DP) / 2));
            b.setTextColor(on ? theme.onAccent : theme.textPrimary);
        }
    }

    // ---- small view helpers ----

    private TextView text(Context ctx, int sp, int color, android.graphics.Typeface face) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(false);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private TextView button(Context ctx, String label, boolean accent) {
        TextView b = text(ctx, 14, accent ? theme.onAccent : theme.textPrimary, Fonts.interBold(ctx));
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        int h = dp(BUTTON_HEIGHT_DP);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(rounded(accent ? theme.accent : theme.surface, h / 2));
        b.setClickable(true);
        return b;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return FabPlacement.dp(density, v);
    }

    /** Interface language. TODO(V12): use the app's own language setting once its source is known. */
    private static String language() {
        String l = Locale.getDefault().getLanguage();
        return l == null || l.isEmpty() ? "en" : l;
    }

    /** Round accent button with a magnifier glyph (drawn, so it needs no icon resource or font). */
    private static final class FabDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        FabDrawable(Theme theme) {
            fill.setColor(theme.accent);
            fill.setStyle(Paint.Style.FILL);
            stroke.setColor(theme.onAccent);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f;
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            c.drawCircle(cx, cy, r, fill);
            stroke.setStrokeWidth(r * 0.14f);
            float lens = r * 0.32f;
            float lx = cx - r * 0.1f;
            float ly = cy - r * 0.1f;
            c.drawCircle(lx, ly, lens, stroke);
            float d = lens * 0.7071f;
            c.drawLine(lx + d, ly + d, cx + r * 0.42f, cy + r * 0.42f, stroke);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            stroke.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            stroke.setColorFilter(cf);
            invalidateSelf();
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
