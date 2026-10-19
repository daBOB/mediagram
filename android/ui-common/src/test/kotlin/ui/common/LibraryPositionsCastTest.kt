package ui.common

import androidx.compose.runtime.MutableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A cast's player frame is saved with the rest, so a recreated screen finds
 * the cast still playing; but a process that died mid-cast took the cast
 * with it, and its frame must not reopen the title as whoever the device's
 * own profile is.
 */
class LibraryPositionsCastTest {
    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    @Test
    fun aCastIsKeptAcrossARecreatedScreen() {
        val saved = Slot("")
        LibraryPositions(saved).castPlayer("s1")

        val again = LibraryPositions(Slot(saved.value))

        assertEquals("s1", again.setId)
        assertTrue(again.casting)
    }

    @Test
    fun aCastSavedByAProcessThatDiedIsNotRestored() {
        val saved = Slot("")
        LibraryPositions(saved).apply {
            openTitle("t1")
            castPlayer("s1")
        }
        // The same stack as another process would have written it: only the process differs.
        val anotherProcess = saved.value.replace(Regex("\u001B[^\u001B]*\u001B"), "\u001Bgone\u001B")

        val restored = LibraryPositions(Slot(anotherProcess))

        assertEquals(FrameKind.TITLE, restored.top)
        assertNull(restored.setId)
    }

    @Test
    fun aTitleOpenedByHandIsNoCast() {
        val at = LibraryPositions(Slot(""))
        at.openPlayer("s1")
        assertFalse(at.casting)
    }

    @Test
    fun anUpNextSwitchInACastIsStillTheCast() {
        val at = LibraryPositions(Slot(""))
        at.castPlayer("e1")
        at.replacePlayer("e2", listOf("e1", "e2"))

        assertEquals("e2", at.setId)
        assertNull(at.run)
        assertTrue(at.casting)
    }

    @Test
    fun aCastTakenFromThePlayerPutsTheRemoteInItsPlace() {
        val at = LibraryPositions(Slot(""))
        at.openTitle("t1")
        at.openPlayer("s1")

        at.replaceWithRemote(7)

        assertEquals(FrameKind.REMOTE, at.top)
        assertEquals(7, at.remoteId)
        assertNull(at.setId)
        at.pop()
        assertEquals(FrameKind.TITLE, at.top)
    }

    @Test
    fun theRemoteReplacesOnlyAPlayer() {
        val at = LibraryPositions(Slot(""))
        at.openTitle("t1")

        at.replaceWithRemote(7)

        assertEquals(FrameKind.TITLE, at.top)
    }
}
