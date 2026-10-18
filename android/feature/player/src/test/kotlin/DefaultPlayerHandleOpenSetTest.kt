package player

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [DefaultPlayerHandle.openSet]: what a TV answers "busy" from, so a queued open counts too. */
@RunWith(RobolectricTestRunner::class)
class DefaultPlayerHandleOpenSetTest {

    @Test
    fun openSetIsSetByOpenEvenQueuedAndClearedByStop() = runTest {
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        handle.open("s1", 0) // queued: the player isn't built yet
        assertEquals("s1", handle.openSet.value)
        handle.stop()
        assertNull(handle.openSet.value)

        deferred.complete(mockk(relaxed = true))
        advanceUntilIdle()
        handle.open("s2", 0)
        assertEquals("s2", handle.openSet.value)
        handle.stop()
        assertNull(handle.openSet.value)
    }

    @Test
    fun aPlayerThatFailedToBuildHoldsNoSet() = runTest {
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)
        handle.open("s1", 0) // queued: the player isn't built yet

        deferred.completeExceptionally(IllegalStateException("no space for the cache index"))
        advanceUntilIdle()

        assertNull(handle.openSet.value)
    }
}
