package settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackageSettingsTest {
    @Test
    fun credentialsRoundTrip() =
        runTest {
            val settings = InMemoryPackageSettings()
            settings.write("https://example.com/latest.json", "a".repeat(44))
            assertEquals("https://example.com/latest.json", settings.read()?.url)
        }

    @Test
    fun nothingStoredMeansNoCredentials() =
        runTest {
            assertNull(InMemoryPackageSettings().read())
        }
}
