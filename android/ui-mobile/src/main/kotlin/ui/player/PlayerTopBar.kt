package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import designsystem.Spacing

/**
 * Back, and what is playing — split out of `PlayerScreen` to keep that
 * file under the project's line guideline.
 *
 * The arrow stays up regardless of the transport bar's own fade (Android's
 * own way back, not something the web has); the title follows [showTitle]
 * instead, the same HUD fade the web's own `now` is under.
 *
 * The ends-at time is not repeated here: the web files it in the rail
 * above its transport, next to the same clock digits `PlayerControls`
 * already draws — that is where this port puts it too, rather than
 * beside a title on the opposite side of the screen.
 *
 * [onEnterPip], when non-null, draws a second button beside the back
 * arrow — the phone's touch equivalent of the web's `p` key, which this
 * app has no keyboard for (CLAUDE.md § Surface Parity). Null hides it:
 * below API 26, or already inside picture-in-picture, where this whole
 * bar is hidden anyway.
 *
 * [onNotes], when non-null, is the web's own "Notes" button: the open title
 * has notes, and this opens or closes their column. It fades with the title,
 * as the web's sits in the HUD that fades.
 */
@Composable
internal fun PlayerTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    onEnterPip: (() -> Unit)? = null,
    onNotes: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.medium)) {
                Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            if (onEnterPip != null) {
                IconButton(
                    onClick = onEnterPip,
                    modifier = Modifier
                        .padding(Spacing.medium)
                        .semantics { contentDescription = "Picture in picture" },
                ) {
                    Text(text = "⧉", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (onNotes != null && showTitle) {
                TextButton(onClick = onNotes) { Text(text = "Notes", color = Color.White) }
            }
        }
        if (showTitle && title.isNotEmpty()) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = Spacing.medium),
            )
        }
    }
}
