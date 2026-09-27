package player

import app.cash.turbine.test
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import playback.FilmPreloadState
import playback.InMemoryLanCacheSettings
import playback.LanServer
import playback.LanSetStatus
import playback.PauseReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [TitlePreloadViewModel]: every [FilmPreloadState] reads back as the label
 * a film page shows, [TitlePreloadViewModel.toggle] reaches the right call
 * on the engine for each of them, and [TitlePreloadViewModel.serverLine]
 * only keeps polling while the film is actually [FilmPreloadState.Running].
 */
class TitlePreloadViewModelTest {
    private val preloading = FakeFilmPreloading()
    private val lanSettings = InMemoryLanCacheSettings()
    private val server = LanServer(baseUrl = "http://192.168.1.9:7788", host = "192.168.1.9:7788")

    private fun viewModel(paired: LanServer? = server, answer: LanSetStatus? = null) =
        TitlePreloadViewModel(preloading, lanSettings, FakeLanServerSource(paired), FakeLanChunkProtocol(answer))

    /** 1024^3 bytes — the unit [model.humanSize] steps to once a count clears the KB/MB thresholds. */
    private val oneGb = 1_073_741_824L

    @Test
    fun everyStateReadsBackAsItsOwnLabel() {
        val total = 5 * oneGb // "5.0 GB"
        val partial = (total * 35) / 100 // exactly 35% of total
        val cases =
            listOf(
                FilmPreloadState.Idle(0L, total) to "Preload · 5.0 GB",
                FilmPreloadState.Idle(partial, total) to "Preload · 35% held",
                FilmPreloadState.Queued to "Queued",
                FilmPreloadState.Running(partial, total) to "Preloading",
                FilmPreloadState.Paused(partial, total, PauseReason.Playing) to "Paused while playing",
                FilmPreloadState.Paused(partial, total, PauseReason.Metered) to "Waiting for Wi-Fi",
                FilmPreloadState.Paused(partial, total, PauseReason.TimeLimit) to "Paused (background limit reached)",
                FilmPreloadState.Done to "Preloaded ✓",
                FilmPreloadState.NeedsSpace(total) to "Needs 5.0 GB · Try again",
                FilmPreloadState.Failed("Could not preload this film") to "Could not preload this film · Retry",
                FilmPreloadState.Failed("x".repeat(40)) to "x".repeat(27) + "… · Retry",
            )
        for ((state, label) in cases) assertEquals(label, preloadLabel(state), "state $state")
    }

    @Test
    fun aFullyHeldIdleReadsExactlyLikeDone() {
        val total = 5 * oneGb
        assertEquals(preloadLabel(FilmPreloadState.Done), preloadLabel(FilmPreloadState.Idle(total, total)))
        assertEquals(PreloadTapAction.NONE, preloadTapAction(FilmPreloadState.Idle(total, total)))
        // Held past total (a moment of eviction/re-count skew) reads the same way, not as an error.
        assertEquals(preloadLabel(FilmPreloadState.Done), preloadLabel(FilmPreloadState.Idle(total + 1, total)))
    }

    @Test
    fun aSliverHeldReadsAsLessThanOnePercentRatherThanZero() {
        val total = 5 * oneGb
        assertEquals("Preload · < 1% held", preloadLabel(FilmPreloadState.Idle(1L, total)))
    }

    @Test
    fun theBarReadsHeldOfTotalAndAPercentage() {
        val total = 5 * oneGb // "5.0 GB"
        val held = (total * 40) / 100 // exactly 40% of total, "2.0 GB"
        assertEquals("2.0 of 5.0 GB · 40%", preloadBarLabel(held, total))
    }

    @Test
    fun theBarKeepsTheHeldFiguresOwnUnitWhenItDiffersFromTheTotals() {
        val total = 5 * oneGb // "5.0 GB"
        val held = 500L * 1024 * 1024 // 500 MB
        assertEquals("500 MB of 5.0 GB · 9%", preloadBarLabel(held, total))
        assertEquals("Home server: 500 MB of 5.0 GB", preloadServerLine(held, total))
    }

