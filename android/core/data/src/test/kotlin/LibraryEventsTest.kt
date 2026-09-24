package data

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryLibrarySettings
import settings.LibrarySettings
import uniffi.mediagram_core.LibraryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LibraryEventsTest {
    @Test
    fun aFailedSettingsReadUsesTheListenerRetryAndThenRecovers() =
        runTest {
            val stored = InMemoryLibrarySettings().apply { write("library-1") }
            var reads = 0
            val settings =
                object : LibrarySettings by stored {
                    override suspend fun read(): String? {
                        if (reads++ == 0) throw SecurityException("keystore unavailable")
                        return stored.read()
                    }

                    override fun selections() = flow { emit(read()) }
                }
            val core = FakeCore(events = listOf(Result.success(LibraryEvent.INDEX)))
            val started = currentTime

            val heard = CoreLibraryEvents(ResolvedCoreProvider(core), settings, retryAfter = 30.seconds).events().first()

            assertEquals(LibraryEvent.INDEX, heard)
            assertEquals(30_000L, currentTime - started)
            assertEquals(2, reads)
        }

    @Test
    fun passesOnWhatTheCoreSaysForTheChosenLibrary() =
        runTest {
            val settings = InMemoryLibrarySettings().apply { write("library-1") }
            val core = FakeCore(events = listOf(Result.success(LibraryEvent.INDEX), Result.success(LibraryEvent.STATE)))

            val heard = CoreLibraryEvents(ResolvedCoreProvider(core), settings).events().take(2).toList()

            assertEquals(listOf(LibraryEvent.INDEX, LibraryEvent.STATE), heard)
            assertEquals("library-1", core.eventHandle)
        }

    /** No library chosen is nothing to listen to: no wait is started, and nothing is said. */
    @Test
    fun noLibraryChosenAsksTheCoreNothing() =
        runTest {
            val core = FakeCore(events = listOf(Result.success(LibraryEvent.INDEX)))
            val heard = mutableListOf<LibraryEvent>()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                CoreLibraryEvents(ResolvedCoreProvider(core), InMemoryLibrarySettings()).events().toList(heard)
            }
            advanceUntilIdle()

            assertEquals(emptyList(), heard)
            assertEquals(0, core.eventCalls)
        }

    /**
     * Listening stops when the phone is offline or the connection goes. It
     * pauses before asking again rather than spinning against a network
     * that is not there, and then carries on as if nothing happened.
     */
    @Test
    fun aFailedWaitPausesThenListensAgain() =
        runTest {
            val settings = InMemoryLibrarySettings().apply { write("library-1") }
            val core =
                FakeCore(
                    events = listOf(Result.failure(IllegalStateException("offline")), Result.success(LibraryEvent.INDEX)),
                )
            val started = currentTime

            val heard = CoreLibraryEvents(ResolvedCoreProvider(core), settings, retryAfter = 30.seconds).events().first()

            assertEquals(LibraryEvent.INDEX, heard)
            assertEquals(30_000L, currentTime - started)
            // At least the failed wait and the one that answered; the listener
            // runs one wait ahead of its collector, so a third may already be
            // parked when the first event is taken.
            assertTrue(core.eventCalls >= 2)
        }

    /**
     * A core replaced while a wait is parked on it: the wait moves to the new
     * core, which is what lets the old one — and its connection — close.
     */
    @Test
    fun aReplacedCoreMovesTheWaitToTheNewOne() =
        runTest {
            val settings = InMemoryLibrarySettings().apply { write("library-1") }
            val quiet = FakeCore()
            val current = MutableStateFlow<CoreClient?>(quiet)
            val provider =
                object : CoreProvider by ResolvedCoreProvider(quiet) {
                    override val core: StateFlow<CoreClient?> = current
                }
            val heard = async { CoreLibraryEvents(provider, settings).events().first() }
            runCurrent()
            assertEquals(1, quiet.eventCalls, "parked on the first core")

            current.value = FakeCore(events = listOf(Result.success(LibraryEvent.INDEX)))

            assertEquals(LibraryEvent.INDEX, heard.await())
        }
}
