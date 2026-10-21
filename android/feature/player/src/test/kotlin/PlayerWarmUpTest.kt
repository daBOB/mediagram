package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A title the viewer opens warms up before it plays: it opens paused and
 * starts once its buffer is full, so a heavy file on a slow link does not
 * start at once and then stall.
 */
class PlayerWarmUpTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anOpenedTitleStartsPausedAndPlaysOnceTheLoaderHasFilledTheBuffer() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle().apply { fakePositionMs = 0L; fakeBufferedPositionMs = 0L; fakeIsLoading = true }
            val vm = buildViewModel(handle)
            vm.open("s1")
            runCurrent()

            assertEquals(false, handle.openedPlayWhenReady)
            assertTrue(vm.upNext.value.awaitingStart)
            assertFalse(handle.playCalled)

            // Half full and still loading: still warming up.
            handle.fakeBufferedPositionMs = 25_000L
            advanceTimeBy(600)
            runCurrent()
            assertFalse(handle.playCalled)

            // media3 stops loading once its buffer is full.
            handle.fakeBufferedPositionMs = 50_000L
            handle.fakeIsLoading = false
            advanceTimeBy(600)
            runCurrent()
            assertTrue(handle.playCalled)
            assertFalse(vm.upNext.value.awaitingStart)
        }

    @Test
    fun theWarmUpLineSaysHowFullTheBufferIs() {
        assertEquals("Warming up · 0:12 of 0:50", warmUpLine(12_000))
        assertEquals("Warming up · 0:50 of 0:50", warmUpLine(70_000))
    }
}
