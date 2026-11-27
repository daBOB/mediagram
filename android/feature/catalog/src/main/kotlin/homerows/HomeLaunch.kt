package homerows

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
