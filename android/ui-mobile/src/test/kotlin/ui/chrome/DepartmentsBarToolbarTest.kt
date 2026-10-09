package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import designsystem.MediagramTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.common.MenuActions
import kotlin.test.assertEquals

/**
 * Search, the avatar and ⋮ stand at the bar's far end, the web's
 * `.library-toolbar { margin-left: auto }` (`shell.css:91`), however few
 * pills there are before them — not straight after the last one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class DepartmentsBarToolbarTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    @Test
    fun theToolbarEndsAtTheBarsGutter() {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                MediagramTheme {
                    DepartmentsBar(
                        pills = listOf(DepartmentPill("Home", null), DepartmentPill("Movies", 3)),
                        selected = 0,
                        onSelect = {},
                        onSearch = {},
                        profile = ProfileBarState(name = "Ada", onChoose = {}),
                        menu = MenuActions(onSystem = {}, onSettings = {}, onUpdate = {}, onTmdbKey = {}, onStartOver = {}),
                        onAskStartOver = {},
                        blend = 1f,
                    )
                }
            }
        }
        compose.waitForIdle()

        val width = compose.onRoot().getUnclippedBoundsInRoot().right
        val menu = compose.onNodeWithContentDescription("Menu").getUnclippedBoundsInRoot()
        // The web's gutter at this width: clamp(16px, 3.2vw, 56px). M3's
        // IconButton draws 40dp inside its 48dp touch target, so its own
        // bounds stop up to 4dp short of where the target ends.
        assertEquals((width - width * 0.032f).value, menu.right.value, 4.5f)
    }
}
