package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [PlayerViewModel.open]/`stop` starting and stopping
 * `PlaybackService` (through [PlaybackServiceController]) alongside
 * whatever set is open — the lock-screen session must exist by the time a
 * title is playing, and must not outlive it.
 */
class PlayerPlaybackServiceTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun openingATitleStartsTheService() = runTest {
        installMainDispatcher()
        val serviceController = FakePlaybackServiceController()
        val vm = buildViewModel(playbackServiceController = serviceController)

        vm.open("s1")

        assertEquals(1, serviceController.startCalls)
    }

    @Test
    fun reopeningTheSameTitleOnARotationStartsItAgain() = runTest {
        // Idempotent by design (starting an already-started service is a
        // no-op) — this only pins that `open` never skips the call for the
        // "same title" fast path.
        installMainDispatcher()
        val serviceController = FakePlaybackServiceController()
        val vm = buildViewModel(playbackServiceController = serviceController)

        vm.open("s1")
        vm.open("s1")

        assertEquals(2, serviceController.startCalls)
    }

    @Test
    fun stoppingTheTitleStopsTheService() = runTest {
        installMainDispatcher()
        val serviceController = FakePlaybackServiceController()
        val vm = buildViewModel(playbackServiceController = serviceController)
        vm.open("s1")

        vm.stop()

        assertEquals(1, serviceController.stopCalls)
    }
}
