package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

/**
 * Up or Back that takes the remote out of this page — Up off its first row,
 * Back from anywhere in it, both onto the bar above — scrolls the page back
 * to its top, so the hero over that first row is on screen again, as it was
 * when the page opened. The television's own scroll rule only ever moves a
 * page to show whatever takes focus, and a hero holds no stop of its own,
 * so nothing else brings it back.
 *
 * Any other way out leaves the page where it is: Left onto the rail, and OK
 * opening a title, whose return restores the row it was opened from.
 */
@Composable
internal fun Modifier.toTopWhenLeftForTheBar(toTop: suspend () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    var upOrBack by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) upOrBack = event.key == Key.DirectionUp || event.key == Key.Back
        false
    }.onFocusChanged {
        // Down from the bar is a key this page never sees, so re-entry clears what the last exit left.
        if (it.hasFocus) upOrBack = false else if (upOrBack) scope.launch { toTop() }
    }
}
