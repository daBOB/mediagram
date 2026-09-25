package playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Runs under Robolectric, not a mocked [Context]: [PlainCacheVolumeSettings]
 * goes through the real `SharedPreferences` API, the same way
 * [CacheBudgetSettingsTest] exercises its budget counterpart.
 */
@RunWith(RobolectricTestRunner::class)
class CacheVolumeSettingsTest {
    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun readReturnsNullWhenNothingHasBeenChosen() =
        runTest {
            assertNull(PlainCacheVolumeSettings(context()).read())
        }

    @Test
    fun aChoiceRoundTrips() =
        runTest {
            val settings = PlainCacheVolumeSettings(context())

            settings.write("card")

            assertEquals("card", settings.read())
        }

    @Test
    fun itSharesThePrefsFileWithTheBudgetWithoutClobberingIt() =
        runTest {
            val budget = PlainCacheBudgetSettings(context())
            val volume = PlainCacheVolumeSettings(context())
            budget.write(MIN_CACHE_BYTES * 4)

            volume.write("card")

            assertEquals(MIN_CACHE_BYTES * 4, budget.read())
            assertEquals("card", volume.read())
        }

    @Test
    fun inMemoryImplementationDefaultsToNullAndRoundTrips() =
        runTest {
            val settings = InMemoryCacheVolumeSettings()
            assertNull(settings.read())

            settings.write("card")

            assertEquals("card", settings.read())
        }
}
