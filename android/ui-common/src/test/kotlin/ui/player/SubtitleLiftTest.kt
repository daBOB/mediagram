package ui.player

import org.junit.Test
import kotlin.test.assertEquals

/** How far a cue may rise above the controls before whatever sits along the top stops it. */
class SubtitleLiftTest {
    @Test
    fun aShortCueRisesTheWholeWay() {
        // Resting bottom at 500, 60 tall: its top is at 440; a lift of 200 leaves it at 240, below 100.
        assertEquals(200f, liftUnder(ceiling = 100f, covered = 200f, restingBottom = 500f, height = 60))
    }

    @Test
    fun aTallCueRisesOnlyUntilItsTopMeetsTheCeiling() {
        assertEquals(100f, liftUnder(ceiling = 100f, covered = 200f, restingBottom = 500f, height = 300))
    }

    @Test
    fun aCueTooTallToClearTheCeilingStaysAtRest() {
        assertEquals(0f, liftUnder(ceiling = 100f, covered = 200f, restingBottom = 500f, height = 450))
    }
}
