package data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.ListOfSets
import model.Profile
import model.WatchSnapshot
import settings.InMemoryLibrarySettings
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.SyncOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Answers [syncState] from a fixed list, in order, holding the last answer once the list runs out. */
private class SyncCoreClient(
    private val outcomes: List<SyncOutcome> = emptyList(),
) : CoreClient by FakeCore() {

    /** Every handle a round asked to sync, in order — one entry per attempted round. */
    val calls = mutableListOf<String>()
    private var next = 0

    override suspend fun syncState(handle: String): SyncOutcome {
        calls += handle
        val outcome = outcomes.getOrElse(next) { outcomes.lastOrNull() ?: SyncOutcome(0uL, false, null) }
        next++
        return outcome
    }
}

/** Counts [reload] calls; nothing else here is exercised by [WatchSync]. */
private class RecordingRepository : WatchStateRepository {
    var reloadCalls = 0
        private set

    override val profiles = MutableStateFlow<List<Profile>>(emptyList())
    override val chosenProfileId = MutableStateFlow<String?>(null)
    override val snapshot = MutableStateFlow(WatchSnapshot.Empty)

    override suspend fun choose(id: String) = false
    override suspend fun create(name: String): Profile? = null
    override suspend fun setProgress(setId: String, at: Double, duration: Double?) = Unit
    override suspend fun clearProgress(setId: String) = Unit
    override suspend fun setWatched(setId: String, finished: Boolean) = Unit
    override suspend fun setWatchlisted(setId: String, listed: Boolean) = Unit
    override suspend fun setKids(setId: String, marked: Boolean) = Unit
    override suspend fun createList(name: String): ListOfSets? = null
    override suspend fun renameList(id: String, name: String) = false
    override suspend fun deleteList(id: String) = false
    override suspend fun setInList(id: String, setId: String, included: Boolean) = false
    override suspend fun reload() {
        reloadCalls++
    }
}

private class TestLibraryEvents : LibraryEvents {
    private val backing = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 1)
    override fun events(): Flow<LibraryEvent> = backing
    suspend fun push(event: LibraryEvent) = backing.emit(event)
}

class WatchSyncTest {

    @Test
    fun foregroundRunsARoundAtOnce() = runTest {
        val core = SyncCoreClient(listOf(SyncOutcome(0uL, false, null)))
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core),
            settingsWithAChosenLibrary("library-1"),
            RecordingRepository(),
            LibraryEvents.None,
            backgroundScope,
        )

        sync.onForeground()
        runCurrent()

        assertEquals(listOf("library-1"), core.calls)
    }

    @Test
    fun theTimerRunsAnotherRoundEveryFiveMinutes() = runTest {
        val core = SyncCoreClient(List(3) { SyncOutcome(0uL, false, null) })
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core),
            settingsWithAChosenLibrary(),
            RecordingRepository(),
            LibraryEvents.None,
            backgroundScope,
        )

        sync.onForeground()
        runCurrent()
        assertEquals(1, core.calls.size)

        advanceTimeBy(5.minutes)
        runCurrent()
        assertEquals(2, core.calls.size)

        advanceTimeBy(5.minutes)
        runCurrent()
        assertEquals(3, core.calls.size)
    }

    @Test
    fun backgroundCancelsTheTimerAndRunsOneLastRound() = runTest {
        val core = SyncCoreClient(List(2) { SyncOutcome(0uL, false, null) })
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core),
            settingsWithAChosenLibrary(),
            RecordingRepository(),
            LibraryEvents.None,
            backgroundScope,
        )
        sync.onForeground()
        runCurrent()
        assertEquals(1, core.calls.size)

        sync.onBackground()
        runCurrent()
        assertEquals(2, core.calls.size, "one last round on background")

        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(2, core.calls.size, "the timer is cancelled, not merely paused")
    }

    @Test
    fun aFailedRoundIsNotedAndDoesNotReload() = runTest {
        val core = SyncCoreClient(listOf(SyncOutcome(0uL, false, "offline")))
        val repository = RecordingRepository()
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core), settingsWithAChosenLibrary(), repository, LibraryEvents.None, backgroundScope,
        )

        sync.onForeground()
        runCurrent()

        assertEquals(0, repository.reloadCalls)
    }

    @Test
    fun aRoundThatPulledRowsReloadsTheRepository() = runTest {
        val core = SyncCoreClient(listOf(SyncOutcome(3uL, false, null)))
        val repository = RecordingRepository()
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core), settingsWithAChosenLibrary(), repository, LibraryEvents.None, backgroundScope,
        )

        sync.onForeground()
        runCurrent()

        assertEquals(1, repository.reloadCalls)
    }

    @Test
    fun aRoundThatPulledNothingDoesNotReload() = runTest {
        val core = SyncCoreClient(listOf(SyncOutcome(0uL, true, null)))
        val repository = RecordingRepository()
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core), settingsWithAChosenLibrary(), repository, LibraryEvents.None, backgroundScope,
        )

        sync.onForeground()
        runCurrent()

        assertEquals(0, repository.reloadCalls)
    }

    @Test
    fun aPushedStateEventRunsAnExtraRound() = runTest {
        val core = SyncCoreClient(List(2) { SyncOutcome(0uL, false, null) })
        val events = TestLibraryEvents()
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RecordingRepository(), events, backgroundScope,
        )
        sync.onForeground()
        runCurrent()
        assertEquals(1, core.calls.size)

        events.push(LibraryEvent.STATE)
        runCurrent()

        assertEquals(2, core.calls.size)
    }

    @Test
    fun awaitFirstRoundReturnsOnceTheForegroundRoundSettles() = runTest {
        val core = SyncCoreClient(listOf(SyncOutcome(0uL, false, null)))
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RecordingRepository(), LibraryEvents.None, backgroundScope,
        )

        var settled = false
        backgroundScope.launch {
            sync.awaitFirstRound()
            settled = true
        }
        runCurrent()
        assertFalse(settled, "nothing has run a round yet")

        sync.onForeground()
        runCurrent()

        assertTrue(settled)
    }

    @Test
    fun awaitFirstRoundReturnsAtOnceWithNoLibraryChosen() = runTest {
        val core = SyncCoreClient()
        val sync = DefaultWatchSync(
            ResolvedCoreProvider(core), InMemoryLibrarySettings(), RecordingRepository(), LibraryEvents.None, backgroundScope,
        )

        sync.onForeground()
        runCurrent()

        var settled = false
        backgroundScope.launch {
            sync.awaitFirstRound()
            settled = true
        }
        runCurrent()

        assertTrue(settled)
        assertEquals(emptyList(), core.calls, "no library, so no round was ever attempted")
    }
}
