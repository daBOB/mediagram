package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * State and lifecycle only. Resume-position math is in
 * [PlayerResumePositionTest], and the save ticker is in
 * [PlayerSaveTickerTest] — split apart to keep each file under the
 * project's line guideline.
 */
class PlayerViewModelTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun preparingIsTheFirstStateForASet() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("s1")
        assertEquals(PlayerUiState.Preparing, vm.state.value)
    }

    @Test
    fun aPlayerErrorSurfacesAsFailed() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val vm = buildViewModel(handle)
        vm.open("s1")
        handle.emitError("decoder init failed")
        assertTrue(vm.state.value is PlayerUiState.Failed)
    }

    @Test
    fun openingAgainClearsAPreviousFailure() = runTest {
        installMainDispatcher()
        // Unlike preparingIsTheFirstStateForASet, this can't pass by
        // relying on _state's initial value alone: it fails a previous
        // set on purpose first, so only an actual reset inside open()
        // can bring it back to Preparing.
        val handle = FakePlayerHandle()
        val vm = buildViewModel(handle)
        vm.open("s1")
        handle.emitError("decoder init failed")
        assertTrue(vm.state.value is PlayerUiState.Failed)

        vm.open("s2")

        assertEquals(PlayerUiState.Preparing, vm.state.value)
    }

    @Test
    fun openIsForwardedToTheHandle() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val vm = buildViewModel(handle)
        vm.open("s1")
        assertEquals("s1", handle.openedSetId)
    }

    @Test
    fun theStateFollowsWhetherThePlayerIsPlaying() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val vm = buildViewModel(handle)
        vm.open("s1")

        handle.emitPlaying(isPlaying = true)
        assertEquals(PlayerUiState.Playing, vm.state.value)

        handle.emitPlaying(isPlaying = false)
        assertEquals(PlayerUiState.Paused, vm.state.value)
    }

    @Test
    fun stopIsForwardedToTheHandle() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val vm = buildViewModel(handle)
        vm.stop()
        assertTrue(handle.stopCalled)
    }

    @Test
    fun stoppingTellsWatchSyncToSyncSoon() = runTest {
        installMainDispatcher()
        val watchSync = FakeWatchSync()
        val vm = buildViewModel(watchSync = watchSync)

        vm.stop()

        assertEquals(1, watchSync.soonCalls)
    }
}
