package data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * How this app's settings stores put a secret on disk: key names under
 * AES-SIV, values under AES-GCM, both beneath a master key the Android
 * keystore holds and never hands back.
 *
 * The account's own Telegram auth key is the exception: `session.key` is a
 * file the native core writes itself, protected by the app sandbox and kept
 * off every backup and device transfer by `data_extraction_rules.xml`.
 *
 * Every settings store goes through here rather than calling
 * `EncryptedSharedPreferences.create` for itself, so a store added later
 * cannot quietly end up on a weaker footing than the one before it — the
 * schemes are chosen once, in one place, for all of them.
 */
internal fun encryptedPreferences(
    context: Context,
    fileName: String,
): SharedPreferences =
    EncryptedSharedPreferences.create(
        context,
        fileName,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
