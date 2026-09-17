/*#######################################################
 *
 *  tsun-markor fork: auto-hiding bars, tsunderick edition
 *  License of this file: Apache 2.0
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.frontend;

import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import net.gsantner.markor.BuildConfig;

/**
 * tsun-markor fork: hides the activity's top app bar and the fragment's bottom
 * text-actions bar based on scroll direction and soft-keyboard state.
 * <p>
 * Rules:
 * <ul>
 *     <li>Scroll down &rarr; hide both bars; scroll up &rarr; show them again;
 *     near the very top of the content &rarr; always show. This holds in
 *     <b>both</b> edit and preview mode.</li>
 *     <li><b>Asymmetric hysteresis</b>: hiding requires a sustained
 *     {@link #HIDE_HYSTERESIS_PX} down-travel past the last flip point, showing
 *     needs only {@link #SHOW_HYSTERESIS_PX} up-travel. Bars are eager to come
 *     back and reluctant to leave — showing is harmless, hiding is disruptive.
 *     (A previous plain direction-vote variant had no hysteresis at all: any
 *     layout-reflow delta re-flipped the state instantly and the bars flapped.)</li>
 *     <li>While editing with the soft keyboard open, the top bar stays hidden
 *     regardless of scroll (typing space first), while the bottom action bar
 *     stays scroll-driven. Scroll events are still evaluated, so the state is
 *     correct when the keyboard closes.</li>
 *     <li>The bottom bar is only ever shown when the user's action-bar preference
 *     allows it (see {@code setBottomBarAllowed}).</li>
 * </ul>
 * Scroll detection uses a window-level {@link ViewTreeObserver.OnScrollChangedListener},
 * which fires for any view scrolling in this window - covering the edit-mode
 * {@code DraggableScrollbarScrollView} plus the view-mode {@code WebView}, which
 * additionally gets its own {@code onScrollChanged} hook (its scrolls do not
 * dispatch window-wide).
 * <p>
 * <b>Programmatic jumps</b> (scroll restore, wikilink heading anchors) must call
 * {@link #resyncScrollBaseline()} afterwards so the jump's own delta is never
 * mistaken for user scrolling.
 */
public final class BarAutoHideHelper {

    private static final String TAG = "BarAutoHide";

    /** Distance (px) from the top of the content below which bars are always shown. */
    private static final int SHOW_AT_TOP_PX = 24;
    /** Fraction of the root view height the covered portion must exceed to count as a keyboard. */
    private static final float IME_HEIGHT_RATIO = 0.15f;
    /** Scroll deltas smaller than this are layout noise (clamping), not user scrolling. */
    private static final int SCROLL_DEAD_ZONE_PX = 12;
    /** How long hide-flips are ignored after we toggled a bar ourselves. */
    private static final long HIDE_GUARD_MS = 400;
    /** Showing is the safe direction — only a short guard against reflow ticks. */
    private static final long SHOW_GUARD_MS = 120;
    /**
     * Sustained down-travel (px) past the last flip point required before the
     * bars hide (reluctant to leave).
     */
    private static final int HIDE_HYSTERESIS_PX = 64;
    /**
     * Up-travel (px) past the last flip point required to show the bars again
     * (eager to return — this is the whole point of the feature).
     */
    private static final int SHOW_HYSTERESIS_PX = 24;
    /** Guard armed by {@link #resyncScrollBaseline()} to swallow the jump's own delta. */
    private static final long RESYNC_GUARD_MS = 150;

    /** Provides the scroll offset and an absolute scroll target for whichever
     * view is currently the content scroller. */
    public interface ScrollController {
        int getScrollY();

        void scrollTo(int y);
    }

    private final Activity _activity;
    private final View _fragmentRoot;
    private final ScrollController _scrollController;

    private ViewTreeObserver.OnScrollChangedListener _scrollListener;
    private ViewTreeObserver.OnGlobalLayoutListener _layoutListener;

    private boolean _attached = false;
    private boolean _barsShown = true;
    private boolean _bottomBarAllowed = true;
    private boolean _previewMode = false;
    private boolean _imeVisible = false;
    private int _lastScrollY = 0;
    /** Scroll offset at the last bar visibility change; hysteresis is measured from here. */
    private int _anchorY = 0;
    private long _guardUntilMs = 0;
    private boolean _topShown = true;
    private long _lastTickLogMs = 0;

