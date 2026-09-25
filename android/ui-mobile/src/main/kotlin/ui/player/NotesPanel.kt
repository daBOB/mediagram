package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import model.markdown.Block
import model.markdown.Span

/**
 * The notes column — `#notes-panel` in the web's `index.html`: a "Notes"
 * head with a ✕, then the summary, scrollable and selectable. Placed by
 * [NotesLayout]; drawn from the parsed tree by [NotesBlock].
 */
@Composable
internal fun NotesPanel(blocks: List<Block>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.medium)) {
            Text("Notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close notes" }) { Text("✕") }
        }
        HorizontalDivider()
        SelectionContainer {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.medium)) {
                blocks.forEach { NotesBlock(it) }
            }
        }
    }
}

/** One block — `nodeFor` in `notes-view.js`. */
@Composable
private fun NotesBlock(block: Block) {
    when (block) {
        is Block.Heading -> Text(
            text = notesText(block.spans),
            style = headingStyle(block.level),
            modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small),
        )
        is Block.Paragraph -> NotesLine(block.spans)
        is Block.MarkdownList -> block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(bottom = Spacing.extraSmall)) {
                Text(if (block.ordered) "${index + 1}." else "•", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium)
                Column {
                    NotesLine(item.spans)
                    item.blocks.forEach { NotesBlock(it) }
                }
            }
        }
        is Block.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min).padding(bottom = Spacing.small)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Spacer(Modifier.width(Spacing.small))
            Column { block.blocks.forEach { NotesBlock(it) } }
        }
        is Block.Code -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(Spacing.small),
        )
        Block.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
    }
}

@Composable
private fun NotesLine(spans: List<Span>) {
    Text(text = notesText(spans), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.small))
}

/** The phone's colours for [rememberNotesText], and its links handed on to another app. */
@Composable
private fun notesText(spans: List<Span>) =
    rememberNotesText(spans, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, LocalUriHandler.current)

/** By [notesHeadingLevel]'s floor. */
@Composable
private fun headingStyle(level: Int): TextStyle = when (notesHeadingLevel(level)) {
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}
