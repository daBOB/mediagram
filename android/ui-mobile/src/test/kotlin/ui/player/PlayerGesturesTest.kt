package ui.player

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which third of the screen a double tap lands in, and what a double tap on
 * either outer third does: seek by the player's own increment — the same
 * fifteen seconds the card's −15 and +15 read off it — never a second number
 * kept here.
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

    @Test
    fun aDoubleTapOnTheLeftSeeksBackByThePlayersOwnSkip() {
        val player = mockk<Player>(relaxed = true) { every { seekBackIncrement } returns 15_000L }

        val flash = handleDoubleTap(player, tapX = 100f, width = width, previous = null)

        verify { player.seekBack() }
        assertEquals(SeekZone.BACK, flash?.zone)
        assertEquals(15L, flash?.totalSeconds)
    }

    @Test
    fun aDoubleTapOnTheRightSeeksForwardByThePlayersOwnSkip() {
        val player = mockk<Player>(relaxed = true) { every { seekForwardIncrement } returns 15_000L }

        val flash = handleDoubleTap(player, tapX = 1_100f, width = width, previous = null)

        verify { player.seekForward() }
        assertEquals(SeekZone.FORWARD, flash?.zone)
        assertEquals(15L, flash?.totalSeconds)
    }
}
