package ui.tv.system

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.TvTypeScale
import kotlinx.coroutines.delay
import system.SystemUiState
import system.cacheRows
import system.catalogueRows
import system.thisAppRows
import system.upstreamRows
import ui.tv.TvTextRow
import ui.tv.settings.TvInfoBlock

/** How often System re-reads while its section stays shown — the web's own `status-lines.js:14` poll, matched rather than left a per-visit snapshot. */
private const val POLL_INTERVAL_MS = 2_000L

/**
 * Settings › System, television-side: the same four blocks from the same
 * [system.SystemViewModel] and the same row builders the phone's System
 * screen draws — the installed catalogue, the disk cache, what has crossed
 * the wire to Telegram, and this app itself. No page or title of its own:
 * [TvSettingsPanes] already draws the page head every section shares.
 *
 * Re-reads every two seconds for as long as this section stays the one
 * shown — this composable is only ever composed then, so the poll starts
 * and stops with it, the same "left once this section is no longer on
 * screen" rule the phone's own poll keeps.
 *
 * Nothing on it is pressed, so each block is a stop the remote steps
 * through; [entryFocusRequester] only actually takes the remote once
 * [focusInContent] says this section was entered, not merely selected, and
 * a failed read offers "Try again" instead — but only the once, landing on
 * whichever of the two is showing right after entry. [landed] guards that:
 * without it, every later poll would fire this effect again (its own
 * [failure] and [current] flip on a schedule this composable does not
 * control), pulling focus back off a row the viewer had since moved to.
 */
@Composable
internal fun TvSystemSection(
    focusInContent: Boolean,
    current: SystemUiState?,
    failure: String?,
    entryFocusRequester: FocusRequester,
    onRetry: () -> Unit,
) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            onRetry()
        }
    }
    val retry = remember { FocusRequester() }
    var landed by remember(focusInContent) { mutableStateOf(false) }
    LaunchedEffect(focusInContent, failure != null, current != null) {
        if (!focusInContent || landed) return@LaunchedEffect
        when {
            current == null && failure != null -> retry.requestFocus()
            current != null -> entryFocusRequester.requestFocus()
            else -> return@LaunchedEffect
        }
        landed = true
    }

    if (failure != null) {
        Column {
            Text(failure, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error)
            TvTextRow(text = "Try again", onClick = onRetry, focusRequester = retry)
        }
    }
    if (current != null) {
        TvInfoBlock("Catalogue", catalogueRows(current, System.currentTimeMillis()), Modifier.focusRequester(entryFocusRequester), focusable = true)
        TvInfoBlock("Cache", cacheRows(current), focusable = true)
        TvInfoBlock("Upstream", upstreamRows(current), focusable = true)
        TvInfoBlock("This app", thisAppRows(current), focusable = true)
    } else if (failure == null) {
        Text("Reading system information…", style = TvTypeScale.body)
    }
}
