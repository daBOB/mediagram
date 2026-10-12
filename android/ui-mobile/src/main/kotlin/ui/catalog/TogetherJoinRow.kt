package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Home's line while a session this profile may join is live — "Watching
 * together: Title · TV, Tablet" and Join, the web's `.together-join`: one
 * line in the notice's ochre, above the shelves and never instead of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TogetherJoinRow(
    line: String,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.error
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = line, style = style, color = color)
        TextButton(onClick = onJoin, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                text = "Join",
                style = style.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                color = color,
            )
        }
    }
}
