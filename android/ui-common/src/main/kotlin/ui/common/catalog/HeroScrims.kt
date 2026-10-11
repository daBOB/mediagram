package ui.common.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The cover story's own scrim over its backdrop — `.cover-stage::after`
 * (`home.css:34-36`, `352-355`) — shared by the phone's `CoverSlide` and the
 * television's own cover slide, so a lift in one never drifts from the
 * other's. [paper] is the page's own ground colour, read by each caller
 * from whichever `MaterialTheme` it has on its own classpath — this module
 * carries neither `androidx.compose.material3` nor `androidx.tv.material3`,
 * so the colour itself is the caller's to resolve, not this function's.
 */
@Composable
fun CoverScrim(
    compact: Boolean,
    paper: Color,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        // `linear-gradient(0deg, paper 0%, rgba(8,8,9,.82) 30%, rgba(8,8,9,.35) 70%, rgba(8,8,9,.2) 100%)` — 0deg starts at the bottom (`home.css:351-354`).
        Box(
            modifier.background(
                Brush.verticalGradient(
                    0f to Color(0x33080809),
                    0.3f to Color(0x59080809),
                    0.7f to Color(0xD1080809),
                    1f to paper,
                ),
            ),
        )
        return
    }
    // CSS multiple backgrounds paint the *first*-listed layer on top — the
    // web declares the 90deg horizontal gradient first (`home.css:34`),
    // then the paper fade (35), then the top scrim (36), so the horizontal
    // gradient is what actually sits over the picture. Compose has no such
    // rule: [drawCoverScrim] draws them back-to-front — top scrim, then
    // paper fade, then the horizontal gradient last — to land in the web's
    // own order. Drawing them in declaration order instead once put the
    // *top scrim* frontmost, which left the button row sitting almost
    // directly on `--paper` in Light theme (contrast around 2:1, against the
    // web's 7-10:1).
    val paperFade = remember(paper) { coverPaperFade(paper) }
    Box(modifier.drawBehind { drawCoverScrim(paperFade) })
}

/** `linear-gradient(180deg, rgba(8,8,9,.55) 0%, 0 22%)` (`home.css:36`): clear below [TopScrimEnd] of the height. */
internal val CoverTopScrim = Brush.verticalGradient(0f to Color(0x8C080809), TopScrimEnd to Color(0x00080809))

/** `linear-gradient(0deg, paper 0%, transparent 26%)` (`home.css:35`): clear above [PaperFadeStart] of the height. */
internal fun coverPaperFade(paper: Color): Brush = Brush.verticalGradient(PaperFadeStart to paper.copy(alpha = 0f), 1f to paper)

/** `linear-gradient(90deg, rgba(8,8,9,.94) 0%, .78 26%, .24 56%, 0 76%)` (`home.css:34`): clear past [SideScrimEnd] of the width. */
internal val CoverSideScrim =
    Brush.horizontalGradient(
        0f to Color(0xF0080809),
        0.26f to Color(0xC7080809),
        0.56f to Color(0x3D080809),
        SideScrimEnd to Color(0x00080809),
    )

/**
 * The cover's three scrim layers, back-to-front, each drawn only over the
 * band where it shows. Every brush still spans the whole cover — a draw
 * scope sizes a brush to itself, not to the rect it fills — so the pixels
 * are the full-size layers' own, while the fill drops from three
 * full-cover blends to about one and a quarter. On the TV box the cover
 * already took more GPU time per frame than one display interval, and a
 * crossfade draws two slides at once.
 */
internal fun DrawScope.drawCoverScrim(paperFade: Brush) {
    drawRect(CoverTopScrim, size = Size(size.width, ceil(size.height * TopScrimEnd)))
    val fadeTop = floor(size.height * PaperFadeStart)
    drawRect(paperFade, topLeft = Offset(0f, fadeTop), size = Size(size.width, size.height - fadeTop))
    drawRect(CoverSideScrim, size = Size(ceil(size.width * SideScrimEnd), size.height))
}

private const val TopScrimEnd = 0.22f
private const val PaperFadeStart = 0.74f
private const val SideScrimEnd = 0.76f
