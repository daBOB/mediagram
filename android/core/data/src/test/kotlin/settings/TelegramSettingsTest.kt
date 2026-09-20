package settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val WELL_FORMED_HASH = "0123456789abcdef0123456789abcdef"

class TelegramSettingsTest {

    @Test
    fun credentialsRoundTrip() = runTest {
        val settings = InMemoryTelegramSettings()
        settings.write(1234, WELL_FORMED_HASH)
        assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), settings.read())
    }

    @Test
    fun nothingStoredMeansNoCredentials() = runTest {
        assertNull(InMemoryTelegramSettings().read())
    }

    @Test
    fun clearingLeavesNothingStored() = runTest {
        val settings = InMemoryTelegramSettings()
        settings.write(1234, WELL_FORMED_HASH)

        settings.clear()

        assertNull(settings.read())
    }

    @Test
    fun theApiHashIsRedactedFromToString() {
        val rendered = TelegramCredentials(1234, WELL_FORMED_HASH).toString()

        // Anything that interpolates these credentials into a message — an
        // exception, a crash report — gets this string. The api id is not a
        // secret and stays legible; the hash must not be in it at all.
        assertFalse(rendered.contains(WELL_FORMED_HASH))
        assertEquals(true, rendered.contains("1234"))
    }
}
