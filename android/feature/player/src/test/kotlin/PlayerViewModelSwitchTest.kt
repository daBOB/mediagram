package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real switch path through [PlayerViewModel] — not [UpNextController]
 * standing alone: a countdown ending, or "Play now" pressed mid-title, asks
 * to switch; only a real reopen (`PlayerScreen`'s own effect, modelled here
 * by calling [PlayerViewModel.open] again) actually calls `handle.open` for
 * the next title. [FakePlayerHandle.open]'s own synthetic "stopped
 * playing" — modelling the real `setMediaItem`'s — is what let the next
 * title's position land on Continue at 0s before [PlayerSession.save]'s own
 * floor; these pin that the floor now catches it.
 */
class PlayerViewModelSwitchTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun endingSwitchesThroughARealReopenAndTheCountdownCancelsItself() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val catalogRepository = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2", durationSecs = 100)))
        val vm = buildViewModel(handle, catalogRepository = catalogRepository)
        vm.open("s1", listOf("s1", "s2"))
        runCurrent()

        handle.emitEnded()
        assertEquals(UpNextPhase.COUNTING, vm.upNext.value.phase)

        advanceTimeBy(10_000) // the countdown running out, cancelling itself from inside its own onFinished
        runCurrent()

        val switch = assertNotNull(vm.pendingSwitch.value)
        assertEquals("s2", switch.setId)

        // What `PlayerScreen`'s own effect does once `LibraryPositions` has moved.
        vm.open(switch.setId, switch.run)
        vm.switchAcknowledged()
        runCurrent()

        assertEquals("s2", handle.openedSetId)
        assertTrue(vm.upNext.value.awaitingStart) // paused, waiting on the gate against the title now actually open
    }

    @Test
    fun playNowMidTitleDoesNotLeaveTheNextTitleOnContinueAtZero() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply { fakePositionMs = 0L }
        val repository = FakeWatchStateRepository()
        val catalogRepository = FakeCatalogRepository(mapOf("s2" to fakeMediaSet("s2", durationSecs = 100)))
        val vm = buildViewModel(handle, repository, catalogRepository = catalogRepository)
        vm.open("s1", listOf("s1", "s2"))
        runCurrent()
        handle.emitPlaying(true) // actively playing when "Play now" is pressed

        vm.playNext()
        val switch = assertNotNull(vm.pendingSwitch.value)
        vm.open(switch.setId, switch.run) // the real `setMediaItem`'s own synchronous stop fires inside this call
        runCurrent()

        assertTrue(repository.calls.none { it.startsWith("setProgress s2") })
    }
}
