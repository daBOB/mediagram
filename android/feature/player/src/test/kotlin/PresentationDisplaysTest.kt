package player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplayManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers [PresentationDisplays]: a wireless display that appears or goes
 * away while the flow is collected is noticed without polling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PresentationDisplaysTest {

    // Display.TYPE_WIFI is hidden API; this is its value.
    private val TYPE_WIFI = 3

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Robolectric's default display is not a presentation display, so the
    // category query ignores it until a flagged one is added.
    private fun connectTv(): Int {
        val id = ShadowDisplayManager.addDisplay("w1920dp-h1080dp", TYPE_WIFI)
        shadowOf(context.getSystemService(DisplayManager::class.java).getDisplay(id)).apply {
            setFlags(Display.FLAG_PRESENTATION)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return id
    }

    @Test
    fun noSecondScreenIsNull() = runTest(UnconfinedTestDispatcher()) {
        val displays = PresentationDisplays(context)
        backgroundScope.launch { displays.display.collect {} }
        assertNull(displays.display.value)
    }

    @Test
    fun aPresentationDisplayIsFound() = runTest(UnconfinedTestDispatcher()) {
        val displays = PresentationDisplays(context)
        backgroundScope.launch { displays.display.collect {} }
        val id = connectTv()
        assertEquals(id, assertNotNull(displays.display.value).displayId)
    }

    @Test
    fun removingItGoesBackToNull() = runTest(UnconfinedTestDispatcher()) {
        val displays = PresentationDisplays(context)
        backgroundScope.launch { displays.display.collect {} }
        val id = connectTv()
        ShadowDisplayManager.removeDisplay(id)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(displays.display.value)
    }
}
