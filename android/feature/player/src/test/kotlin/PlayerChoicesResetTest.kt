package player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import playback.PlaybackCounters
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule the whole phase depends on: the player is an app-scoped
 * singleton that never resets its own playback rate, so a speed remembered
 * for one show must never leak into the next title opened after it — and,
 * the other way round, a rotation or a Retry reopening the *same* title
 * must never be mistaken for a new one and reset a speed that never leaked
 * anywhere.
 */
class PlayerChoicesResetTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val episode = fakeMediaSet(setId = "ep1", posterKey = "show-x", show = "30 Rock")
    private val film = fakeMediaSet(setId = "film1")

    private fun viewModel(
        handle: FakePlayerHandle,
        preferences: FakePlayerPreferences,
        watchState: FakeWatchStateRepository = FakeWatchStateRepository(),
    ) = PlayerViewModel(
        handle,
        PlaybackCounters(),
        watchState,
        ProgressRecorder(watchState),
        NoopWatchSync,
        FakeCatalogRepository(mapOf(episode.setId to episode, film.setId to film)),
        preferences,
        FakeSubtitleTrackSource(),
    )

    private object NoopWatchSync : data.WatchSync {
        override fun onForeground() = Unit
        override fun onBackground() = Unit
        override fun soon() = Unit
        override suspend fun awaitFirstRound() = Unit
    }

    @Test
    fun openingAShowWithARememberedSpeedAppliesIt() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("speed" to "1.5")))
        val vm = viewModel(handle, preferences)

        vm.open(episode.setId)
        advanceUntilIdle()

        assertEquals(1.5f, handle.lastSpeed)
        assertEquals(1.5f, vm.choices.value.speed)
    }

    @Test
    fun openingATitleWithNothingRememberedReturnsToOneRatherThanKeepingTheLastShowsSpeed() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("speed" to "1.5")))
        val vm = viewModel(handle, preferences)
        vm.open(episode.setId)
        advanceUntilIdle()
        assertEquals(1.5f, handle.lastSpeed)

        vm.open(film.setId)
        advanceUntilIdle()

        assertEquals(1f, handle.lastSpeed)
        assertEquals(1f, vm.choices.value.speed)
    }

    /**
     * H1: a rotation destroys and recreates `PlayerScreen`, which re-runs
     * `LaunchedEffect(setId)` — calling `open` again for the *same* set.
     * With no profile chosen, a hand-picked speed is never written to
     * `preferences`, so nothing survives a real re-resolution; it has to
     * survive by `open` not re-resolving anything for a title that never
     * actually closed.
     */
    @Test
    fun reopeningTheSameTitleWithNoProfileChosenKeepsAHandPickedSpeed() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences()
        val vm = viewModel(handle, preferences, watchState = FakeWatchStateRepository(profileChosen = false))
        vm.open(episode.setId)
        advanceUntilIdle()

        vm.setSpeed(1.5f)
        handle.speedCalls.clear() // isolate what the reopen itself asks for

        vm.open(episode.setId) // the rotation: same id, same screen re-running its effect

        assertEquals(emptyList(), handle.speedCalls, "no reload means nothing here should touch the transport's rate at all")
        assertEquals(1.5f, vm.choices.value.speed)
    }

    /**
     * Retry reopens a *failed* player, which the handle leaves in
     * `STATE_IDLE` — a real reload, which floors the rate to 1x inside
     * `DefaultPlayerHandle.openOn` the same as any other reload. Unlike a
     * fresh title, nothing re-resolves the speed for it, so retry has to
     * put it back itself.
     */
    @Test
    fun retryKeepsTheChosenSpeedAcrossTheReload() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("speed" to "1.5")))
        val vm = viewModel(handle, preferences)
        vm.open(episode.setId)
        advanceUntilIdle()
        assertEquals(1.5f, vm.choices.value.speed)
        handle.emitError("decoder init failed")

        vm.retry()

        assertEquals(1.5f, handle.lastSpeed)
        assertEquals(1.5f, vm.choices.value.speed)
    }

    /**
     * L2: opening a fresh title starts resolving the remembered speed for
     * it asynchronously (a core round trip). A speed picked by hand before
     * that resolution lands must win over it, not be overwritten the
     * moment it finally does — and, since the resolution is also where the
     * scope becomes known, the hand-picked choice is what gets remembered
     * under it, not whatever [FakePlayerPreferences] happened to hold.
     */
    @Test
    fun aSpeedPickedWhileTheScopeIsStillResolvingWinsOverTheLateResult() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val gate = CompletableDeferred<Unit>()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("speed" to "1.25")))
        val repository = FakeWatchStateRepository()
        val vm = PlayerViewModel(
            handle,
            PlaybackCounters(),
            repository,
            ProgressRecorder(repository),
            NoopWatchSync,
            FakeCatalogRepository(mapOf(episode.setId to episode), gate),
            preferences,
            FakeSubtitleTrackSource(),
        )

        vm.open(episode.setId) // resolve() suspends on the gate, mid-flight
        vm.setSpeed(1.75f) // picked by hand before the remembered 1.25 has even been read

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1.75f, handle.lastSpeed)
        assertEquals(1.75f, vm.choices.value.speed)
        assertEquals(listOf("p1 key:show-x speed=1.75"), preferences.remembered)
    }

    @Test
    fun settingASpeedByHandRemembersItUnderTheOpenShowsScope() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences()
        val vm = viewModel(handle, preferences)
        vm.open(episode.setId)
        advanceUntilIdle()

        vm.setSpeed(1.75f)
        advanceUntilIdle()

        assertEquals(1.75f, handle.lastSpeed)
        assertEquals(listOf("p1 key:show-x speed=1.75"), preferences.remembered)
    }
}
