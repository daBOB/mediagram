package catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the position holder's six keys. [resolve] and [leave]
 * — the branch priority and the back order — are covered by
 * [LibraryPositionsResolveTest]; these only pin how the keys themselves
 * behave.
 */
class LibraryPositionsTest {
    /**
     * The one that was wrong. Held as a flag each, asking for the key
     * screen from the system screen set both; the branch that renders them
     * tested the system flag first, so nothing changed on screen, and back
     * then cleared it and dropped the viewer on the key screen they had
     * stopped asking for. Reading `Holds: 0 posters` and going to the menu
     * for the key is the ordinary way to arrive there.
     */
    @Test
    fun askingForOneMenuScreenFromTheOtherMovesToIt() {
        val at = LibraryPositions()
        at.menuScreen = MenuScreen.System

        at.menuScreen = MenuScreen.TmdbKey

        assertEquals(Destination.TmdbKey, at.menuScreen?.destination)
    }

    /** A menu screen sits over the library, not in it: leaving one uncovers what it covered. */
    @Test
    fun leavingAMenuScreenUncoversTheTitleItOpenedOver() {
        val at = LibraryPositions()
        at.titleId = "set-1"
        at.menuScreen = MenuScreen.System

        at.menuScreen = null

        assertNull(at.menuScreen)
        assertEquals("set-1", at.titleId)
    }

    /** Asked for the library from anywhere, the library is what is shown — every other position goes. */
    @Test
    fun theCatalogIsReachedByLeavingEveryOtherPositionAtOnce() {
        val at = LibraryPositions()
        at.collection = "spartacus"
        at.season = "Season 1"
        at.titleId = "set-1"
        at.setId = "set-1"
        at.listId = "list-1"
        at.menuScreen = MenuScreen.TmdbKey

        at.toCatalog()

        assertNull(at.setId)
        assertNull(at.titleId)
        assertNull(at.collection)
        assertNull(at.season)
        assertNull(at.listId)
        assertNull(at.menuScreen)
    }

    /** An open list is its own position, untouched by opening and leaving a title over it. */
    @Test
    fun anOpenListSurvivesATitleOpenedOverIt() {
        val at = LibraryPositions()
        at.listId = "list-1"
        at.titleId = "set-1"

        at.titleId = null

        assertEquals("list-1", at.listId)
    }
}
