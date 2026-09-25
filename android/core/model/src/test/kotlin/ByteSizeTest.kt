package model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a byte count is said, and how what the cache holds is said against its
 * ceiling. Mirrors the web player's `format.js`.
 */
class ByteSizeTest {
    /** Against the real ceiling: CacheProvider's budget is a fixed 2 GiB, which is the whole range this is read over. */
    @Test
    fun heldSpaceIsShownAgainstItsBudget() {
        assertEquals("1.0 GB of 2.0 GB (50%)", heldOfBudget(held = 1_073_741_824, budget = 2_147_483_648))
    }

    @Test
    fun anEmptyCacheStillSaysWhatItMayHold() {
        assertEquals("nothing yet of 2.0 GB", heldOfBudget(held = 0, budget = 2_147_483_648))
    }
}
