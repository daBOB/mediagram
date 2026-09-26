package ui.tv.system

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import designsystem.Accent
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvAppearanceBlockTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var picked: Accent? = null

    private fun show(selected: Accent) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvAppearanceBlock(selected = selected, onSelect = { picked = it }) }
        }
        compose.waitForIdle()
    }

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theSelectedAccentIsAnnouncedAsSelected() {
        show(selected = Accent.TEAL)
        compose.onNodeWithContentDescription("Teal").assertIsSelected()
        compose.onNodeWithContentDescription("Coral").assertIsNotSelected()
    }

    @Test
    fun pressingAnAccentReportsItChosen() {
        show(selected = Accent.CORAL)
        // Not performClick(): tv-material's ClickableSurface drives its click
        // through pointer-input gesture detection Robolectric doesn't run to
        // completion (see TvTmdbKeyScreenTest's own `press` helper for the
        // same reason). The semantics action is the click callback itself.
        compose.onNodeWithContentDescription("Blue").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(Accent.BLUE, picked)
    }
}
