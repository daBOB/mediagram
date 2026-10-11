package ui.tv

import designsystem.inStyle
import androidx.compose.ui.text.TextStyle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.MaterialTheme
import designsystem.Accent
import designsystem.LocalReducedMotion
import designsystem.LocalTvStyle
import designsystem.Palette
import designsystem.TvStyle
import designsystem.TvType
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvTheme] is the only place a TV screen can reach [Palette] and
 * [TvType] through tv-material's own `MaterialTheme` — `:ui-tv`
 * never has M3 on its compile classpath, so nothing here can fall back to
 * `designsystem.MediagramTheme`. These tests pin the two facts every later
 * screen relies on without re-checking: the accent colour a focused card
 * borders itself in is the same red the phone calls "active", and a slot a
 * tv-material component reaches for on its own holds the television's role for
 * that slot, not tv-material's own default.
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
    fun choosingAnAccentSetsThePrimaryColour() {
        var primary: Color? = null
        show(accent = Accent.TEAL) { primary = MaterialTheme.colorScheme.primary }
        assertEquals(Accent.TEAL.dark, primary)
    }

    /** "Remove animations" reaches every TV screen through the theme, as the web's `prefers-reduced-motion` does. */
    @Test
    fun reducedMotionFollowsTheSystemsAnimatorScale() {
        var still: Boolean? = null
        show { still = LocalReducedMotion.current }
        assertEquals(false, still)
        close()

        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show { still = LocalReducedMotion.current }
        assertEquals(true, still)
    }

    @Test
    fun titleSlotHoldsTheTelevisionsTitleRole() {
        var titleSize: TextUnit? = null
        show { titleSize = MaterialTheme.typography.titleLarge.fontSize }
        assertEquals(TvType.Base.titleLarge.fontSize, titleSize)
    }

    @Test
    fun theTypographySlotsTakeTheStylesFace() {
        var body: TextStyle? = null
        show(style = TvStyle.APPLE) { body = MaterialTheme.typography.bodyMedium }
        assertEquals(TvType.Base.bodyMedium.inStyle(TvStyle.APPLE), body)
    }

    @Test
    fun bodyAndLabelSlotsHoldTheTelevisionsRoles() {
        var body: TextUnit? = null
        var label: TextUnit? = null
        show {
            body = MaterialTheme.typography.bodyMedium.fontSize
            label = MaterialTheme.typography.labelLarge.fontSize
        }
        assertEquals(TvType.Base.bodyMedium.fontSize, body)
        assertEquals(TvType.Base.labelLarge.fontSize, label)
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

    @Test
    fun appleAndGoogleSetTheirOwnNeutralsButKeepTheAccent() {
        val apple = tvColorScheme(Accent.Default.dark, TvStyle.APPLE.look.neutrals)
        assertEquals(Color(0xFF000000), apple.background)
        assertEquals(Color(0xFFFFFFFF), apple.onSurface)
        assertEquals(Color(0xFF1C1C1E), apple.surface)
        assertEquals(Accent.Default.dark, apple.primary)

        val google = tvColorScheme(Accent.Default.dark, TvStyle.GOOGLE.look.neutrals)
        assertEquals(Color(0xFF202124), google.background)
        assertEquals(Color(0xFFE8EAED), google.onSurface)
        assertEquals(Color(0xFF3C4043), google.surfaceVariant)
        assertEquals(Palette.Ochre, google.error)
    }

    @Test
    fun theThemeHandsItsStyleAndGroundToEveryScreen() {
        var style: TvStyle? = null
        var ground: Color? = null
        show(style = TvStyle.GOOGLE) {
            style = LocalTvStyle.current
            ground = MaterialTheme.colorScheme.background
        }
        assertEquals(TvStyle.GOOGLE, style)
        assertEquals(Color(0xFF202124), ground)
    }

    /** The player keeps mediagram's look in every style, with the viewer's own accent. */
    @Test
    fun thePlayerDrawsInMediagramsLookUnderAnotherStyle() {
        var style: TvStyle? = null
        var colours: androidx.tv.material3.ColorScheme? = null
        var body: TextStyle? = null
        var ink: Color? = null
        show(accent = Accent.TEAL, style = TvStyle.GOOGLE) {
            TvMediagramPlayerTheme {
                style = LocalTvStyle.current
                colours = MaterialTheme.colorScheme
                body = MaterialTheme.typography.bodyMedium
                ink = androidx.tv.material3.LocalContentColor.current
            }
        }
        assertEquals(TvStyle.MEDIAGRAM, style)
        assertEquals(Palette.Ground, colours?.background)
        assertEquals(Accent.TEAL.dark, colours?.primary)
        assertEquals(TvType.Base.bodyMedium, body)
        assertEquals(Palette.Text, ink)
    }

    private fun show(
        accent: Accent = Accent.Default,
        style: TvStyle = TvStyle.Default,
        content: @Composable () -> Unit,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme(accent = accent, style = style) { content() } }
        }
        compose.waitForIdle()
    }
}
