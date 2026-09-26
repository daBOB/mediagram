package ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Accent
import designsystem.Appearance
import designsystem.InMemoryAppearanceSettings
import designsystem.ThemeChoice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import setup.AppearanceViewModel
import kotlin.test.assertEquals

/**
 * End to end through [AppearanceViewModel] and a real [InMemoryAppearanceSettings]:
 * picking Light then Blue on [AppearanceSection] must reach the stored [Appearance],
 * the same way a viewer's tap does through Settings' real Hilt-provided instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppearanceSectionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val settings = InMemoryAppearanceSettings()
    private val model = AppearanceViewModel(settings)

    @Before
    fun open() {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                val appearance by model.state.collectAsStateWithLifecycle()
                MaterialTheme {
                    AppearanceSection(appearance = appearance, onChooseTheme = model::chooseTheme, onChooseAccent = model::chooseAccent)
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun pickingLightThenBlueUpdatesTheStoredChoice() {
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("Blue").performClick()

        assertEquals(Appearance(ThemeChoice.LIGHT, Accent.BLUE), settings.appearance.value)
        compose.onNodeWithText("Light").assertIsSelected()
        compose.onNodeWithText("Blue").assertIsSelected()
        compose.onNodeWithText("Dark").assertIsNotSelected()
        compose.onNodeWithText("Coral").assertIsNotSelected()
    }

    @Test
    fun defaultsToAutoAndCoralSelected() {
        compose.onNodeWithText("Auto").assertIsSelected()
        compose.onNodeWithText("Coral").assertIsSelected()
    }
}
