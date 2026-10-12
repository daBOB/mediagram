package ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Called first inside a dialog's content, so its window opens and closes with
 * no platform animation, as the web's dialogs do: `showModal()` with no
 * transition on the dialog or its backdrop (`featured-reel.js:51`,
 * `pin-prompt.js:124`, `playback.css:19`).
 *
 * A dialog's first composition runs while its window attaches. Current
 * Android reads changed window attributes after that attach and sends them
 * with the window's first layout, before the window is shown, so the open is
 * as still as the close. Android 6 sent that first layout before attaching,
 * so there the open keeps the platform's fade.
 */
@Composable
internal fun NoDialogAnimation() {
    val view = LocalView.current
    SideEffect { (view.parent as? DialogWindowProvider)?.window?.setWindowAnimations(0) }
}
