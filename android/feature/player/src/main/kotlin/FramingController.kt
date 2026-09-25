package player

import data.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import playback.Framing

/**
 * How this viewer framed the open title — split out of
 * [PlayerChoicesController] the same way [SubtitleStyleController] is: a
 * single stored value with no in-flight track race of its own, remembered
 * per show under the same preferences scope speed and subtitle style are.
 */
class FramingController(
    private val launchScope: CoroutineScope,
    private val preferences: PlayerPreferences,
    private val onChanged: (Framing) -> Unit,
) {
    private var scope: String? = null
    private var profileId: String? = null
    private var framing: Framing = Framing.Default
    private var userChose = false

    /** Drops whatever the last title had, for a genuinely new one. */
    fun reset() {
        scope = null
        profileId = null
        framing = Framing.Default
        userChose = false
        onChanged(framing)
    }

    fun onPreferencesLoaded(scope: String, profileId: String?, stored: String?) {
        this.scope = scope
        this.profileId = profileId
        if (userChose) {
            remember(framing)
        } else {
            framing = Framing.orDefault(stored)
        }
        onChanged(framing)
    }

    /** The viewer picked a framing by hand — the sheet's own row, or a pinch. */
    fun choose(next: Framing) {
        userChose = true
        framing = next
        remember(framing)
        onChanged(framing)
    }

    /** No-ops until [onPreferencesLoaded] has named a scope — retried from there once it has. */
    private fun remember(value: Framing) {
        val scope = scope ?: return
        val profileId = profileId ?: return
        launchScope.launch { safely(Unit) { preferences.remember(profileId, scope, "framing", value.stored) } }
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
