package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrDefaultTest {
    @Test
    fun aBlockThatAnswersGivesItsValue() = runTest {
        assertEquals(7, orDefault(0) { 7 })
    }

    @Test
    fun aBlockThatThrowsGivesTheDefault() = runTest {
        assertEquals(0, orDefault(0, "count") { throw IllegalStateException("no core") })
    }

    /** A cancelled caller must stop, not carry on as if the work had merely failed. */
    @Test
    fun cancellationIsRethrownNotTurnedIntoTheDefault() = runTest {
        assertFailsWith<CancellationException> {
            orDefault(0) { throw CancellationException("stopped") }
        }
    }
}
