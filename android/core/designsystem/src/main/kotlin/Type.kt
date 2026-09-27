// The optical-size axis is reached through `FontVariation.Setting`, which
// carries Compose's experimental-text opt-in. The axis is the reason these
// two faces are worth their bytes, and the alternative is shipping a static
// cut per size; the opt-in is the cheaper of the two risks.
@file:OptIn(ExperimentalTextApi::class)

package designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mediagram.android.core.designsystem.R

/**
 * The catalogue's three faces, the same three the web player sets it in.
 *
 * Fraunces and Newsreader are variable on an optical-size axis, which is why
 * one file can set a title at 23sp and a count at 11sp without either
 * looking like the other scaled. The axis is declared per face here because
 * Android does nothing automatic with `opsz`: a browser has
 * `font-optical-sizing`, and this does not. Geist has no optical-size axis —
 * only weight — so [Interface] carries none.
 *
 * Fraunces is not asked for a weight below 500. On an ink ground its 400
 * goes thin enough to shimmer, and the catalogue sets its names in medium
 * on paper anyway.
 *
 * On API 24 and 25 variation settings are ignored and every face renders at
 * its default instance. That is a legible fallback on two releases this app
 * still supports, not a reason to ship a static cut of each.
 */
private fun display(weight: Int) =
    Font(
        resId = R.font.fraunces,
        weight = FontWeight(weight),
        variationSettings =
            FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", DISPLAY_OPTICAL),
            ),
    )

private fun read(weight: Int) =
    Font(
        resId = R.font.newsreader,
        weight = FontWeight(weight),
        variationSettings =
            FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", READ_OPTICAL),
            ),
    )

