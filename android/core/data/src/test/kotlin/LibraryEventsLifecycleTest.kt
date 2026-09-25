package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryLibrarySettings
import uniffi.mediagram_core.LibraryEvent
import kotlin.test.Test
import kotlin.test.assertEquals

private class ListeningCore : CoreClient by FakeCore() {
    val handles = mutableListOf<String>()
    val incoming = Channel<LibraryEvent>(Channel.UNLIMITED)
    var active = 0
    var cancelled = 0

    override suspend fun nextLibraryEvent(
        handle: String,
        ownDevice: String,
    ): LibraryEvent {
        handles += handle
        active++
        try {
            return incoming.receive()
        } catch (failure: CancellationException) {
            cancelled++
            throw failure
        } finally {
            active--
        }
    }
}

class LibraryEventsLifecycleTest {
    @Test
    fun selectionAfterSubscriptionStartsListeningWithoutReplacingTheCore() =
        runTest {
            val core = ListeningCore()
            val settings = InMemoryLibrarySettings()
            val heard = mutableListOf<LibraryEvent>()
            val events = SharedLibraryEvents(CoreLibraryEvents(ResolvedCoreProvider(core), settings), backgroundScope)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.events().collect { heard += it } }
            runCurrent()
            assertEquals(0, core.active)

            settings.write("new-library")
            runCurrent()
            assertEquals(listOf("new-library"), core.handles)
            core.incoming.send(LibraryEvent.INDEX)
            runCurrent()
            assertEquals(listOf(LibraryEvent.INDEX), heard)
        }

    @Test
    fun changingAndClearingTheLibraryCancelsThePreviousNativeWait() =
        runTest {
            val core = ListeningCore()
            val settings = InMemoryLibrarySettings().apply { write("first") }
            val events = SharedLibraryEvents(CoreLibraryEvents(ResolvedCoreProvider(core), settings), backgroundScope)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.events().collect {} }
            runCurrent()
            assertEquals(1, core.active)

            settings.write("second")
            runCurrent()
            assertEquals(listOf("first", "second"), core.handles)
            assertEquals(1, core.cancelled)
            assertEquals(1, core.active)

            settings.clear()
            runCurrent()
            assertEquals(2, core.cancelled)
            assertEquals(0, core.active)
        }

    @Test
    fun removingTheCoreCancelsItsWaitWhileSubscribersRemain() =
        runTest {
            val core = ListeningCore()
            val current = MutableStateFlow<CoreClient?>(core)
            val provider =
                object : CoreProvider by ResolvedCoreProvider(core) {
                    override val core: StateFlow<CoreClient?> = current
                }
            val settings = InMemoryLibrarySettings().apply { write("library") }
            val events = SharedLibraryEvents(CoreLibraryEvents(provider, settings), backgroundScope)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.events().collect {} }
            runCurrent()
            assertEquals(1, core.active)

            current.value = null
            runCurrent()

            assertEquals(1, core.cancelled)
            assertEquals(0, core.active)
        }

    @Test
    fun twoSubscribersShareOneUpstreamAndTheLastDepartureEndsItAfterTheGracePeriod() =
        runTest {
            var collections = 0
            var cancellations = 0
            val incoming = MutableSharedFlow<LibraryEvent>()
            val source =
                LibraryEvents {
                    flow {
                        collections++
                        try {
                            emitAll(incoming)
                        } finally {
                            cancellations++
                        }
                    }
                }
            val shared = SharedLibraryEvents(source, backgroundScope)
            val first = mutableListOf<LibraryEvent>()
            val second = mutableListOf<LibraryEvent>()
            val one = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { shared.events().collect { first += it } }
            val two = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { shared.events().collect { second += it } }
            runCurrent()

            assertEquals(1, collections)
            incoming.emit(LibraryEvent.INDEX)
            runCurrent()
            assertEquals(listOf(LibraryEvent.INDEX), first)
            assertEquals(first, second)
            one.cancel()
            runCurrent()
            incoming.emit(LibraryEvent.STATE)
            runCurrent()
            assertEquals(listOf(LibraryEvent.INDEX), first)
            assertEquals(listOf(LibraryEvent.INDEX, LibraryEvent.STATE), second)
            assertEquals(0, cancellations)

            two.cancel()
            runCurrent()
            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(0, cancellations)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, cancellations)
            assertEquals(1, collections)
        }
}
