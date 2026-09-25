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
import androidx.compose.ui.unit.sp
import com.mediagram.android.core.designsystem.R

/**
 * The catalogue's two faces, the same two the web player sets it in.
 *
 * Both are variable and both carry an optical-size axis, which is why one
 * file can set a title at 23sp and a count at 11sp without either looking
 * like the other scaled. The axis is declared per face here because Android
 * does nothing automatic with `opsz`: a browser has `font-optical-sizing`,
 * and this does not.
 *
 * Fraunces is not asked for a weight below 500. On an ink ground its 400
 * goes thin enough to shimmer, and the catalogue sets its names in medium
 * on paper anyway.
 *
 * On API 24 and 25 variation settings are ignored and both faces render at
 * their default instance. That is a legible fallback on two releases this
 * app still supports, not a reason to ship a static cut of each.
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

/** Drawn for a name held at arm's length: the wordmark, headings, titles. */
private const val DISPLAY_OPTICAL = 28f

/** Drawn for a sentence or a figure read at reading distance. */
private const val READ_OPTICAL = 16f

/** Fraunces: the wordmark, the shelf headings, and the name of a title. */
internal val Display = FontFamily(display(500), display(600))

/** Newsreader: everything meant to be read as a sentence or a figure. */
internal val Read = FontFamily(read(400), read(500), read(600))

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
            bodyLarge = bodyLarge.copy(fontFamily = Read, fontSize = 17.sp),
            bodyMedium = bodyMedium.copy(fontFamily = Read, fontSize = 15.sp),
            bodySmall = bodySmall.copy(fontFamily = Read, fontSize = 13.sp),
            labelLarge = labelLarge.copy(fontFamily = Read, fontWeight = FontWeight.Medium),
            labelMedium =
                labelMedium.copy(
                    fontFamily = Read,
                    fontSize = 13.sp,
                    fontFeatureSettings = TABULAR,
                ),
            labelSmall =
                labelSmall.copy(
                    fontFamily = Read,
                    fontSize = 11.sp,
                    fontFeatureSettings = TABULAR,
                ),
        )
    }

/**
 * The same two faces, set for a screen read from the couch rather than
 * from the hand. Plain `TextStyle`, not an M3 `Typography` like
 * [CatalogueTypography] above: `:ui-tv` never has material3 on its compile
 * classpath, only `compose-ui`, so `TextStyle` is the type this scale can
 * actually hand across that boundary.
 *
 * Newsreader body copy holds at 18sp — this catalogue's floor for what
 * reads at roughly 3m, the couch distance a 960x540dp TV viewport assumes.
 * Fraunces titles are set at 34sp, since a shelf name here is read across
 * the room rather than at arm's length, the way the phone's own titles are.
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
            fontFamily = Read,
            fontSize = 18.sp,
        )
}
