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
import data.WatchStateRepository
import data.WatchSync
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import model.WatchSnapshot
import playback.PlaybackCounters
import player.DefaultPlayerHandle
import player.PlayerViewModel
import player.ProgressRecorder

/** Real handle, ViewModel and recorder; only media decoding and persistent storage are replaced. */
internal class PlayerLifecycleFixture : AutoCloseable {
    val media = mockk<ExoPlayer>(relaxed = true)
    val repository = mockk<WatchStateRepository>(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = mutableListOf<Player.Listener>()
    private var playbackState = Player.STATE_IDLE
    var positionMs = 42_000L
    var compositions = 0
    val disposals = mutableListOf<Boolean>()
    var createdViewModels = 0

    init {
        every { repository.snapshot } returns MutableStateFlow(WatchSnapshot.Empty)
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
                createdViewModels++
                @Suppress("UNCHECKED_CAST")
                return PlayerViewModel(
                    handle,
                    PlaybackCounters(),
                    repository,
                    ProgressRecorder(repository),
                    mockk<WatchSync>(relaxed = true),
                ) as T
            }
        }

    override fun close() = scope.cancel()
}
