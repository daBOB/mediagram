package ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import kotlinx.coroutines.delay
import system.SystemUiState
import system.SystemViewModel
import system.cacheRows
import system.catalogueRows
import system.thisAppRows
import system.upstreamRows
import ui.components.Block
import ui.settings.SettingsColumns

/** How often System re-reads while its page stays on screen — the web's own `status-lines.js:14` poll, matched rather than left a per-visit snapshot. */
private const val POLL_INTERVAL_MS = 2_000L

/**
 * What the app is actually doing, in four blocks: the installed catalog,
 * the disk cache, what has crossed the wire to Telegram, and this app
 * itself — its version, its Telegram session, and how long it has been
 * running.
 *
 * Rows are label-and-value pairs set in the app's own type, not a
 * monospace grid — the same choice the web player's status panel makes,
 * for the same reason its own comment gives: a catalogue is a printed
 * thing, and a telemetry table dropped into it reads as somebody else's
 * tool bolted on.
 *
 * There is no Conversion block. The web has one because it transcodes on
 * the way out to a browser; this app decodes natively into ExoPlayer and
 * never will, so there is nothing here for a conversion block to report —
 * its absence is not an oversight.
 */
@Composable
internal fun SystemScreen(expanded: Boolean) {
    val viewModel: SystemViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    // Re-reads while this page stays composed — left once this section is
    // no longer on screen, the same `WhileSubscribed` window that already
    // drops the read 5s after the last collector leaves.
    LaunchedEffect(Unit) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            viewModel.retry()
        }
    }
    SystemContent(expanded, state, failure, viewModel::retry)
}

/** A failed read leaves earlier facts visible and offers the same retry on a first visit. No own padding: [ui.settings.SettingsPage] already supplies it. */
@Composable
internal fun SystemContent(
    expanded: Boolean,
    current: SystemUiState?,
    failure: String?,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        if (failure != null) {
            Column {
                Text(failure, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
        when {
            current != null ->
                SettingsColumns(
                    expanded = expanded,
                    columns =
                        listOf(
                            {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
                                    CatalogueBlock(current)
                                    ThisAppBlock(current)
                                }
                            },
                            { CacheBlock(current) },
                            { UpstreamBlock(current) },
                        ),
                )

            failure == null -> Text("Reading system information…")
        }
    }
}

@Composable
private fun CatalogueBlock(state: SystemUiState) = Block(heading = "Catalogue", rows = catalogueRows(state, System.currentTimeMillis()))

@Composable
private fun CacheBlock(state: SystemUiState) = Block(heading = "Cache", rows = cacheRows(state))

@Composable
private fun UpstreamBlock(state: SystemUiState) = Block(heading = "Upstream", rows = upstreamRows(state))

@Composable
private fun ThisAppBlock(state: SystemUiState) = Block(heading = "This app", rows = thisAppRows(state))
