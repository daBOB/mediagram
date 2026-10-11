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
// Every Fraunces cut here and in `TitleType.kt` is a static instance for that
// reason now, the same way `PageTitle`'s own pair already were. Newsreader
// and Geist stay variable: Newsreader's own default instance (`wght 400`,
// `opsz 18`) is close enough to what every call site here actually asks
// for that nothing has shown the same failure, and Geist's requested
// weights (500, 600) read correctly on the same device against its own
// `wght 400` default.
@file:OptIn(ExperimentalTextApi::class)

package designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mediagram.android.core.designsystem.R

/**
 * Newsreader and Geist: still variable, unlike [Display]/[PageTitle]/
 * [CoverTitle] in `TitleType.kt` — see this file's own top note for why those three
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
private fun read(
    weight: Int,
    italic: Boolean = false,
) = Font(
    resId = if (italic) R.font.newsreader_italic else R.font.newsreader,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings =
        FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("opsz", READ_OPTICAL),
        ),
)

internal fun interfaceFont(weight: Int) =
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

/**
 * Newsreader: everything meant to be read as a sentence or a figure. Its
 * italic is the web's own italic file, not the roman slanted: a pull-quote
 * asks for `FontStyle.Italic` and gets the true cut, single-storey *a* and
 * all. Only 400 is set in italic anywhere, so only 400 ships.
 */
internal val Read = FontFamily(read(400), read(500), read(600), read(400, italic = true))

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
internal const val TABULAR = "tnum"
