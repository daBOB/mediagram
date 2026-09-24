package player

import data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Progress
import model.WatchSnapshot
import org.junit.After
import playback.PlaybackCounters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeWatchSync : WatchSync {
    var soonCalls = 0
        private set

    override fun onForeground() = Unit

    override fun onBackground() = Unit

    override fun soon() {
        soonCalls++
    }

    override suspend fun awaitFirstRound() = Unit
}

class PlayerViewModelTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handle: FakePlayerHandle = FakePlayerHandle(),
        repository: FakeWatchStateRepository = FakeWatchStateRepository(),
        watchSync: FakeWatchSync = FakeWatchSync(),
    ) = PlayerViewModel(handle, PlaybackCounters(), repository, ProgressRecorder(repository), watchSync)

    @Test
    fun preparingIsTheFirstStateForASet() =
        runTest {
            installMainDispatcher()
            val vm = viewModel()
            vm.open("s1")
            assertEquals(PlayerUiState.Preparing, vm.state.value)
        }

    @Test
    fun aPlayerErrorSurfacesAsFailed() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val vm = viewModel(handle)
            vm.open("s1")
            handle.emitError("decoder init failed")
            assertTrue(vm.state.value is PlayerUiState.Failed)
        }

    @Test
    fun openingAgainClearsAPreviousFailure() =
        runTest {
            installMainDispatcher()
            // Unlike preparingIsTheFirstStateForASet, this can't pass by
            // relying on _state's initial value alone: it fails a previous
            // set on purpose first, so only an actual reset inside open()
            // can bring it back to Preparing.
            val handle = FakePlayerHandle()
            val vm = viewModel(handle)
            vm.open("s1")
            handle.emitError("decoder init failed")
            assertTrue(vm.state.value is PlayerUiState.Failed)

            vm.open("s2")

            assertEquals(PlayerUiState.Preparing, vm.state.value)
        }

    @Test
    fun openIsForwardedToTheHandle() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val vm = viewModel(handle)
            vm.open("s1")
            assertEquals("s1", handle.openedSetId)
        }

    @Test
    fun theStateFollowsWhetherThePlayerIsPlaying() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val vm = viewModel(handle)
            vm.open("s1")

            handle.emitPlaying(isPlaying = true)
            assertEquals(PlayerUiState.Playing, vm.state.value)

            handle.emitPlaying(isPlaying = false)
            assertEquals(PlayerUiState.Paused, vm.state.value)
        }

    @Test
    fun stopIsForwardedToTheHandle() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val vm = viewModel(handle)
            vm.stop()
            assertTrue(handle.stopCalled)
        }

    @Test
    fun stoppingTellsWatchSyncToSyncSoon() =
        runTest {
            installMainDispatcher()
            val watchSync = FakeWatchSync()
            val vm = viewModel(watchSync = watchSync)

            vm.stop()

            assertEquals(1, watchSync.soonCalls)
        }

    @Test
    fun openingAMidTitlePositionResumesTherePastTheGlanceThreshold() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val repository =
                FakeWatchStateRepository(
                    initialSnapshot = WatchSnapshot.Empty.copy(progress = listOf(Progress("s1", 1200.0, 2400.0, 0))),
                )
            val vm = viewModel(handle, repository)

            vm.open("s1")

            assertEquals(1_200_000L, handle.openedStartAtMs)
        }

    @Test
    fun openingAGlancePositionStartsFromTheTop() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val repository =
                FakeWatchStateRepository(
                    initialSnapshot = WatchSnapshot.Empty.copy(progress = listOf(Progress("s1", 12.0, 2400.0, 0))),
                )
            val vm = viewModel(handle, repository)

            vm.open("s1")

            assertEquals(0L, handle.openedStartAtMs)
        }

    @Test
    fun openingATitleWithNoRecordedProgressStartsFromTheTop() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val vm = viewModel(handle)

            vm.open("s1")

            assertEquals(0L, handle.openedStartAtMs)
        }

    @Test
    fun pausingSavesTheCurrentPosition() =
        runTest {
            installMainDispatcher()
            val handle =
                FakePlayerHandle().apply {
                    fakePositionMs = 20_000L
                    fakeDurationMs = 100_000L
                }
            val repository = FakeWatchStateRepository()
            val vm = viewModel(handle, repository)
            vm.open("s1")

            handle.emitPlaying(true)
            handle.emitPlaying(false)
            advanceUntilIdle()

            assertEquals(listOf("setProgress s1 20.0 100.0"), repository.calls)
        }

    @Test
    fun theTenSecondTickerSavesRepeatedlyWhilePlaying() =
        runTest {
            installMainDispatcher()
            val handle =
                FakePlayerHandle().apply {
                    fakePositionMs = 5_000L
                    fakeDurationMs = 100_000L
                }
            val repository = FakeWatchStateRepository()
            val vm = viewModel(handle, repository)
            vm.open("s1")

            handle.emitPlaying(true)
            // Not followed by advanceUntilIdle(): the ticker's `while (true)`
            // always has one more save queued past however far time is moved,
            // so draining the scheduler to empty would never return.
            advanceTimeBy(25_000)

            // Two ticks land in 25s of playing: at 10s and 20s: the third is
            // due at 30s, past the window this test advances.
            assertEquals(2, repository.calls.count { it.startsWith("setProgress") })

            handle.emitPlaying(false) // stop the ticker so the test ends cleanly
        }

    @Test
    fun pausingStopsTheTicker() =
        runTest {
            installMainDispatcher()
            val handle =
                FakePlayerHandle().apply {
                    fakePositionMs = 5_000L
                    fakeDurationMs = 100_000L
                }
            val repository = FakeWatchStateRepository()
            val vm = viewModel(handle, repository)
            vm.open("s1")

            handle.emitPlaying(true)
            advanceTimeBy(5_000)
            handle.emitPlaying(false) // the pause's own save
            advanceUntilIdle()
            val afterPause = repository.calls.size

            advanceTimeBy(30_000)
            advanceUntilIdle()

            assertEquals(afterPause, repository.calls.size, "no ticker save should land once paused")
        }

    @Test
    fun noProfileChosenRecordsNothing() =
        runTest {
            installMainDispatcher()
            val handle =
                FakePlayerHandle().apply {
                    fakePositionMs = 20_000L
                    fakeDurationMs = 100_000L
                }
            val repository = FakeWatchStateRepository(profileChosen = false)
            val vm = viewModel(handle, repository)
            vm.open("s1")

            handle.emitPlaying(true)
            handle.emitPlaying(false)
            advanceUntilIdle()

            assertTrue(repository.calls.isEmpty())
        }
}
