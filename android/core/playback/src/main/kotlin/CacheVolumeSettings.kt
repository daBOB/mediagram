package playback

import android.content.Context
import android.content.SharedPreferences

/**
 * Which volume a viewer chose for the cache, as a [CacheVolume.id] — plain,
 * not a secret, the same reasoning [CacheBudgetSettings] gives for its own
 * byte count.
 */
interface CacheVolumeSettings {
    /** The chosen volume's id, or `null` when nothing has ever been chosen. */
    suspend fun read(): String?

    /** Persists [id] as the chosen volume. */
    suspend fun write(id: String)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryCacheVolumeSettings : CacheVolumeSettings {
    @Volatile
    private var stored: String? = null

    override suspend fun read(): String? = stored

    override suspend fun write(id: String) {
        stored = id
    }
}

/**
 * Persisted in the same plain `SharedPreferences` file as
 * [PlainCacheBudgetSettings] — one screen's two choices belong in one file
 * — under its own key, so writing one never touches the other.
 */
class PlainCacheVolumeSettings(
    private val context: Context,
) : CacheVolumeSettings {
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun read(): String? = preferences.getString(KEY_VOLUME_ID, null)

    override suspend fun write(id: String) {
        preferences.edit().putString(KEY_VOLUME_ID, id).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "playback_settings"
        const val KEY_VOLUME_ID = "cache_volume_id"
    }
}
