package ui

import player.PlayerUiState
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
        assertTrue(controlsShouldFade(isPlaying = true))
    }

    /** The tap that would bring them back is the one a viewer cannot see. */
    @Test
    fun aPausedPictureKeepsThemOrThereIsNoWayOut() {
        assertFalse(controlsShouldFade(isPlaying = false))
    }
}
