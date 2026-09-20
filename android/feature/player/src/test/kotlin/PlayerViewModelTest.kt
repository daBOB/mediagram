package player

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerViewModelTest {

    @Test
    fun preparingIsTheFirstStateForASet() = runTest {
        val vm = PlayerViewModel(FakePlayerHandle())
        vm.open("s1")
        assertEquals(PlayerUiState.Preparing, vm.state.value)
    }

    @Test
    fun aPlayerErrorSurfacesAsFailed() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle)
        vm.open("s1")
        handle.emitError("decoder init failed")
        assertTrue(vm.state.value is PlayerUiState.Failed)
    }

    @Test
    fun openIsForwardedToTheHandle() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle)
        vm.open("s1")
        assertEquals("s1", handle.openedSetId)
    }

    @Test
    fun positionUpdatesReflectWhetherThePlayerIsPlaying() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle)
        vm.open("s1")

        handle.emitPosition(positionMs = 1_000, durationMs = 10_000, isPlaying = true)
        assertEquals(PlayerUiState.Playing(1_000, 10_000), vm.state.value)

        handle.emitPosition(positionMs = 2_000, durationMs = 10_000, isPlaying = false)
        assertEquals(PlayerUiState.Paused(2_000, 10_000), vm.state.value)
    }
}
