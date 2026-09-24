package player

import data.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import playback.DEFAULT_CUE_BACKING
import playback.DEFAULT_CUE_SIZE_PERCENT
import playback.cueBackingOrDefault
import playback.cueOffsetSecondsOrDefault
import playback.cueSizePercentOrDefault
import playback.nudgeCueOffsetSeconds

/**
 * A subtitle's size, backing and timing offset — [SubtitleChoiceController]'s
 * sibling, split out because neither a size/backing pick nor a timing nudge
 * ever starts a load job the way a language pick does; each here only races
 * its own single, later preference read, the same shape
 * [PlayerChoicesController.setSpeed] guards for speed.
 *
 * Publishes the raw, stored-shaped values rather than the `CueAppearance`
 * they render to — see [PlayerChoices] for why; `SubtitleLayer` computes its
 * own appearance from these where it draws.
 */
class SubtitleStyleController(
    private val launchScope: CoroutineScope,
    private val preferences: PlayerPreferences,
    private val onChanged: (sizePercent: Int, backing: String, offsetMs: Long) -> Unit,
) {
    private var scope: String? = null
    private var profileId: String? = null

    private var sizePercent: Int = DEFAULT_CUE_SIZE_PERCENT
    private var backing: String = DEFAULT_CUE_BACKING
    // Two flags, not one: a size picked before preferences load must not
    // write the default backing over a remembered one, and the web saves
    // only the value that changed.
    private var userChoseSize = false
    private var userChoseBacking = false

    private var offsetSeconds: Double = 0.0
    private var userChoseOffset = false

    /** Drops whatever the last title had, for a genuinely new one. */
    fun reset() {
        scope = null
        profileId = null
        sizePercent = DEFAULT_CUE_SIZE_PERCENT
        backing = DEFAULT_CUE_BACKING
        userChoseSize = false
        userChoseBacking = false
        offsetSeconds = 0.0
        userChoseOffset = false
        publish()
    }

    fun onPreferencesLoaded(scope: String, profileId: String?, loaded: Map<String, String>) {
        this.scope = scope
        this.profileId = profileId
        if (userChoseSize) rememberSize() else sizePercent = cueSizePercentOrDefault(loaded["cue-size"])
        if (userChoseBacking) rememberBacking() else backing = cueBackingOrDefault(loaded["cue-backing"])
        if (userChoseOffset) {
            rememberOffset()
        } else {
            offsetSeconds = cueOffsetSecondsOrDefault(loaded["cue-offset"])
        }
        publish()
    }

    fun setSize(percent: Int) {
        userChoseSize = true
        sizePercent = percent
        rememberSize()
        publish()
    }

    fun setBacking(stored: String) {
        userChoseBacking = true
        backing = stored
        rememberBacking()
        publish()
    }

    fun nudgeOffset(steps: Int) {
        userChoseOffset = true
        offsetSeconds = nudgeCueOffsetSeconds(offsetSeconds, steps)
        rememberOffset()
        publish()
    }

    fun resetOffset() {
        userChoseOffset = true
        offsetSeconds = 0.0
        rememberOffset()
        publish()
    }

    // Rounded rather than truncated: 0.3 seconds is 299.9999999999998 in a
    // double, and truncating that reads back as 299ms — visibly the wrong
    // number the moment `cueOffsetLabel` divides it back by 1000.
    private fun publish() = onChanged(sizePercent, backing, Math.round(offsetSeconds * 1000))

    private fun rememberSize() = remember("cue-size", sizePercent.toString())

    private fun rememberBacking() = remember("cue-backing", backing)

    private fun rememberOffset() = remember("cue-offset", offsetSeconds.toString())

    /** Saves one value for this show; a no-op until [onPreferencesLoaded] has named the scope and profile. */
    private fun remember(key: String, value: String) {
        val scope = scope ?: return
        val profileId = profileId ?: return
        launchScope.launch { safely(Unit) { preferences.remember(profileId, scope, key, value) } }
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
