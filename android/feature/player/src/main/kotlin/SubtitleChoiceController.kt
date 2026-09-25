package player

import data.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import playback.SubtitleTrackSource
import playback.TimedCue

/**
 * Which subtitle language is on for the open title, and its cues once
 * fetched — split out of [PlayerChoicesController] the same way
 * [AudioChoiceController] is, because a language pick starts its own async
 * work the rest of that controller never needs: fetching and parsing a
 * file's VTT. [SubtitleStyleController] is this choice's sibling for
 * size/backing/offset, kept apart because those never touch a load job at
 * all.
 *
 * Unlike audio, nothing here ever touches [PlayerHandle] or a real
 * `Player` — a title's languages come straight off its [model.MediaSet],
 * and the cues this hands `SubtitleLayer` are drawn in Compose rather than
 * through ExoPlayer's own (disabled) text renderer, so there is no
 * `Tracks` event of any kind for this controller to race or be confused by.
 *
 * The language pick has two sources that can answer in either order —
 * [onLanguagesKnown], from the set resolving, and [onPreferencesLoaded],
 * from the core round trip that follows it — so it carries a [settled]
 * guard, the same shape [AudioChoiceController.applyIfReady] uses for
 * tracks-vs-preference. Nothing is chosen until both have answered: a
 * provisional default would fetch a file the remembered choice may turn off
 * again, and could flash its cues on a show the viewer switched off.
 */
class SubtitleChoiceController(
    private val launchScope: CoroutineScope,
    private val trackSource: SubtitleTrackSource,
    private val preferences: PlayerPreferences,
    private val onChanged: (options: List<SubtitleOption>, cues: List<TimedCue>) -> Unit,
) {
    private var setId: String? = null
    private var scope: String? = null
    private var profileId: String? = null

    private var languages: List<String> = emptyList()
    private var languagesKnown = false

    private var language: String = SUBTITLES_OFF
    private var rememberedLanguage: String? = null
    private var preferencesLoaded = false
    private var userChose = false
    private var settled = false

    private var cues: List<TimedCue> = emptyList()

    /** The fetch this open's chosen language started; cancelled the moment another is chosen, so a stale one can never land after it. */
    private var loadJob: Job? = null

    /** Drops whatever the last title had, for a genuinely new one. */
    fun reset() {
        loadJob?.cancel()
        loadJob = null
        setId = null; scope = null; profileId = null
        languages = emptyList(); languagesKnown = false
        language = SUBTITLES_OFF; rememberedLanguage = null; preferencesLoaded = false
        userChose = false; settled = false
        cues = emptyList()
        publish()
    }

    /** [PlayerChoicesController.resolve] calls this once the set itself resolves — before its own preference round trip, so the section can show its rows while [onPreferencesLoaded] is still deciding which one is on. */
    fun onLanguagesKnown(setId: String, languages: List<String>) {
        this.setId = setId
        this.languages = languages
        languagesKnown = true
        publish()
        settleIfReady()
    }

    /** Called once the scope and this profile's remembered `"subtitle"` value are known — `null` for nothing remembered. */
    fun onPreferencesLoaded(scope: String, profileId: String?, remembered: String?) {
        this.scope = scope
        this.profileId = profileId
        if (userChose) {
            rememberLanguage(language)
            return
        }
        rememberedLanguage = remembered
        preferencesLoaded = true
        settleIfReady()
    }

    /** Applies the default rule once both [languagesKnown] and [preferencesLoaded] have answered, in whichever order — a no-op once [userChose] or [settled]. */
    private fun settleIfReady() {
        if (userChose || settled || !languagesKnown || !preferencesLoaded) return
        settled = true
        applyLanguage(chooseSubtitleLanguage(languages, rememberedLanguage), remember = false)
    }

    /** The viewer picked a row by hand. Wins over whatever [onPreferencesLoaded] answers later, or already has. */
    fun choose(languageOrOff: String) {
        userChose = true
        applyLanguage(languageOrOff, remember = true)
    }

    private fun applyLanguage(languageOrOff: String, remember: Boolean) {
        val picked = languages.firstOrNull { it == languageOrOff } ?: SUBTITLES_OFF
        if (remember) rememberLanguage(picked)
        // Re-picking the row already on keeps its cues, or the fetch already bringing them.
        if (picked == language && (cues.isNotEmpty() || loadJob?.isActive == true)) return
        language = picked
        loadJob?.cancel()
        if (language == SUBTITLES_OFF) {
            loadJob = null
            cues = emptyList()
            publish()
            return
        }
        publish() // the picked row shows selected at once; its cues follow once fetched
        val openSetId = setId ?: return
        val forLanguage = language
        loadJob = launchScope.launch {
            val loaded = safely(emptyList()) { trackSource.load(openSetId, forLanguage) }
            if (language == forLanguage) {
                cues = loaded
                publish()
            }
        }
    }

    private fun publish() = onChanged(subtitleOptions(languages, language), cues)

    private fun rememberLanguage(value: String) {
        val scope = scope ?: return
        val profileId = profileId ?: return
        launchScope.launch { safely(Unit) { preferences.remember(profileId, scope, "subtitle", value) } }
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
