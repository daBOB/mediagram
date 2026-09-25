package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import designsystem.TvTypeScale
import ui.tv.setup.TvDialog

/**
 * What a fetch of artwork and descriptions reported, or what stopped it —
 * the twin of the phone's `FetchResultDialog`, with its title and its one
 * way out. Shown over whichever library screen is up when the fetch ends,
 * and nothing at all while there is no [message].
 *
 * OK takes the remote as the dialog opens, and Back dismisses it the same
 * way: there is nothing here to decide, only something to have read.
 */
@Composable
internal fun TvFetchResultDialog(
    message: String?,
    onDismiss: () -> Unit,
) {
    if (message == null) return
    val ok = remember { FocusRequester() }
    TvDialog(title = "Update library", body = message, onDismissRequest = onDismiss, initialFocus = ok) {
        Button(onClick = onDismiss, modifier = Modifier.focusRequester(ok)) {
            Text("OK", style = TvTypeScale.body)
        }
    }
}
