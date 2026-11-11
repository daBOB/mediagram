package games

import data.DefaultWatchStateRepository
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import uniffi.mediagram_core.GameNightUrls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Remembers what the view model asked to announce on the network. */
private class RecordingAnnouncer : GameNightAnnouncer {
    val announced = mutableListOf<String>()
    var withdrawals = 0

    override fun announce(joinUrl: String) {
        announced += joinUrl
    }

    override fun withdraw() {
        withdrawals++
    }
}

class GameNightViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val core = FakeCore().apply { profiles = listOf(uniffi.mediagram_core.Profile("a", "Ada")); chosen = "a" }
    private val provider = FakeCoreProvider(core)
    private val watch = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
    private val tmdb = InMemoryTmdbSettings()
    private val announcer = RecordingAnnouncer()

    private fun model(scope: kotlinx.coroutines.CoroutineScope) =
        GameNightViewModel(provider, watch, SetsOnly(listOf(film("f0", "0"), film("f16", "16"))), tmdb, Dispatchers.Unconfined, scope, announcer)

    @Test
    fun startPassesTheConfigAndShowsTheTvPage() =
        runTest {
            watch.reload()
            tmdb.write("key")
            val model = model(backgroundScope)

            model.start()

            assertEquals(GameNightUi.Ready("http://127.0.0.1:8080/tv"), model.state.first { it !is GameNightUi.Starting })
            val config = core.gameNightStarts.single()
            assertEquals(listOf("f0"), config.kidsSetIds)
            assertEquals("key", config.tmdbKey)
            assertEquals(listOf("f0", "f16"), config.visibleSetIds)
        }

    @Test
    fun setsInAHiddenSectionAreNotPassed() =
        runTest {
            core.profiles = listOf(uniffi.mediagram_core.Profile("a", "Ada", hiddenSections = listOf("movies")))
            watch.reload()
            val model = model(backgroundScope)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertEquals(emptyList(), core.gameNightStarts.single().visibleSetIds)
        }

    @Test
    fun failedStartShowsTheMessage() =
        runTest {
            watch.reload()
            core.gameNightStartFailure = IllegalStateException("port taken")
            val model = model(backgroundScope)

            model.start()

            val failed = assertIs<GameNightUi.Failed>(model.state.first { it !is GameNightUi.Starting })
            assertTrue("port taken" in failed.message)
        }

    private val kid = uniffi.mediagram_core.Profile("k", "Kim", kids = true, kidsAge = 6u)

    @Test
    fun aChosenKidsProfileStartsLockedWithOnlyKidSafeTitles() =
        runTest {
            core.profiles = listOf(kid)
            core.chosen = "k"
            watch.reload()
            val model = GameNightViewModel(provider, watch, SetsOnly(listOf(film("f0", "0"), film("f12", "12"))), tmdb, Dispatchers.Unconfined, backgroundScope, announcer)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            val config = core.gameNightStarts.single()
            assertTrue(config.kidsLocked)
            assertEquals(listOf("f0"), config.kidsSetIds)
        }

    @Test
    fun aKidsProfileNotYetLoadedIsWaitedForAndStartsLocked() =
        runTest {
            core.profiles = listOf(kid)
            core.chosen = "k"
            val model = model(backgroundScope)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertTrue(core.gameNightStarts.single().kidsLocked)
        }

    @Test
    fun noProfileEvenAfterReloadStartsLocked() =
        runTest {
            core.profiles = emptyList()
            core.chosen = null
            val model = model(backgroundScope)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            val config = core.gameNightStarts.single()
            assertTrue(config.kidsLocked)
            assertEquals(listOf("f0"), config.kidsSetIds)
        }

    @Test
    fun startAnnouncesTheJoinUrl() =
        runTest {
            watch.reload()
            val model = model(backgroundScope)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertEquals(listOf("http://192.168.0.2:8080/"), announcer.announced)
        }

    @Test
    fun failedStartAnnouncesNothing() =
        runTest {
            watch.reload()
            core.gameNightStartFailure = IllegalStateException("port taken")
            val model = model(backgroundScope)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertEquals(emptyList(), announcer.announced)
        }

    @Test
    fun stopWithdrawsTheAnnouncement() =
        runTest {
            watch.reload()
            val model = model(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
            model.start()
            model.state.first { it !is GameNightUi.Starting }

            model.stop()

            assertEquals(1, announcer.withdrawals)
        }

    @Test
    fun stopReachesTheCore() =
        runTest {
            val model = model(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))

            model.stop()

            assertEquals(1, core.gameNightStops)
        }

    @Test
    fun stopWhileStartIsInFlightLeavesNoHostRunning() =
        runTest {
            watch.reload()
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            core.gameNightStartGate = gate
            val model = model(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))

            model.start()
            testScheduler.advanceUntilIdle()
            assertEquals(1, core.gameNightStarts.size)
            model.stop()
            gate.complete(Unit)
            testScheduler.advanceUntilIdle()

            // The interrupted start is cancelled rather than finishing after the stop, so no host is left up.
            assertEquals(GameNightUi.Starting, model.state.value)
            assertEquals(1, core.gameNightStops)
        }

    @Test
    fun aSecondStopHasNothingLeftToEnd() =
        runTest {
            val model = model(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))

            model.stop()
            model.stop()

            assertEquals(1, core.gameNightStops)
        }

    @Test
    fun aStartRightAfterAStopWaitsForItAndNeverShowsTheEndedPage() =
        runTest {
            watch.reload()
            // The stop runs at once on its scope, as the app scope does, rather than as background work the scheduler skips.
            val model = model(kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            model.start()
            val ended = GameNightUi.Ready("http://127.0.0.1:8080/tv")
            assertEquals(ended, model.state.first { it !is GameNightUi.Starting })
            val shown = mutableListOf<GameNightUi>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect { shown += it } }
            val stopping = CompletableDeferred<Unit>()
            core.gameNightStopGate = stopping
            core.gameNightUrls = GameNightUrls("http://127.0.0.1:8081/tv", "http://192.168.0.2:8081/")

            model.stop()
            model.start()
            testScheduler.advanceUntilIdle()
            assertEquals(1, core.gameNightStops)
            assertEquals(1, core.gameNightStarts.size, "the new night waits for the old one to end")

            stopping.complete(Unit)
            val next = GameNightUi.Ready("http://127.0.0.1:8081/tv")
            assertEquals(next, model.state.first { it !is GameNightUi.Starting })
            assertEquals(2, core.gameNightStarts.size)
            assertEquals(listOf(ended, GameNightUi.Starting, next), shown)
        }
}
