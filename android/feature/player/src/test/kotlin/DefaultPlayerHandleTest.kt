package player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression coverage for a singleton handle over a singleton player:
 * [DefaultPlayerHandle.release] must detach only the current subscriber,
 * never the bridge [Player.Listener] registered on the real player once
 * it's built — that bridge never runs again, so removing it here would
 * silence every future [PlayerViewModel] that reuses this handle.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultPlayerHandleTest {

    @Test
    fun releaseNeverDetachesTheListenerBridgeFromThePlayer() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()
        handle.setListener(object : PlayerHandle.Listener {
            override fun onPositionChanged(positionMs: Long, durationMs: Long, isPlaying: Boolean) = Unit
            override fun onError(message: String) = Unit
        })

        handle.release()

        verify(exactly = 0) { player.removeListener(any()) }
    }

    @Test
    fun aListenerSetAfterReleaseStillReceivesEventsFromThePlayer() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val bridgeSlot = slot<Player.Listener>()
        every { player.addListener(capture(bridgeSlot)) } returns Unit
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 10_000L

        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()
        handle.release()

        var delivered = false
        handle.setListener(object : PlayerHandle.Listener {
            override fun onPositionChanged(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
                delivered = true
            }
            override fun onError(message: String) = Unit
        })

        // The real player would fire this on the one bridge it has — the
        // same instance captured when the handle was first constructed,
        // since release() never asked the player to forget it.
        bridgeSlot.captured.onIsPlayingChanged(true)

        assertTrue(delivered)
    }

    @Test
    fun openBeforeThePlayerIsReadyIsAppliedOnceItArrives() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        handle.open("s1") // requested before the handle's own setup coroutine has even run
        verify(exactly = 0) { player.setMediaItem(any<MediaItem>()) }

        deferred.complete(player)
        advanceUntilIdle()

        verify(exactly = 1) { player.setMediaItem(any<MediaItem>()) }
        verify(exactly = 1) { player.prepare() }
    }
}
