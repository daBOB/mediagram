package ui.tv.system

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus

private val LedgerRowPadding = 10.dp

/**
 * A heading and its label/value rows — the phone's own ledger (`ui.components.Block`)
 * at a television's sizes: a quiet label left, its value in the page's own
 * ink right, divided from the row after it by a soft hairline, never a
 * monospace grid. A row whose value is null or empty is left out entirely,
 * as on the phone.
 *
 * [focusable] makes the whole block a stop for the remote, its heading
 * taking the focus treatment: a page of facts has nothing to press, but
 * the remote still has to rest somewhere, and stepping block by block is
 * what scrolls a page longer than the screen.
 */
@Composable
internal fun TvInfoBlock(
    heading: String,
    rows: List<Pair<String, String?>>,
    modifier: Modifier = Modifier,
    focusable: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val tones = LocalCatalogueTones.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // Read as one, heading and rows together, wherever the remote rests on it.
                .let {
                    if (focusable) {
                        it.onFocusChanged { state -> focused = state.isFocused }.focusable().semantics(mergeDescendants = true) {}
                    } else {
                        it
                    }
                },
    ) {
        Text(text = heading, style = TvFocus.textStyle(TvTypeScale.title, focused))
        Spacer(Modifier.height(Spacing.small))
        for ((label, value) in rows) {
            if (value.isNullOrEmpty()) continue
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = LedgerRowPadding),
                    // A real gap, and the label never shrinks to make room —
                    // only the value's own column gives, wrapping within
                    // itself rather than colliding with the label before it.
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Text(text = label, style = TvTypeScale.body, color = tones.quiet)
                    Text(
                        text = value,
                        style = TvTypeScale.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tones.ruleSoft))
            }
        }
    }
}
