package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Progress
import org.junit.After
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ten-second save ticker [PlayerSession] runs while playing. */
class PlayerSaveTickerTest {
    private val watch = WatchStateFixture()

    /** Every write the repository takes to the core — each save is one. */
    private var writes = 0

    init {
        watch.provider.beforeCore = { writes++ }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun pausingSavesTheCurrentPosition() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply {
            fakePositionMs = 20_000L
            fakeDurationMs = 100_000L
        }
        val vm = buildViewModel(handle, watch.repository)
        vm.open("s1")

        handle.emitPlaying(true)
        handle.emitPlaying(false)
        advanceUntilIdle()

        val saved = watch.repository.snapshot.value.progress
        assertEquals(listOf(Progress("s1", 20.0, 100.0, saved.single().updatedAt)), saved)
        assertEquals(1, writes)
    }

    @Test
    fun theTenSecondTickerSavesRepeatedlyWhilePlaying() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply {
            fakePositionMs = 5_000L
            fakeDurationMs = 100_000L
        }
        val vm = buildViewModel(handle, watch.repository)
        vm.open("s1")

        handle.emitPlaying(true)
        // Not followed by advanceUntilIdle(): the ticker's `while (true)`
        // always has one more save queued past however far time is moved,
        // so draining the scheduler to empty would never return.
        advanceTimeBy(25_000)

        // Two ticks land in 25s of playing: at 10s and 20s: the third is
        // due at 30s, past the window this test advances.
        assertEquals(2, writes)

        handle.emitPlaying(false) // stop the ticker so the test ends cleanly
    }

    @Test
    fun pausingStopsTheTicker() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply {
            fakePositionMs = 5_000L
            fakeDurationMs = 100_000L
        }
        val vm = buildViewModel(handle, watch.repository)
        vm.open("s1")

        handle.emitPlaying(true)
        advanceTimeBy(5_000)
        handle.emitPlaying(false) // the pause's own save
        advanceUntilIdle()
        val afterPause = writes

        advanceTimeBy(30_000)
        advanceUntilIdle()

        assertEquals(afterPause, writes, "no ticker save should land once paused")
    }

    @Test
    fun noProfileChosenRecordsNothing() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply {
            fakePositionMs = 20_000L
            fakeDurationMs = 100_000L
        }
        val nobody = WatchStateFixture(chosen = null)
        val vm = buildViewModel(handle, nobody.repository)
        vm.open("s1")

        handle.emitPlaying(true)
        handle.emitPlaying(false)
        advanceUntilIdle()

        assertTrue(nobody.core.snapshot(WatchStateFixture.VIEWER.id).progress.isEmpty())
    }
}
