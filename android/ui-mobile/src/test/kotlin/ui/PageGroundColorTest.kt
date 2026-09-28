package ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import designsystem.Appearance
import designsystem.MediagramTheme
import designsystem.Palette
import designsystem.ThemeChoice
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * [pageGround] is what every page-level container reaches for now
 * (`MobileApp`'s root `Surface`, `AppChrome`'s pushed-frame `Scaffold`,
 * every department hero's own scrim) — one shared, named token rather than
 * each call site typing `colorScheme.background` (or, once, `colorScheme.surface`)
 * on its own. This is composed, not drawn: a pixel-level render check was
 * tried first and dropped after three different harnesses (a plain manual
 * `ActivityController`, the same with `@GraphicsMode(NATIVE)`, and a
 * self-managed `ActivityScenarioRule`) each failed on `captureToImage()`
 * for their own environment reason, none of them the fix — this project's
 * Robolectric setup has no working path to a real rendered pixel in a JVM
 * unit test yet; a contrast check elsewhere in this app's own history
 * reached the same conclusion and settled for on-device verification
 * instead. Composing [MediagramTheme] and capturing what it resolves needs
 * none of that, and catches the same regression: the ground and the page
 * one step above it staying two different colours, in both themes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageGroundColorTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun resolve(theme: ThemeChoice): Pair<Color, Color> {
        var ground = Color.Unspecified
        var page = Color.Unspecified
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme(appearance = Appearance(theme = theme)) {
                    ground = MaterialTheme.colorScheme.pageGround
                    page = MaterialTheme.colorScheme.surface
                }
            }
        }
        compose.waitForIdle()
        return ground to page
    }

    @Test
    fun darkThemesPageGroundIsThePalettesGroundNotItsPage() {
        val (ground, page) = resolve(ThemeChoice.DARK)
        assertEquals(Palette.Ground, ground)
        assertNotEquals(page, ground, "the page ground and a raised plate's own colour must stay two different tokens")
    }

    @Test
    fun lightThemesPageGroundIsThePalettesGroundNotItsPage() {
        val (ground, page) = resolve(ThemeChoice.LIGHT)
        assertEquals(Palette.LightGround, ground)
        assertNotEquals(page, ground, "the page ground and a raised plate's own colour must stay two different tokens")
    }
}