    @Test
    fun idleAndFailedToggleEnqueue() = runTest {
        val vm = viewModel()
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.Idle(0L, 100L))
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.Failed("network"))
        assertEquals(listOf(Triple("f1", "Dune", 100L), Triple("f1", "Dune", 100L)), preloading.enqueueCalls)
    }

    @Test
    fun queuedRunningAndAnOrdinaryPauseToggleCancel() = runTest {
        val vm = viewModel()
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.Queued)
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.Running(1L, 100L))
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.Paused(1L, 100L, PauseReason.Metered))
        assertEquals(listOf("f1", "f1", "f1"), preloading.cancelCalls)
    }

    @Test
    fun aTimeLimitPauseTogglesEnqueueRatherThanCancel() = runTest {
        val vm = viewModel()
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.Paused(1L, 100L, PauseReason.TimeLimit))
        assertEquals(listOf(Triple("f1", "Dune", 100L)), preloading.enqueueCalls)
        assertEquals(emptyList(), preloading.cancelCalls)
    }

    @Test
    fun doneToggleDoesNothing() = runTest {
        val vm = viewModel()
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.Done)
        assertEquals(emptyList(), preloading.enqueueCalls)
        assertEquals(emptyList(), preloading.cancelCalls)
    }

    /** NeedsSpace retries the same way Idle/Failed start — the only thing that re-judges `fits` against a budget the viewer may just have raised. */
    @Test
    fun needsSpaceTogglesEnqueueSoRaisingTheBudgetIsNotADeadEnd() = runTest {
        val vm = viewModel()
        vm.toggle("f1", "Dune", 100L, FilmPreloadState.NeedsSpace(100L))
        assertEquals(listOf(Triple("f1", "Dune", 100L)), preloading.enqueueCalls)
        assertEquals(emptyList(), preloading.cancelCalls)
    }

    @Test
    fun removeReachesTheEngine() = runTest {
        val vm = viewModel()
        vm.remove("f1")
        assertEquals(listOf("f1"), preloading.removeCalls)
    }

    @Test
    fun serverLineIsNullWithNoServerPaired() = runTest {
        val vm = viewModel(paired = null, answer = LanSetStatus(total = 100L, chunksHeld = 1L, bytesHeld = 50L))
        preloading.setState("f1", 100L, FilmPreloadState.Idle(0L, 100L))
        vm.serverLine("f1", 100L).test { assertNull(awaitItem()) }
    }

    @Test
    fun serverLineIsNullWhenTheServerHoldsNothing() = runTest {
        val vm = viewModel(answer = LanSetStatus(total = null, chunksHeld = 0L, bytesHeld = 0L))
        preloading.setState("f1", 100L, FilmPreloadState.Idle(0L, 100L))
        vm.serverLine("f1", 100L).test { assertNull(awaitItem()) }
    }

    @Test
    fun serverLineTakesTheFilmsOwnTotalNeverTheServersPossiblyNullOne() = runTest {
        val vm = viewModel(answer = LanSetStatus(total = null, chunksHeld = 1L, bytesHeld = 50L))
        preloading.setState("f1", 100L, FilmPreloadState.Idle(0L, 100L))
        vm.serverLine("f1", 100L).test { assertEquals("Home server: 50 of 100 B", awaitItem()) }
    }

    @Test
    fun serverLinePollsOnlyWhileRunningAndStopsOnceItIsNot() = runTest {
        val client = FakeLanChunkProtocol(LanSetStatus(total = 100L, chunksHeld = 1L, bytesHeld = 10L))
        val vm = TitlePreloadViewModel(preloading, lanSettings, FakeLanServerSource(server), client)
        preloading.setState("f1", 100L, FilmPreloadState.Idle(0L, 100L))

        vm.serverLine("f1", 100L).test {
            awaitItem() // the immediate poll on open
            assertEquals(1, client.setStatusCalls)

            preloading.setState("f1", 100L, FilmPreloadState.Running(10L, 100L))
            awaitItem() // running started: an immediate re-poll, not a wait
            assertEquals(2, client.setStatusCalls)

            advanceTimeBy(5_001L)
            awaitItem()
            assertEquals(3, client.setStatusCalls, "still running: one poll every 5s")

            preloading.setState("f1", 100L, FilmPreloadState.Done)
            awaitItem() // no longer running: one last poll, then the loop stops
            val callsAtStop = client.setStatusCalls

            advanceTimeBy(20_000L)
            expectNoEvents()
            assertEquals(callsAtStop, client.setStatusCalls, "polling stopped once the film left Running")
        }
    }
}
