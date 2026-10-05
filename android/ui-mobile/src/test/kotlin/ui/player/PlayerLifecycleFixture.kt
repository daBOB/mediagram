@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import data.WatchSync
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import playback.PlaybackCounters
import player.DefaultPlayerHandle
import player.PlayerViewModel
import player.ProgressRecorder
import data.CatalogRepository
import data.PlayerPreferences
import playback.SubtitleTrackSource
import playback.SeriesPreloading
import playback.HeldSetsQuery
import player.PlaybackServiceController
import testing.WatchStateFixture

/**
 * Real handle, ViewModel, recorder and watch-state repository; only media
 * decoding and the core behind the repository are replaced. [watchState]
 * is whose state the player reads and writes — the default viewer unless a
 * test needs someone else, or nobody.
 */
internal class PlayerLifecycleFixture(
    val watchState: WatchStateFixture = WatchStateFixture(),
    private val catalog: CatalogRepository = mockk(relaxed = true),
) : AutoCloseable {
    val media = mockk<ExoPlayer>(relaxed = true)
    val repository = watchState.repository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = mutableListOf<Player.Listener>()
    private var playbackState = Player.STATE_IDLE
    var positionMs = 42_000L

    init {
        every { media.applicationLooper } returns Looper.getMainLooper()
        every { media.videoSize } returns VideoSize.UNKNOWN
        every { media.currentTracks } returns Tracks.EMPTY
        every { media.mediaMetadata } returns MediaMetadata.EMPTY
        every { media.currentTimeline } returns Timeline.EMPTY
        every { media.availableCommands } returns Player.Commands.EMPTY
        every { media.playbackState } answers { playbackState }
        every { media.isPlaying } answers { playbackState == Player.STATE_READY }
        every { media.currentPosition } answers { positionMs }
        every { media.duration } returns 600_000L
        every { media.addListener(any()) } answers { listeners.add(firstArg()) }
        every { media.removeListener(any()) } answers { listeners.remove(firstArg()) }
        every { media.prepare() } answers {
            playbackState = Player.STATE_READY
            listeners.toList().forEach { it.onPlaybackStateChanged(playbackState) }
        }
        every { media.stop() } answers {
            playbackState = Player.STATE_IDLE
            listeners.toList().forEach { it.onIsPlayingChanged(false) }
        }
    }

    private val handle = DefaultPlayerHandle(CompletableDeferred(media), scope)
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PlayerViewModel(
                    handle,
                    PlaybackCounters(),
                    repository,
                    ProgressRecorder(repository),
                    mockk<WatchSync>(relaxed = true),
                    catalog,
                    mockk<PlayerPreferences>(relaxed = true),
                    mockk<SubtitleTrackSource>(relaxed = true),
                    PlaybackServiceController.Noop,
                    SeriesPreloading.Noop,
                    HeldSetsQuery.Noop,
                ) as T
            }
        }

    override fun close() = scope.cancel()
}
