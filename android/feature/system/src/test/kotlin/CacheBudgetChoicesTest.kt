package system

import playback.budgetLadder
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheBudgetChoicesTest {
    @Test
    fun theChoicesAreTheDoublingLadderUpToTheCap() {
        val cap = 4L * 1024 * 1024 * 1024

        assertEquals(budgetLadder(cap), cacheBudgetChoices(cap))
    }
}
