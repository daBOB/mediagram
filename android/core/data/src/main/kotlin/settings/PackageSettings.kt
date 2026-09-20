package settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The package URL and its decryption key, as pasted in during provisioning.
 * The URL is not a secret (docs/mlib-package-v1.md); the key is, so
 * [toString] is overridden — the generated one would print [keyB64] in
 * full the moment anything interpolates or logs this object.
 */
data class PackageCredentials(val url: String, val keyB64: String) {
    override fun toString(): String = "PackageCredentials(url=$url, keyB64=<redacted>)"
}

interface PackageSettings {
    suspend fun read(): PackageCredentials?
    suspend fun write(url: String, keyB64: String)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryPackageSettings : PackageSettings {

    @Volatile
    private var stored: PackageCredentials? = null

    override suspend fun read(): PackageCredentials? = stored

    override suspend fun write(url: String, keyB64: String) {
        stored = PackageCredentials(url, keyB64)
    }
}

/**
 * Persists the package key in `EncryptedSharedPreferences`, backed by a
 * keystore-guarded master key. The URL is stored alongside it for
 * convenience only; it carries no confidentiality requirement of its own.
 */
class EncryptedPackageSettings(context: Context) : PackageSettings {

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun read(): PackageCredentials? {
        val url = preferences.getString(KEY_URL, null) ?: return null
        val keyB64 = preferences.getString(KEY_KEY_B64, null) ?: return null
        return PackageCredentials(url, keyB64)
    }

    override suspend fun write(url: String, keyB64: String) {
        preferences.edit()
            .putString(KEY_URL, url)
            .putString(KEY_KEY_B64, keyB64)
            .apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "package_settings"
        const val KEY_URL = "package_url"
        const val KEY_KEY_B64 = "package_key_b64"
    }
}
