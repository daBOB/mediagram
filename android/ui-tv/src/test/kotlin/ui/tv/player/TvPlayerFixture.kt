@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import data.CatalogRepository
import data.PlayerPreferences
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
import model.Profile
import model.WatchSnapshot
import playback.HeldSetsQuery
import playback.PlaybackCounters
import playback.SeriesPreloading
import playback.SubtitleTrackSource
import player.DefaultPlayerHandle
import player.PlaybackServiceController
import player.PlayerViewModel
import player.ProgressRecorder

/**
 * The phone's player fixture, for the television: a real handle, ViewModel
 * and recorder over an [ExoPlayer] that decodes nothing. It answers play,
 * pause and seek the way the real one reports them — through its listeners
 * — so the screen's media3 state holders and the ViewModel's own state
 * both see a press land, and it offers the commands a playing film offers,
 * so the transport is enabled as it would be.
 *
 * [snapshot] and [profile] are what the stubbed repository holds when no
 * [repository] is given: whose lists the player files into, and who is
 * watching — a kids profile hides the Kids mark.
 */
internal class TvPlayerFixture(
    repository: WatchStateRepository? = null,
    snapshot: WatchSnapshot = WatchSnapshot.Empty,
    profile: Profile? = null,
) : AutoCloseable {
    val media = mockk<ExoPlayer>(relaxed = true)
    val repository: WatchStateRepository = repository ?: mockk(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = mutableListOf<Player.Listener>()
    private var playbackState = Player.STATE_IDLE
    private var playWhenReady = true
    var positionMs = 42_000L
    val durationMs = 600_000L

    init {
        if (repository == null) {
            every { this@TvPlayerFixture.repository.snapshot } returns MutableStateFlow(snapshot)
            every { this@TvPlayerFixture.repository.profiles } returns MutableStateFlow(listOfNotNull(profile))
            every { this@TvPlayerFixture.repository.chosenProfileId } returns MutableStateFlow(profile?.id)
        }
        every { media.applicationLooper } returns Looper.getMainLooper()
        every { media.videoSize } returns VideoSize.UNKNOWN
        every { media.currentTracks } returns Tracks.EMPTY
        every { media.mediaMetadata } returns MediaMetadata.EMPTY
        every { media.currentTimeline } returns Timeline.EMPTY
        every { media.playbackParameters } returns PlaybackParameters.DEFAULT
        every { media.availableCommands } returns Player.Commands.Builder().addAll(*OFFERED).build()
        every { media.isCommandAvailable(any()) } answers { firstArg<Int>() in OFFERED }
        every { media.seekBackIncrement } returns 10_000L
        every { media.seekForwardIncrement } returns 10_000L
        every { media.playbackState } answers { playbackState }
        every { media.playWhenReady } answers { playWhenReady }
        every { media.isPlaying } answers { playbackState == Player.STATE_READY && playWhenReady }
        every { media.currentPosition } answers { positionMs }
        every { media.contentPosition } answers { positionMs }
        every { media.duration } returns durationMs
        every { media.contentDuration } returns durationMs
        every { media.addListener(any()) } answers { listeners.add(firstArg()) }
        every { media.removeListener(any()) } answers { listeners.remove(firstArg()) }
        every { media.prepare() } answers {
            playbackState = Player.STATE_READY
            listeners.toList().forEach { it.onPlaybackStateChanged(playbackState) }
        }
        every { media.play() } answers { setPlaying(true) }
        every { media.pause() } answers { setPlaying(false) }
        every { media.seekTo(any<Long>()) } answers { positionMs = firstArg() }
        every { media.stop() } answers {
            playbackState = Player.STATE_IDLE
            listeners.toList().forEach { it.onIsPlayingChanged(false) }
        }
    }

    val isPlaying: Boolean get() = media.isPlaying

    private fun setPlaying(playing: Boolean) {
        playWhenReady = playing
        listeners.toList().forEach {
            it.onPlayWhenReadyChanged(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            it.onIsPlayingChanged(media.isPlaying)
            it.onEvents(media, Player.Events(FlagSet.Builder().add(Player.EVENT_IS_PLAYING_CHANGED).add(Player.EVENT_PLAY_WHEN_READY_CHANGED).build()))
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
                    this@TvPlayerFixture.repository,
                    ProgressRecorder(this@TvPlayerFixture.repository),
                    mockk<WatchSync>(relaxed = true),
                    mockk<CatalogRepository>(relaxed = true),
                    mockk<PlayerPreferences>(relaxed = true),
                    mockk<SubtitleTrackSource>(relaxed = true),
                    PlaybackServiceController.Noop,
                    SeriesPreloading.Noop,
                    HeldSetsQuery.Noop,
                ) as T
            }
        }

    override fun close() = scope.cancel()

    private companion object {
        val OFFERED =
            intArrayOf(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            )
    }
}
