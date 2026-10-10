package ui.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import designsystem.hoverMotion
import ui.chrome.linkClickable

/**
 * The web's `.see-all` link: a label and an arrow that nudges toward the wall
 * (`home-view.js:39`, `department-hero.js:51`). The arrow is its own node so it
 * can move alone; the link reads as [spoken] (the label by default), because
 * the arrow is decoration, as `::after` is.
 */
@Composable
internal fun SeeAllLink(
    label: String,
    style: TextStyle,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spoken: String = label,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        // Links don't shrink when pressed (`theme.css:167` is `button:active` only).
        modifier = modifier.linkClickable(source, onClick = onClick).clearAndSetSemantics { text = AnnotatedString(spoken) },
        // `.see-all { gap: 8px }` (`home.css:200`).
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = style, color = color)
        // `.see-all::after` moves 4px toward the wall over 480ms on hover (`home.css:208,210`); a press nudges it too.
        Text(text = "→", style = style, color = color, modifier = Modifier.hoverMotion(source, durationMs = 480, nudge = 4.dp))
    }
}
