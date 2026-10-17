package ui.common

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import player.PlayGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The daily limit's door: the library opens the player through [LibraryPositions.openPlayer], and a refused title does not open. */
class LibraryPositionsPlayGateTest {
    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private class Gate(var applies: Boolean = true, var answer: CompletableDeferred<Boolean> = CompletableDeferred(true)) : PlayGate {
        var asked = 0
        override fun applies() = applies
        override suspend fun mayOpen(): Boolean { asked++; return answer.await() }
    }

    private fun positions(gate: Gate) = LibraryPositions(Slot(""), gate, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun aRefusedTitleLeavesTheViewerWhereTheyWere() {
        val at = positions(Gate(answer = CompletableDeferred(false)))
        at.openPlayer("s1")
        assertNull(at.top)
        assertNull(at.setId)
    }

    @Test
    fun anAllowedTitleOpensOnceTheAnswerComes() {
        val gate = Gate(answer = CompletableDeferred())
        val at = positions(gate)
        at.openPlayer("s1")
        assertNull(at.setId)
        gate.answer.complete(true)
        assertEquals("s1", at.setId)
    }

    @Test
    fun aSecondTapWhileAskingDoesNotAskAgain() {
        val gate = Gate(answer = CompletableDeferred())
        val at = positions(gate)
        at.openPlayer("s1")
        at.openPlayer("s2")
        gate.answer.complete(true)
        assertEquals("s1", at.setId)
        assertEquals(1, gate.asked)
    }

    @Test
    fun someoneWithoutALimitOpensAtOnceWithoutBeingAsked() {
        val gate = Gate(applies = false, answer = CompletableDeferred(false))
        val at = positions(gate)
        at.openPlayer("s1", listOf("s1", "s2"))
        assertEquals("s1", at.setId)
        assertEquals(0, gate.asked)
    }
}
