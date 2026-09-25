package ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which third of the screen a double tap lands in — [handleDoubleTap] itself
 * needs a real `Player` to prove anything more, which this module has no
 * fake for; this proves the split it reads off, the same way `PlayerScreenTest`
 * proves `shouldStopOnDispose` rather than `PlayerScreen`'s own wiring.
 */
class PlayerGesturesTest {

    private val width = 1200f

    @Test
    fun theLeftThirdSeeksBack() {
        assertEquals(SeekZone.BACK, seekZoneFor(0f, width))
        assertEquals(SeekZone.BACK, seekZoneFor(399f, width))
    }

    @Test
    fun theRightThirdSeeksForward() {
        assertEquals(SeekZone.FORWARD, seekZoneFor(801f, width))
        assertEquals(SeekZone.FORWARD, seekZoneFor(width, width))
    }

    @Test
    fun theMiddleThirdTogglesPlayPause() {
        assertEquals(SeekZone.PLAY_PAUSE, seekZoneFor(400f, width))
        assertEquals(SeekZone.PLAY_PAUSE, seekZoneFor(600f, width))
        assertEquals(SeekZone.PLAY_PAUSE, seekZoneFor(800f, width))
    }

    /** A width of zero (measured before the first layout pass) is nothing to divide into thirds — the safe answer is the one that does not seek. */
    @Test
    fun aScreenNotYetMeasuredNeverSeeks() {
        assertEquals(SeekZone.PLAY_PAUSE, seekZoneFor(50f, 0f))
    }
}
