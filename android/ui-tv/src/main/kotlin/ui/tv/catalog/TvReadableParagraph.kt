package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.TvTypeScale
import ui.tv.TvFocus

/**
 * A paragraph the remote can rest on, for a page whose only other stop is
 * above it: a remote cannot scroll a page it has no stop in, so an
 * overview longer than the screen would otherwise run off the bottom out
 * of reach. Down lands here and the page scrolls it into view; Up goes back.
 *
 * Its focus is quieter than a row's — the accent as a rule down its
 * leading edge rather than a whole paragraph turned red and underlined — so
 * reading is not mistaken for something to press, while it still shows
 * from across a room where the remote is.
 */
@Composable
internal fun TvReadableParagraph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TvTypeScale.body,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        style = style,
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .drawBehind {
                    if (focused) {
                        val x = -ReadingRuleGap.toPx()
                        drawLine(accent, Offset(x, 0f), Offset(x, size.height), TvFocus.BorderWidth.toPx())
                    }
                },
    )
}

/** How far outside the paragraph its focus rule sits, so the rule never touches a letter. */
private val ReadingRuleGap = 12.dp
