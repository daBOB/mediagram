package settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real `EncryptedSharedPreferences` path, not the in-memory
 * fakes already covered elsewhere: this is the only test that proves the
 * two secrets this app stores — the package key and the Telegram api hash —
 * actually survive keystore-backed encryption and decryption on a device,
 * and that the plain preferences files backing them never hold either in
 * the clear.
 *
 * Both stores are checked by the same assertions on purpose. They are a
 * pair, added a round apart, and a second store that quietly writes
 * plaintext is exactly the regression a test covering only the first one
 * would miss.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedSettingsTest {

    // Match the private PREFS_FILE_NAMEs in the two stores; there is no
    // production accessor for either, so the names are duplicated here.
    private val packagePrefsFile = "package_settings"
    private val telegramPrefsFile = "telegram_settings"

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearBackingFiles() {
        context.deleteSharedPreferences(packagePrefsFile)
        context.deleteSharedPreferences(telegramPrefsFile)
    }

    /**
     * Reading the same file name through plain, unencrypted
     * SharedPreferences is exactly what an attacker with a rooted device or
     * a backup extraction tool would do; neither the key names nor the
     * values may leak a secret this way.
     */
    private fun rawEntriesOf(fileName: String): List<String> =
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).all.entries
            .map { "${it.key}=${it.value}" }

    @Test
    fun packageCredentialsRoundTripThroughRealEncryption() = runTest {
        val settings = EncryptedPackageSettings(context)
        val url = "https://example.com/latest.json"
        val keyB64 = "b".repeat(43) + "="

        settings.write(url, keyB64)
        val read = settings.read()

        assertEquals(url, read?.url)
        assertEquals(keyB64, read?.keyB64)
    }

    @Test
    fun thePackageBackingFileNeverHoldsTheKeyInPlainText() = runTest {
        val keyB64 = "c".repeat(43) + "="
        EncryptedPackageSettings(context).write("https://example.com/latest.json", keyB64)

        assertFalse(rawEntriesOf(packagePrefsFile).any { it.contains(keyB64) })
    }

    @Test
    fun telegramCredentialsRoundTripThroughRealEncryption() = runTest {
        val settings = EncryptedTelegramSettings(context)
        val apiHash = "0123456789abcdef0123456789abcdef"

        settings.write(1234, apiHash)
        val read = settings.read()

        assertEquals(1234, read?.apiId)
        assertEquals(apiHash, read?.apiHash)
    }

    @Test
    fun theTelegramBackingFileNeverHoldsTheApiHashInPlainText() = runTest {
        val apiHash = "fedcba9876543210fedcba9876543210"
        EncryptedTelegramSettings(context).write(1234, apiHash)

        assertFalse(rawEntriesOf(telegramPrefsFile).any { it.contains(apiHash) })
    }

    @Test
    fun startingOverLeavesNeitherStoreReadable() = runTest {
        val telegram = EncryptedTelegramSettings(context)
        val library = EncryptedPackageSettings(context)
        telegram.write(1234, "0123456789abcdef0123456789abcdef")
        library.write("https://example.com/latest.json", "d".repeat(43) + "=")

        telegram.clear()
        library.clear()

        assertNull(telegram.read())
        assertNull(library.read())
    }
}
