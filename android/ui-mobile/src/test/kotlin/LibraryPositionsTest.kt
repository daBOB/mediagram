package ui

import androidx.compose.runtime.MutableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which screen the menu is on, and what leaving it uncovers.
 *
 * The saved slots are stood in for by a plain holder: this module's tests
 * are plain JVM ones with no Compose runtime, and what is under test is the
 * rule about positions, not the saving of them.
 */
class LibraryPositionsTest {

    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private fun positions() = LibraryPositions(
        setId = Slot<String?>(null),
        titleId = Slot<String?>(null),
        collection = Slot<String?>(null),
        menuScreen = Slot<MenuScreen?>(null),
    )

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
        val at = positions()
        at.menuScreen = MenuScreen.System

        at.menuScreen = MenuScreen.TmdbKey

        assertEquals(Destination.TmdbKey, at.menuScreen?.destination)
    }

    /** A menu screen sits over the library, not in it: leaving one uncovers what it covered. */
    @Test
    fun leavingAMenuScreenUncoversTheTitleItOpenedOver() {
        val at = positions()
        at.titleId = "set-1"
        at.menuScreen = MenuScreen.System

        at.menuScreen = null

        assertNull(at.menuScreen)
        assertEquals("set-1", at.titleId)
    }

    /** Asked for the library from anywhere, the library is what is shown — every other position goes. */
    @Test
    fun theCatalogIsReachedByLeavingEveryOtherPositionAtOnce() {
        val at = positions()
        at.collection = "spartacus"
        at.titleId = "set-1"
        at.setId = "set-1"
        at.menuScreen = MenuScreen.TmdbKey

        at.toCatalog()

        assertNull(at.setId)
        assertNull(at.titleId)
        assertNull(at.collection)
        assertNull(at.menuScreen)
    }
}
