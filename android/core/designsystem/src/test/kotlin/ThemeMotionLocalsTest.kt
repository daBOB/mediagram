package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeMotionLocalsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theThemeAnswersPressesTheWebsWayAndNeverWithRipple() {
        var indication: Indication? = null
        var ripple: RippleConfiguration? = RippleConfiguration()
        var tone: IndicationTone? = null
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme(appearance = Appearance(theme = ThemeChoice.LIGHT, accent = Accent.TEAL)) {
                    indication = LocalIndication.current
                    ripple = LocalRippleConfiguration.current
                    tone = LocalIndicationTone.current
                }
            }
        }
        compose.waitForIdle()
        assertEquals(catalogueIndication(), indication)
        assertNull(ripple)
        assertEquals(IndicationTone(ink = Palette.LightText, ring = Accent.TEAL.light), tone)
    }
}
