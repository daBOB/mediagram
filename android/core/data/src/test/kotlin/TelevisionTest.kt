package data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelevisionTest {
    @Test
    fun aTelevisionUiModeSelectsTheTvSurface() {
        val context = mockContextWithUiMode(Configuration.UI_MODE_TYPE_TELEVISION)
        assertTrue(isTelevision(context))
    }

    @Test
    fun aPhoneUiModeSelectsTheTouchSurface() {
        val context = mockContextWithUiMode(Configuration.UI_MODE_TYPE_NORMAL)
        assertFalse(isTelevision(context))
    }

    /** Android's own `sw600dp` line: a phone below it, a tablet from it on. */
    @Test
    fun aSmallestWidthOf600dpOrMoreIsATablet() {
        assertFalse(isTablet(mockContextWithSmallestWidth(411)))
        assertFalse(isTablet(mockContextWithSmallestWidth(599)))
        assertTrue(isTablet(mockContextWithSmallestWidth(600)))
        assertTrue(isTablet(mockContextWithSmallestWidth(800)))
    }

    private fun mockContextWithSmallestWidth(dp: Int): Context {
        val resources = mockk<Resources>()
        every { resources.configuration } returns Configuration().apply { smallestScreenWidthDp = dp }
        val context = mockk<Context>()
        every { context.resources } returns resources
        return context
    }

    private fun mockContextWithUiMode(uiModeType: Int): Context {
        val uiModeManager = mockk<UiModeManager>()
        every { uiModeManager.currentModeType } returns uiModeType

        val context = mockk<Context>()
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        return context
    }
}
