package player

import androidx.media3.common.Player
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The card's ↺ and ⏮ and a row picked in the episode sidebar, through [PlayerViewModel]. */
class PlayerRunStepsTest {

    private val run = listOf("e1", "e2", "e3")

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun previousOpensTheTitleBeforeThroughTheSwitchNextTakes() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("e2", run)
        advanceUntilIdle()

        vm.previous()

        assertEquals(PendingPlayerSwitch("e1", run), vm.pendingSwitch.value)
    }

    @Test
    fun previousOnTheFirstTitleDoesNothing() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("e1", run)
        advanceUntilIdle()

        vm.previous()

        assertFalse(vm.upNext.value.hasPrevious)
        assertNull(vm.pendingSwitch.value)
    }

    @Test
    fun aFilmHasNoRunToStepThrough() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("f1")
        advanceUntilIdle()

        vm.previous()

        assertFalse(vm.upNext.value.inRun)
        assertNull(vm.pendingSwitch.value)
    }

    @Test
    fun aRowPickedInTheSidebarOpensThroughTheSameSwitch() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("e1", run)
        advanceUntilIdle()

        vm.playFromRun("e3")

        assertEquals(PendingPlayerSwitch("e3", run), vm.pendingSwitch.value)
    }

    /** ↺ is a seek, never a reopen: whatever play or pause was, stays. */
    @Test
    fun restartSeeksToTheTopAndLeavesPlayAndPauseAlone() = runTest {
        installMainDispatcher()
        val player = mockk<Player>(relaxed = true)
        val vm = buildViewModel(FakePlayerHandle().apply { installPlayer(player) })
        vm.open("e2", run)
        advanceUntilIdle()

        vm.restart()

        verify { player.seekTo(0L) }
        verify(exactly = 0) { player.play() }
        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.playWhenReady = any() }
    }
}
