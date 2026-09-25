package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import designsystem.Spacing
import ui.tv.TvTextRow

/**
 * A plate with one more thing to do beneath it — a list's "Remove",
 * Continue's "Mark finished" — the web's `withAction` in `shelf-view.js`
 * and the phone's text button under a plate, carried onto a remote.
 *
 * Beneath rather than on the plate: a Centre press on a plate always opens
 * it, so a second action has to be a stop of its own, one press down, where
 * nobody reaches it on the way to starting the title.
 */
@Composable
internal fun TvPlateWithAction(
    label: String,
    onAction: () -> Unit,
    plate: @Composable () -> Unit,
) {
    // Far enough below that the plate, grown by [ui.tv.TvFocus.Scale] under
    // the remote, does not cover the words it sits over.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        plate()
        TvTextRow(text = label, onClick = onAction, modifier = Modifier.padding(horizontal = Spacing.small))
    }
}

/**
 * Where the remote goes when the item it was on leaves the wall under it:
 * the one after it, or the one before it at the end — so it is never left
 * resting on a plate that is no longer there. `null` when it was the last,
 * or was never on the wall at all.
 */
internal fun <T> neighbourOf(
    items: List<T>,
    key: (T) -> String,
    leaving: String,
): String? {
    val at = items.indexOfFirst { key(it) == leaving }
    if (at < 0) return null
    return (items.getOrNull(at + 1) ?: items.getOrNull(at - 1))?.let(key)
}
