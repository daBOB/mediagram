package ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import model.together.CastWords
import ui.chrome.CatalogueTextButton
import ui.chrome.IndicationInk
import ui.chrome.NoDialogAnimation

/** The question's words. Android only, so not in `cast.json`: the web casts one way. */
internal object CastWayWords {
    const val THE_TV = "The TV plays it"
    const val THE_TV_DETAIL = "Mediagram on the TV box plays it. This device becomes the remote."
    const val THIS_DEVICE = "This device plays it"
    const val THIS_DEVICE_DETAIL = "The picture goes to a TV screen through Cast. This device becomes the remote."
}

/**
 * Play on TV's question, where the system's Cast screen opens: the TV box
 * plays the title ([onTv]), greyed with [tvReason], which a screen reader
 * hears after its name; or this device plays it and its picture goes to a
 * TV screen ([onThisDevice]), which touches no channel and is never greyed.
 * The first choice that can be pressed takes focus, as the ask dialog's first
 * button does. Back and Cancel are [onDismiss].
 */
@Composable
internal fun CastWayDialog(
    tvReason: String?,
    onTv: () -> Unit,
    onThisDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Asked from over the video too: the dialog is the page's own surface, so its buttons tint in the page's ink.
    IndicationInk(MaterialTheme.colorScheme.onSurface) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                NoDialogAnimation()
                Text(CastWords.PLAY_ON_TV)
            },
            text = {
                Column {
                    WayChoice(CastWayWords.THE_TV, CastWayWords.THE_TV_DETAIL, tvReason, first = tvReason == null, onTv)
                    WayChoice(CastWayWords.THIS_DEVICE, CastWayWords.THIS_DEVICE_DETAIL, reason = null, first = tvReason != null, onThisDevice)
                }
            },
            confirmButton = { CatalogueTextButton(onClick = onDismiss) { Text(CastWords.CANCEL) } },
        )
    }
}

/** One way to play: its [name] over what it does ([detail]), greyed with [reason]; the [first] takes focus as it shows. */
@Composable
private fun WayChoice(
    name: String,
    detail: String,
    reason: String?,
    first: Boolean,
    onClick: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    if (first) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val words = listOfNotNull(name, reason, detail).joinToString(", ")
    CatalogueTextButton(
        onClick = onClick,
        enabled = reason == null,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = words },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
