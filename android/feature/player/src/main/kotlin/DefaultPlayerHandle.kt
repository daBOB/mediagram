package player

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 *
 * Subscribers therefore come and go for reasons unrelated to playback, and
 * ask to [open] sets that are already open; [open] works out what that means.
 */
class DefaultPlayerHandle @Inject constructor(
    private val playerDeferred: @JvmSuppressWildcards Deferred<ExoPlayer>,
    private val scope: CoroutineScope,
) : PlayerHandle {

    private val _player = MutableStateFlow<Player?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()

    private var listener: PlayerHandle.Listener? = null

    /** A set requested through [open] before the player finished building. */
    private var pendingOpen: PendingOpen? = null

    private data class PendingOpen(val setId: String, val startAtMs: Long, val playWhenReady: Boolean)

    /** Whichever set was last handed to the player; [stop] clears it. */
    private var currentSetId: String? = null

    /** Set once the player has failed to build; see [failConstruction]. */
    private var constructionError: String? = null

    /** A speed requested through [setPlaybackSpeed] before the player finished building. */
    private var pendingSpeed: Float? = null
    private val pendingMetadata = PendingMetadata()

    private val playerListener = PlayerHandleListener(
        isCurrentlyPlaying = { _player.value?.isPlaying ?: false },
        notifyPlaying = ::notifyPlaying,
        notifyError = { message -> listener?.onError(message) },
        notifyEnded = { listener?.onEnded() },
        notifySeeked = { listener?.onSeeked() },
    )

    init {
        scope.launch {
            try {
                val built = playerDeferred.await()
                built.addListener(playerListener)
                _player.value = built
                pendingOpen?.let { pending ->
                    pendingOpen = null
                    openOn(built, pending.setId, pending.startAtMs, pending.playWhenReady)
                }
                // After the pending open, whose own reset to 1x this overrides.
                pendingSpeed?.let { rate ->
                    pendingSpeed = null
                    built.setPlaybackSpeed(rate)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Corrupt cache index, no disk space for it, and similar
                // construction failures land here rather than in
                // Player.Listener.onPlayerError — there is no player yet
                // to have raised that. Left uncaught, this reaches the
                // process's default handler, which on a device is a crash.
                failConstruction(e.message ?: "Could not prepare the player")
            }
        }
    }

    /**
     * Asking for a set that is already on screen is routine, not a
     * mistake: a rotation recreates the Composition that asks. What must
     * not happen is either half going wrong — reloading media that is
     * playing (the single-item `setMediaItem` overload always resets to
     * position zero), or falling silent about media that is not.
     *
     * `STATE_IDLE` is the player's own answer to which case this is: it is
     * the state a player is in before its first `prepare`, after `stop`,
     * and after a playback error, and `ExoPlayer.prepare()` itself does
     * nothing unless the player is in it. So a set that is still loaded is
     * left strictly alone, and anything else — a different set, an errored
     * player, a stopped one — is loaded for real.
     */
    override fun open(setId: String, startAtMs: Long, playWhenReady: Boolean) {
        constructionError?.let { message ->
            // There will never be a player to open this on, and the caller
            // has just reset itself to "preparing" expecting one. Nothing
            // else would ever speak up, so repeat the failure.
            listener?.onError(message)
            return
        }
        val current = _player.value
        if (current == null) {
            pendingOpen = PendingOpen(setId, startAtMs, playWhenReady)
            return
        }
        if (setId == currentSetId && current.playbackState != Player.STATE_IDLE) {
            republishPlaybackState(current)
            return
        }
        openOn(current, setId, startAtMs, playWhenReady)
    }

    override fun setListener(listener: PlayerHandle.Listener?) {
        this.listener = listener
        // A failure the previous subscriber was told about, or that landed
        // while there was none, is still true for this one.
        constructionError?.let { listener?.onError(it) }
    }

    override fun stop() {
        // Clearing these matters even when the player isn't built yet: a
        // set requested just before the screen was left must not start
        // playing the moment construction finishes on a screen the user
        // has already backed out of.
        pendingOpen = null
        currentSetId = null
        _player.value?.stop()
    }

    override fun release() {
        listener = null
    }

    override fun positionMs(): Long? = _player.value?.trustedPositionMs()

    override fun durationMs(): Long? = _player.value?.trustedDurationMs()

    override fun bufferedPositionMs(): Long? = _player.value?.trustedBufferedPositionMs()

    override fun play() {
        _player.value?.play()
    }

    override fun pause() {
        _player.value?.pause()
    }

    override fun isLoading(): Boolean = _player.value?.isLoading ?: false

    private fun republishPlaybackState(player: Player) = player.republishTo(::notifyPlaying)

    /**
     * A construction failure is permanent — the deferred that failed is the
     * only one, and nothing retries it — and whether a subscriber exists to
     * hear it depends on nothing more than how long the cache took to open.
     * Holding it takes the delivery out of that race: whoever subscribes
     * next, or asks to open a set next, is told.
     */
    private fun failConstruction(message: String) {
        constructionError = message
        pendingOpen = null
        listener?.onError(message)
    }

    override fun setPlaybackSpeed(rate: Float) {
        _player.value?.setPlaybackSpeed(rate) ?: run { pendingSpeed = rate }
    }

    override fun setMetadata(metadata: MediaMetadata) {
        pendingMetadata.remember(metadata)
        _player.value?.setMetadataReal(metadata)
    }

    private fun openOn(player: Player, setId: String, startAtMs: Long, playWhenReady: Boolean) {
        currentSetId = setId
        player.openReal(setId, startAtMs, playWhenReady)
        pendingMetadata.reapply(player)
    }

    private fun notifyPlaying(isPlaying: Boolean) {
        listener?.onPlayingChanged(isPlaying)
    }
}
