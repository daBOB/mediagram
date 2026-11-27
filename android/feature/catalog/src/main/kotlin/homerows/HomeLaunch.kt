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

/** What Who's watching's gate shows, as a pick made for the home screen reads it. */
enum class PickGate { Loading, Picking, Chosen }

/** What that pick does next: nothing yet, open the picker over the person chosen, or hand the remote back. */
enum class PickStep { Wait, Reopen, Done }

/**
 * Who's watching opened for the home screen. The gate starts from Loading whenever it is collected
 * again, so "someone is chosen" is only known once it says Chosen: then the picker is opened over
 * them, once. The pick is over when someone is chosen after the picker showed (a new person, or
 * "Stay as I am"), and only then does the remote go back to the home screen.
 */
class PickForHome {
    private var active = false
    private var sawPicker = false
    private var reopened = false

    fun start() {
        active = true
        sawPicker = false
        reopened = false
    }

    /** Fed every gate state; [PickStep.Done] exactly once, when the pick is over. */
    fun next(gate: PickGate): PickStep {
        if (!active) return PickStep.Wait
        return when (gate) {
            PickGate.Loading -> PickStep.Wait
            PickGate.Picking -> {
                sawPicker = true
                PickStep.Wait
            }
            PickGate.Chosen ->
                when {
                    sawPicker -> {
                        active = false
                        PickStep.Done
                    }
                    reopened -> PickStep.Wait
                    else -> {
                        reopened = true
                        PickStep.Reopen
                    }
                }
        }
    }
}
