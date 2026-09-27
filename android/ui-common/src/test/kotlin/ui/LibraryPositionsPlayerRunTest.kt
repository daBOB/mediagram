package ui

import androidx.compose.runtime.MutableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A player frame's run is there only when the viewer picked it by hand — a
 * list or the Kids wall — and an up-next switch must not change that: a
 * show's own run is worked out from the catalog again, and a set [run] is
 * what tells the player not to preload.
 */
class LibraryPositionsPlayerRunTest {

    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private fun positions() = LibraryPositions(Slot(""))

    @Test
    fun aTitleOpenedFromItsShowStaysWithoutARunAcrossASwitch() {
        val at = positions()
        at.openPlayer("e1")
        at.replacePlayer("e2", listOf("e1", "e2", "e3"))
        assertEquals("e2", at.setId)
        assertNull(at.run)
    }

    @Test
    fun aListKeepsItsRunAcrossASwitch() {
        val at = positions()
        at.openPlayer("a", listOf("a", "b", "c"))
        at.replacePlayer("b", listOf("a", "b", "c"))
        assertEquals("b", at.setId)
        assertEquals(listOf("a", "b", "c"), at.run)
    }
}