    public BarAutoHideHelper(final Activity activity, final View fragmentRoot, final ScrollController scrollController) {
        _activity = activity;
        _fragmentRoot = fragmentRoot;
        _scrollController = scrollController;
    }

    /** Begin listening; safe to call once per fragment view creation. */
    public void attach() {
        if (_attached) {
            return;
        }
        _attached = true;
        _scrollListener = this::onScrollChanged;
        _layoutListener = this::onGlobalLayout;
        _fragmentRoot.getViewTreeObserver().addOnScrollChangedListener(_scrollListener);
        _fragmentRoot.getViewTreeObserver().addOnGlobalLayoutListener(_layoutListener);
    }

    /** Stop listening and restore both bars; call from onDestroyView. */
    public void detach() {
        if (!_attached) {
            return;
        }
        _attached = false;
        final ViewTreeObserver vto = _fragmentRoot.getViewTreeObserver();
        if (vto.isAlive()) {
            if (_scrollListener != null) {
                vto.removeOnScrollChangedListener(_scrollListener);
            }
            if (_layoutListener != null) {
                vto.removeOnGlobalLayoutListener(_layoutListener);
            }
        }
        setTopBarShown(true);
        setBottomBarShown(true);
    }

    /** Must be called whenever preview (view) mode is toggled; resets scroll tracking. */
    public void setPreviewMode(final boolean preview) {
        if (_previewMode != preview) {
            _previewMode = preview;
            _lastScrollY = scrollY();
            _anchorY = _lastScrollY;
            _barsShown = true;
            applyAll();
        }
    }

    /**
     * Whether the bottom text-actions bar may be shown at all
     * (user preference AND has action buttons).
     */
    public void setBottomBarAllowed(final boolean allowed) {
        _bottomBarAllowed = allowed;
        applyAll();
    }

    private int scrollY() {
        try {
            return _scrollController.getScrollY();
        } catch (Exception ignored) {
            return _lastScrollY;
        }
    }

    /**
     * Evaluate hide/show state from the current scroll position.
     * Called both from the window scroll listener and from scroll hooks of
     * views whose scrolling does not dispatch window-wide (e.g. the WebView).
     * <p>
     * The decision is anchored with <b>asymmetric hysteresis</b>: hiding needs
     * {@link #HIDE_HYSTERESIS_PX} of sustained down-travel past the previous
     * flip, showing needs only {@link #SHOW_HYSTERESIS_PX} up-travel. Toggling
     * a bar reflows the layout and thereby induces scroll deltas all by itself
     * (viewport clamping, editor cursor re-centering); with plain per-tick
     * direction voting those self-induced deltas flip the state back, producing
     * an endless jitter loop (the "vibrating screen"). The guards below break
     * that loop — and because showing the bars is the harmless direction, its
     * guard and hysteresis are much lighter, so bars come back eagerly.
     */
    public void onScrollTick() {
        final int y = scrollY();
        final int dy = y - _lastScrollY;
        _lastScrollY = y;
        if (android.os.SystemClock.uptimeMillis() < _guardUntilMs) {
            return;
        }
        if (Math.abs(dy) < SCROLL_DEAD_ZONE_PX) {
            return;
        }
        if (y <= SHOW_AT_TOP_PX) {
            setBarsShown(true);
        } else if (_barsShown && dy > 0 && y - _anchorY >= HIDE_HYSTERESIS_PX) {
            setBarsShown(false);
        } else if (!_barsShown && dy < 0 && _anchorY - y >= SHOW_HYSTERESIS_PX) {
            setBarsShown(true);
        }
        logTick(y, dy);
    }

