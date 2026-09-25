package player

import androidx.media3.common.Player
import androidx.media3.common.Tracks
import data.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import playback.AudioOption
import playback.AudioTrackFacts
import playback.audioOptions
import playback.audioOptionsSelecting
import playback.audioTrackForLanguage
import playback.isUsableAudioLanguage

/**
 * Which audio track this viewer is on for the open title, and the menu the
 * file offers to switch to — split out of [PlayerChoicesController]
 * because this choice races a source nothing else there depends on: the
 * file's own tracks, reported by ExoPlayer only after `prepare()`, never
 * at open. The remembered language (a core round trip) and the tracks can
 * each arrive first; a manual pick always wins over either, and with no
 * remembered language (or one this file has lost) ExoPlayer's own
 * selection is left exactly as it is — see `playback.audioOptions`.
 *
 * Attaches its own [Player.Listener] directly to whatever [PlayerHandle]
 * exposes, rather than routing through `DefaultPlayerHandle`'s permanent
 * one — that is process-lifetime and would need a callback threaded
 * through it for the one controller that uses it. [release] detaches this
 * one, since it lives and dies with a single [PlayerViewModel].
 */
class AudioChoiceController(
    private val launchScope: CoroutineScope,
    private val handle: PlayerHandle,
    private val preferences: PlayerPreferences,
    private val onOptionsChanged: (List<AudioOption>) -> Unit,
) {
    private val trackListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) = handleTracksChanged(tracks)
    }

    /** The player [trackListener] is attached to — the singleton is built once, so this is set at most once. */
    private var attachedTo: Player? = null

    /** This open's audio tracks, once ExoPlayer has reported them for it; null before then, or empty for a file with none. */
    private var facts: List<AudioTrackFacts>? = null

    /** The exact `Tracks` [facts] was read from — an override is built from the `TrackGroup` inside *this* snapshot, never by re-deriving one from `player.currentTracks`, which may already answer for a different open by the time a pick applies. */
    private var lastTracks: Tracks? = null

    private var scope: String? = null
    private var profileId: String? = null

    /** The language stored for [scope], once [onPreferencesLoaded] has read it. */
    private var rememberedLanguage: String? = null
    private var preferencesLoaded = false

    /** Set by [choose]; a manual pick always wins over whatever the remembered language would otherwise apply. */
    private var userChose = false

    /** The language [choose] picked — held so [onPreferencesLoaded] can persist it once there is a scope, for a pick made before one existed. */
    private var userChosenLanguage: String? = null

    /**
     * True once the preference has been folded in. Until then,
     * [applyIfReady] still publishes a menu the moment the tracks arrive,
     * reflecting whatever ExoPlayer already selected, and corrects to the
     * remembered language once that is read. Once true, a repeat
     * `onTracksChanged` (a retry re-preparing the same file) no-ops rather
     * than reapplying an override nothing changed.
     */
    private var settled = false

    init {
        launchScope.launch {
            handle.player.collect { player ->
                if (player != null && player !== attachedTo) {
                    player.addListener(trackListener)
                    attachedTo = player
                }
            }
        }
    }

    /** Drops whatever the last title had, and clears any override left on the player — the singleton never resets this itself. */
    fun reset() {
        facts = null
        lastTracks = null
        scope = null
        profileId = null
        rememberedLanguage = null
        preferencesLoaded = false
        userChose = false
        userChosenLanguage = null
        settled = false
        handle.player.value?.let(::clearAudioOverride)
        onOptionsChanged(emptyList())
    }

    /**
     * Called once [PlayerChoicesController.resolve] knows the scope, with
     * whatever it loaded under `"audio"` — `null` for nothing remembered.
     * A pick already made by hand wins: this only re-tries persisting it
     * now that there is a scope to write it under, the same as
     * [PlayerChoicesController] does for a speed chosen before its own
     * resolution finished.
     */
    fun onPreferencesLoaded(scope: String, profileId: String?, storedLanguage: String?) {
        this.scope = scope
        this.profileId = profileId
        if (userChose) {
            rememberLanguage(userChosenLanguage)
            return
        }
        rememberedLanguage = storedLanguage
        preferencesLoaded = true
        applyIfReady()
    }

    /** The viewer picked a row by hand. Left exactly as it was if the pick can no longer be applied, rather than claiming a choice this controller cannot honour. */
    fun choose(option: AudioOption) {
        if (!pinAudioTrack(handle.player.value, lastTracks, option.groupIndex, option.trackIndex)) return
        userChose = true
        userChosenLanguage = option.language
        onOptionsChanged(audioOptionsSelecting(facts.orEmpty(), option.groupIndex, option.trackIndex))
        if (isUsableAudioLanguage(option.language)) rememberLanguage(option.language)
    }

    /** No-ops until [scope] is known — [onPreferencesLoaded] retries once it is. */
    private fun rememberLanguage(language: String?) {
        val scope = scope ?: return
        val profileId = profileId ?: return
        launchScope.launch { safely(Unit) { preferences.remember(profileId, scope, "audio", language) } }
    }

    /** Detaches [trackListener] — called once this controller's own [PlayerViewModel] is cleared. */
    fun release() {
        attachedTo?.removeListener(trackListener)
        attachedTo = null
    }

    private fun handleTracksChanged(tracks: Tracks) {
        // ExoPlayerImpl fires a synthetic `Tracks.EMPTY` clearing this the
        // moment `setMediaItem`/`prepare()` reset it for a new item,
        // strictly before the real probe of that item answers. Settling on
        // that as this open's (track-less) answer would permanently ignore
        // the real tracks the moment they followed.
        if (tracks.groups.isEmpty()) return
        lastTracks = tracks
        facts = extractAudioFacts(tracks)
        applyIfReady()
    }

    /**
     * Applies the remembered track the moment there is enough to know it —
     * corrected if the tracks and the preference arrive in the other
     * order — but never pins anything when nothing is remembered (or this
     * file has lost the language that was): ExoPlayer's own selection
     * already accounts for what the device can actually decode, and this
     * app has no better guess to offer instead. A no-op once [settled]
     * (nothing left to correct) or after [choose] (nothing here should
     * ever win against a manual pick).
     */
    private fun applyIfReady() {
        if (userChose || settled) return
        val tracks = facts ?: return
        if (tracks.size <= 1) {
            settled = true
            onOptionsChanged(emptyList())
            return
        }
        // Only trusted once the load has actually run — a `null` from
        // "not yet known" and a `null` from "nothing remembered" must not
        // be told apart the wrong way, or this would settle before the
        // real preference had a chance to load.
        val remembered = rememberedLanguage.takeIf { preferencesLoaded }
        audioTrackForLanguage(tracks, remembered)?.let { chosen ->
            pinAudioTrack(handle.player.value, lastTracks, tracks[chosen].groupIndex, tracks[chosen].trackIndex)
        }
        onOptionsChanged(audioOptions(tracks, remembered))
        if (preferencesLoaded) settled = true
    }

    /** Runs [block], answering [default] for anything but cancellation — which is rethrown, so a cancelled coroutine stays cancelled. */
    private suspend fun <T> safely(default: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        default
    }
}
