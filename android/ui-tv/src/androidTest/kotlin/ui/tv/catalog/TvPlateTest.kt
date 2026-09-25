package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme

/**
 * The focus and centre-key behaviour [TvPlate] exists for, which only run
 * true on a real window manager — Robolectric's tv-material nodes are known
 * to misbehave for exactly this (`TvFocusTest`'s history). What a given
 * `progress` and `watched` draw and that a click reaches `onOpen` are
 * proven without one in `TvPlateStateTest` instead.
 */
@RunWith(AndroidJUnit4::class)
class TvPlateTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val plateTag = "tv-plate-under-test"

    @Test
    fun aRequestedPlateReportsFocused() {
        show()

        compose.onNodeWithTag(plateTag).performSemanticsAction(SemanticsActions.RequestFocus)

        compose.onNodeWithTag(plateTag).assertIsFocused()
    }

    @Test
    fun theCentreKeyCallsOnOpen() {
        var opened = false
        show(onOpen = { opened = true })

        compose.onNodeWithTag(plateTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(plateTag).performKeyInput { pressKey(Key.Enter) }

        assertEquals(true, opened)
    }

    @Test
    fun theDPadCentreKeyAlsoCallsOnOpen() {
        var opened = false
        show(onOpen = { opened = true })

        compose.onNodeWithTag(plateTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(plateTag).performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(true, opened)
    }

    private fun show(onOpen: () -> Unit = {}) {
        compose.setContent {
            TvTheme {
                TvPlate(
                    title = "A Quiet Film",
                    posterPath = null,
                    onOpen = onOpen,
                    modifier = Modifier.testTag(plateTag),
                )
            }
        }
    }
}
