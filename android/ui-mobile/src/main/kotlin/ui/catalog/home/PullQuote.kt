package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.CoverTitle
import model.MediaSet
import ui.chrome.linkClickable

/** The typographic break — a real tagline, set large, the mark hung in the margin — `.pull-quote` (`home.css:276-304`). */
@Composable
internal fun Quote(
    set: MediaSet,
    width: Dp,
    onOpenTitle: (String) -> Unit,
) {
    val tagline = set.tagline ?: return
    // A link on the web (`home-break.js:22`), and links don't shrink when pressed.
    Column(modifier = Modifier.fillMaxWidth().linkClickable(null) { onOpenTitle(set.setId) }) {
        // The mark hangs in the margin beside the first line, not above it
        // — `blockquote::before`, absolutely positioned at the paragraph's
        // own top-left (`home.css:277-285`).
        Box {
            Text(text = "“", style = CoverTitle.copy(fontSize = 64.sp, lineHeight = 1.em), modifier = Modifier.align(Alignment.TopStart))
            Text(
                text = tagline,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic,
                        fontSize = fluid(25.6f, 0.023f, 37.6f, width.value).sp,
                        lineHeight = 1.2.em,
                        letterSpacing = (-0.01).em,
                    ),
                modifier = Modifier.padding(start = 54.dp),
            )
        }
        Box(modifier = Modifier.padding(start = 54.dp, top = 24.dp, bottom = 18.dp).width(32.dp).height(1.dp).background(MaterialTheme.colorScheme.outline))
        val credit = listOfNotNull(set.title, set.year?.takeIf { it > 0 }?.toString()).joinToString(", ").uppercase()
        Text(
            text = credit,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.28.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 54.dp),
        )
    }
}
