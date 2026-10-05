package data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class BackdropWidthTest {
    @Test
    fun aTelevisionAsksForTheWideBackdropDespiteItsDpWidthBeingBelowTheTabletLine() {
        assertEquals(1280, width(Configuration.UI_MODE_TYPE_TELEVISION, smallestWidthDp = 540))
    }

    @Test
    fun aPhoneAsksForTheNarrowBackdrop() {
        assertEquals(780, width(Configuration.UI_MODE_TYPE_NORMAL, smallestWidthDp = 411))
    }

    @Test
    fun aTabletAsksForTheWideBackdrop() {
        assertEquals(1280, width(Configuration.UI_MODE_TYPE_NORMAL, smallestWidthDp = 800))
    }

    private fun width(
        uiModeType: Int,
        smallestWidthDp: Int,
    ): Int {
        val uiModeManager = mockk<UiModeManager>()
        every { uiModeManager.currentModeType } returns uiModeType
        val configuration = Configuration().apply { smallestScreenWidthDp = smallestWidthDp }
        val resources = mockk<Resources>()
        every { resources.configuration } returns configuration
        val context = mockk<Context>()
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.resources } returns resources
        return DeviceBackdropWidth(context).pixels()
    }
}
