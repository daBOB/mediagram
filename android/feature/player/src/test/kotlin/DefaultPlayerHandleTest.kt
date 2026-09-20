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
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
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
            override fun onPlayingChanged(isPlaying: Boolean) = Unit
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

        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()
        handle.release()

        var delivered = false
        handle.setListener(object : PlayerHandle.Listener {
            override fun onPlayingChanged(isPlaying: Boolean) {
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

    @Test
    fun reopeningTheSameSetDoesNotResetItsPosition() = runTest {
        // BasePlayer.setMediaItem(MediaItem) always resets to position
        // zero; a rotation re-runs PlayerScreen's LaunchedEffect(setId)
        // with the same id in a brand-new Composition, so open() must not
        // call it a second time for a set that's already loaded.
        val player = mockk<ExoPlayer>(relaxed = true)
        // Stated rather than left to the mock's default, which is not any
        // real playback state: "still loaded" is the whole premise here.
        every { player.playbackState } returns Player.STATE_READY
        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()

        handle.open("s1")
        handle.open("s1")

        verify(exactly = 1) { player.setMediaItem(any<MediaItem>()) }
        verify(exactly = 1) { player.prepare() }
    }

    @Test
    fun stoppingDuringConstructionPreventsThePendingOpenFromStartingPlayback() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        handle.open("s1") // queued: the player isn't built yet
        handle.stop() // the screen is left before construction finishes

        deferred.complete(player)
        advanceUntilIdle()

        verify(exactly = 0) { player.setMediaItem(any<MediaItem>()) }
        verify(exactly = 0) { player.prepare() }
    }

    @Test
    fun aFailedPlayerConstructionSurfacesAsAnErrorRatherThanCrashing() = runTest {
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)
        var errorMessage: String? = null
        handle.setListener(object : PlayerHandle.Listener {
            override fun onPlayingChanged(isPlaying: Boolean) = Unit
            override fun onError(message: String) {
                errorMessage = message
            }
        })

        deferred.completeExceptionally(IOException("no space left for the cache"))
        advanceUntilIdle()

        assertEquals("no space left for the cache", errorMessage)
    }
}
