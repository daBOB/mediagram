package homerows

import catalog.Shelf
import catalog.allSetsById

/** Resume the title at its saved position. Sent by the TV home screen to `MainActivity`, with [EXTRA_SET_ID]. */
const val ACTION_RESUME = "com.mediagram.android.action.RESUME"

/** Open the title's page, with [EXTRA_SET_ID]. */
const val ACTION_OPEN = "com.mediagram.android.action.OPEN"

/** Who's watching, in a mode that hands the remote back to the home screen once someone is picked. */
const val ACTION_PICK_PROFILE = "com.mediagram.android.action.PICK_PROFILE"

/** The title a resume or an open is for. */
const val EXTRA_SET_ID = "set_id"

/** What the TV home screen asked this app to open. */
sealed interface HomeLaunch {
    data class Resume(
        val setId: String,
    ) : HomeLaunch

    data class Open(
        val setId: String,
    ) : HomeLaunch

    data object PickProfile : HomeLaunch
}

/** The launch an intent's [action] and [setId] extra ask for; `null` for an ordinary start or a request missing its title. */
fun homeLaunchOf(
    action: String?,
    setId: String?,
): HomeLaunch? {
    val id = setId?.takeIf(String::isNotBlank)
    return when (action) {
        ACTION_RESUME -> id?.let(HomeLaunch::Resume)
        ACTION_OPEN -> id?.let(HomeLaunch::Open)
        ACTION_PICK_PROFILE -> HomeLaunch.PickProfile
        else -> null
    }
}

/** Where a resume or an open lands: [setId]'s page, and the player over it when [play]. */
data class LaunchTarget(
    val setId: String,
    val play: Boolean,
)

/**
 * This launch's place in [shelves] — the chosen person's, already filtered — or `null` when it names
 * no title they hold (gone from the library, or not theirs to see): the library opens as usual then.
 */
fun HomeLaunch.targetIn(shelves: List<Shelf>): LaunchTarget? {
    val held = { id: String -> id.takeIf { it in allSetsById(shelves) } }
    return when (this) {
        is HomeLaunch.Resume -> held(setId)?.let { LaunchTarget(it, play = true) }
        is HomeLaunch.Open -> held(setId)?.let { LaunchTarget(it, play = false) }
        HomeLaunch.PickProfile -> null
    }
}

/**
 * Who's watching opened for the home screen: over once the picker has shown and a person is chosen
 * again (a new one, or "Stay as I am"), so the remote goes back to the home screen then and only then.
 */
class PickForHome {
    private var active = false
    private var sawPicker = false

    fun start() {
        active = true
        sawPicker = false
    }

    /** Fed every gate state; `true` exactly once, when the pick is over. */
    fun over(picking: Boolean): Boolean {
        if (!active) return false
        if (picking) {
            sawPicker = true
            return false
        }
        if (!sawPicker) return false
        active = false
        return true
    }
}
