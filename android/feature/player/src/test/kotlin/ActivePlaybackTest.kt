package player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import playback.OpenTitle
import playback.setUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Counts [get] calls — a plain [dagger.Lazy] a test can ask "was the player ever actually resolved". */
private class FakeLazy<T>(private val provider: () -> T) : dagger.Lazy<T> {
    var calls = 0
        private set

    override fun get(): T {
        calls++
        return provider()
    }
}

/**
 * The regression this closes: reading the player from a film's own
 * background worker crashed the app outright (media3 asserts the calling
 * thread on nearly every read), and merely injecting `FilmPreloading` —
 * which the catalogue now does — used to force the real `ExoPlayer` to
 * start building. Neither is exercised through a real `ExoPlayer` here;
 * `DefaultPlayerHandleTest` already established the pattern of a relaxed
 * mock plus the captured `Player.Listener` bridge for this module's tests.
 */
@RunWith(RobolectricTestRunner::class)
class ActivePlaybackTest {

    @Test
    fun constructionNeverResolvesTheDeferredPlayer() = runTest {
        val lazy = FakeLazy<Deferred<ExoPlayer>> { CompletableDeferred(mockk<ExoPlayer>(relaxed = true)) }
        ActivePlayback(lazy, mockk<CatalogRepository>(relaxed = true), this)
        advanceUntilIdle()

        assertEquals(0, lazy.calls, "building ActivePlayback (e.g. from the catalogue opening) must never itself build the player")
    }

    @Test
    fun ensureListeningResolvesThePlayerExactlyOnceEvenCalledAgain() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.playbackState } returns Player.STATE_IDLE
        val lazy = FakeLazy<Deferred<ExoPlayer>> { CompletableDeferred(player) }
        val active = ActivePlayback(lazy, mockk(relaxed = true), this)

        active.ensureListening()
        active.ensureListening()
        advanceUntilIdle()

        assertEquals(1, lazy.calls)
    }

    @Test
    fun anOpenTitleIsPublishedFromTheListenerBridgeAndReadableWithoutTouchingThePlayer() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val bridge = slot<Player.Listener>()
        every { player.addListener(capture(bridge)) } returns Unit
        every { player.playbackState } returns Player.STATE_READY
        every { player.currentMediaItem } returns MediaItem.fromUri(setUri("f1"))
        val repo = mockk<CatalogRepository>()
        coEvery { repo.mediaSet("f1") } returns fakeMediaSet("f1", totalBytes = 5_000L)
        val active = ActivePlayback(FakeLazy<Deferred<ExoPlayer>> { CompletableDeferred(player) }, repo, this)

        active.ensureListening()
        advanceUntilIdle()
        // The same call media3 itself would make on main once a set opens.
        bridge.captured.onPlaybackStateChanged(Player.STATE_READY)
        advanceUntilIdle()

        // Read straight off openTitle.value — a plain StateFlow read, the
        // same as FilmPreloader's own worker thread would do; nothing here
        // asks `player` anything.
        assertEquals(OpenTitle("f1", 5_000L), active.openTitle.value)
    }

    @Test
    fun stoppingClearsTheOpenTitle() = runTest {
        val player = mockk<ExoPlayer>(relaxed = true)
        val bridge = slot<Player.Listener>()
        every { player.addListener(capture(bridge)) } returns Unit
        every { player.playbackState } returns Player.STATE_READY
        every { player.currentMediaItem } returns MediaItem.fromUri(setUri("f1"))
        val repo = mockk<CatalogRepository>()
        coEvery { repo.mediaSet("f1") } returns fakeMediaSet("f1", totalBytes = 5_000L)
        val active = ActivePlayback(FakeLazy<Deferred<ExoPlayer>> { CompletableDeferred(player) }, repo, this)
        active.ensureListening()
        advanceUntilIdle()
        bridge.captured.onPlaybackStateChanged(Player.STATE_READY)
        advanceUntilIdle()

        every { player.playbackState } returns Player.STATE_IDLE
        bridge.captured.onPlaybackStateChanged(Player.STATE_IDLE)
        advanceUntilIdle()

        assertNull(active.openTitle.value)
    }
}
