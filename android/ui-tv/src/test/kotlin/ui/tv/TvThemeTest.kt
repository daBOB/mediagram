package ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.MaterialTheme
import designsystem.Accent
import designsystem.Palette
import designsystem.TvTypeScale
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
 * [TvTheme] is the only place a TV screen can reach [Palette] and
 * [TvTypeScale] through tv-material's own `MaterialTheme` — `:ui-tv`
 * never has M3 on its compile classpath, so nothing here can fall back to
 * `designsystem.MediagramTheme`. These tests pin the two facts every later
 * screen relies on without re-checking: the accent colour a focused card
 * borders itself in is the same red the phone calls "active", and a title
 * or a line of body copy is set at the couch-distance sizes [TvTypeScale]
 * chose, not tv-material's own defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvThemeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun primaryColourIsTheDefaultAccentsDarkValue() {
        var primary: Color? = null
        show { primary = MaterialTheme.colorScheme.primary }
        assertEquals(Accent.Default.dark, primary)
    }

    @Test
    fun choosingAnAccentSetsPaletteImprintSoEveryOtherWidgetFollowsIt() {
        show(accent = Accent.TEAL) {}
        assertEquals(Accent.TEAL.dark, Palette.Imprint)
    }

    @Test
    fun titleTextTracksTvTypeScale() {
        var titleSize: TextUnit? = null
        show { titleSize = MaterialTheme.typography.titleLarge.fontSize }
        assertEquals(TvTypeScale.title.fontSize, titleSize)
    }

    @Test
    fun bodyTextTracksTvTypeScale() {
        var bodySize: TextUnit? = null
        show { bodySize = MaterialTheme.typography.bodyLarge.fontSize }
        assertEquals(TvTypeScale.body.fontSize, bodySize)
    }

    /**
     * No composition needed for this one: [tvColorScheme] is a plain
     * function, so the roles a later screen actually reaches for — the
     * ground it draws on, the plate a card sits on, the text on both, and
     * the two rule weights a divider or a focus outline would use — can be
     * pinned straight against [Palette] the way [CatalogueColorsTest] pins
     * the phone's scheme, without a Robolectric activity in between.
     */
    @Test
    fun coreRolesComeFromThePalette() {
        val colours = tvColorScheme(Accent.Default.dark)
        assertEquals(Palette.Ground, colours.background)
        assertEquals(Palette.Text, colours.onBackground)
        assertEquals(Palette.Page, colours.surface)
        assertEquals(Palette.Text, colours.onSurface)
        assertEquals(Palette.RuleStrong, colours.border)
        assertEquals(Palette.Rule, colours.borderVariant)
        assertEquals(Accent.Default.dark, colours.primary)
        assertEquals(Palette.Sunk, colours.surfaceVariant)
        assertEquals(Palette.Figures, colours.onSurfaceVariant)
        assertEquals(Palette.Ochre, colours.error)
        assertEquals(Palette.Sage, colours.tertiary)
    }

    private fun show(
        accent: Accent = Accent.Default,
        content: @Composable () -> Unit,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme(accent = accent) { content() } }
        }
        compose.waitForIdle()
    }
}
