package settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanCacheTokenSettingsTest {
    @Test
    fun tokenRoundTrips() =
        runTest {
            val settings = InMemoryLanCacheTokenSettings()

            settings.write("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff")

            assertEquals("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", settings.read())
        }

    @Test
    fun nothingStoredMeansNoToken() =
        runTest {
            assertNull(InMemoryLanCacheTokenSettings().read())
        }

    @Test
    fun clearingLeavesNothingStored() =
        runTest {
            val settings = InMemoryLanCacheTokenSettings()
            settings.write("a".repeat(64))

            settings.clear()

            assertNull(settings.read())
        }
}
