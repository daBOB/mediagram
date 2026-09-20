package settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real `EncryptedSharedPreferences` path, not the in-memory
 * fake `InMemoryPackageSettings` already covered elsewhere: this is the only
 * test that proves the package key actually survives keystore-backed
 * encryption and decryption on a device, and that the plain preferences
 * file backing it never holds that key in the clear.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedPackageSettingsTest {

    // Matches the private PREFS_FILE_NAME in EncryptedPackageSettings; there
    // is no production accessor for it, so the name is duplicated here.
    private val prefsFileName = "package_settings"

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearBackingFile() {
        context.deleteSharedPreferences(prefsFileName)
    }

    @Test
    fun credentialsRoundTripThroughRealEncryption() = runTest {
        val settings = EncryptedPackageSettings(context)
        val url = "https://example.com/latest.json"
        val keyB64 = "b".repeat(44)

        settings.write(url, keyB64)
        val read = settings.read()

        assertEquals(url, read?.url)
        assertEquals(keyB64, read?.keyB64)
    }

    @Test
    fun theBackingFileNeverHoldsTheKeyInPlainText() = runTest {
        val keyB64 = "c".repeat(44)
        EncryptedPackageSettings(context).write("https://example.com/latest.json", keyB64)

        // Reading the same file name through plain, unencrypted
        // SharedPreferences is exactly what an attacker with a rooted
        // device or a backup extraction tool would do; neither the key
        // names nor the values may leak the secret this way.
        val rawPreferences = context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
        val rawStrings = rawPreferences.all.entries.map { "${it.key}=${it.value}" }

        assertFalse(rawStrings.any { it.contains(keyB64) })
    }
}
