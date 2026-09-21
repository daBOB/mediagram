package player

import kotlinx.coroutines.test.runTest
import playback.PlaybackCounters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerViewModelTest {

    @Test
    fun preparingIsTheFirstStateForASet() = runTest {
        val vm = PlayerViewModel(FakePlayerHandle(), PlaybackCounters())
        vm.open("s1")
        assertEquals(PlayerUiState.Preparing, vm.state.value)
    }

    @Test
    fun aPlayerErrorSurfacesAsFailed() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle, PlaybackCounters())
        vm.open("s1")
        handle.emitError("decoder init failed")
        assertTrue(vm.state.value is PlayerUiState.Failed)
    }

    @Test
    fun openingAgainClearsAPreviousFailure() = runTest {
        // Unlike preparingIsTheFirstStateForASet, this can't pass by
        // relying on _state's initial value alone: it fails a previous
        // set on purpose first, so only an actual reset inside open()
        // can bring it back to Preparing.
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle, PlaybackCounters())
        vm.open("s1")
        handle.emitError("decoder init failed")
        assertTrue(vm.state.value is PlayerUiState.Failed)

        vm.open("s2")

        assertEquals(PlayerUiState.Preparing, vm.state.value)
    }

    @Test
    fun openIsForwardedToTheHandle() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle, PlaybackCounters())
        vm.open("s1")
        assertEquals("s1", handle.openedSetId)
    }

    @Test
    fun theStateFollowsWhetherThePlayerIsPlaying() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle, PlaybackCounters())
        vm.open("s1")

        handle.emitPlaying(isPlaying = true)
        assertEquals(PlayerUiState.Playing, vm.state.value)

        handle.emitPlaying(isPlaying = false)
        assertEquals(PlayerUiState.Paused, vm.state.value)
    }

    @Test
    fun stopIsForwardedToTheHandle() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle, PlaybackCounters())
        vm.stop()
        assertTrue(handle.stopCalled)
    }
}
