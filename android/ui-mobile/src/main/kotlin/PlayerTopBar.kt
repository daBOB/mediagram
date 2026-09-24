package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 */
@Composable
internal fun PlayerTopBar(title: String, showTitle: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.medium)) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
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
