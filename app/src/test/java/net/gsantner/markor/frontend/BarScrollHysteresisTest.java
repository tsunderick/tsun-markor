/*#######################################################
 *
 *  tsun-markor fork: ratcheting hysteresis tests for the auto-hiding bars
 *  License of this file: Apache 2.0
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.frontend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.gsantner.markor.frontend.BarScrollHysteresis.Decision;

import org.junit.Test;

/**
 * Simulates tick sequences (one {@code onScroll} call per scroll event) against
 * {@link BarScrollHysteresis}. The caller contract mirrored here: whenever a
 * tick returns SHOW/HIDE, the consumer applies the flip via
 * {@code setState(y, shown)} before the next tick.
 */
public class BarScrollHysteresisTest {

    private static void tickSequence(final BarScrollHysteresis h, final Decision expected, final int... ys) {
        for (final int y : ys) {
            assertEquals(expected, h.onScroll(y));
        }
    }

    /** The reported bug: bars hid early in a downward fling, the fling coasted far
     * past the hide point, and the bars then only came back near the very top. */
    @Test
    public void flingPastHidePointThenScrollUpSlightlyShows() {
        final BarScrollHysteresis h = new BarScrollHysteresis();
        h.setState(0, true);

        // Scroll down a little (above top zone): not enough to hide yet.
        tickSequence(h, Decision.KEEP, 30, 45, 60);
        // 64px+ of down-travel hides; consumer applies the flip at y=70.
        assertEquals(Decision.HIDE, h.onScroll(70));
        h.setState(70, false);

        // Momentum fling coasts far past the hide point - no flips allowed.
        tickSequence(h, Decision.KEEP, 120, 500, 1200, 2000, 3000);
        assertFalse(h.barsShown());
        // Anchor ratcheted along with the deepest point instead of freezing at 70.
        assertEquals(3000, h.anchorY());

        // Scroll up just 25px from wherever the user is -> bars come back.
        tickSequence(h, Decision.KEEP, 2995, 2990);
        assertEquals(Decision.SHOW, h.onScroll(2975));
        h.setState(2975, true);
        assertTrue(h.barsShown());
    }

    /** Regression: a per-tick dead zone used to swallow the tiny per-frame deltas
     * of slow drags whole; against the ratchet they must accumulate. */
    @Test
    public void slowUpwardDragAccumulatesToShow() {
        final BarScrollHysteresis h = new BarScrollHysteresis();
        h.setState(500, false);

        // -5px per tick: 5, 10, 15, 20 accumulated - not yet.
        tickSequence(h, Decision.KEEP, 495, 490, 485, 480);
        // 25px of cumulative up-travel: eager show fires.
        assertEquals(Decision.SHOW, h.onScroll(475));
    }

    /** Slow downward drag accumulates against the high-water mark until the
     * reluctant hide fires. */
    @Test
    public void slowDownwardDragAccumulatesToHide() {
        final BarScrollHysteresis h = new BarScrollHysteresis();
        h.setState(0, true);

        // +5px per tick well below top zone; 60px is not yet 64.
        tickSequence(h, Decision.KEEP, 30, 35, 40, 45, 50, 55, 60);
        assertEquals(Decision.HIDE, h.onScroll(65));
    }

    /** Oscillating clamp/layout noise is self-limiting against the ratchet: the
     * anchor only moves toward the extreme, so jitter can never accumulate into
     * a flip (the reason the old per-tick dead zone existed). */
    @Test
    public void oscillatingNoiseNeverFlips() {
        final BarScrollHysteresis shown = new BarScrollHysteresis();
        shown.setState(500, true);
        tickSequence(shown, Decision.KEEP, 505, 495, 505, 495, 505, 496, 504, 495);
        assertTrue(shown.barsShown());

        final BarScrollHysteresis hidden = new BarScrollHysteresis();
        hidden.setState(500, false);
        tickSequence(hidden, Decision.KEEP, 495, 505, 495, 505, 495, 504, 496, 505);
        assertFalse(hidden.barsShown());
    }

