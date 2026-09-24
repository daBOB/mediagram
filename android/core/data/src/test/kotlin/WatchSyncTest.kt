package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
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
import settings.LibrarySettings
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

    override fun invalidate() {
        profiles.value = emptyList()
        chosenProfileId.value = null
        snapshot.value = WatchSnapshot.Empty
    }

    override suspend fun chooseProfile(id: String) = false

    override suspend fun createProfile(name: String): Profile? = null

    override suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    ) = Unit

    override suspend fun clearProgress(setId: String) = Unit

    override suspend fun setWatched(
        setId: String,
        finished: Boolean,
    ) = Unit

    override suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    ) = Unit

    override suspend fun setKids(
        setId: String,
        marked: Boolean,
    ) = Unit

    override suspend fun createList(name: String): ListOfSets? = null

    override suspend fun renameList(
        id: String,
        name: String,
    ) = false

    override suspend fun deleteList(id: String) = false

    override suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ) = false

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
    fun stateEventsDuringARoundCoalesceOneFollowUpRead() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val core =
                object : CoreClient by FakeCore() {
                    override suspend fun syncState(handle: String): SyncOutcome {
                        calls++
                        if (calls == 1) release.await()
                        return SyncOutcome(0uL, false, null)
                    }
                }
            val events = TestLibraryEvents()
            val sync =
                DefaultWatchSync(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), RecordingRepository(), events, backgroundScope)
            sync.onForeground()
            runCurrent()
            repeat(3) {
                events.push(LibraryEvent.STATE)
                runCurrent()
            }
            assertEquals(1, calls, "the current read stays the sole native call")

            release.complete(Unit)
            runCurrent()

            assertEquals(2, calls, "updates received during the read need one fresh read")
        }

    @Test
    fun aQueuedFollowUpIsDiscardedWhenTheLibraryChangesWithoutAPicker() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val core =
                object : CoreClient by FakeCore() {
                    override suspend fun syncState(handle: String): SyncOutcome {
                        calls += handle
                        if (calls.size == 1) release.await()
                        return SyncOutcome(0uL, false, null)
                    }
                }
            val settings = InMemoryLibrarySettings().apply { write("old") }
            val events = TestLibraryEvents()
            val sync = DefaultWatchSync(ResolvedCoreProvider(core), settings, RecordingRepository(), events, backgroundScope)
            sync.onForeground()
            runCurrent()
            events.push(LibraryEvent.STATE)
            runCurrent()
            settings.write("new")
            release.complete(Unit)
            runCurrent()

            assertEquals(listOf("old"), calls, "news for the old selection cannot queue another read from it")
            sync.soon()
            runCurrent()
            assertEquals(listOf("old", "new"), calls)
        }

    @Test
    fun aQueuedFollowUpCannotUseAReplacedCore() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var oldCalls = 0
            val old =
                object : CoreClient by FakeCore() {
                    override suspend fun syncState(handle: String): SyncOutcome {
                        oldCalls++
                        release.await()
                        return SyncOutcome(0uL, false, null)
                    }
                }
            val replacement = SyncCoreClient()
            val current = MutableStateFlow<CoreClient?>(old)
            val provider =
                object : CoreProvider by ResolvedCoreProvider(old) {
                    override val core = current

                    override suspend fun coreOrNull(): CoreClient? = current.value
                }
            val events = TestLibraryEvents()
            val sync = DefaultWatchSync(provider, settingsWithAChosenLibrary(), RecordingRepository(), events, backgroundScope)
            sync.onForeground()
            runCurrent()
            events.push(LibraryEvent.STATE)
            runCurrent()
            current.value = replacement
            release.complete(Unit)
            runCurrent()

            assertEquals(1, oldCalls)
            sync.soon()
            runCurrent()
            assertEquals(listOf("a1b2c3"), replacement.calls)
        }

    @Test
    fun concurrentPickerWaitsJoinTheAlreadyRunningRound() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val core =
                object : CoreClient by FakeCore() {
                    override suspend fun syncState(handle: String): SyncOutcome {
                        calls++
                        release.await()
                        return SyncOutcome(0uL, false, null)
                    }
                }
            val sync =
                DefaultWatchSync(
                    ResolvedCoreProvider(core),
                    settingsWithAChosenLibrary(),
                    RecordingRepository(),
                    LibraryEvents.None,
                    backgroundScope,
                )
            sync.onForeground()
            runCurrent()
            val one = backgroundScope.async { sync.awaitFirstRound() }
            val two = backgroundScope.async { sync.awaitFirstRound() }
            runCurrent()
            assertEquals(1, calls)
            assertFalse(one.isCompleted)
            assertFalse(two.isCompleted)

            release.complete(Unit)
            runCurrent()

            assertTrue(one.isCompleted)
            assertTrue(two.isCompleted)
            assertEquals(1, calls)
        }

    @Test
    fun switchingTheLibraryWhileWaitingCancelsTheOldRoundAndWaitsForTheNewOne() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            var oldCancelled = false
            val core =
                object : CoreClient by FakeCore() {
                    override suspend fun syncState(handle: String): SyncOutcome {
                        calls += handle
                        if (handle == "old") {
                            try {
                                awaitCancellation()
                            } finally {
                                oldCancelled = true
                            }
                        }
                        release.await()
                        return SyncOutcome(0uL, false, null)
                    }
                }
            val settings = InMemoryLibrarySettings().apply { write("old") }
            val sync = DefaultWatchSync(ResolvedCoreProvider(core), settings, RecordingRepository(), LibraryEvents.None, backgroundScope)
            sync.onForeground()
            runCurrent()
            val waiting = backgroundScope.async { sync.awaitFirstRound() }
            runCurrent()
            settings.write("new")
            runCurrent()

            assertTrue(oldCancelled)
            assertEquals(listOf("old", "new"), calls)
            assertFalse(waiting.isCompleted)
            release.complete(Unit)
            runCurrent()
            assertTrue(waiting.isCompleted)
            assertEquals(listOf("old", "new"), calls, "superseded callers join the new round without queuing another")
        }

    @Test
    fun cancellingAPickerWaitDoesNotCancelTheSharedRound() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var completed = false
            var cancelled = false
            val core =
                object : CoreClient by FakeCore() {
                    override suspend fun syncState(handle: String): SyncOutcome {
                        try {
                            release.await()
                            completed = true
                        } catch (failure: CancellationException) {
                            cancelled = true
                            throw failure
                        }
                        return SyncOutcome(0uL, false, null)
                    }
                }
            val sync =
                DefaultWatchSync(
                    ResolvedCoreProvider(core),
                    settingsWithAChosenLibrary(),
                    RecordingRepository(),
                    LibraryEvents.None,
                    backgroundScope,
                )
            val waiting = backgroundScope.async { sync.awaitFirstRound() }
            runCurrent()
            waiting.cancel()
            runCurrent()
            assertFalse(cancelled)

            release.complete(Unit)
            runCurrent()
            assertTrue(completed)
        }

    @Test
    fun foregroundRunsARoundAtOnce() =
        runTest {
            val core = SyncCoreClient(listOf(SyncOutcome(0uL, false, null)))
            val sync =
                DefaultWatchSync(
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
    fun theTimerRunsAnotherRoundEveryFiveMinutes() =
        runTest {
            val core = SyncCoreClient(List(3) { SyncOutcome(0uL, false, null) })
            val sync =
                DefaultWatchSync(
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
    fun backgroundCancelsTheTimerAndRunsOneLastRound() =
        runTest {
            val core = SyncCoreClient(List(2) { SyncOutcome(0uL, false, null) })
            val sync =
                DefaultWatchSync(
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
    fun aFailedRoundIsNotedAndDoesNotReload() =
        runTest {
            val core = SyncCoreClient(listOf(SyncOutcome(0uL, false, "offline")))
            val repository = RecordingRepository()
            val sync =
                DefaultWatchSync(
                    ResolvedCoreProvider(core),
                    settingsWithAChosenLibrary(),
                    repository,
                    LibraryEvents.None,
                    backgroundScope,
                )

            sync.onForeground()
            runCurrent()

            assertEquals(0, repository.reloadCalls)
        }

    @Test
    fun aRoundThatPulledRowsReloadsTheRepository() =
        runTest {
            val core = SyncCoreClient(listOf(SyncOutcome(3uL, false, null)))
            val repository = RecordingRepository()
            val sync =
                DefaultWatchSync(
                    ResolvedCoreProvider(core),
                    settingsWithAChosenLibrary(),
                    repository,
                    LibraryEvents.None,
                    backgroundScope,
                )

            sync.onForeground()
            runCurrent()

            assertEquals(1, repository.reloadCalls)
        }

    @Test
    fun aRoundThatPulledNothingDoesNotReload() =
        runTest {
            val core = SyncCoreClient(listOf(SyncOutcome(0uL, true, null)))
            val repository = RecordingRepository()
            val sync =
                DefaultWatchSync(
                    ResolvedCoreProvider(core),
                    settingsWithAChosenLibrary(),
                    repository,
                    LibraryEvents.None,
                    backgroundScope,
                )

            sync.onForeground()
            runCurrent()

            assertEquals(0, repository.reloadCalls)
        }

    @Test
    fun aPushedStateEventRunsAnExtraRound() =
        runTest {
            val core = SyncCoreClient(List(2) { SyncOutcome(0uL, false, null) })
            val events = TestLibraryEvents()
            val sync =
                DefaultWatchSync(
                    ResolvedCoreProvider(core),
                    settingsWithAChosenLibrary(),
                    RecordingRepository(),
                    events,
                    backgroundScope,
                )
            sync.onForeground()
            runCurrent()
            assertEquals(1, core.calls.size)

            events.push(LibraryEvent.STATE)
            runCurrent()

            assertEquals(2, core.calls.size)
        }

    @Test
    fun awaitFirstRoundRequestsTheCurrentLibraryWithoutWaitingForForeground() =
        runTest {
            val core = SyncCoreClient(listOf(SyncOutcome(0uL, false, null)))
            val sync =
                DefaultWatchSync(
                    ResolvedCoreProvider(core),
                    settingsWithAChosenLibrary(),
                    RecordingRepository(),
                    LibraryEvents.None,
                    backgroundScope,
                )

            var settled = false
            backgroundScope.launch {
                sync.awaitFirstRound()
                settled = true
            }
            runCurrent()
            assertTrue(settled)
            assertEquals(listOf("a1b2c3"), core.calls)
            sync.awaitFirstRound()
            assertEquals(2, core.calls.size, "a later picker requests current state rather than process-lifetime readiness")
        }

    @Test
    fun awaitFirstRoundReturnsAtOnceWithNoLibraryChosen() =
        runTest {
            val core = SyncCoreClient()
            var coreLookups = 0
            val provider =
                object : CoreProvider by ResolvedCoreProvider(core) {
                    override suspend fun coreOrNull(): CoreClient? {
                        coreLookups++
                        return null
                    }
                }
            val sync =
                DefaultWatchSync(
                    provider,
                    InMemoryLibrarySettings(),
                    RecordingRepository(),
                    LibraryEvents.None,
                    backgroundScope,
                )

            var settled = false
            backgroundScope.launch {
                sync.awaitFirstRound()
                settled = true
            }
            runCurrent()

            assertTrue(settled)
            assertEquals(0, coreLookups, "no selection must return before resolving or awaiting credentials")
            assertEquals(emptyList(), core.calls, "no library, so no round was ever attempted")
        }

    @Test
    fun setupCompletionDoesNotInheritTheSkippedStartupRound() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val core =
                object : CoreClient by FakeCore() {
                    override suspend fun syncState(handle: String): SyncOutcome {
                        entered.complete(Unit)
                        release.await()
                        return SyncOutcome(0uL, false, null)
                    }
                }
            val settings = InMemoryLibrarySettings()
            val sync = DefaultWatchSync(ResolvedCoreProvider(core), settings, RecordingRepository(), LibraryEvents.None, backgroundScope)
            sync.onForeground()
            runCurrent()
            settings.write("chosen-after-setup")

            val waiting = backgroundScope.async { sync.awaitFirstRound() }
            runCurrent()
            assertTrue(entered.isCompleted, "selection must request its own first round")
            assertFalse(waiting.isCompleted)
            release.complete(Unit)
            runCurrent()
            assertTrue(waiting.isCompleted)
        }

    @Test
    fun readinessIsResetWhenTheCoreOrLibraryChanges() =
        runTest {
            val first = SyncCoreClient()
            val second = SyncCoreClient()
            val current = MutableStateFlow<CoreClient?>(first)
            val provider =
                object : CoreProvider by ResolvedCoreProvider(first) {
                    override val core = current

                    override suspend fun coreOrNull(): CoreClient? = current.value

                    override suspend fun awaitCore(): CoreClient = current.value!!
                }
            val settings = InMemoryLibrarySettings().apply { write("first-library") }
            val sync = DefaultWatchSync(provider, settings, RecordingRepository(), LibraryEvents.None, backgroundScope)
            sync.onForeground()
            runCurrent()

            settings.write("second-library")
            sync.awaitFirstRound()
            assertEquals(listOf("first-library", "second-library"), first.calls)
            current.value = second
            sync.awaitFirstRound()
            assertEquals(listOf("second-library"), second.calls)
        }

    @Test
    fun aSettingsReadFailureSettlesTheWaitAndAFollowingCallCanRecover() =
        runTest {
            val stored = InMemoryLibrarySettings().apply { write("library") }
            var refuse = true
            val settings =
                object : LibrarySettings by stored {
                    override suspend fun read(): String? {
                        if (refuse) throw SecurityException("keystore unavailable")
                        return stored.read()
                    }
                }
            val core = SyncCoreClient()
            val sync = DefaultWatchSync(ResolvedCoreProvider(core), settings, RecordingRepository(), LibraryEvents.None, backgroundScope)

            val waiting = backgroundScope.async { sync.awaitFirstRound() }
            runCurrent()
            assertTrue(waiting.isCompleted)
            assertEquals(emptyList(), core.calls)
            refuse = false
            sync.awaitFirstRound()
            assertEquals(listOf("library"), core.calls)
        }
}
