package com.mediagram.spieltisch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uniffi.spieltisch_core.NightUrls
import uniffi.spieltisch_core.SpieltischConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameNightViewModelTest {
    private val core = FakeSpieltischCore()
    private val announcer = RecordingAnnouncer()

    @BeforeTest fun mainOnTheTestThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun model(
        scope: CoroutineScope,
        media: MediaLibrary = NoMediaApp,
    ) = GameNightViewModel(NightSession(core, announcer, scope, "/data/spieltisch", media, NoImages))

    @Test
    fun theMediaAppsAnswerIsWhatTheNightStartsWith() =
        runTest {
            val model = model(backgroundScope) { MediaNight(kidsLocked = false, titles = "[{\"key\":\"A\"}]") }

            model.start()

            assertEquals(GameNightUi.Ready("http://127.0.0.1:8080/tv"), model.state.first { it !is GameNightUi.Starting })
            assertEquals(
                SpieltischConfig(kidsLocked = false, dataDir = "/data/spieltisch", titles = "[{\"key\":\"A\"}]", articles = ""),
                core.starts.single(),
            )
            assertIs<NightImages>(core.startImages.single(), "the media app's images and the articles' photos, for the host to fetch")
            assertFalse(model.filmsUnavailable.value)
        }

    @Test
    fun theArticleListIsWhatTheNightStartsWith() =
        runTest {
            val model = model(backgroundScope) { MediaNight(kidsLocked = false, titles = "[]", articles = ARTICLE_LIST) }

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertEquals(ARTICLE_LIST, core.starts.single().articles)
        }

    @Test
    fun aKidsProfilesAnswerStartsLocked() =
        runTest {
            val model = model(backgroundScope) { MediaNight(kidsLocked = true, titles = "[]") }

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertTrue(core.starts.single().kidsLocked)
            assertFalse(model.filmsUnavailable.value, "answered, with nothing a kid may see")
        }

    @Test
    fun noAnswerStartsLockedWithNoFilmsAndSaysSo() =
        runTest {
            val model = model(backgroundScope)

            model.start()

            assertEquals(GameNightUi.Ready("http://127.0.0.1:8080/tv"), model.state.first { it !is GameNightUi.Starting })
            assertEquals(SpieltischConfig(kidsLocked = true, dataDir = "/data/spieltisch", titles = "[]", articles = ""), core.starts.single())
            assertTrue(model.filmsUnavailable.value)
        }

    @Test
    fun failedStartShowsTheMessage() =
        runTest {
            core.startFailure = IllegalStateException("port taken")
            val model = model(backgroundScope)

            model.start()

            val failed = assertIs<GameNightUi.Failed>(model.state.first { it !is GameNightUi.Starting })
            assertTrue("port taken" in failed.message)
        }

    @Test
    fun startAnnouncesTheJoinUrl() =
        runTest {
            val model = model(backgroundScope)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertEquals(listOf("http://192.168.0.2:8080/"), announcer.announced)
        }

    @Test
    fun failedStartAnnouncesNothing() =
        runTest {
            core.startFailure = IllegalStateException("port taken")
            val model = model(backgroundScope)

            model.start()
            model.state.first { it !is GameNightUi.Starting }

            assertEquals(emptyList(), announcer.announced)
        }

    @Test
    fun stopWithdrawsTheAnnouncement() =
        runTest {
            val model = model(CoroutineScope(Dispatchers.Unconfined))
            model.start()
            model.state.first { it !is GameNightUi.Starting }

            model.stop()

            assertEquals(null, announcer.live)
        }

    @Test
    fun stopReachesTheCore() =
        runTest {
            val model = model(CoroutineScope(Dispatchers.Unconfined))

            model.stop()

            assertEquals(1, core.stops)
        }

    @Test
    fun stopWhileStartIsInFlightLeavesNoHostRunning() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            core.startGate = gate
            val model = model(CoroutineScope(Dispatchers.Unconfined))

            model.start()
            testScheduler.advanceUntilIdle()
            assertEquals(1, core.starts.size)
            model.stop()
            gate.complete(Unit)
            testScheduler.advanceUntilIdle()

            // The interrupted start is cancelled rather than finishing after the stop, so no host is left up.
            assertEquals(GameNightUi.Starting, model.state.value)
            assertEquals(1, core.stops)
        }

    @Test
    fun aSecondStopHasNothingLeftToEnd() =
        runTest {
            val model = model(CoroutineScope(Dispatchers.Unconfined))

            model.stop()
            model.stop()

            assertEquals(1, core.stops)
        }

    @Test
    fun aStartRightAfterAStopWaitsForItAndNeverShowsTheEndedPage() =
        runTest {
            // The stop runs at once on its scope, as the app scope does, rather than as background work the scheduler skips.
            val model = model(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            model.start()
            val ended = GameNightUi.Ready("http://127.0.0.1:8080/tv")
            assertEquals(ended, model.state.first { it !is GameNightUi.Starting })
            val shown = mutableListOf<GameNightUi>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect { shown += it } }
            val stopping = CompletableDeferred<Unit>()
            core.stopGate = stopping
            core.urls = NightUrls("http://127.0.0.1:8081/tv", "http://192.168.0.2:8081/")

            model.stop()
            model.start()
            testScheduler.advanceUntilIdle()
            assertEquals(1, core.stops)
            assertEquals(1, core.starts.size, "the new night waits for the old one to end")

            stopping.complete(Unit)
            val next = GameNightUi.Ready("http://127.0.0.1:8081/tv")
            assertEquals(next, model.state.first { it !is GameNightUi.Starting })
            assertEquals(2, core.starts.size)
            assertEquals(listOf(ended, GameNightUi.Starting, next), shown)
        }
}
