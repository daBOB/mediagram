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

    /**
     * A picture-in-picture dismissal (`pauseForPipDismissal`) stops the
     * service but leaves the title open; pressing play again never runs
     * through `open()`, which used to leave the title playing with no
     * session, no foreground state and no notification behind it.
     */
    @Test
    fun resumingPlaybackAfterAPipDismissalRestartsTheService() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val serviceController = FakePlaybackServiceController()
        val vm = buildViewModel(handle, playbackServiceController = serviceController)
        vm.open("s1")
        handle.emitPlaying(true)
        vm.pauseForPipDismissal()
        assertEquals(1, serviceController.stopCalls)
        val startsBeforeResuming = serviceController.startCalls

        handle.emitPlaying(true)

        assertEquals(startsBeforeResuming + 1, serviceController.startCalls)
        // Stops the session/up-next tickers this restart started — left
        // running, `runTest`'s own drain to idle at the end of the test
        // never terminates (kotlinx-coroutines-test's scheduler keeps
        // advancing virtual time to serve them forever). The fake mirrors
        // a real player's own synchronous `onIsPlayingChanged(false)` on
        // pause, the same way the ticker is stopped everywhere else.
        handle.emitPlaying(false)
    }
}
