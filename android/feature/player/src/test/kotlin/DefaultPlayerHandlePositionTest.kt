package player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DefaultPlayerHandle.positionMs] and [DefaultPlayerHandle.durationMs] —
 * split out of [DefaultPlayerHandleTest] to keep that file under the
 * project's line guideline.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultPlayerHandlePositionTest {

    @Test
    fun positionAndDurationAreNothingToTrustBeforeThePlayerIsBuilt() = runTest {
        // backgroundScope, not `this`: the deferred is deliberately left
        // incomplete to model "still building", and runTest requires every
        // coroutine on its own scope to finish by the time the test ends.
        val handle = DefaultPlayerHandle(CompletableDeferred<ExoPlayer>(), backgroundScope)

        assertEquals(null, handle.positionMs())
        assertEquals(null, handle.durationMs())
    }

    @Test
    fun positionAndDurationAreNothingToTrustWhileIdle() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.playbackState } returns Player.STATE_IDLE
        every { player.currentPosition } returns 5_000L
        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()

        // STATE_IDLE covers "never prepared" and "just errored" alike —
        // a stale currentPosition from before a failure is not a place to
        // resume to, so this must read null in both, not the stale number.
        assertEquals(null, handle.positionMs())
        assertEquals(null, handle.durationMs())
    }

    @Test
    fun positionAndDurationReadFromThePlayerOnceReady() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.playbackState } returns Player.STATE_READY
        every { player.currentPosition } returns 42_000L
        every { player.duration } returns 100_000L
        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()

        assertEquals(42_000L, handle.positionMs())
        assertEquals(100_000L, handle.durationMs())
    }
}
