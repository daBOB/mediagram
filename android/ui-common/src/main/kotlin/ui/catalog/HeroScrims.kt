package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
    // rule: each `Box` below draws over the one before it, so they are
    // built back-to-front — top scrim, then paper fade, then the horizontal
    // gradient last — to land in the web's own order. Drawing them in
    // declaration order instead once put the *top scrim* frontmost, which
    // left the button row sitting almost directly on `--paper` in Light
    // theme (contrast around 2:1, against the web's 7-10:1).
    Box(modifier) {
        // `linear-gradient(180deg, rgba(8,8,9,.55) 0%, 0 22%)` (`home.css:36`).
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x8C080809), 0.22f to Color(0x00080809))))
        // `linear-gradient(0deg, paper 0%, transparent 26%)` (`home.css:35`).
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.74f to paper.copy(alpha = 0f), 1f to paper)))
        // `linear-gradient(90deg, rgba(8,8,9,.94) 0%, .78 26%, .24 56%, 0 76%)` (`home.css:34`).
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xF0080809),
                    0.26f to Color(0xC7080809),
                    0.56f to Color(0x3D080809),
                    0.76f to Color(0x00080809),
                ),
            ),
        )
    }
}
