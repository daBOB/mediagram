package ui.tv.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Up from the page that 2-D focus search answers with nothing still leaves
 * for [target]. A bar drawn over the page (Apple TV and Google TV styles
 * float theirs) lets a row scroll up under it until the focused card's top
 * is above the pills' own, and the search only takes a stop whose top is
 * higher still: it finds none, and the chrome's `onExit` — which runs only
 * once a search has found somewhere outside to go — never runs, so the
 * remote was stuck until Back.
 *
 * An Up the search does answer, inside the page or onto the bar, moves
 * exactly as it would have. The event is consumed either way, so the
 * system's own search does not run a second time.
 */
@Composable
internal fun Modifier.upWithNothingAboveLeavesFor(target: () -> FocusRequester): Modifier {
    val focusManager = LocalFocusManager.current
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onKeyEvent false
        if (!focusManager.moveFocus(FocusDirection.Up)) target().requestFocus()
        true
    }
}
