package playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The title currently open in the player, and its whole size — [FilmPreloader]'s only view of playback. */
data class OpenTitle(val setId: String, val totalBytes: Long)

/**
 * What [FilmPreloader] needs from the player without depending on it
 * directly — `:core:playback` cannot see `:feature:player`'s `ExoPlayer`
 * wiring, so `ActivePlayback` (feature/player) implements this and is
 * handed in as the interface.
 *
 * [openTitle] is `null` whenever nothing is open — playing, buffering, or
 * merely paused by the viewer all count as open; only closing the player
 * (`stop()`) or never having opened anything answers `null`. Written only
 * on the player's own thread (main); a [StateFlow] snapshot any thread may
 * read, which is how the preloader's worker reads it without touching
 * `ExoPlayer` — see `ActivePlayback`'s own doc for why that would crash.
 */
interface OpenTitleSource {
    val openTitle: StateFlow<OpenTitle?>

    /**
     * Starts listening to the real player, if it has not already, and
     * suspends until the listener is attached *and* [openTitle] reflects
     * whatever is open right now — not just fire-and-forget: a caller
     * that read [openTitle] before this settled would see `null` even
     * with a title genuinely open, on the very first preload of a
     * process. Every call after the first returns as soon as that one
     * finished, which is instant once it has.
     *
     * Not done at construction: `ActivePlayback` is reachable from
     * `FilmPreloading`, which the catalogue now injects, and the
     * catalogue opening must never be what builds the app's `ExoPlayer`
     * (renderers, a playback thread) — only a film actually being
     * preloaded should.
     */
    suspend fun ensureListening()

    companion object {
        /** Nothing is ever open — a constructor default for a test that does not care. */
        val Noop: OpenTitleSource = object : OpenTitleSource {
            override val openTitle: StateFlow<OpenTitle?> = MutableStateFlow(null)
            override suspend fun ensureListening() = Unit
        }
    }
}
