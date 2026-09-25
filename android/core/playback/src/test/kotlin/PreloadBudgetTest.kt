package playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreloadBudgetTest {

    private val budget = 1_000L

    @Test
    fun fitsWhenEverythingTogetherStaysUnderTheFraction() {
        // 100 held + 200 current + 400 candidate = 700, three quarters of 1000 is 750.
        assertTrue(fitsInPreloadBudget(heldBytes = 100, currentBytes = 200, candidateBytes = 400, budgetBytes = budget))
    }

    @Test
    fun skipsWhenTheCandidateWouldCrossTheFraction() {
        // 100 held + 200 current + 500 candidate = 800, past the 750 line.
        assertFalse(fitsInPreloadBudget(heldBytes = 100, currentBytes = 200, candidateBytes = 500, budgetBytes = budget))
    }

    @Test
    fun exactlyOnTheFractionFits() {
        assertTrue(fitsInPreloadBudget(heldBytes = 0, currentBytes = 0, candidateBytes = 750, budgetBytes = budget))
    }

    @Test
    fun theCurrentlyPlayingTitleIsCountedEvenWhenNothingIsHeldYet() {
        // A fresh cache with nothing on disk still reserves the whole of
        // the playing title — a big enough one alone can push a small
        // candidate over the line.
        assertFalse(fitsInPreloadBudget(heldBytes = 0, currentBytes = 600, candidateBytes = 200, budgetBytes = budget))
    }

    @Test
    fun aZeroBudgetFitsNothing() {
        assertFalse(fitsInPreloadBudget(heldBytes = 0, currentBytes = 0, candidateBytes = 1, budgetBytes = 0))
    }
}