    /**
     * Actually change the scroll-driven bar state. Arms the direction-specific
     * toggle guard and re-anchors the hysteresis point, then re-syncs the scroll
     * baseline once the reflow triggered by this very change has settled, so its
     * delta can never be mistaken for user scrolling.
     */
    private void setBarsShown(final boolean shown) {
        if (_barsShown == shown) {
            return;
        }
        _barsShown = shown;
        _anchorY = scrollY();
        _guardUntilMs = android.os.SystemClock.uptimeMillis() + (shown ? SHOW_GUARD_MS : HIDE_GUARD_MS);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "bars " + (shown ? "SHOWN" : "hidden") + " at y=" + _anchorY
                    + " preview=" + _previewMode + " ime=" + _imeVisible);
        }
        applyAll();
        _fragmentRoot.post(() -> {
            if (_attached) {
                _lastScrollY = scrollY();
                _anchorY = scrollY();
            }
        });
    }

    /**
     * tsun-markor fork: re-anchor the scroll baseline after a <b>programmatic</b>
     * scroll jump (scroll restore, wikilink heading anchor, …). Without this the
     * jump's large single-tick delta leaves a stale/phantom anchor and the next
     * real user scroll needs an unreasonable distance before the bars react.
     */
    public void resyncScrollBaseline() {
        if (!_attached) {
            return;
        }
        _lastScrollY = scrollY();
        _anchorY = _lastScrollY;
        _guardUntilMs = Math.max(_guardUntilMs, android.os.SystemClock.uptimeMillis() + RESYNC_GUARD_MS);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "resync baseline to y=" + _anchorY + " preview=" + _previewMode);
        }
    }

    /**
     * tsun-markor: single place deciding bar visibility.
     * <p>
     * The top bar is scroll-driven in <b>both</b> modes ({@code _barsShown});
     * while editing with the soft keyboard open it additionally stays hidden
     * regardless of scroll, keeping the typing space clean. The original
     * hesitation to scroll-toggle the top bar in edit mode (a toggle reflows
     * the layout and the focused editor scrolls its cursor back into view,
     * inducing scroll deltas) is contained by the guard + hysteresis +
     * post-reflow re-sync, exactly like for the bottom bar — which has been
     * scroll-driven in edit mode all along without oscillating.
     */
    private void applyAll() {
        final boolean topVisible = _barsShown && !(!_previewMode && _imeVisible);
        setTopBarShown(topVisible);
        setBottomBarShown(_barsShown);
    }

    private void onScrollChanged() {
        onScrollTick();
    }

    private void onGlobalLayout() {
        final Rect visible = new Rect();
        _fragmentRoot.getWindowVisibleDisplayFrame(visible);
        final int rootHeight = _fragmentRoot.getRootView().getHeight();
        final boolean imeVisible = (rootHeight - visible.bottom) > rootHeight * IME_HEIGHT_RATIO;
        if (imeVisible != _imeVisible) {
            _imeVisible = imeVisible;
            _lastScrollY = scrollY();
            _anchorY = _lastScrollY;
            if (!_previewMode) {
                _barsShown = true;
            }
            applyAll();
        }
    }

    private void setTopBarShown(final boolean shown) {
        if (_topShown == shown) {
            return;
        }
        _topShown = shown;
        if (isAlive()) {
            final View toolbar = _activityToolbar();
            if (toolbar != null && toolbar.getParent() instanceof View) {
                ((View) toolbar.getParent()).setVisibility(shown ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void setBottomBarShown(final boolean shown) {
        if (!isAlive()) {
            return;
        }
        final View parent = _fragmentRoot.findViewById(net.gsantner.markor.R.id.document__fragment__edit__text_actions_bar__scrolling_parent);
        if (parent != null) {
            parent.setVisibility(_bottomBarAllowed && shown ? View.VISIBLE : View.GONE);
        }
    }

    private View _activityToolbar() {
        return _activity == null ? null : _activity.findViewById(net.gsantner.markor.R.id.toolbar);
    }

    private boolean isAlive() {
        return _attached && _fragmentRoot != null && _fragmentRoot.isAttachedToWindow();
    }

    private void logTick(final int y, final int dy) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        final long now = android.os.SystemClock.uptimeMillis();
        if (now - _lastTickLogMs < 250) {
            return;
        }
        _lastTickLogMs = now;
        Log.d(TAG, "tick y=" + y + " dy=" + dy + " shown=" + _barsShown
                + " anchor=" + _anchorY + " preview=" + _previewMode + " ime=" + _imeVisible);
    }
}
