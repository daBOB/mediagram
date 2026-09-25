package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import system.SystemUiState
import system.SystemViewModel
import system.cacheRows
import system.catalogueRows
import system.thisAppRows
import system.upstreamRows
import ui.tv.TvTextRow
import ui.tv.catalog.TvPage

/**
 * The phone's System screen on a television: the same four blocks from the
 * same [SystemViewModel] and the same row builders — the installed
 * catalogue, the disk cache, what has crossed the wire to Telegram, and
 * this app itself — in the page's own type, as the web player's status
 * panel sets them.
 *
 * Nothing on it is pressed, so each block is a stop the remote steps
 * through, the first taking it on arrival; a failed read offers the
 * phone's "Try again", which takes the remote instead.
 */
@Composable
internal fun TvSystemScreen() {
    val viewModel: SystemViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    TvSystemContent(state, failure, viewModel::retry)
}

@Composable
internal fun TvSystemContent(
    current: SystemUiState?,
    failure: String?,
    onRetry: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val retry = remember { FocusRequester() }
    LaunchedEffect(failure != null, current != null) {
        when {
            failure != null -> retry.requestFocus()
            current != null -> first.requestFocus()
        }
    }

    // Arriving on the first row leaves the heading where it is rather than
    // scrolling it up into the overscan edge — the rule every catalogue page keeps.
    TvPage {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            Text(text = "System", style = TvTypeScale.title)
            if (failure != null) {
                Column {
                    Text(failure, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error)
                    TvTextRow(text = "Try again", onClick = onRetry, focusRequester = retry)
                }
            }
            if (current != null) {
                TvInfoBlock("Catalogue", catalogueRows(current, System.currentTimeMillis()), Modifier.focusRequester(first), focusable = true)
                TvInfoBlock("Cache", cacheRows(current), focusable = true)
                TvInfoBlock("Upstream", upstreamRows(current), focusable = true)
                TvInfoBlock("This app", thisAppRows(current), focusable = true)
            } else if (failure == null) {
                Text("Reading system information…", style = TvTypeScale.body)
            }
        }
    }
}
