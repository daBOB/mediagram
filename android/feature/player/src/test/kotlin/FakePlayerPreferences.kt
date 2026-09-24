package player

import data.PlayerPreferences

/**
 * In-memory preferences, keyed by profile and scope — real enough for
 * [PlayerViewModel] to load from and write through without a core behind
 * it.
 */
class FakePlayerPreferences(initial: Map<Pair<String, String>, Map<String, String>> = emptyMap()) : PlayerPreferences {

    private val stored = initial.toMutableMap()

    /** Every `remember` call this fake was asked for, in order, e.g. `"p1 key:x speed=1.5"`. */
    val remembered: MutableList<String> = mutableListOf()

    override suspend fun load(profileId: String, scope: String): Map<String, String> =
        stored[profileId to scope].orEmpty()

    override suspend fun remember(profileId: String, scope: String, name: String, value: String?): Boolean {
        remembered += "$profileId $scope $name=$value"
        val current = stored[profileId to scope].orEmpty()
        stored[profileId to scope] = if (value == null) current - name else current + (name to value)
        return true
    }
}