    /** Near the very top of the content the bars are always shown, regardless
     * of state or direction. */
    @Test
    public void absoluteTopAlwaysShows() {
        final BarScrollHysteresis h = new BarScrollHysteresis();
        h.setState(3000, false);
        assertEquals(Decision.SHOW, h.onScroll(BarScrollHysteresis.SHOW_AT_TOP_PX));
        assertEquals(Decision.SHOW, h.onScroll(0));
    }

    /** After a show flip, hiding again requires fresh sustained down-travel -
     * no instant flapping back. */
    @Test
    public void rehideAfterShowRequiresFreshDowntravel() {
        final BarScrollHysteresis h = new BarScrollHysteresis();
        h.setState(0, true);
        assertEquals(Decision.HIDE, h.onScroll(70));
        h.setState(70, false);
        assertEquals(Decision.SHOW, h.onScroll(45));
        h.setState(45, true);

        // A little bob downward is not enough.
        tickSequence(h, Decision.KEEP, 50, 60, 70, 80);
        // 64px below the high-water mark (45) -> 109+ hides again.
        assertEquals(Decision.HIDE, h.onScroll(110));
    }

    /** While shown the anchor tracks the highest point; while hidden it tracks
     * the deepest - the ratchet that keeps hysteresis relative to the user's
     * current position. */
    @Test
    public void anchorRatchetsTowardStateExtreme() {
        final BarScrollHysteresis h = new BarScrollHysteresis();
        h.setState(300, true);
        // Downward ticks keep the anchor at the high-water mark...
        assertEquals(Decision.KEEP, h.onScroll(320));
        assertEquals(Decision.KEEP, h.onScroll(310));
        assertEquals(300, h.anchorY());
        // ...and a single upward tick lowers it immediately.
        assertEquals(Decision.KEEP, h.onScroll(280));
        assertEquals(280, h.anchorY());

        assertEquals(Decision.HIDE, h.onScroll(345));
        h.setState(345, false);
        // While hidden the anchor follows the content deeper...
        assertEquals(Decision.KEEP, h.onScroll(400));
        assertEquals(400, h.anchorY());
        // ...and never moves back up on upward ticks.
        assertEquals(Decision.KEEP, h.onScroll(390));
        assertEquals(400, h.anchorY());
    }

    /** reanchor() moves the anchor (programmatic jumps) without touching the
     * state; setState() sets both. */
    @Test
    public void reanchorAndSetStateSemantics() {
        final BarScrollHysteresis h = new BarScrollHysteresis();
        h.setState(0, true);
        assertEquals(Decision.HIDE, h.onScroll(70));
        h.setState(70, false);

        h.reanchor(900);
        assertFalse(h.barsShown());
        assertEquals(900, h.anchorY());
        // Post-jump hysteresis is measured from the re-anchored position:
        // 24px+ of up-travel from 900 shows.
        tickSequence(h, Decision.KEEP, 890, 880);
        assertEquals(Decision.SHOW, h.onScroll(875));

        h.setState(0, true);
        assertTrue(h.barsShown());
        assertEquals(0, h.anchorY());
    }

    /** Reflow-tick classifier: finger-speed deltas pass, single-frame jumps
     * of reflow-compensation magnitude do not (either sign — compensation
     * direction depends on which bars toggled). */
    @Test
    public void reflowTickClassification() {
        // Typical slow/normal drag per-frame deltas: user scrolling.
        for (final int dy : new int[]{0, 1, 3, 5, 12, 20, 40, 60, 72}) {
            assertFalse("dy=" + dy + " must be user scrolling", BarScrollHysteresis.isReflowTick(dy));
            assertFalse("dy=" + -dy + " must be user scrolling", BarScrollHysteresis.isReflowTick(-dy));
        }
        // Reflow/compensation magnitude (bar heights in px): not draggable in one frame.
        for (final int dy : new int[]{73, 80, 100, 150, 280, 300}) {
            assertTrue("dy=" + dy + " must be reflow", BarScrollHysteresis.isReflowTick(dy));
            assertTrue("dy=" + -dy + " must be reflow", BarScrollHysteresis.isReflowTick(-dy));
        }
    }
}
