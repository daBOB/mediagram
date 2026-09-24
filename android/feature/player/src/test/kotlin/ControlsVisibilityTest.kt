package player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlsVisibilityTest {
    @Test
    fun thereIsNothingToControlWhileASetIsPreparing() {
        assertFalse(controlsMayShow(PlayerUiState.Preparing))
    }

    @Test
    fun thereIsNothingToControlOverAnError() {
        assertFalse(controlsMayShow(PlayerUiState.Failed("no route to the channel")))
    }

    @Test
    fun aPlayingSetHasControls() {
        assertTrue(controlsMayShow(PlayerUiState.Playing))
    }

    @Test
    fun aPausedSetHasControls() {
        assertTrue(controlsMayShow(PlayerUiState.Paused))
    }

    @Test
    fun aRunningFilmTakesItsControlsBack() {
        assertTrue(controlsShouldFade(isPlaying = true, isScrubbing = false))
    }

    /** The tap that would bring them back is the one a viewer cannot see. */
    @Test
    fun aPausedPictureKeepsThemOrThereIsNoWayOut() {
        assertFalse(controlsShouldFade(isPlaying = false, isScrubbing = false))
    }

    /**
     * Four seconds is easily a long drag. A bar that timed out mid-scrub would
     * take the slider with it and drop the viewer wherever the thumb had got
     * to.
     */
    @Test
    fun aBarBeingDraggedStaysUnderTheThumb() {
        assertFalse(controlsShouldFade(isPlaying = true, isScrubbing = true))
    }
}
