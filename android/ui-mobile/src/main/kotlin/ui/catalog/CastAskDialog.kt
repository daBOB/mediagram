package ui.catalog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import model.together.CastWords
import model.together.busyQuestion
import player.CastAsk
import player.CastSenderViewModel
import ui.chrome.CatalogueTextButton
import ui.chrome.NoDialogAnimation

/**
 * What Play on TV says while the TV answers, the web's `cast-ask` dialog:
 * "Asking the TV…" with nothing to press, the Replace question with Cancel
 * and Replace, or what the TV said with OK. Its first button takes focus
 * once there is one. Back is [onDismiss], which waits out the asking and
 * cancels the question. Nothing shows for no ask, nor once the TV took it.
 */
@Composable
internal fun CastAskDialog(
    ask: CastAsk?,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val line =
        when (ask) {
            CastAsk.Asking -> CastWords.ASKING
            is CastAsk.Busy -> busyQuestion(ask.title)
            is CastAsk.Told -> ask.text
            is CastAsk.On, null -> return
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = CastWords.PLAY_ON_TV },
        text = {
            NoDialogAnimation()
            Text(line)
        },
        dismissButton = if (ask is CastAsk.Busy) ({ AskButton(CastWords.CANCEL, first = true, onCancel) }) else null,
        confirmButton = {
            when (ask) {
                is CastAsk.Busy -> AskButton(CastWords.REPLACE, first = false, onReplace)
                is CastAsk.Told -> AskButton("OK", first = true, onDismiss)
                else -> Unit
            }
        },
    )
}

/** One of the dialog's buttons; the [first] takes focus as it shows, as the web's does. */
@Composable
private fun AskButton(
    words: String,
    first: Boolean,
    onClick: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    if (first) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CatalogueTextButton(onClick = onClick, modifier = if (first) Modifier.focusRequester(focus) else Modifier) { Text(words) }
}

/**
 * Play on TV's answer, wherever it was pressed: [CastAskDialog] for the
 * words, and [onOn] with the cast's id once the TV took it, which opens the
 * remote.
 */
@Composable
internal fun CastAskHost(
    onOn: (Int) -> Unit,
    sender: CastSenderViewModel = hiltViewModel(),
) {
    val ask by sender.ask.collectAsStateWithLifecycle()
    LaunchedEffect(ask) {
        val on = ask as? CastAsk.On ?: return@LaunchedEffect
        onOn(on.id)
        sender.dismiss()
    }
    CastAskDialog(ask, onReplace = sender::replace, onCancel = sender::cancel, onDismiss = sender::dismiss)
}
