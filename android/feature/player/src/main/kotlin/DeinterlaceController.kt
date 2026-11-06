package player

import data.DEINTERLACE_ON
import data.DEINTERLACE_PREFERENCE
import data.PlayerPreferences
import data.orDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Whether this viewer switched deinterlacing on for the open show — split out
 * of [PlayerChoicesController] the way [FramingController] is: one stored
 * value, remembered per show under the same preferences scope. Off is no
 * stored row at all, which is also what the web player writes.
 */
class DeinterlaceController(
    private val launchScope: CoroutineScope,
    private val preferences: PlayerPreferences,
    private val onChanged: (Boolean) -> Unit,
) {
    private var scope: String? = null
    private var profileId: String? = null
    private var on = false
    private var userChose = false

    /** Drops whatever the last title had, for a genuinely new one. */
    fun reset() {
        scope = null
        profileId = null
        on = false
        userChose = false
        onChanged(on)
    }

    fun onPreferencesLoaded(scope: String, profileId: String?, stored: String?) {
        this.scope = scope
        this.profileId = profileId
        if (userChose) {
            remember(on)
        } else {
            on = stored == DEINTERLACE_ON
        }
        onChanged(on)
    }

    /** The viewer flipped the Framing menu's Deinterlace row. */
    fun choose(next: Boolean) {
        userChose = true
        on = next
        remember(on)
        onChanged(on)
    }

    /** No-ops until [onPreferencesLoaded] has named a scope — retried from there once it has. */
    private fun remember(value: Boolean) {
        val scope = scope ?: return
        val profileId = profileId ?: return
        launchScope.launch {
            orDefault(Unit) { preferences.remember(profileId, scope, DEINTERLACE_PREFERENCE, DEINTERLACE_ON.takeIf { value }) }
        }
    }
}
