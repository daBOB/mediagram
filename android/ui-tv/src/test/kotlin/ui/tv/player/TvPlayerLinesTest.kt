package ui.tv.player

import org.junit.Test
import kotlin.test.assertEquals

/**
 * The player's own steps, apart from the screen: how far a held arrow
 * moves the film. What the top bar calls a title is the shared
 * `player.titleLine`, and the end time beside the clock the shared
 * `player.endsLine`, each tested where it lives.
 */
class TvPlayerLinesTest {
    @Test
    fun aTapOrShortHoldSkipsTenSeconds() {
        assertEquals(-10, seekStepSeconds(-10, repeatCount = 0))
        assertEquals(10, seekStepSeconds(10, repeatCount = 19))
    }

    @Test
    fun aLongerHoldSkipsFurtherEachRepeat() {
        assertEquals(30, seekStepSeconds(10, repeatCount = 20))
        assertEquals(-60, seekStepSeconds(-10, repeatCount = 60))
    }
}
