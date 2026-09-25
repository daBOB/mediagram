package ui

import androidx.compose.runtime.MutableState
import catalog.MenuScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The reason [LibraryPositions] is a stack rather than one slot per kind: a
 * screen can be opened over another already open over a third, and the
 * same kind can recur — a title opened from a genre page opened from
 * another title — without the inner one erasing the outer one's own key.
 * [LibraryPositionsTest] covers every hop that never nests; this covers the
 * ones that do.
 */
class LibraryPositionsBackStackTest {

    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private fun positions(frames: MutableState<String> = Slot("")) = LibraryPositions(frames)

    /** Search opened from a menu screen shows at once, rather than only once the menu is later left. */
    @Test
    fun openingSearchFromAMenuScreenShowsItOverTheMenu() {
        val at = positions()
        at.openMenu(MenuScreen.System)

        at.openSearch()

        assertEquals(FrameKind.SEARCH, at.top)

        at.pop()
        assertEquals(FrameKind.MENU, at.top)
        at.pop()
        assertNull(at.top)
    }

    /** A hit played from search leaves back to the search screen, not to whatever search itself sat over. */
    @Test
    fun playingAResultFromSearchLeavesBackToSearch() {
        val at = positions()
        at.openTitle("set-1")
        at.openSearch()
        at.openPlayer("set-2")

        assertEquals(FrameKind.PLAYER, at.top)

        at.pop()
        assertEquals(FrameKind.SEARCH, at.top)

        at.pop()
        assertEquals(FrameKind.TITLE, at.top)
        assertEquals("set-1", at.titleId)
    }

    /** Typing does not push a new screen — the field's own text updates in place. */
    @Test
    fun typingInSearchUpdatesItsTextWithoutPushingANewFrame() {
        val at = positions()
        at.openSearch()
        at.typeSearch("s")
        at.typeSearch("steuer")

        assertEquals(FrameKind.SEARCH, at.top)
        assertEquals("steuer", at.search)

        at.pop()
        assertNull(at.top)
    }

    /** The genre page's own cards: a film opens a title and leaves back to the genre page, not past it. */
    @Test
    fun aTitleOpenedFromAGenrePageLeavesBackToThatGenrePage() {
        val at = positions()
        at.openTitle("set-1")
        at.openGenre("Krimi")
        at.openTitle("set-2")

        assertEquals(FrameKind.TITLE, at.top)
        assertEquals("set-2", at.titleId)

        at.pop()
        assertEquals(FrameKind.GENRE, at.top)
        assertEquals("Krimi", at.genre)

        at.pop()
        assertEquals(FrameKind.TITLE, at.top)
        assertEquals("set-1", at.titleId)
    }

    /** A series card on a genre page opens its collection, and leaves back to the genre page the same way. */
    @Test
    fun aCollectionOpenedFromAGenrePageLeavesBackToThatGenrePage() {
        val at = positions()
        at.openGenre("Drama")
        at.openCollection("30-rock")

        assertEquals(FrameKind.COLLECTION, at.top)

        at.pop()
        assertEquals(FrameKind.GENRE, at.top)
        assertEquals("Drama", at.genre)
    }

    /** A genre page opened from a title leaves back to that title, the mirror image of the case above. */
    @Test
    fun aGenrePageOpenedFromATitleLeavesBackToThatTitle() {
        val at = positions()
        at.openTitle("set-1")
        at.openGenre("Krimi")

        at.pop()

        assertEquals(FrameKind.TITLE, at.top)
        assertEquals("set-1", at.titleId)
    }

    /**
     * What a killed process hands back: the same saved string, read into a
     * fresh [LibraryPositions]. The stack has to resolve from it exactly as
     * it did before, since nothing else survived to say what order these
     * screens were opened in.
     */
    @Test
    fun theStackSurvivesBeingRebuiltFromItsSavedField() {
        val frames = Slot("")
        val before = positions(frames)
        before.openGenre("Krimi")
        before.openTitle("set-1")

        val after = positions(frames)

        assertEquals(FrameKind.TITLE, after.top)
        after.pop()
        assertEquals(FrameKind.GENRE, after.top)
    }
}
