package player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import playback.setUri
import javax.inject.Inject

/**
 * Wraps the app's single [ExoPlayer], translating its events into
 * [PlayerHandle.Listener] calls. [playerDeferred] is awaited rather than
 * built here directly: building it means constructing the disk cache,
 * real disk/database I/O, and this handle must never block whichever
 * thread constructs it — see `PlaybackModule`.
 *
 * This handle is itself process-lifetime (a `@Singleton`, same as the
 * player it wraps), so [playerListener] is attached exactly once, the
 * moment the player becomes available, and never removed: whichever
 * [PlayerViewModel] is current is only ever the *subscriber* ([listener]),
 * swapped in and out by [setListener]/[release]. Removing [playerListener]
 * from the player itself on [release] would permanently silence every
 * future subscriber, since this setup never runs a second time to
 * re-attach it.
 */
class DefaultPlayerHandle @Inject constructor(
    private val playerDeferred: @JvmSuppressWildcards Deferred<ExoPlayer>,
    private val scope: CoroutineScope,
) : PlayerHandle {

    private val _player = MutableStateFlow<Player?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()

    private var listener: PlayerHandle.Listener? = null

    /** A set requested through [open] before the player finished building. */
    private var pendingSetId: String? = null

    /** Whichever set is currently loaded or queued to load; [stop] clears it. */
    private var currentSetId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = notifyPosition(isPlaying)

        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = _player.value ?: return
            if (playbackState == Player.STATE_READY) notifyPosition(current.isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(error.message ?: "Playback failed")
        }
    }

    init {
        scope.launch {
            try {
                val built = playerDeferred.await()
                built.addListener(playerListener)
                _player.value = built
                pendingSetId?.let { setId ->
                    pendingSetId = null
                    openOn(built, setId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Corrupt cache index, no disk space for it, and similar
                // construction failures land here rather than in
                // Player.Listener.onPlayerError — there is no player yet
                // to have raised that. Left uncaught, this reaches the
                // process's default handler, which on a device is a crash.
                listener?.onError(e.message ?: "Could not prepare the player")
            }
        }
    }

    override fun open(setId: String) {
        // Rotation destroys and recreates the whole Composition, so
        // PlayerScreen's LaunchedEffect(setId) fires again with the same
        // id; reopening a set that is already loaded or queued must not
        // disturb playback — the single-item setMediaItem overload always
        // resets to position zero.
        if (setId == currentSetId) return
        currentSetId = setId
        val current = _player.value
        if (current == null) {
            pendingSetId = setId
        } else {
            openOn(current, setId)
        }
    }

    override fun setListener(listener: PlayerHandle.Listener?) {
        this.listener = listener
    }

    override fun stop() {
        // Clearing these matters even when the player isn't built yet: a
        // set requested just before the screen was left must not start
        // playing the moment construction finishes on a screen the user
        // has already backed out of.
        pendingSetId = null
        currentSetId = null
        _player.value?.stop()
    }

    override fun release() {
        listener = null
    }

    private fun openOn(player: Player, setId: String) {
        player.setMediaItem(MediaItem.fromUri(setUri(setId)))
        player.prepare()
        player.playWhenReady = true
    }

    private fun notifyPosition(isPlaying: Boolean) {
        val current = _player.value ?: return
        listener?.onPositionChanged(
            positionMs = current.currentPosition,
            durationMs = current.duration.coerceAtLeast(0),
            isPlaying = isPlaying,
        )
    }
}
