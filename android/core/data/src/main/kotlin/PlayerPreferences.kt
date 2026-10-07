package data

import uniffi.mediagram_core.CoreInterface

/** The scope a profile's own defaults are filed under, apart from any show's scope. */
const val PROFILE_SCOPE = "profile"

// The names every player choice is filed under. They are persisted in
// state.db and must match the web player's, which reads and writes the
// same rows.

/** The preference name that holds a subtitle choice, whether for a show or a profile. */
const val SUBTITLE_PREFERENCE = "subtitle"
const val AUDIO_PREFERENCE = "audio"
const val SPEED_PREFERENCE = "speed"
const val FRAMING_PREFERENCE = "framing"
const val CUE_SIZE_PREFERENCE = "cue-size"
const val CUE_BACKING_PREFERENCE = "cue-backing"
const val CUE_OFFSET_PREFERENCE = "cue-offset"

/** The stored value of a subtitle choice made off, which is a choice like any other. */
const val SUBTITLE_OFF = "off"

/**
 * What a viewer chose for a show, so the player does not ask again — a
 * thin repository over [CoreInterface.preferences]/[CoreInterface.setPreference],
 * the way [WatchStateRepository] sits over the rest of `state.db`. Scoped
 * by a caller-supplied string (`player.scopeOf`'s output) rather than a
 * set id: choosing a speed on episode one is choosing it for the whole
 * show, and what "the whole show" means is `feature:player`'s question,
 * not this repository's.
 *
 * Core and provider failures propagate from both calls, as does
 * cancellation; [remember] returns false when the core wrote nothing.
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
