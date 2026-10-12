package ui.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import designsystem.hoverMotion
import ui.chrome.CatalogueButton

/** The smallest a control on a title's page may be, so a thumb never has to aim for less. */
internal val MIN_TOUCH_TARGET = 48.dp

/** The solid pill that starts a title — `title-spread.js`'s `playPill`: Play, Resume, or Join. */
@Composable
internal fun PlayPill(
    label: String,
    onClick: () -> Unit,
) {
    val play = remember { MutableInteractionSource() }
    CatalogueButton(onClick = onClick, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), interactionSource = play) {
        // Two nodes so the triangle can move on its own, still read as the one label it was.
        Row(
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("▶ $label") },
            // `.pill { gap: 10px }` (`title-page.css:9`).
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // `.pill:hover .pill-play` moves 2px over 520ms (`title-page.css:26-27`); a press nudges it too.
            Text("▶", modifier = Modifier.hoverMotion(play, durationMs = 520, nudge = 2.dp))
            Text(label)
        }
    }
}
