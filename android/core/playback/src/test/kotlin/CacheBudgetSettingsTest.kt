package playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs under Robolectric, not a mocked [Context]: [PlainCacheBudgetSettings]
 * goes through the real `SharedPreferences` API, which a mock would have to
 * reimplement badly to exercise at all.
 */
@RunWith(RobolectricTestRunner::class)
class CacheBudgetSettingsTest {
    private fun newSettings(): PlainCacheBudgetSettings = PlainCacheBudgetSettings(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun readReturnsTheDefaultWhenNothingHasBeenWritten() =
        runTest {
            assertEquals(CACHE_MAX_BYTES, newSettings().read())
        }

    @Test
    fun writeBelowTheFloorIsClampedUpToIt() =
        runTest {
            val settings = newSettings()

            settings.write(MIN_CACHE_BYTES - 1)

            assertEquals(MIN_CACHE_BYTES, settings.read())
        }

    @Test
    fun writeAtOrAboveTheFloorIsPersistedAsIs() =
        runTest {
            val settings = newSettings()
            val chosenBytes = MIN_CACHE_BYTES * 2

            settings.write(chosenBytes)

            assertEquals(chosenBytes, settings.read())
        }

    @Test
    fun inMemoryImplementationClampsTheSameWayTheRealOneDoes() =
        runTest {
            val settings = InMemoryCacheBudgetSettings()

            settings.write(0)

            assertEquals(MIN_CACHE_BYTES, settings.read())
        }
}
