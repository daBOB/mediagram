package ui

import androidx.compose.runtime.MutableState
import catalog.Destination
import catalog.MenuScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which screen is on top, and what leaving it uncovers — the model behind
 * [LibraryBranches]. `top` alone decides what is on screen; every other
 * property reads the most recent frame of its own kind, so opening one
 * position never disturbs another already open beneath it.
 *
 * Every existing hop, covered here: catalog → collection → season → title →
 * player, a menu screen, and a hand-built list. The nesting a screen opened
 * over another needs — a title opened from a genre page, search opened from
 * anywhere — is [LibraryPositionsBackStackTest].
 *
 * The saved slot is stood in for by a plain holder: this module's tests are
 * plain JVM ones with no Compose runtime, and what is under test is the
 * rule about positions, not the saving of them.
 */
class LibraryPositionsTest {

    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private fun positions(frames: MutableState<String> = Slot("")) = LibraryPositions(frames)

    @Test
    fun theCatalogIsWhereNothingHasBeenOpenedYet() {
        assertNull(positions().top)
    }

    @Test
    fun openingATitleShowsItAndLeavingReturnsToTheCatalog() {
        val at = positions()
        at.openTitle("set-1")

        assertEquals(FrameKind.TITLE, at.top)
        assertEquals("set-1", at.titleId)

        at.pop()

        assertNull(at.top)
        assertNull(at.titleId)
    }

    /** Every existing hop, still one level at a time: catalog → collection → season → title → player. */
    @Test
    fun eachOfCollectionSeasonTitleAndPlayerLeavesToTheOneBeneathIt() {
        val at = positions()
        at.openCollection("spartacus")
        at.openSeason("Season 1")
        at.openTitle("set-1")
        at.openPlayer("set-1")
        assertEquals(FrameKind.PLAYER, at.top)

        at.pop()
        assertEquals(FrameKind.TITLE, at.top)

        at.pop()
        assertEquals(FrameKind.SEASON, at.top)
        assertEquals("spartacus", at.collection)

        at.pop()
        assertEquals(FrameKind.COLLECTION, at.top)
        assertEquals("spartacus", at.collection)

        at.pop()
        assertNull(at.top)
        assertNull(at.collection)
    }

    /** The one that was wrong: asking for the key screen from the system screen used to set both flags at once. */
    @Test
    fun askingForOneMenuScreenFromTheOtherMovesToIt() {
        val at = positions()
        at.openMenu(MenuScreen.System)

        at.openMenu(MenuScreen.TmdbKey)

        assertEquals(Destination.TmdbKey, at.menuScreen?.destination)
        assertEquals(FrameKind.MENU, at.top)

        // Moved, not stacked: one leave clears the menu entirely.
        at.pop()
        assertNull(at.top)
    }

    @Test
    fun leavingAMenuScreenUncoversTheTitleItOpenedOver() {
        val at = positions()
        at.openTitle("set-1")
        at.openMenu(MenuScreen.System)

        at.pop()

        assertEquals(FrameKind.TITLE, at.top)
        assertEquals("set-1", at.titleId)
    }

    @Test
    fun openingAListLeavesBackToTheCatalog() {
        val at = positions()
        at.openList("list-1")

        assertEquals(FrameKind.LIST, at.top)

        at.pop()
        assertNull(at.top)
        assertNull(at.listId)
    }

    @Test
    fun poppingWithNothingOpenDoesNothing() {
        val at = positions()

        at.pop()

        assertNull(at.top)
    }

    /** Asked for the library from anywhere, the library is what is shown — every position and the stack go. */
    @Test
    fun toCatalogClearsEveryPositionAndTheStack() {
        val at = positions()
        at.openCollection("spartacus")
        at.openSeason("Season 1")
        at.openTitle("set-1")
        at.openPlayer("set-1")

        at.toCatalog()

        assertNull(at.top)
        assertNull(at.setId)
        assertNull(at.titleId)
        assertNull(at.collection)
        assertNull(at.season)
        assertNull(at.listId)
        assertNull(at.search)
        assertNull(at.genre)
        assertNull(at.menuScreen)
    }
}
