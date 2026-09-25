package com.mediagram.android

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceSelectionTest {
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

    private fun mockContextWithUiMode(uiModeType: Int): Context {
        val uiModeManager = mockk<UiModeManager>()
        every { uiModeManager.currentModeType } returns uiModeType

        val context = mockk<Context>()
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        return context
    }
}
