package ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.tv.material3.LocalContentColor
import designsystem.Palette
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * `TvApp`'s start rule, mirrored from ui-mobile's `MobileAppTest`: `Ready`
 * opens the library stub, anything else stays on a setup stub. This is the
 * one behaviour a television and a phone must not disagree about, since
 * both read the same [setup.SetupViewModel] the same way.
 *
 * `assertIsDisplayed()`/`performClick()` misbehave against tv-material
 * under Robolectric — no real focus or measurement pass happens there — so
 * this checks with `assertExists()` instead, the way `TvFocusTest` does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvAppTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @After
    fun close() {
        try {
            compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun readySetupStateShowsTheLibraryStub() {
        show(ready = true)
        compose.onNodeWithText("library").assertExists()
    }

    @Test
    fun outstandingSetupStateShowsTheSetupStepStub() {
        show(ready = false)
        compose.onNodeWithText("NeedsSignIn").assertExists()
    }

    /**
     * The bug this closes: a stub `Text` sat directly in a `Box` painted
     * with `background(colorScheme.background)`, which sets no content
     * colour at all, so tv-material's `LocalContentColor` fell back to its
     * own default (black) on the catalogue's near-black ground — text that
     * exists in the tree but reads as invisible on screen, which
     * `assertExists()` alone cannot catch. [TvShell] is what fixes it —
     * this reads the colour every later TV screen would actually inherit
     * from it, not just whether something composed.
     */
    @Test
    fun tvShellPublishesTheCatalogueTextColourAsContentColour() {
        var contentColor: Color? = null
        showShell { contentColor = LocalContentColor.current }
        assertEquals(Palette.Text, contentColor)
    }

    private fun showShell(probe: @Composable () -> Unit) {
        lateinit var shellController: ActivityController<ComponentActivity>
        compose.runOnUiThread {
            shellController = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            shellController.get().setContent { TvTheme { TvShell { probe() } } }
        }
        compose.waitForIdle()
        compose.runOnUiThread { shellController.close() }
    }

    private fun show(ready: Boolean) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            TvAppTestActivity.fixture = TvAppFixture(ready)
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }
}
