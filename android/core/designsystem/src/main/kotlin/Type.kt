// The optical-size axis is reached through `FontVariation.Setting`, which
// carries Compose's experimental-text opt-in — Newsreader still takes that
// route, and is worth its bytes for it. Fraunces does not, any more: on the
// device that surfaced `PageTitle`'s own trap, its `wght`/`opsz` axes went
// unhonoured wherever they were asked for away from the font's own default
// instance (`wght 900`, `opsz 9`) — not only through `TextAutoSize`'s own
// re-measurement, which is the trap `PageTitle`'s own doc comment already
// named, but through a plain `Text` with an explicit `FontVariation.Settings`
// too. A/B'd on-device to be sure: the same string at the same size, one
// drawn through `FontVariation.Settings`, one through a static cut
// fontTools' own instancer pinned at the same `wght`/`opsz` — the variable
// one rendered at the font's own heavy default regardless of what was
// asked for, the static one at the requested weight and optical size.
// Every Fraunces cut this file exposes is a static instance for that
// reason now, the same way `PageTitle`'s own pair already were. Newsreader
// and Geist stay variable: Newsreader's own default instance (`wght 400`,
// `opsz 18`) is close enough to what every call site here actually asks
// for that nothing has shown the same failure, and Geist's requested
// weights (500, 600) read correctly on the same device against its own
// `wght 400` default.
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
 * Newsreader and Geist: still variable, unlike [Display]/[PageTitle]/
 * [CoverTitle] below — see this file's own top note for why those three
 * draw through static, pre-instanced files instead.
 *
 * Newsreader is variable on an optical-size axis, which is why one file can
 * set a title at 23sp and a count at 11sp without either looking like the
 * other scaled. The axis is declared per face here because Android does
 * nothing automatic with `opsz`: a browser has `font-optical-sizing`, and
 * this does not. Geist has no optical-size axis — only weight — so
 * [Interface] carries none.
 *
 * On API 24 and 25 variation settings are ignored and every face renders at
 * its default instance. That is a legible fallback on two releases this app
 * still supports — Newsreader's and Geist's own default instances are close
 * enough to what every call site here actually asks for that neither showed
 * the failure the top note describes, unlike Fraunces.
 */
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

/** Drawn for a sentence or a figure read at reading distance. */
private const val READ_OPTICAL = 16f

/**
 * Fraunces: the wordmark, the shelf headings, and the name of a title —
 * static cuts at `opsz 28` (drawn for a name held at arm's length), one per
 * weight this catalogue actually asks for. See this file's own top note on
 * why a static pair, not `FontVariation.Settings`, draws these now.
 *
 * Never below 500: on an ink ground Fraunces' own 400 goes thin enough to
 * shimmer, and the catalogue sets its names in medium on paper anyway.
 */
internal val Display =
    FontFamily(
        Font(resId = R.font.fraunces_display_500, weight = FontWeight.Medium),
        Font(resId = R.font.fraunces_display_600, weight = FontWeight.SemiBold),
    )

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
 * and optical size for a wide viewport (`.dept-title`, `departments.css:30-35`).
 * Font size is deliberately unset: [PageHead] sizes it per screen with
 * `TextAutoSize.StepBased`, so only the shape of the title — weight,
 * tracking, how tight the lines sit — belongs to the style. `Center` +
 * `Trim.None` keep the 0.86em line height from clipping a capital's top and
 * bottom, which `Trim.Both` (Compose's own default) does not guarantee.
 *
 * A static font, the same reason [Display] and [CoverTitle] are — see this
 * file's own top note. This was the face that first surfaced the bug: a
 * page title draws through `TextAutoSize.StepBased` (see [PageHead]),
 * which re-measures the same `FontFamily` at several candidate sizes in one
 * pass, and on the device that found this, that re-measurement left the
 * requested `wght`/`opsz` axis values behind and fell back to
 * `fraunces.ttf`'s own registered default instance — `wght 900`, `opsz 9`,
 * its heaviest, most decorative cut. It was not the only face that carried
 * the failure, only the one whose own re-measurement pass happened to
 * surface it first — the top note's own on-device A/B is what showed
 * [Display] dropped the same axis values through a plain `Text`, no
 * `TextAutoSize` involved at all.
 */
val PageTitle =
    TextStyle(
        fontFamily = FontFamily(Font(resId = R.font.fraunces_page_title, weight = FontWeight.Medium)),
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.03).em,
        lineHeight = 0.86.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * [PageTitle] at the web's narrow-viewport optical size (`departments.css:90`)
 * — a compact phone screen, or a television read from the couch rather than
 * a tablet held in the hand. Its own static instance for the same reason.
 */
val PageTitleCompact =
    PageTitle.copy(fontFamily = FontFamily(Font(resId = R.font.fraunces_page_title_compact, weight = FontWeight.Medium)))

/**
 * The cover story's own headline — Fraunces at the widest cut the variable
 * font's optical-size axis offers, `opsz 144` (`.cover-title`'s own
 * `font-variation-settings: "opsz" 144`, `home.css:48-55`), a size no other
 * title on this catalogue reaches for. A static cut, like [Display] — see
 * this file's own top note; font size is left to the caller
 * (`ui.catalog.home.HomeType`'s own `fluid()`), the same split [PageTitle]
 * makes.
 */
val CoverTitle =
    TextStyle(
        fontFamily = FontFamily(Font(resId = R.font.fraunces_cover_600, weight = FontWeight.SemiBold)),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.035).em,
        lineHeight = 0.86.em,
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

    /** The size [PageHead] steps [PageTitleCompact] down from on a television — the ten-foot floor under its own 72-opsz ceiling. */
    val pageTitleMax: TextUnit = 72.sp

    /** [Eyebrow], at the ten-foot floor: double the phone's 11sp, read from the couch rather than the hand. */
    val eyebrow: TextUnit = 16.sp
}
