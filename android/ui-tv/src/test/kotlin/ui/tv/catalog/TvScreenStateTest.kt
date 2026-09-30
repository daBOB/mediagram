package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
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

    protected fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}
