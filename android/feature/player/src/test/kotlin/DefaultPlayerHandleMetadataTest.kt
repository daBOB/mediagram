package player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Covers [DefaultPlayerHandle.setMetadata] reapplying itself on every real
 * load through [PendingMetadata] — a cold start (the resolve that supplies
 * it can finish before the player itself is ready) and a retry (a real
 * reload of the *same* title, whose metadata already resolved once and
 * never will again) both silently lost it before.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultPlayerHandleMetadataTest {

    @Test
    fun metadataSetBeforeThePlayerExistsIsAppliedOnceTheQueuedOpenLands() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.currentMediaItem } returns MediaItem.fromUri("mlib://set/s1")
        every { player.currentMediaItemIndex } returns 0
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        handle.open("s1", 0) // queued: the player isn't built yet
        val metadata = MediaMetadata.Builder().setTitle("Blade Runner 2049").build()
        handle.setMetadata(metadata) // also before the player exists — must not throw, and must be remembered

        deferred.complete(player)
        advanceUntilIdle()

        verify { player.replaceMediaItem(0, match { sameTitle(it, metadata) }) }
    }

    @Test
    fun retryingAfterAnErrorReappliesTheAlreadyResolvedMetadata() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.currentMediaItem } returns MediaItem.fromUri("mlib://set/s1")
        every { player.currentMediaItemIndex } returns 0
        val handle = DefaultPlayerHandle(CompletableDeferred(player), this)
        advanceUntilIdle()

        handle.open("s1", 0) // the original open, real (currentSetId was null)
        val metadata = MediaMetadata.Builder().setTitle("Blade Runner 2049").build()
        handle.setMetadata(metadata) // resolves once, as usual

        every { player.playbackState } returns Player.STATE_IDLE // the failure retry() reopens from
        handle.open("s1", 0) // retry(): same title, but a real reload since STATE_IDLE

        verify(exactly = 2) { player.replaceMediaItem(0, match { sameTitle(it, metadata) }) }
    }

    private fun sameTitle(item: MediaItem, metadata: MediaMetadata): Boolean =
        item.mediaMetadata.title == metadata.title
}
