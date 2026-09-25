package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import kotlinx.coroutines.launch
import model.markdown.Block
import model.markdown.Span
import ui.player.notesHeadingLevel
import ui.player.rememberNotesText
import ui.tv.TvFocus

/** Finds the notes column in a test: the region the remote pages through. */
internal const val TvNotesTag = "tv-player-notes"

/**
 * The phone's notes column — the web's `#notes-panel` — for a television:
 * a "Notes" head over the summary, drawn from the parsed tree.
 *
 * The body is one focusable region rather than a stop per paragraph: Up and
 * Down move it a page at a time ([PAGE_SHARE] of what is in view, so the
 * last lines of one page are still there at the top of the next to keep
 * the reader's place), which crosses a long transcript in a few presses
 * where a stop per line would take hundreds. It keeps the remote at either
 * end rather than letting a press fall out of the column onto something
 * else. Focused, it wears the house accent, so a viewer across the room
 * can see the remote is on the notes and not the film.
 *
 * No ✕ as on the phone: Back closes the column, as it closes the settings
 * panel, and the Notes button in the controls opens and closes it too.
 * Selection is left out for the same reason — a remote has nothing to
 * select with.
 */
@Composable
internal fun TvNotesPanel(
    blocks: List<Block>,
    region: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    left: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .background(Palette.Page)
                // Inside the overscan margin on the edges it meets; the side
                // facing the picture needs only breathing room.
                .padding(start = Spacing.large, end = Overscan.horizontal, top = Overscan.vertical, bottom = Overscan.vertical),
    ) {
        Text(text = "Notes", style = TvTypeScale.title, color = Palette.Text, modifier = Modifier.padding(bottom = Spacing.medium))
        Column(
            modifier =
                Modifier
                    .testTag(TvNotesTag)
                    .focusRequester(region)
                    .focusProperties { if (left != null) this.left = left }
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChanged(it.isFocused)
                    }
                    .onKeyEvent { event ->
                        val direction =
                            when (event.key) {
                                Key.DirectionUp -> -1
                                Key.DirectionDown -> 1
                                else -> return@onKeyEvent false
                            }
                        if (event.type == KeyEventType.KeyDown) {
                            scope.launch { scroll.animateScrollBy(direction * scroll.viewportSize * PAGE_SHARE) }
                        }
                        true
                    }.focusable()
                    .border(TvFocus.BorderWidth, if (focused) Palette.Imprint else Color.Transparent)
                    .verticalScroll(scroll)
                    .padding(Spacing.medium),
        ) {
            blocks.forEach { TvNotesBlock(it) }
        }
    }
}

/** How much of what is in view one press moves the notes: most of a page, with a few lines kept for place. */
private const val PAGE_SHARE = 0.8f

/** One block — `nodeFor` in `notes-view.js`, in the television's type. */
@Composable
private fun TvNotesBlock(block: Block) {
    when (block) {
        is Block.Heading ->
            Text(
                text = tvNotesText(block.spans),
                style = headingStyle(block.level),
                color = Palette.Text,
                modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small),
            )
        is Block.Paragraph -> TvNotesLine(block.spans)
        is Block.MarkdownList ->
            block.items.forEachIndexed { index, item ->
                Row(modifier = Modifier.padding(bottom = Spacing.extraSmall)) {
                    Text(if (block.ordered) "${index + 1}." else "•", style = TvTypeScale.body, color = Palette.Figures, modifier = Modifier.width(32.dp))
                    Column {
                        TvNotesLine(item.spans)
                        item.blocks.forEach { TvNotesBlock(it) }
                    }
                }
            }
        is Block.Quote ->
            Row(modifier = Modifier.height(IntrinsicSize.Min).padding(bottom = Spacing.small)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Palette.RuleStrong))
                Spacer(Modifier.width(Spacing.small))
                Column { block.blocks.forEach { TvNotesBlock(it) } }
            }
        // Wrapped rather than scrolled sideways as on the phone: a remote
        // already pages this column up and down, and has no second axis
        // to spare for one block.
        is Block.Code ->
            Text(
                text = block.text,
                style = TvTypeScale.body.copy(fontFamily = FontFamily.Monospace),
                color = Palette.Text,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.small).background(Palette.Sunk).padding(Spacing.small),
            )
        Block.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = Spacing.small).height(1.dp).background(Palette.Rule))
    }
}

@Composable
private fun TvNotesLine(spans: List<Span>) {
    Text(text = tvNotesText(spans), style = TvTypeScale.body, color = Palette.Text, modifier = Modifier.padding(bottom = Spacing.small))
}

/** No link handler: nothing on a remote can point at one word of a paragraph, so a link keeps only its words. */
@Composable
private fun tvNotesText(spans: List<Span>) = rememberNotesText(spans, linkColor = Palette.Imprint, codeBackground = Palette.Sunk, uriHandler = null)

/** By [notesHeadingLevel]'s floor; a size between the body and the head, so a section heading never outranks "Notes". */
private fun headingStyle(level: Int): TextStyle =
    when (notesHeadingLevel(level)) {
        2 -> TvTypeScale.title.copy(fontSize = 26.sp)
        3 -> TvTypeScale.body.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        else -> TvTypeScale.body.copy(fontWeight = FontWeight.SemiBold)
    }
