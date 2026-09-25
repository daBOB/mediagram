package playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LanCacheSettingsTest {
    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun enabledDefaultsToTrueWithNothingChosen() =
        runTest {
            assertTrue(PlainLanCacheSettings(context()).enabled())
        }

    @Test
    fun anEnabledChoiceRoundTrips() =
        runTest {
            val settings = PlainLanCacheSettings(context())

            settings.setEnabled(false)

            assertEquals(false, settings.enabled())
        }

    @Test
    fun manualAddressDefaultsToNull() =
        runTest {
            assertNull(PlainLanCacheSettings(context()).manualAddress())
        }

    @Test
    fun aManualAddressRoundTrips() =
        runTest {
            val settings = PlainLanCacheSettings(context())

            settings.setManualAddress("http://192.168.1.9:7788")

            assertEquals("http://192.168.1.9:7788", settings.manualAddress())
        }

    @Test
    fun itSharesThePrefsFileWithTheVolumeChoiceWithoutClobberingIt() =
        runTest {
            val volume = PlainCacheVolumeSettings(context())
            val lan = PlainLanCacheSettings(context())
            volume.write("card")

            lan.setEnabled(false)

            assertEquals("card", volume.read())
            assertEquals(false, lan.enabled())
        }

    @Test
    fun inMemoryImplementationDefaultsAndRoundTrips() =
        runTest {
            val settings = InMemoryLanCacheSettings()
            assertTrue(settings.enabled())
            assertNull(settings.manualAddress())

            settings.setEnabled(false)
            settings.setManualAddress("http://10.0.0.5:7788")

            assertEquals(false, settings.enabled())
            assertEquals("http://10.0.0.5:7788", settings.manualAddress())
        }
}
