package ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.NEW_ACHIEVEMENT
import ui.common.RailItem

/** [TvIndexRow]'s dot: on the icon, so the collapsed rail — icon only — still shows it and says it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvIndexRowDotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    private fun show(
        expanded: Boolean,
        dot: String?,
    ) {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                TvTheme {
                    TvIndexRow(
                        icon = painterResource(RailItem.STATS.icon),
                        label = "Stats",
                        selected = false,
                        focusRequester = remember { FocusRequester() },
                        onSelect = {},
                        expanded = expanded,
                        contentDescription = "Stats",
                        dot = dot,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aCollapsedRowStillShowsItsDotAndSaysItWithItsName() {
        show(expanded = false, dot = NEW_ACHIEVEMENT)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stats").assert(hasContentDescription(NEW_ACHIEVEMENT))
    }

    @Test
    fun anOpenRowShowsItOnTheIconBesideItsLabel() {
        show(expanded = true, dot = NEW_ACHIEVEMENT)
        compose.onNodeWithText("Stats").assertIsDisplayed()
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aRowWithNoDotDrawsNone() {
        show(expanded = false, dot = null)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }
}
