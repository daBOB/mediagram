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
import designsystem.CatalogueTones
import designsystem.LocalCatalogueTones
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

    /** Settings' sidebar sits on each style's own page, not mediagram's near-black; mediagram keeps the phone's dark tones. */
    @Test
    fun theThemeHandsEachStyleItsOwnCatalogueTones() {
        for (style in TvStyle.entries) {
            var tones: CatalogueTones? = null
            show(style = style) { tones = LocalCatalogueTones.current }
            val n = style.look.neutrals
            val want = if (style == TvStyle.MEDIAGRAM) CatalogueTones(Palette.Sidebar, Palette.RuleSoft, Palette.RuleStrong) else CatalogueTones(n.page, n.rule, n.figures)
            assertEquals(want, tones, "$style")
            close()
        }
    }

    /** The player keeps mediagram's look in every style, with the viewer's own accent. */
    @Test
    fun thePlayerDrawsInMediagramsLookUnderAnotherStyle() {
        var style: TvStyle? = null
        var colours: androidx.tv.material3.ColorScheme? = null
        var body: TextStyle? = null
        var ink: Color? = null
        var tones: CatalogueTones? = null
        show(accent = Accent.TEAL, style = TvStyle.GOOGLE) {
            TvMediagramPlayerTheme {
                style = LocalTvStyle.current
                colours = MaterialTheme.colorScheme
                body = MaterialTheme.typography.bodyMedium
                ink = androidx.tv.material3.LocalContentColor.current
                tones = LocalCatalogueTones.current
            }
        }
        assertEquals(TvStyle.MEDIAGRAM, style)
        assertEquals(Palette.Ground, colours?.background)
        assertEquals(Accent.TEAL.dark, colours?.primary)
        assertEquals(TvType.Base.bodyMedium, body)
        assertEquals(Palette.Text, ink)
        assertEquals(Palette.Sidebar, tones?.sidebar)
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
