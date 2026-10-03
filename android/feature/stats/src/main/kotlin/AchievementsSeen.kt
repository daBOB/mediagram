package stats

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which achievements this device has shown each profile — the rail's dot
 * lights for anything earned that is not in here. Per device and never
 * synced, as on the web (`localStorage` `mediagram.stats-seen.<profileId>`):
 * a badge earned on the television is still news on the tablet.
 */
interface AchievementsSeen {
    /** Profile id to the ids this device has shown it. */
    val seen: StateFlow<Map<String, Set<String>>>

    /** What the Stats page is showing [profileId] becomes what this device has shown it. */
    fun markSeen(
        profileId: String,
        ids: Set<String>,
    )
}

/** In memory, for tests. */
class InMemoryAchievementsSeen : AchievementsSeen {
    private val held = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    override val seen: StateFlow<Map<String, Set<String>>> = held.asStateFlow()

    override fun markSeen(
        profileId: String,
        ids: Set<String>,
    ) = held.update { it + (profileId to ids) }
}

/**
 * One plain preferences file, a string set per profile id. Not the encrypted
 * store: which badges a screen has shown is no secret, and a keystore
 * failure must not be able to light every dot again.
 */
class SharedPreferencesAchievementsSeen(
    context: Context,
) : AchievementsSeen {
    private val preferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    private val held =
        MutableStateFlow(
            preferences.all
                .mapNotNull { (profileId, ids) -> (ids as? Set<*>)?.let { profileId to it.filterIsInstance<String>().toSet() } }
                .toMap(),
        )
    override val seen: StateFlow<Map<String, Set<String>>> = held.asStateFlow()

    override fun markSeen(
        profileId: String,
        ids: Set<String>,
    ) {
        held.update { it + (profileId to ids) }
        preferences.edit().putStringSet(profileId, ids).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "achievements_seen"
    }
}
