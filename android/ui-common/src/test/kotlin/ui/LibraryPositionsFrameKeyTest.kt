package ui

import androidx.compose.runtime.MutableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [LibraryPositions.frameKey] — the saved-state slot a frame is given in
 * `LibraryBranches`. Wrong here means a title pushed straight over another
 * of the same kind reuses the first's own tab and scroll, or a search
 * field's own filter is torn down on every keystroke; see the doc comment
 * on `frameKey` itself for both.
 */
class LibraryPositionsFrameKeyTest {

    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    private fun positions() = LibraryPositions(Slot(""))

    @Test
    fun theCatalogHasNoFrameKeyOfItsOwn() {
        assertNull(positions().frameKey)
    }

    @Test
    fun twoTitlesPushedOneOverTheOtherGetDifferentKeys() {
        val at = positions()
        at.openTitle("film-a")
        val first = at.frameKey

        at.openTitle("film-b")
        val second = at.frameKey

        assertNotEquals(first, second)
    }

    /**
     * A pop followed by a different title at the same depth must not reuse
     * the first one's own slot — the bug this key exists to close: without
     * the payload, both would read as "depth 1, TITLE".
     */
    @Test
    fun aDifferentTitleAtTheSameDepthAfterAPopGetsItsOwnKey() {
        val at = positions()
        at.openTitle("film-a")
        val first = at.frameKey
        at.pop()

        at.openTitle("film-c")
        val second = at.frameKey

        assertNotEquals(first, second)
    }

    @Test
    fun leavingAndReturningToTheSameTitleReadsTheSameKeyAgain() {
        val at = positions()
        at.openTitle("film-a")
        at.openPerson("42")
        at.pop()

        assertEquals("1${FIELD_SEP}TITLE${FIELD_SEP}film-a", at.frameKey)
    }

    /**
     * Typing does not change the key: keying search on its own live payload
     * would tear the field down and rebuild it, losing focus, on every
     * keystroke.
     */
    @Test
    fun typingIntoSearchDoesNotChangeItsFrameKey() {
        val at = positions()
        at.openSearch()
        val beforeTyping = at.frameKey

        at.typeSearch("a")
        at.typeSearch("ab")

        assertEquals(beforeTyping, at.frameKey)
    }

    @Test
    fun searchAndATitleAtTheSameDepthNeverCollide() {
        val at = positions()
        at.openSearch()
        val searchKey = at.frameKey
        at.pop()

        at.openTitle("film-a")
        val titleKey = at.frameKey

        assertNotEquals(searchKey, titleKey)
    }

    /** Moving to the next episode must not rebuild the video surface, which a new key would. */
    @Test
    fun theNextEpisodeKeepsThePlayersFrameKey() {
        val at = positions()
        at.openPlayer("ep-1", listOf("ep-1", "ep-2"))
        val first = at.frameKey

        at.replacePlayer("ep-2", listOf("ep-1", "ep-2"))

        assertEquals(first, at.frameKey)
    }

    /** A frame pushed over keeps its saved state for the way back; a popped one gives it up. */
    @Test
    fun aFramePushedOverIsStillHeldButAPoppedOneIsNot() {
        val at = positions()
        at.openTitle("film-a")
        val under = requireNotNull(at.frameKey)
        at.openTitle("film-b")
        val over = requireNotNull(at.frameKey)

        assertTrue(at.holdsFrameKey(under))

        at.pop()

        assertTrue(at.holdsFrameKey(under))
        assertFalse(at.holdsFrameKey(over))
    }
}

/** Mirrors the private constant in `LibraryPositions.kt` — the field separator no title or query ever contains. */
private const val FIELD_SEP = "\u001F"
