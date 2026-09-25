package player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
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

        handle.open("s1", 0) // requested before the handle's own setup coroutine has even run
        verify(exactly = 0) { player.setMediaItem(any<MediaItem>(), any<Long>()) }

        deferred.complete(player)
        advanceUntilIdle()

        verify(exactly = 1) { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify(exactly = 1) { player.prepare() }
    }

    @Test
    fun aStartPositionQueuedBeforeThePlayerIsReadyIsAppliedOnceItArrives() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        handle.open("s1", 90_000)
        deferred.complete(player)
        advanceUntilIdle()

        verify(exactly = 1) { player.setMediaItem(any<MediaItem>(), 90_000L) }
    }

    /**
     * L5: a speed asked for before the player exists is queued the same
     * way a start position is — [aStartPositionQueuedBeforeThePlayerIsReadyIsAppliedOnceItArrives]
     * above — but it must land *after* the queued open's own floor to 1x
     * (`openOn`), not before it: applied in the other order, the real
     * reset would be the last word and the remembered speed would be
     * silently dropped the moment the player actually opens.
     */
    @Test
    fun aSpeedQueuedBeforeThePlayerIsReadyIsAppliedAfterTheQueuedOpensOwnFloor() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        handle.open("s1", 0)
        handle.setPlaybackSpeed(1.5f)
        deferred.complete(player)
        advanceUntilIdle()

        verifyOrder {
            player.prepare()
            player.setPlaybackSpeed(1f)
            player.setPlaybackSpeed(1.5f)
        }
    }

    @Test
    fun openingASetSeeksToTheGivenStartPositionBeforePreparing() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()

        handle.open("s1", 45_000)

        // The two-argument overload before prepare(), not a seek after: a
        // seek once the player is already prepared briefly shows the start
        // of the title before jumping to the resume point.
        verifyOrder {
            player.setMediaItem(any<MediaItem>(), 45_000L)
            player.prepare()
        }
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

        handle.open("s1", 0)
        handle.open("s1", 0)

        verify(exactly = 1) { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify(exactly = 1) { player.prepare() }
    }

    @Test
    fun stoppingDuringConstructionPreventsThePendingOpenFromStartingPlayback() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        handle.open("s1", 0) // queued: the player isn't built yet
        handle.stop() // the screen is left before construction finishes

        deferred.complete(player)
        advanceUntilIdle()

        verify(exactly = 0) { player.setMediaItem(any<MediaItem>(), any<Long>()) }
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

        // The cause is logged; a viewer is told in words, not in the exception's own.
        assertEquals("Could not prepare the player", errorMessage)
    }
}
