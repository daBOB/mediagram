package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [PlayerViewModel.pauseForPipDismissal] — the viewer closing the
 * picture-in-picture window, per the user decision: a pause that saves
 * progress and drops the service, never a full [PlayerViewModel.stop]
 * that would clear the title itself.
 */
class PlayerPipDismissalTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun pausesTheHandleAndStopsTheServiceWithoutClearingTheOpenTitle() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply {
            fakePositionMs = 20_000L
            fakeDurationMs = 100_000L
        }
        val serviceController = FakePlaybackServiceController()
        val vm = buildViewModel(handle, playbackServiceController = serviceController)
        vm.open("s1")
        handle.emitPlaying(true)

        vm.pauseForPipDismissal()

        assertTrue(handle.pauseCalled)
        assertEquals(1, serviceController.stopCalls)
        // Never handle.stop(): the title stays open so reopening the app
        // finds it exactly where it was, not back at the catalog.
        assertTrue(!handle.stopCalled)
        assertEquals("s1", handle.openedSetId)
    }

    @Test
    fun savesTheCurrentPosition() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply {
            fakePositionMs = 20_000L
            fakeDurationMs = 100_000L
        }
        val repository = FakeWatchStateRepository()
        val vm = buildViewModel(handle, repository)
        vm.open("s1")

        vm.pauseForPipDismissal()
        advanceUntilIdle()

        assertEquals(listOf("setProgress s1 20.0 100.0"), repository.calls)
    }
}
