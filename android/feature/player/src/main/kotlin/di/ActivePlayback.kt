package player.di

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Lazy
import data.CatalogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import player.safely
import playback.OpenTitle
import playback.OpenTitleSource
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Feeds `FilmPreloader` what the app's one [ExoPlayer] has open, without
 * ever letting it touch the player directly. media3 asserts the calling
 * thread on nearly every read ([Player.getCurrentMediaItem],
 * [Player.getPlaybackState]), and a film's preload worker runs on its own
 * background dispatcher, never main — reading the player from there is
 * what used to crash the app on a film's first enqueue. [openTitle] is
 * written only from inside the [Player.Listener] [ensureListening]
 * attaches, which media3 calls back on the same thread the player itself
 * lives on (main, in this app); it is then a plain published snapshot any
 * thread can read safely.
 *
 * [playerDeferred] is [Lazy], not the plain `Deferred<ExoPlayer>` every
 * other consumer of it takes: Hilt resolves a plain `Deferred<ExoPlayer>`
 * dependency the moment anything downstream is injected, which starts
 * building the app's real `ExoPlayer` (renderers, a playback thread)
 * immediately — and `FilmPreloading` now reaches this class from the
 * catalogue, which must never be what does that. Only [ensureListening],
 * called the first time a film is actually being preloaded, should.
 */
class ActivePlayback(
    private val playerDeferred: Lazy<@JvmSuppressWildcards Deferred<ExoPlayer>>,
    private val catalogRepository: CatalogRepository,
    private val scope: CoroutineScope,
) : OpenTitleSource {
    private val _openTitle = MutableStateFlow<OpenTitle?>(null)
    override val openTitle: StateFlow<OpenTitle?> = _openTitle.asStateFlow()

    private val starting = AtomicBoolean(false)

    /** Completed once the listener is attached and the first [refreshNow] (its own catalogue lookup included) has actually settled [openTitle] — what [ensureListening] awaits. */
    private val attached = CompletableDeferred<Unit>()

    /** Guards each [refreshNow]'s own catalogue lookup: a later transition must win over a slower earlier one still resolving, never the reverse. */
    @Volatile
    private var generation = 0

    override suspend fun ensureListening() {
        if (starting.compareAndSet(false, true)) {
            scope.launch {
                val built = playerDeferred.get().await()
                built.addListener(
                    object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh(built)

                        override fun onPlaybackStateChanged(playbackState: Int) = refresh(built)
                    },
                )
                refreshNow(built)
                attached.complete(Unit)
            }
        }
        attached.await()
    }

    /** The fire-and-forget shape [Player.Listener]'s own (non-suspend) callbacks need. */
    private fun refresh(player: Player) {
        scope.launch { refreshNow(player) }
    }

    /**
     * Always entered on main (media3's own listener contract, or
     * [ensureListening]'s own setup coroutine), so reading
     * [Player.getPlaybackState]/[Player.getCurrentMediaItem] here is safe;
     * only the catalogue lookup below needs guarding — a core exception
     * on every playback-state change would otherwise crash the app on a
     * handler-less scope.
     */
    private suspend fun refreshNow(player: Player) {
        generation++
        val myGeneration = generation
        // stop()/never-opened both land here — nothing reserved, nothing paused for.
        if (player.playbackState == Player.STATE_IDLE) {
            _openTitle.value = null
            return
        }
        // Not mediaId: MediaItem.fromUri leaves that at its own default
        // ("") unless .setMediaId is called explicitly, which
        // DefaultPlayerHandleOps.openReal never does. localConfiguration.uri
        // is the URI openReal actually built the item from — setUri's own
        // string form, `mlib://set/<id>` — so its last path segment is the id.
        val setId = player.currentMediaItem?.localConfiguration?.uri?.lastPathSegment
        if (setId == null) {
            _openTitle.value = null
            return
        }
        val totalBytes = safely(0L) { catalogRepository.mediaSet(setId)?.totalBytes ?: 0L }
        if (generation == myGeneration) _openTitle.value = OpenTitle(setId, totalBytes)
    }
}
