package designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mediagram.android.core.designsystem.R

/**
 * Geist, the whole of Apple TV style's type: Apple's own face is not
 * licensed off Apple's devices, and Geist is cut in the same grotesk
 * manner. 400–600 come from the variable file, which reads true at those
 * weights on the box; the cover title's bold is a static cut, for the
 * reason `Type.kt`'s top note gives.
 */
internal val AppleTvFamily =
    FontFamily(interfaceFont(400), interfaceFont(500), interfaceFont(600), Font(resId = R.font.geist_700, weight = FontWeight.Bold))

/**
 * Google Sans Flex, the whole of Google TV style's type — Google's own
 * face, under the OFL since 2025-11. One static cut per weight the TV asks
 * for, for the same on-device reason.
 */
internal val GoogleTvFamily =
    FontFamily(
        Font(resId = R.font.google_sans_flex_400, weight = FontWeight.Normal),
        Font(resId = R.font.google_sans_flex_500, weight = FontWeight.Medium),
        Font(resId = R.font.google_sans_flex_600, weight = FontWeight.SemiBold),
        Font(resId = R.font.google_sans_flex_700, weight = FontWeight.Bold),
    )

/** The one face [style] sets every TV word in, or null where each role keeps its own (mediagram's three). */
private fun familyOf(style: TvStyle): FontFamily? =
    when (style) {
        TvStyle.MEDIAGRAM -> null
        TvStyle.APPLE -> AppleTvFamily
        TvStyle.GOOGLE -> GoogleTvFamily
    }

/** [this] role in [style]'s face; its size, weight and spacing stay its own. Unchanged for mediagram. */
fun TextStyle.inStyle(style: TvStyle): TextStyle = familyOf(style)?.let { copy(fontFamily = it) } ?: this

/** [inStyle] for the style the TV draws in now — [LocalTvStyle]. */
@Composable
@ReadOnlyComposable
fun TextStyle.inTvStyle(): TextStyle = inStyle(LocalTvStyle.current)
