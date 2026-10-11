package ui.tv

import androidx.compose.ui.graphics.Color
import designsystem.Accent
import designsystem.Palette
import designsystem.TvStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/** The scheme [TvTheme] hands tv-material, in mediagram and in the other styles. */
class TvColorSchemeTest {
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
}