private fun interfaceFont(weight: Int) =
    Font(
        resId = R.font.geist,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

/**
 * Fraunces at [PageTitle]'s own optical size rather than [DISPLAY_OPTICAL]:
 * a page title is drawn many times larger than a headline, and the axis
 * this face is worth its bytes for exists precisely so that size gets its
 * own cut instead of a headline's scaled up.
 */
private fun pageTitleFont(opticalSize: Float) =
    Font(
        resId = R.font.fraunces,
        weight = FontWeight.Medium,
        variationSettings =
            FontVariation.Settings(
                FontVariation.weight(500),
                FontVariation.Setting("opsz", opticalSize),
            ),
    )

/** Drawn for a name held at arm's length: the wordmark, headings, titles. */
private const val DISPLAY_OPTICAL = 28f

/** Drawn for a sentence or a figure read at reading distance. */
private const val READ_OPTICAL = 16f

/** [PageTitle]'s optical size on a wide viewport — a phone or tablet held in the hand. */
private const val PAGE_TITLE_OPTICAL = 112f

/** [PageTitleCompact]'s optical size — the web's narrow-viewport clamp ceiling, 4.5rem. */
private const val PAGE_TITLE_COMPACT_OPTICAL = 72f

/** Fraunces: the wordmark, the shelf headings, and the name of a title. */
internal val Display = FontFamily(display(500), display(600))

/** Newsreader: everything meant to be read as a sentence or a figure. */
internal val Read = FontFamily(read(400), read(500), read(600))

/**
 * Geist: the web's interface face — navigation, captions, the spaced-caps
 * eyebrow above a page title. Everything on this catalogue that reads as
 * chrome rather than as a sentence is set in this, not in [Read]; the one
 * kept exception is [CatalogueTypography.bodyLarge], the web's own reading
 * copy size, which stays Newsreader.
 */
internal val Interface = FontFamily(interfaceFont(400), interfaceFont(500), interfaceFont(600))

/**
 * Figures set in tabular numerals, so a column of counts or runtimes lines
 * up down the page instead of shifting with each digit's own width.
 */
private const val TABULAR = "tnum"

internal val CatalogueTypography =
    Typography().run {
        copy(
            headlineSmall =
                headlineSmall.copy(
                    fontFamily = Display,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    letterSpacing = (-0.1).sp,
                ),
            titleLarge =
                titleLarge.copy(
                    fontFamily = Display,
                    fontWeight = FontWeight.Medium,
                    fontSize = 23.sp,
                    letterSpacing = (-0.1).sp,
                ),
            titleMedium =
                titleMedium.copy(
                    fontFamily = Display,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                ),
            titleSmall =
                titleSmall.copy(
                    fontFamily = Display,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                ),
            // The web's own reading-copy size: whole sentences (loading, empty,
            // failure states) stay in the serif, not the interface face.
            bodyLarge = bodyLarge.copy(fontFamily = Read, fontSize = 17.sp),
            bodyMedium = bodyMedium.copy(fontFamily = Interface, fontSize = 15.sp),
            bodySmall = bodySmall.copy(fontFamily = Interface, fontSize = 13.sp),
            labelLarge = labelLarge.copy(fontFamily = Interface, fontWeight = FontWeight.Medium),
            labelMedium =
                labelMedium.copy(
                    fontFamily = Interface,
                    fontSize = 13.sp,
                    fontFeatureSettings = TABULAR,
                ),
            labelSmall =
                labelSmall.copy(
                    fontFamily = Interface,
                    fontSize = 11.sp,
                    fontFeatureSettings = TABULAR,
                ),
        )
    }

/**
 * The huge uppercase title atop a page — Fraunces at the web's own weight
 * and optical size for a wide viewport (`.dept-title`, `departments.css:31-36`).
 * Font size is deliberately unset: [PageHead] sizes it per screen with
 * `TextAutoSize.StepBased`, so only the shape of the title — weight,
 * tracking, how tight the lines sit — belongs to the style. `Center` +
 * `Trim.None` keep the 0.86em line height from clipping a capital's top and
 * bottom, which `Trim.Both` (Compose's own default) does not guarantee.
 */
val PageTitle =
    TextStyle(
        fontFamily = FontFamily(pageTitleFont(PAGE_TITLE_OPTICAL)),
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.03).em,
        lineHeight = 0.86.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * [PageTitle] at the web's narrow-viewport optical size (`departments.css:91`)
 * — a compact phone screen, or a television read from the couch rather than
 * a tablet held in the hand.
 */
val PageTitleCompact = PageTitle.copy(fontFamily = FontFamily(pageTitleFont(PAGE_TITLE_COMPACT_OPTICAL)))

/** Spaced capitals over a page title, the way a magazine labels a department (`.eyebrow`, `theme.css:196-203`). */
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

/**
 * The same faces, set for a screen read from the couch rather than from
 * the hand. Plain `TextStyle`, not an M3 `Typography` like
 * [CatalogueTypography] above: `:ui-tv` never has material3 on its compile
 * classpath, only `compose-ui`, so `TextStyle` is the type this scale can
 * actually hand across that boundary.
 *
 * Body copy holds at 18sp — this catalogue's floor for what reads at
 * roughly 3m, the couch distance a 960x540dp TV viewport assumes — now set
 * in Geist rather than Newsreader, the same swap [CatalogueTypography]
 * makes for everything that isn't a whole sentence. Fraunces titles are set
 * at 34sp, since a shelf name here is read across the room rather than at
 * arm's length, the way the phone's own titles are.
 */
object TvTypeScale {
    val title: TextStyle =
        TextStyle(
            fontFamily = Display,
            fontWeight = FontWeight.Medium,
            fontSize = 34.sp,
            letterSpacing = (-0.1).sp,
        )
    val body: TextStyle =
        TextStyle(
            fontFamily = Interface,
            fontSize = 18.sp,
        )

    /** The size [PageHead] steps [PageTitleCompact] down from on a television — the ten-foot floor under [PAGE_TITLE_COMPACT_OPTICAL]'s own ceiling. */
    val pageTitleMax: TextUnit = 72.sp

    /** [Eyebrow], at the ten-foot floor: double the phone's 11sp, read from the couch rather than the hand. */
    val eyebrow: TextUnit = 16.sp
}
