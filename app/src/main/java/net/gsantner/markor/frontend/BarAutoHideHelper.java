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
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * tsun-markor fork: hides the activity's top app bar and the fragment's bottom
 * text-actions bar based on scroll direction and soft-keyboard state.
 * <p>
 * Rules:
 * <ul>
 *     <li>Scroll down &rarr; hide both bars; scroll up &rarr; show them again;
 *     near the very top of the content &rarr; always show.</li>
 *     <li>While editing with the soft keyboard open, the top bar is hidden and the
 *     bottom action bar kept, maximizing typing space without losing the buttons.
 *     Scroll events are ignored while the keyboard is open, so cursor auto-scrolling
 *     does not fight the user.</li>
 *     <li>The bottom bar is only ever shown when the user's action-bar preference
 *     allows it (see {@code setBottomBarAllowed}).</li>
 * </ul>
 * Scroll detection uses a window-level {@link ViewTreeObserver.OnScrollChangedListener},
 * which fires for any view scrolling in this window - covering both the edit-mode
 * {@code DraggableScrollbarScrollView} and the view-mode {@code WebView}.
 */
public final class BarAutoHideHelper {

    /** Distance (px) from the top of the content below which bars are always shown. */
    private static final int SHOW_AT_TOP_PX = 24;
    /** Fraction of the root view height the covered portion must exceed to count as a keyboard. */
    private static final float IME_HEIGHT_RATIO = 0.15f;
    /** Scroll deltas smaller than this are layout noise (clamping), not user scrolling. */
    private static final int SCROLL_DEAD_ZONE_PX = 12;
    /** How long scroll ticks are ignored after we toggled a bar ourselves. */
    private static final long TOGGLE_GUARD_MS = 250;

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
    private long _guardUntilMs = 0;
    private boolean _topShown = true;

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
            _barsShown = true;
        } else if (dy > 0) {
            _barsShown = false;
        } else if (dy < 0) {
            _barsShown = true;
        }
        applyAll();
    }

    /**
     * tsun-markor: single place deciding bar visibility.
     * <p>
     * The TOP bar in edit mode is deliberately <b>not</b> scroll-toggled: a
     * {@code GONE}/{@code VISIBLE} toggle reflows the layout, and the focused
     * editor then scrolls its cursor back into view, yanking the scroll position
     * to the selection (the "jumps to the bottom" bug - the editor even documents
     * that reflow "will bring focus back to the cursor and reset scroll
     * position"). Instead the top bar in edit mode follows only the keyboard
     * state. The bottom bar grows the viewport downward when hidden, which
     * cannot push the cursor out of view, so it stays scroll-driven in both
     * modes; the toggle guard swallows the small clamp delta its reflow produces.
     */
    private void applyAll() {
        final boolean topVisible = _previewMode ? _barsShown : !_imeVisible;
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

    private void applyBottomVisibility(final boolean shown) {
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
}
