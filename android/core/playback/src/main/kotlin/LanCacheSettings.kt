package playback

import android.content.Context
import android.content.SharedPreferences

/**
 * The plain half of the LAN cache's Settings block — everything about it
 * that is not a secret: whether to use it at all, and an address typed in
 * by hand for when discovery does not find it. The pairing token itself is
 * the one secret here, and lives in its own encrypted store
 * (`settings.LanCacheTokenSettings`, `:core:data`) the same way
 * [PackageSettings][settings.PackageSettings]' key does.
 */
interface LanCacheSettings {
    /**
     * Defaults to `true` when nothing has been chosen yet. With no token
     * paired and no server ever found, this default costs nothing — the
     * feature has nothing to do until a token is entered — so a viewer who
     * pairs a server sees it start working at once, with no separate switch
     * to remember to flip.
     */
    suspend fun enabled(): Boolean

    suspend fun setEnabled(value: Boolean)

    /** `null` when discovery alone is relied on. */
    suspend fun manualAddress(): String?

    suspend fun setManualAddress(address: String?)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryLanCacheSettings : LanCacheSettings {
    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var manualAddress: String? = null

    override suspend fun enabled(): Boolean = enabled

    override suspend fun setEnabled(value: Boolean) {
        enabled = value
    }

    override suspend fun manualAddress(): String? = manualAddress

    override suspend fun setManualAddress(address: String?) {
        manualAddress = address
    }
}

/**
 * Persisted in the same plain `SharedPreferences` file as
 * [PlainCacheVolumeSettings] — one screen's choices belong in one file —
 * under their own keys.
 */
class PlainLanCacheSettings(
    private val context: Context,
) : LanCacheSettings {
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun enabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    override suspend fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    override suspend fun manualAddress(): String? = preferences.getString(KEY_MANUAL_ADDRESS, null)

    override suspend fun setManualAddress(address: String?) {
        preferences.edit().putString(KEY_MANUAL_ADDRESS, address).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "playback_settings"
        const val KEY_ENABLED = "lan_cache_enabled"
        const val KEY_MANUAL_ADDRESS = "lan_cache_manual_address"
    }
}
