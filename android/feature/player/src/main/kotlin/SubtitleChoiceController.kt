package player

import data.PlayerPreferences
import data.SUBTITLE_PREFERENCE
import data.orDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import model.SubtitleTrackInfo
import playback.SubtitleTrackSource
import playback.TimedCue

/**
 * Which subtitle track is on for the open title — regular and forced both —
 * and its cues once fetched, by the subtitle rule every surface shares
 * ([chooseSubtitles], [toggleOn]); see [SubtitleChoice] for the pure rule
 * itself.
 *
 * The audio language the rule needs is not this controller's own: it
 * follows [AudioChoiceController]'s own language callback through
 * [onAudioLanguageChanged], falling back to the set's own `alang` until a
 * real playing track answers — see [audioLanguage].
 *
 * Nothing here ever touches [PlayerHandle] or a real `Player` directly: a
 * title's tracks come straight off its [model.MediaSet], and the cues this
 * hands `SubtitleLayer` are drawn in Compose rather than through
 * ExoPlayer's own (disabled) text renderer.
 *
 * The pick has two sources that can answer in either order —
 * [onTracksKnown], from the set resolving, and [onPreferencesLoaded], from
 * the core round trip that follows it — so it carries a [settled] guard,
 * the same shape [AudioChoiceController.applyIfReady] uses. Nothing is
 * chosen until both have answered.
 */
class SubtitleChoiceController(
    private val launchScope: CoroutineScope,
    private val trackSource: SubtitleTrackSource,
    private val preferences: PlayerPreferences,
    private val onChanged: (options: List<SubtitleOption>, styleVisible: Boolean, cues: List<TimedCue>) -> Unit,
) {
    private var setId: String? = null
    private var scope: String? = null
    private var profileId: String? = null

    private var tracks: List<SubtitleTrackInfo> = emptyList()
    private var alang: List<String> = emptyList()
    private var tracksKnown = false

    private var audioTag: String? = null
    private var audio: String? = null

    private var remembered: String? = null
    private var preferred: String? = null
    private var preferencesLoaded = false

    /** The regular track key currently shown, or `null` for "off". */
    private var regularKey: String? = null

    /** The track [activeTrack] was last loaded for — cues follow whichever of {regular, forced} is showing. */
    private var activeTrack: SubtitleTrackInfo? = null
    private var cues: List<TimedCue> = emptyList()
    private var loadJob: Job? = null

    /** The last regular track key chosen this session, in memory only — [toggleOn]'s own first candidate. */
    private var last: String? = null

    private var userChose = false
    private var settled = false

    /** Drops whatever the last title had, for a genuinely new one. */
    fun reset() {
        loadJob?.cancel()
        loadJob = null
        setId = null; scope = null; profileId = null
        tracks = emptyList(); alang = emptyList(); tracksKnown = false
        audioTag = null; audio = null
        remembered = null; preferred = null; preferencesLoaded = false
        regularKey = null; activeTrack = null; cues = emptyList(); last = null
        userChose = false; settled = false
        publish()
    }

    /** [PlayerChoicesController.resolve] calls this once the set itself resolves — before its own preference round trip. */
    fun onTracksKnown(setId: String, tracks: List<SubtitleTrackInfo>, alang: List<String>) {
        this.setId = setId
        this.tracks = tracks
        this.alang = alang
        tracksKnown = true
        audio = audioLanguage(audioTag, alang)
        publish()
        settleIfReady()
    }

    /** [AudioChoiceController.onLanguageChanged]'s own answer — may arrive before or after [onTracksKnown]. */
    fun onAudioLanguageChanged(playingTag: String?) {
        audioTag = playingTag
        audio = audioLanguage(audioTag, alang)
        loadActiveTrack() // only forced can change from this alone; regular never depends on audio
    }

    /** Called once the scope and this profile's remembered/preferred values are known — `null` for nothing stored. */
    fun onPreferencesLoaded(scope: String, profileId: String?, remembered: String?, preferred: String?) {
        this.scope = scope
        this.profileId = profileId
        if (userChose) {
            rememberChoice(regularKey ?: SUBTITLES_OFF)
            return
        }
        this.remembered = remembered
        this.preferred = preferred
        preferencesLoaded = true
        settleIfReady()
    }

    private fun settleIfReady() {
        if (userChose || settled || !tracksKnown || !preferencesLoaded) return
        settled = true
        applySelection(chooseSubtitles(remembered, preferred, audio, tracks).regular, remember = false)
    }

    /** The viewer picked a row by hand — a track key, or [SUBTITLES_OFF]. */
    fun choose(trackKeyOrOff: String) {
        userChose = true
        applySelection(trackKeyOrOff.takeIf { it != SUBTITLES_OFF }, remember = true)
    }

    /** The captions key or CC control: on turns [toggleOn] on, on turns off. */
    fun toggle() {
        // Nothing regular to turn on — a forced-only or subtitle-less title,
        // or one still loading: pressing must not file an "off" for the show.
        if (!settled || tracks.none { !it.forced }) return
        userChose = true
        val next = if (regularKey != null) null else toggleOn(last, preferred, audio, tracks)
        applySelection(next, remember = true)
    }

    private fun applySelection(picked: String?, remember: Boolean) {
        if (remember) rememberChoice(picked ?: SUBTITLES_OFF)
        if (picked != null) last = picked
        regularKey = picked
        loadActiveTrack()
    }

    /** Whichever of {the shown regular track, a forced one} should be playing now; loads it if it changed. */
    private fun loadActiveTrack() {
        val forcedTrack =
            if (regularKey == null && audio != null) tracks.firstOrNull { it.forced && it.lang == audio } else null
        val wanted = regularKey?.let { key -> tracks.filterNot { it.forced }.firstOrNull { trackKey(it) == key } } ?: forcedTrack
        if (wanted?.track == activeTrack?.track && (cues.isNotEmpty() || loadJob?.isActive == true)) {
            publish()
            return
        }
        activeTrack = wanted
        loadJob?.cancel()
        if (wanted == null) {
            loadJob = null
            cues = emptyList()
            publish()
            return
        }
        publish() // the picker/forced state shows at once; its cues follow once fetched
        val openSetId = setId ?: return
        val forTrack = wanted.track
        loadJob = launchScope.launch {
            val loaded = orDefault(emptyList()) { trackSource.load(openSetId, forTrack) }
            if (activeTrack?.track == forTrack) {
                cues = loaded
                publish()
            }
        }
    }

    private fun publish() {
        val vis = visibility(tracks)
        onChanged(subtitleOptions(tracks, regularKey), vis.styleVisible, cues)
    }

    private fun rememberChoice(value: String) {
        val scope = scope ?: return
        val profileId = profileId ?: return
        launchScope.launch { orDefault(Unit) { preferences.remember(profileId, scope, SUBTITLE_PREFERENCE, value) } }
    }
}
