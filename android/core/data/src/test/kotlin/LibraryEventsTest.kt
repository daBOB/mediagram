package data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import settings.InMemoryLibrarySettings
import uniffi.mediagram_core.LibraryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class LibraryEventsTest {

    @Test
    fun passesOnWhatTheCoreSaysForTheChosenLibrary() = runTest {
        val settings = InMemoryLibrarySettings().apply { write("library-1") }
        val core = FakeCore(events = listOf(Result.success(LibraryEvent.INDEX), Result.success(LibraryEvent.STATE)))

        val heard = CoreLibraryEvents(ResolvedCoreProvider(core), settings).events().take(2).toList()

        assertEquals(listOf(LibraryEvent.INDEX, LibraryEvent.STATE), heard)
        assertEquals("library-1", core.eventHandle)
    }

    /** No library chosen is nothing to listen to — not an error, and not a wait for ever. */
    @Test
    fun noLibraryChosenEndsWithoutAskingTheCore() = runTest {
        val core = FakeCore(events = listOf(Result.success(LibraryEvent.INDEX)))

        val heard = CoreLibraryEvents(ResolvedCoreProvider(core), InMemoryLibrarySettings()).events().toList()

        assertEquals(emptyList(), heard)
        assertEquals(0, core.eventCalls)
    }

    /**
     * Listening stops when the phone is offline or the connection goes. It
     * pauses before asking again rather than spinning against a network
     * that is not there, and then carries on as if nothing happened.
     */
    @Test
    fun aFailedWaitPausesThenListensAgain() = runTest {
        val settings = InMemoryLibrarySettings().apply { write("library-1") }
        val core = FakeCore(
            events = listOf(Result.failure(IllegalStateException("offline")), Result.success(LibraryEvent.INDEX)),
        )
        val started = currentTime

        val heard = CoreLibraryEvents(ResolvedCoreProvider(core), settings, retryAfter = 30.seconds).events().first()

        assertEquals(LibraryEvent.INDEX, heard)
        assertEquals(30_000L, currentTime - started)
        assertEquals(2, core.eventCalls)
    }
}
