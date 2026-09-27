package ui.chrome

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The rail's and Settings' own size — the web's `.brand` at its widest (`shell.css:29-36`). */
val WordmarkSize = 27.sp

/**
 * "mediagram", set the one way this catalogue ever sets it — Fraunces 600
 * with a tightened tracking, the web's own `.brand`. Was inline in
 * [ui.settings.SettingsIndex]; the rail wants the identical mark, so it is
 * pulled out here rather than grown a second time nearby. [fontSize] is the
 * one thing that still varies — the web's own `.brand` shrinks under 900px
 * and again under 480px (`shell.css:184, 199`), which
 * [ui.chrome.CompactLibraryHeader] asks for by passing a smaller size here
 * rather than keeping a second copy of the mark's own shape.
 *
 * Fraunces' default instance is `wght 900`/`opsz 9` — its heaviest,
 * squarest cut — so the weight is always named explicitly rather than left
 * to the face's own default the way a lighter caller might get away with.
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = WordmarkSize,
) {
    Text(
        text = "mediagram",
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                letterSpacing = (-0.02).em,
            ),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}
