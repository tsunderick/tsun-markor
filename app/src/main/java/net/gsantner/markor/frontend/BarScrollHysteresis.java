/*#######################################################
 *
 *  tsun-markor fork: ratcheting hysteresis for auto-hiding bars
 *  License of this file: Apache 2.0
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.frontend;

/**
 * tsun-markor fork: pure (framework-free) scroll-direction hysteresis deciding
 * whether the auto-hiding bars should currently be shown. Extracted from
 * {@code BarAutoHideHelper} so the decision core is unit-testable without
 * Android.
 * <p>
 * <b>Ratcheting (extreme-tracking) hysteresis.</b> The anchor is not a static
 * "last flip point" — it continuously ratchets toward the extreme of the
 * current state:
 * <ul>
 *     <li>While the bars are <b>shown</b>, the anchor tracks the
 *     <b>highest</b> point reached ({@code anchor = min(anchor, y)}); hiding
 *     requires {@link #HIDE_HYSTERESIS_PX} of sustained down-travel below
 *     that high-water mark.</li>
 *     <li>While the bars are <b>hidden</b>, the anchor tracks the
 *     <b>deepest</b> point reached ({@code anchor = max(anchor, y)}); showing
 *     needs only {@link #SHOW_HYSTERESIS_PX} of up-travel above that
 *     low-water mark.</li>
 * </ul>
 * Why ratcheting: a static anchor re-set only at flips goes stale when scroll
 * <i>continues in the same direction past the flip</i> — which is exactly what
 * happens with momentum flings. The bars hid 64px into a fast downward fling,
 * the scroller then coasted thousands of pixels further, and the show
 * condition ("24px above the stale anchor") effectively demanded scrolling
 * all the way back up to near the hide point — in practice, to the very top
 * of the document. Ratcheting the anchor to the extreme makes the decision
 * relative to <i>wherever the user currently is</i>: any genuine 24px of
 * up-travel reveals the bars.
 * <p>
 * The ratchet also replaces the old per-tick scroll dead zone: oscillating
 * layout/clamp noise is self-limiting against a ratchet (the anchor only ever
 * moves toward the extreme, so a jitter of &plusmn;5px cannot accumulate into
 * a flip), while genuinely sustained slow scrolling — whose per-frame deltas
 * are tiny and were previously swallowed whole by the dead zone — accumulates
 * naturally against the ratcheted anchor. Directional guards against
 * self-induced reflow remain the caller's ({@code BarAutoHideHelper}) job.
 */
public final class BarScrollHysteresis {

    /** Distance (px) from the top of the content below which bars are always shown. */
    public static final int SHOW_AT_TOP_PX = 24;
    /**
     * Sustained down-travel (px) below the highest point since the bars were
     * shown required before the bars hide (reluctant to leave).
     */
    public static final int HIDE_HYSTERESIS_PX = 64;
    /**
     * Up-travel (px) above the deepest point since the bars were hidden
     * required to show the bars again (eager to return).
     */
    public static final int SHOW_HYSTERESIS_PX = 24;

    /** Verdict of a scroll tick; the caller applies SHOW/HIDE flips. */
    public enum Decision {
        /** Bars should be (re-)shown. */
        SHOW,
        /** Bars should be hidden. */
        HIDE,
        /** No change. */
        KEEP
    }

    private int _anchorY = 0;
    private boolean _barsShown = true;

    /**
     * Evaluate a scroll tick. Ratchets the anchor toward the current state's
     * extreme, then compares the distance travelled past it against the
     * state's hysteresis threshold. The absolute-top rule always wins.
     *
     * @param y current scroll offset of the content scroller
     * @return the verdict for this tick
     */
    public Decision onScroll(final int y) {
        if (y <= SHOW_AT_TOP_PX) {
            return Decision.SHOW;
        }
        if (_barsShown) {
            _anchorY = Math.min(_anchorY, y);
            return (y - _anchorY >= HIDE_HYSTERESIS_PX) ? Decision.HIDE : Decision.KEEP;
        } else {
            _anchorY = Math.max(_anchorY, y);
            return (_anchorY - y >= SHOW_HYSTERESIS_PX) ? Decision.SHOW : Decision.KEEP;
        }
    }

    /**
     * Adopt a state change decided by the caller (the tick that returned
     * SHOW/HIDE); re-anchors the hysteresis at the given offset so tracking
     * of the new state's extreme starts fresh here.
     */
    public void setState(final int y, final boolean shown) {
        _anchorY = y;
        _barsShown = shown;
    }

    /**
     * Move the anchor to the given offset while keeping the current state.
     * For programmatic jumps (scroll restore, anchor navigation) and
     * post-reflow re-syncs, so the jump's own delta is never mistaken for
     * user scrolling.
     */
    public void reanchor(final int y) {
        _anchorY = y;
    }

    /** Current hysteresis anchor (extreme tracked for the current state). */
    public int anchorY() {
        return _anchorY;
    }

    /** Whether the bars are currently meant to be shown. */
    public boolean barsShown() {
        return _barsShown;
    }
}
