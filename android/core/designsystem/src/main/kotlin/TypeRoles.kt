package designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The catalogue's text roles as plain [TextStyle]s, each named for the M3
 * `Typography` slot it fills on the phone ([CatalogueTypography]). One list
 * for both surfaces: `:ui-tv` never has material3 on its compile classpath,
 * so a television screen reads a role here directly, and
 * `CatalogueType.bodyMedium` there is the phone's
 * `MaterialTheme.typography.bodyMedium`, size for size. Each starts from
 * M3's own baseline, so the line heights the phone has always drawn with
 * come along; the baseline's tracking does not, since the web sets these
 * lines at `letter-spacing: normal` and M3's +0.1 to +0.5sp loosened every
 * one of them against it.
 *
 * The numbers are the web's: Geist 13/14/15, Newsreader 17, Fraunces
 * 16/18/23/26, counts 11. A call site reaching a size the web sets and no
 * role carries copies the nearest role, as the phone's call sites do.
 */
object CatalogueType {
    private val baseline = Typography()

    val headlineSmall: TextStyle =
        baseline.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = (-0.1).sp)
    val titleLarge: TextStyle =
        baseline.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 23.sp, letterSpacing = (-0.1).sp)
    val titleMedium: TextStyle = baseline.titleMedium.copy(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 0.sp)
    val titleSmall: TextStyle = baseline.titleSmall.copy(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.sp)

    /** The web's own reading-copy size: whole sentences (loading, empty, failure states) stay in the serif, not the interface face. */
    val bodyLarge: TextStyle = baseline.bodyLarge.copy(fontFamily = Read, fontSize = 17.sp, letterSpacing = 0.sp)
    val bodyMedium: TextStyle = baseline.bodyMedium.copy(fontFamily = Interface, fontSize = 15.sp, letterSpacing = 0.sp)
    val bodySmall: TextStyle = baseline.bodySmall.copy(fontFamily = Interface, fontSize = 13.sp, letterSpacing = 0.sp)
    val labelLarge: TextStyle = baseline.labelLarge.copy(fontFamily = Interface, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    val labelMedium: TextStyle = baseline.labelMedium.copy(fontFamily = Interface, fontSize = 13.sp, letterSpacing = 0.sp, fontFeatureSettings = TABULAR)
    val labelSmall: TextStyle = baseline.labelSmall.copy(fontFamily = Interface, fontSize = 11.sp, letterSpacing = 0.sp, fontFeatureSettings = TABULAR)
}

/**
 * [CatalogueType]'s roles in the M3 slots they are named for — the phone's
 * and tablet's theme. Every other slot keeps M3's own baseline, as before.
 * Declared here, not beside the faces in `Type.kt`: [CatalogueType] reads
 * those faces as it initialises, and a reference back from that file would
 * hand this a half-built object on whichever surface touched the faces first.
 */
internal val CatalogueTypography =
    Typography().copy(
        headlineSmall = CatalogueType.headlineSmall,
        titleLarge = CatalogueType.titleLarge,
        titleMedium = CatalogueType.titleMedium,
        titleSmall = CatalogueType.titleSmall,
        bodyLarge = CatalogueType.bodyLarge,
        bodyMedium = CatalogueType.bodyMedium,
        bodySmall = CatalogueType.bodySmall,
        labelLarge = CatalogueType.labelLarge,
        labelMedium = CatalogueType.labelMedium,
        labelSmall = CatalogueType.labelSmall,
    )

/** Spaced capitals over a page title, the way a magazine labels a department (`.eyebrow`, `theme.css:197-204`). */
val Eyebrow =
    TextStyle(
        fontFamily = Interface,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.32.em,
        lineHeight = 1.4.em,
    )

/** A settings section's own heading — smaller and heavier than [Eyebrow], set in the interface face rather than Fraunces. */
val SectionHead =
    TextStyle(
        fontFamily = Interface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )

/** A ledger row's label — what a value beside it is *of*. */
val LedgerLabel = TextStyle(fontFamily = Interface, fontSize = 14.sp)

/** A ledger row's value, in tabular numerals so a column of them lines up. */
val LedgerValue = TextStyle(fontFamily = Interface, fontSize = 15.sp, fontFeatureSettings = TABULAR)
