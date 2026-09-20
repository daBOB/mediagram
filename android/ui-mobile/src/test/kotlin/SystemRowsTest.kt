package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How the System screen says a number. Mirrors the web player's status panel:
 * a row whose value is not known is left out entirely rather than shown
 * blank, because a blank row reads as a broken value rather than an absent one.
 */
class SystemRowsTest {

    @Test
    fun heldSpaceIsShownAgainstItsBudget() {
        assertEquals("7.0 GB of 20.0 GB (35%)", heldOfBudget(held = 7_516_192_768, budget = 21_474_836_480))
    }

    @Test
    fun anEmptyCacheStillSaysWhatItMayHold() {
        assertEquals("nothing yet of 2.0 GB", heldOfBudget(held = 0, budget = 2_147_483_648))
    }

    @Test
    fun readsAreAShareAndTheCountsBehindIt() {
        assertEquals("81% from disk (34 hits, 8 misses)", cacheReadsLine(fromCache = 81, fromUpstream = 19, hits = 34, misses = 8))
    }

    /** Before anything has played there is no share to take. */
    @Test
    fun nothingReadYetIsSaidPlainly() {
        assertEquals("nothing read yet", cacheReadsLine(fromCache = 0, fromUpstream = 0, hits = 0, misses = 0))
    }

    /** An absent fact is an omitted row, not an empty one. */
    @Test
    fun aValueNobodyKnowsHasNoRow() {
        assertNull(telegramLine(connected = null))
        assertEquals("connected", telegramLine(connected = true))
        assertEquals("disconnected", telegramLine(connected = false))
    }
}
