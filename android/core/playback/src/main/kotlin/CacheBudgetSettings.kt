package playback

import android.content.Context
import android.content.SharedPreferences

/** What the cache budget defaults to until a viewer changes it in Settings. */
const val CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB

/**
 * The floor a chosen budget is clamped to. Below this a cache could evict
 * everything it just wrote before a single episode finishes buffering,
 * which is not a smaller cache so much as a broken one.
 */
const val MIN_CACHE_BYTES = 512L * 1024 * 1024 // 512 MiB

/**
 * How large the on-disk media cache is allowed to grow, as a plain number
 * a viewer picks — not the encrypted stores [settings.TmdbSettings] and
 * [settings.LibrarySettings] use, because a byte count is not a secret the
 * way a key or a session is.
 */
interface CacheBudgetSettings {
    /** The chosen budget, or [CACHE_MAX_BYTES] when nothing has been chosen yet. */
    suspend fun read(): Long

    /** Persists [bytes], raised to [MIN_CACHE_BYTES] first if it falls short. */
    suspend fun write(bytes: Long)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryCacheBudgetSettings(initialBytes: Long = CACHE_MAX_BYTES) : CacheBudgetSettings {

    @Volatile
    private var stored: Long = initialBytes

    override suspend fun read(): Long = stored

    override suspend fun write(bytes: Long) {
        stored = maxOf(bytes, MIN_CACHE_BYTES)
    }
}

/**
 * Persisted in its own plain `SharedPreferences` file, not the encrypted
 * store the rest of Settings uses: opening it needs no keystore round
 * trip, which matters here because [CacheProvider.setBudget] is on the
 * path a viewer's slider drag runs on, not a one-off setup screen.
 */
class PlainCacheBudgetSettings(private val context: Context) : CacheBudgetSettings {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun read(): Long =
        preferences.getLong(KEY_BUDGET_BYTES, CACHE_MAX_BYTES)

    override suspend fun write(bytes: Long) {
        preferences.edit().putLong(KEY_BUDGET_BYTES, maxOf(bytes, MIN_CACHE_BYTES)).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "playback_settings"
        const val KEY_BUDGET_BYTES = "cache_budget_bytes"
    }
}
