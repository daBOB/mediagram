package player

import data.CatalogRepository
import data.PlayerPreferences
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.MediaSet
import playback.AudioOption
import playback.Framing
import playback.SubtitleTrackSource
import playback.TimedCue

/**
 * What this viewer has chosen for the open title, and how a choice made
 * for one show is kept from leaking into the next — split out of
 * [PlayerViewModel] to keep that file under the project's line guideline.
 * Speed is resolved here directly; the audio menu's own races (it depends
 * on the file's tracks as well as a remembered preference) are guarded by
 * [audioChoice] instead, which this controller only feeds the scope and
 * the stored value it loads for it.
 */
class PlayerChoicesController(
    private val launchScope: CoroutineScope,
    private val session: PlayerSession,
    private val repository: WatchStateRepository,
    private val catalogRepository: CatalogRepository,
    private val preferences: PlayerPreferences,
    private val handle: PlayerHandle,
    private val subtitleTrackSource: SubtitleTrackSource,
) {
    private val _openSet = MutableStateFlow<MediaSet?>(null)
    val openSet: StateFlow<MediaSet?> = _openSet.asStateFlow()

    private val _choices = MutableStateFlow(PlayerChoices.Default)
    val choices: StateFlow<PlayerChoices> = _choices.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<TimedCue>>(emptyList())

    /** The open title's subtitle cues, once its chosen language's VTT has been fetched and parsed — kept apart from [choices], which a feature film's whole transcript has no business being copied onto with every speed or size change. */
    val subtitleCues: StateFlow<List<TimedCue>> = _subtitleCues.asStateFlow()

    private val audioChoice = AudioChoiceController(launchScope, handle, preferences) { options ->
        _choices.value = _choices.value.copy(audioOptions = options)
    }

    private val subtitleChoice = SubtitleChoiceController(launchScope, subtitleTrackSource, preferences) { options, cues ->
        _choices.value = _choices.value.copy(subtitleOptions = options)
        _subtitleCues.value = cues
    }

    private val subtitleStyle = SubtitleStyleController(launchScope, preferences) { sizePercent, backing, offsetMs ->
        _choices.value = _choices.value.copy(
            subtitleSizePercent = sizePercent,
            subtitleBacking = backing,
            subtitleOffsetMs = offsetMs,
        )
    }

    private val framingChoice = FramingController(launchScope, preferences) { framing ->
        _choices.value = _choices.value.copy(framing = framing)
    }

    /** Where [setSpeed] remembers a choice; null before the open title's set resolves. */
    private var openScope: String? = null

    /**
     * Set by [setSpeed] for the title now open, so a still in-flight
     * [resolve] does not clobber a choice made while it was working.
     * Cleared by [reset] — a genuinely different title opening, never a
     * rotation reopening the same one; see [PlayerViewModel.open].
     */
    private var userChoseSpeed = false

    /** Drops whatever the last title had, for a genuinely new one. */
    fun reset() {
        openScope = null
        _openSet.value = null
        _choices.value = PlayerChoices.Default
        userChoseSpeed = false
        audioChoice.reset()
        subtitleChoice.reset()
        subtitleStyle.reset()
        framingChoice.reset()
    }

    /** Detaches the audio listener this controller's [audioChoice] holds on the player. */
    fun release() = audioChoice.release()

    /**
     * Resolves the set behind [setId], its preference scope, and the
     * speed and audio choice remembered under it, applying each to
     * [handle]. Falls back to a raw `set:<id>` scope when the set itself
     * cannot be resolved — a cold start straight into the player with the
     * catalog not yet loaded.
     *
     * Every read here is checked against [PlayerSession.openSetId] before
     * it is acted on: a later [PlayerViewModel.open] racing ahead of this
     * must win, never be overwritten by a resolution landing after it —
     * and [userChoseSpeed] (audio's own equivalent lives in [audioChoice])
     * guards the same race for a choice made by hand: whichever speed the
     * viewer picked while this was still working wins, and is remembered
     * under the scope this resolves rather than lost or overwritten by it.
     *
     * Speed and audio are applied independently rather than behind one
     * shared early return: a speed picked by hand while this was in
     * flight must not also suppress loading the audio preference, which
     * has nothing to do with it.
     */
    suspend fun resolve(setId: String) {
        val set = safely(null) { catalogRepository.mediaSet(setId) }
        if (session.openSetId != setId) return
        _openSet.value = set
        val scope = scopeOf(set) ?: "set:$setId"
        openScope = scope
        // Ahead of the preference round trip below: a title's own languages
        // are a fact about the set, not about this profile's choices for it.
        subtitleChoice.onLanguagesKnown(setId, set?.subtitleLanguages.orEmpty())

        val profileId = repository.chosenProfileId.value
        val loaded = if (profileId == null) emptyMap() else safely(emptyMap()) { preferences.load(profileId, scope) }
        if (session.openSetId != setId) return

        if (userChoseSpeed) {
            rememberSpeed(scope, _choices.value.speed)
        } else {
            val speed = speedOrDefault(loaded["speed"])
            _choices.value = _choices.value.copy(speed = speed)
            handle.setPlaybackSpeed(speed)
        }

        audioChoice.onPreferencesLoaded(scope, profileId, loaded["audio"])
        subtitleChoice.onPreferencesLoaded(scope, profileId, loaded["subtitle"])
        subtitleStyle.onPreferencesLoaded(scope, profileId, loaded)
        framingChoice.onPreferencesLoaded(scope, profileId, loaded["framing"])
    }

    /** Applies a chosen speed and remembers it for this show; a no-op write with no profile chosen. */
    fun setSpeed(rate: Float) {
        userChoseSpeed = true
        _choices.value = _choices.value.copy(speed = rate)
        handle.setPlaybackSpeed(rate)
        // Remembered once [resolve] has a scope, if it hasn't yet.
        val scope = openScope ?: return
        rememberSpeed(scope, rate)
    }

    /** The viewer picked an audio track by hand. */
    fun chooseAudioTrack(option: AudioOption) = audioChoice.choose(option)

    /** The viewer picked a subtitle row by hand — "off" or one of the offered languages. */
    fun chooseSubtitleLanguage(languageOrOff: String) = subtitleChoice.choose(languageOrOff)

    fun setSubtitleSize(percent: Int) = subtitleStyle.setSize(percent)
    fun setSubtitleBacking(stored: String) = subtitleStyle.setBacking(stored)
    fun nudgeSubtitleOffset(steps: Int) = subtitleStyle.nudgeOffset(steps)
    fun resetSubtitleOffset() = subtitleStyle.resetOffset()

    /** The viewer picked a framing by hand — the sheet's own row, or a pinch. */
    fun chooseFraming(next: Framing) = framingChoice.choose(next)

    /** Fire-and-forget: a core round trip failing to remember a speed is not a reason to crash the player. */
    private fun rememberSpeed(scope: String, rate: Float) {
        val profileId = repository.chosenProfileId.value ?: return
        launchScope.launch {
            safely(Unit) { preferences.remember(profileId, scope, "speed", speedPreferenceValue(rate)) }
        }
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
