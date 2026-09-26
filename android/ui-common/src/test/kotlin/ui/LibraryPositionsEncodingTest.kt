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

    /**
     * A stack a build before [FrameKind.PERSON] and its siblings wrote is
     * still exactly the stack it was: encoding keys every frame by
     * [FrameKind.name], never by its ordinal, so widening the enum after the
     * fact — the whole point of this phase's own frames — never shifts what
     * an already-saved token names.
     */
    @Test
    fun aStackSavedBeforeTheNewFramesExistedRestoresUnchanged() {
        val at = positions("COLLECTION\u001Fspartacus\u001ESEASON\u001FSeason 1\u001ETITLE\u001Fset-1")

        assertEquals(FrameKind.TITLE, at.top)
        assertEquals("set-1", at.titleId)
        assertEquals("spartacus", at.collection)
        assertEquals("Season 1", at.season)

        at.pop()
        assertEquals(FrameKind.SEASON, at.top)
        at.pop()
        assertEquals(FrameKind.COLLECTION, at.top)
    }
}
