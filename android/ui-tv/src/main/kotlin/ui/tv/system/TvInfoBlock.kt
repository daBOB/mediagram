package ui.tv.system

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.TvTypeScale
import ui.tv.TvFocus

/**
 * A heading and its label/value rows, the phone's `Block` at a television's
 * sizes: set in the page's own type, a label at the start and its value at
 * the end, never a monospace grid. A row whose value is null or empty is
 * left out entirely, as on the phone.
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
        for ((label, value) in rows) {
            if (value.isNullOrEmpty()) continue
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = label, style = TvTypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = TvTypeScale.body)
            }
        }
    }
}
