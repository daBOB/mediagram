package data

/**
 * What a viewer chose for a show, so the player does not ask again — a
 * thin repository over [CoreClient.preferences]/[CoreClient.setPreference],
 * the way [WatchStateRepository] sits over the rest of `state.db`. Scoped
 * by a caller-supplied string (`player.scopeOf`'s output) rather than a
 * set id: choosing a speed on episode one is choosing it for the whole
 * show, and what "the whole show" means is `feature:player`'s question,
 * not this repository's.
 */
interface PlayerPreferences {
    /** Every choice this profile has filed under [scope], by name. */
    suspend fun load(profileId: String, scope: String): Map<String, String>

    /** Remembers a choice, or forgets it ([value] `null`). `false` when nothing could be written. */
    suspend fun remember(profileId: String, scope: String, name: String, value: String?): Boolean
}

class DefaultPlayerPreferences(private val coreProvider: CoreProvider) : PlayerPreferences {

    // core.preferences(profileId) answers every scope this profile has ever
    // chosen anything under, in one round trip (see the core's own doc) —
    // filtered here to the one scope asked for rather than added to the
    // core's surface, since nothing else needs a per-scope read yet.
    override suspend fun load(profileId: String, scope: String): Map<String, String> {
        val core = coreProvider.awaitCore()
        return core.preferences(profileId)
            .filter { it.scope == scope }
            .associate { it.name to it.value }
    }

    override suspend fun remember(profileId: String, scope: String, name: String, value: String?): Boolean {
        val core = coreProvider.awaitCore()
        return core.setPreference(profileId, scope, name, value)
    }
}
