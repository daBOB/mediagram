package playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilmPreloadBudgetTest {

    private val budget = 1_000L

    @Test
    fun fitsWhenTheFilmAloneStaysUnderTheBudget() {
        assertTrue(fitsFilmPreloadBudget(totalBytes = 1_000, reservedBytes = 0, budgetBytes = budget))
    }

    @Test
    fun aFilmLargerThanTheWholeBudgetNeverFits() {
        assertFalse(fitsFilmPreloadBudget(totalBytes = 1_001, reservedBytes = 0, budgetBytes = budget))
    }

    @Test
    fun whatIsAlreadyHeldDoesNotCountAgainstTheBudget() {
        // An LRU cache with no pinning can evict anything already held,
        // preloads included, so a full cache is not itself a reason to
        // refuse a film that would otherwise fit.
        assertTrue(fitsFilmPreloadBudget(totalBytes = 900, reservedBytes = 0, budgetBytes = budget))
    }

    @Test
    fun theOpenTitleIsReservedInFull() {
        assertFalse(fitsFilmPreloadBudget(totalBytes = 600, reservedBytes = 500, budgetBytes = budget))
        assertTrue(fitsFilmPreloadBudget(totalBytes = 500, reservedBytes = 500, budgetBytes = budget))
    }

    @Test
    fun aZeroBudgetFitsNothing() {
        assertFalse(fitsFilmPreloadBudget(totalBytes = 1, reservedBytes = 0, budgetBytes = 0))
    }
}
