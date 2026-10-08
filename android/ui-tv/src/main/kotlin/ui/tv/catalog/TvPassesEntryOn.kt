package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch

/**
 * A focus stop that only forwards: while [enabled], it takes the remote and
 * at once runs [passOn], which moves it somewhere real.
 *
 * For a wall whose header has nothing the remote can rest on (Anime with
 * nothing underway: the hero alone). Down from the bar or Right from the
 * rail asks the page to take focus, and Compose gives it to the first stop
 * already laid out. Under a hero that fills the screen no plate is laid out
 * yet, so the remote stayed where it was. The header takes the entry
 * instead, and [passOn] scrolls a plate in and focuses it. The wall turns
 * [enabled] off while it holds focus, so Up from the plates still leaves for
 * the bar instead of landing here.
 */
@Composable
internal fun Modifier.passesEntryOn(
    enabled: Boolean,
    passOn: suspend () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    // Still focusable while it holds the remote: a focused stop turned off
    // mid-hand-off clears focus to the root, and the bar takes it.
    var holding by remember { mutableStateOf(false) }
    return focusProperties { canFocus = enabled || holding }
        .onFocusChanged {
            holding = it.isFocused
            if (it.isFocused) scope.launch { passOn() }
        }.focusTarget()
}
