package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import designsystem.Spacing

/** The top gradient the web keeps behind its own bar: [SCRIM_ALPHA] black at the top edge, clear by the bottom of the bar. */
private val TOP_GRADIENT = Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM_ALPHA), Color.Transparent))

/**
 * The slim bar along the top: back, what is playing, and the controls the
 * web keeps in its own top bar — My List, Kids and Add to list ([marks]) and
 * Notes ([onNotes], when the open title has notes). Picture-in-picture rides
 * in the card instead.
 *
 * The arrow stays up regardless of the card's fade — Android's own way
 * back, not something the web has; everything else follows [showTitle], the
 * same fade the card is under. Beside the arrow, the title over a row of the
 * marks and Notes that wraps rather than squeezes on a narrow phone. Inset
 * by the status bar's band even while it is hidden, so the bar does not jump
 * on fullscreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    onNotes: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    marks: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (showTitle) Modifier.background(TOP_GRADIENT) else Modifier)
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.small)) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        if (showTitle) {
            Column(modifier = Modifier.weight(1f).padding(top = Spacing.medium, end = Spacing.small)) {
                if (title.isNotEmpty()) {
                    Text(text = title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), itemVerticalAlignment = Alignment.CenterVertically) {
                    marks()
                    if (onNotes != null) TextButton(onClick = onNotes) { Text(text = "Notes", color = Color.White) }
                }
            }
        }
    }
}
