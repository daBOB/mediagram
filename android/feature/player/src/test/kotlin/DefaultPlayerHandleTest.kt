package player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression coverage for a singleton handle over a singleton player:
 * [DefaultPlayerHandle.release] must detach only the current subscriber,
 * never the bridge [Player.Listener] registered on the real player in
 * `init` — that bridge never runs again, so removing it here would
 * silence every future [PlayerViewModel] that reuses this handle.
 */
class DefaultPlayerHandleTest {

    @Test
    fun releaseNeverDetachesTheListenerBridgeFromThePlayer() {
        val player = mockk<ExoPlayer>(relaxed = true)
        val handle = DefaultPlayerHandle(player)
        handle.setListener(object : PlayerHandle.Listener {
            override fun onPositionChanged(positionMs: Long, durationMs: Long, isPlaying: Boolean) = Unit
            override fun onError(message: String) = Unit
        })

        handle.release()

        verify(exactly = 0) { player.removeListener(any()) }
    }

    @Test
    fun aListenerSetAfterReleaseStillReceivesEventsFromThePlayer() {
        val player = mockk<ExoPlayer>(relaxed = true)
        val bridgeSlot = slot<Player.Listener>()
        every { player.addListener(capture(bridgeSlot)) } returns Unit
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 10_000L

        val handle = DefaultPlayerHandle(player)
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
}
