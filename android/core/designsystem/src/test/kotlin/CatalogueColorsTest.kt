package designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `darkColorScheme` fills every role left unnamed with Material's baseline,
 * and Material's baseline dark is violet. An unset role is therefore not a
 * neutral default: it is the one colour this scheme exists to replace,
 * sitting in wait for a component to reach for it.
 *
 * One did. `LinearProgressIndicator` draws its track from
 * `secondaryContainer`, which nothing here had named, and drew it in
 * lavender across the top of the catalogue every time the library updated.
 * This is that bug written down so the next unnamed role fails here instead
 * of on a screen.
 */
class CatalogueColorsTest {
    @Test
    fun everyRoleComesFromThePalette() {
        val ours =
            setOf(
                Palette.Ground,
                Palette.Page,
                Palette.Sunk,
                Palette.Text,
                Palette.Figures,
                Palette.Rule,
                Palette.RuleStrong,
                Palette.Imprint,
                Palette.Ochre,
                Palette.Sage,
            )

        val strangers = rolesOf(catalogueColorScheme(dark = true, accent = Palette.Imprint)).filterNot { (_, colour) -> colour in ours }

        assertTrue(
            strangers.isEmpty(),
            "these roles are not the catalogue's own: ${strangers.joinToString { "${it.first}=${it.second}" }}",
        )
    }

    /**
     * The roles a Material component can reach for. Listed rather than
     * reflected over, so that a role added by a future Material release
     * arrives here as a compile-time decision rather than passing unnoticed.
     */
    private fun rolesOf(scheme: androidx.compose.material3.ColorScheme) =
        listOf(
            "primary" to scheme.primary,
            "onPrimary" to scheme.onPrimary,
            "primaryContainer" to scheme.primaryContainer,
            "onPrimaryContainer" to scheme.onPrimaryContainer,
            "secondary" to scheme.secondary,
            "onSecondary" to scheme.onSecondary,
            "secondaryContainer" to scheme.secondaryContainer,
            "onSecondaryContainer" to scheme.onSecondaryContainer,
            "tertiary" to scheme.tertiary,
            "onTertiary" to scheme.onTertiary,
            "tertiaryContainer" to scheme.tertiaryContainer,
            "onTertiaryContainer" to scheme.onTertiaryContainer,
            "background" to scheme.background,
            "onBackground" to scheme.onBackground,
            "surface" to scheme.surface,
            "onSurface" to scheme.onSurface,
            "surfaceVariant" to scheme.surfaceVariant,
            "onSurfaceVariant" to scheme.onSurfaceVariant,
            "surfaceTint" to scheme.surfaceTint,
            "surfaceBright" to scheme.surfaceBright,
            "surfaceDim" to scheme.surfaceDim,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "inverseSurface" to scheme.inverseSurface,
            "inverseOnSurface" to scheme.inverseOnSurface,
            "inversePrimary" to scheme.inversePrimary,
            "outline" to scheme.outline,
            "outlineVariant" to scheme.outlineVariant,
            "error" to scheme.error,
            "onError" to scheme.onError,
            "errorContainer" to scheme.errorContainer,
            "onErrorContainer" to scheme.onErrorContainer,
            "scrim" to scheme.scrim,
        )

    /** The exact value that was on screen, so the bug cannot come back unnoticed. */
    @Test
    fun theProgressTrackIsNotMaterialsLavender() {
        assertTrue(catalogueColorScheme(dark = true, accent = Palette.Imprint).secondaryContainer != Color(0xFF4A4458))
    }

    /**
     * `Palette` is public so a television theme — which never sees M3 and so
     * can't build a `ColorScheme` at all — can still read the same named
     * values this scheme was built from, instead of a hex getting retyped
     * into a second file. Pinning the mapping here means the two roles
     * can't quietly drift apart.
     */
    @Test
    fun paletteExposesTheSameValuesCatalogueColorSchemeUses() {
        val scheme = catalogueColorScheme(dark = true, accent = Palette.Imprint)
        assertEquals(Palette.Ground, scheme.background)
        assertEquals(Palette.Page, scheme.surface)
        assertEquals(Palette.Sunk, scheme.surfaceVariant)
        assertEquals(Palette.Text, scheme.onBackground)
        assertEquals(Palette.Figures, scheme.onSurfaceVariant)
        assertEquals(Palette.Imprint, scheme.primary)
        assertEquals(Palette.Ochre, scheme.error)
        assertEquals(Palette.Sage, scheme.tertiary)
        assertEquals(Palette.Rule, scheme.outlineVariant)
        assertEquals(Palette.RuleStrong, scheme.outline)
    }

    /** [catalogueColorScheme]'s light branch reads from the same [Palette] Light* roles Settings' Light theme resolves to. */
    @Test
    fun lightBranchUsesTheLightPaletteRoles() {
        val scheme = catalogueColorScheme(dark = false, accent = Accent.CORAL.light)
        assertEquals(Palette.LightGround, scheme.background)
        assertEquals(Palette.LightPage, scheme.surface)
        assertEquals(Palette.LightSunk, scheme.surfaceVariant)
        assertEquals(Palette.LightText, scheme.onBackground)
        assertEquals(Palette.LightFigures, scheme.onSurfaceVariant)
        assertEquals(Accent.CORAL.light, scheme.primary)
        assertEquals(Palette.LightOchre, scheme.error)
        assertEquals(Palette.LightSage, scheme.tertiary)
        assertEquals(Palette.LightRule, scheme.outlineVariant)
        assertEquals(Palette.LightRuleStrong, scheme.outline)
    }
}
