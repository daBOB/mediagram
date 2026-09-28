package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Focus for a page whose own row list keeps changing under the remote —
 * [TvPreloadsPage]'s own problem, unlike [TvWall]'s: a progress tick, a
 * promotion, or a resume must never move the remote off whatever a viewer
 * is actually sitting on. Focus lands once, on arrival — [restoreKey] if
 * it is still among [rowIds], otherwise the first — and is asked for again
 * only if the row that held it has since left [rowIds] entirely (a
 * cancel, a remove, or the film it named finishing), never merely because
 * some other row's own content changed.
 */
@Composable
internal fun rememberQueueFocus(rowIds: List<String>, restoreKey: String?): QueueFocus {
    val focusRequester = remember { FocusRequester() }
    var hasArrived by remember { mutableStateOf(false) }
    var focusedRowId by remember { mutableStateOf<String?>(null) }
    val targetRowId =
        remember(rowIds, hasArrived, focusedRowId) {
            when {
                !hasArrived -> restoreKey?.takeIf { it in rowIds } ?: rowIds.firstOrNull()
                focusedRowId != null && focusedRowId !in rowIds -> rowIds.firstOrNull()
                else -> null
            }
        }
    LaunchedEffect(targetRowId) {
        if (targetRowId != null) {
            hasArrived = true
            runCatching { focusRequester.requestFocus() }
        }
    }
    return QueueFocus(focusRequester, targetRowId) { setId -> focusedRowId = setId }
}

/** [Modifier.trackedBy]/[requesterFor] are what a row's own title control wires up — see [rememberQueueFocus]. */
internal class QueueFocus(
    private val focusRequester: FocusRequester,
    private val targetRowId: String?,
    private val onFocused: (setId: String) -> Unit,
) {
    fun reportFocused(setId: String) = onFocused(setId)

    fun requesterFor(setId: String): FocusRequester? = if (setId == targetRowId) focusRequester else null
}

/** A row's title control reports its own focus back to [focus] — a plain factory here would trip Compose's own "extend `Modifier`, don't return one" lint rule. */
internal fun Modifier.trackedBy(focus: QueueFocus, setId: String): Modifier = onFocusChanged { if (it.isFocused) focus.reportFocused(setId) }
