package player

import data.PlayerPreferences
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory preferences, keyed by profile and scope — real enough for
 * [PlayerViewModel] to load from and write through without a core behind
 * it.
 *
 * [gate], held while running, is what lets a test open a genuine window
 * where [load] is still resolving — the same reason [FakeCatalogRepository]
 * carries one, needed here for a race that starts *after* the set itself
 * (and so this profile's remembered choices for it) is already known.
 */
class FakePlayerPreferences(
    initial: Map<Pair<String, String>, Map<String, String>> = emptyMap(),
    private val gate: CompletableDeferred<Unit>? = null,
) : PlayerPreferences {

    private val stored = initial.toMutableMap()

    /** Every `remember` call this fake was asked for, in order, e.g. `"p1 key:x speed=1.5"`. */
    val remembered: MutableList<String> = mutableListOf()

    override suspend fun load(profileId: String, scope: String): Map<String, String> {
        gate?.await()
        return stored[profileId to scope].orEmpty()
    }

    override suspend fun remember(profileId: String, scope: String, name: String, value: String?): Boolean {
        remembered += "$profileId $scope $name=$value"
        val current = stored[profileId to scope].orEmpty()
        stored[profileId to scope] = if (value == null) current - name else current + (name to value)
        return true
    }
}
