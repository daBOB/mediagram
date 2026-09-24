package ui

import androidx.compose.runtime.MutableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The saved stack is one string with control-character separators, read
 * back on every recomposition and after a killed process — never trusted
 * to be well-formed. A pasted query holding one of those separators used
 * to split into a token `decode` could not destructure, throwing on every
 * redraw; an unknown frame kind (an older or newer build's own saved
 * string) threw the same way through `FrameKind.valueOf`.
 */
class LibraryPositionsEncodingTest {

    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private fun positions(raw: String) = LibraryPositions(Slot(raw))

    @Test
    fun aStringWithNoFieldSeparatorIsSkippedRatherThanThrown() {
        assertNull(positions("garbage with no separator at all").top)
    }

    @Test
    fun anUnknownFrameKindIsSkippedRatherThanThrown() {
        assertNull(positions("NOT_A_REAL_KIND\u001Fpayload").top)
    }

    @Test
    fun anUnknownMenuScreenNameAnswersNullRatherThanThrowing() {
        val at = positions("MENU\u001FNotARealScreen")
        assertEquals(FrameKind.MENU, at.top)
        assertNull(at.menuScreen)
    }

    /** A frame separator typed or pasted into the query is stripped, not saved raw. */
    @Test
    fun aFrameSeparatorTypedIntoSearchIsStrippedBeforeItIsSaved() {
        val at = positions("")
        at.openSearch()
        at.typeSearch("a\u001Eb\u001Fc")

        assertEquals(FrameKind.SEARCH, at.top)
        assertEquals("abc", at.search)
    }
}
