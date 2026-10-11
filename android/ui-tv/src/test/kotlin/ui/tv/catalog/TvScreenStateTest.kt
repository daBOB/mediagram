package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import designsystem.TvStyle
import org.junit.After
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import ui.tv.TvTheme

/**
 * The host every catalogue page's Robolectric test shows its page in: a
 * real, visible activity per test, closed after it, so each page is
 * composed the way the app composes it rather than inside a bare rule.
 */
abstract class TvScreenStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    /** The remote's Back, through the activity's own dispatcher — whatever a page registered, or nothing. */
    protected fun back() {
        compose.runOnUiThread { controller?.get()?.onBackPressedDispatcher?.onBackPressed() }
        compose.waitForIdle()
    }

    /** Whether anything on the page would take a Back — `false` leaves it to the frame that shows the page. */
    protected fun pageTakesBack(): Boolean {
        var takes = false
        compose.runOnUiThread { takes = controller?.get()?.onBackPressedDispatcher?.hasEnabledCallbacks() == true }
        return takes
    }

    /** The style [text] was laid out in, once its call site's copies and the theme's merges are done. */
    protected fun styleOf(text: String): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style
    }

    protected fun show(
        style: TvStyle = TvStyle.Default,
        content: @Composable () -> Unit,
    ) {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent { TvTheme(style = style) { content() } }
        }
        compose.waitForIdle()
    }
}
