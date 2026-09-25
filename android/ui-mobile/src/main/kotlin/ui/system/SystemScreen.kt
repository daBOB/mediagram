package ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import system.SystemUiState
import system.SystemViewModel
import ui.components.Block
import ui.formatting.heldOfBudget
import ui.formatting.humanSize

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
fun SystemScreen() {
    val viewModel: SystemViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    SystemContent(state, failure, viewModel::retry)
}

/** A failed read leaves earlier facts visible and offers the same retry on a first visit. */
@Composable
internal fun SystemContent(
    current: SystemUiState?,
    failure: String?,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        if (failure != null) {
            item {
                Column {
                    Text(failure, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }
        if (current != null) {
            item { CatalogueBlock(current) }
            item { CacheBlock(current) }
            item { UpstreamBlock(current) }
            item { ThisAppBlock(current) }
        } else if (failure == null) {
            item { Text("Reading system information…") }
        }
    }
}

@Composable
private fun CatalogueBlock(state: SystemUiState) =
    Block(
        heading = "Catalogue",
        rows =
            listOf(
                // Not "this machine" for the other case, which is what the web
                // player says: nothing here is ever assembled on the device it is
                // read on. Every catalogue this app holds was pushed to a channel
                // and pulled back down — which is what the Refresh row two lines
                // below is reporting the age of.
                "Source" to
                    when (state.origin) {
                        "package" -> "published package"
                        "channel" -> "the library's channel"
                        else -> "nothing installed yet"
                    },
                "Holds" to "${state.sets} playable sets, ${state.posters} posters",
                // Read against the wall clock at the moment this block is composed
                // rather than when the facts were taken: the state is re-read on
                // every visit to this screen, so the two are the same moment, and a
                // clock carried inside the state would be a second thing to keep
                // current.
                "Refresh" to refreshLine(state.publishedAt, state.lastRefresh, System.currentTimeMillis()),
                // schema is this build's own compiled constant, not a value read
                // back out of the installed catalog — see CatalogFacts' own doc.
                "Schema" to "v${state.schema}, expected by this build",
            ),
    )

@Composable
private fun CacheBlock(state: SystemUiState) = Block(heading = "Cache", rows = cacheRows(state))

/**
 * What the Cache block says, as label-and-value pairs.
 *
 * Pure and `internal` so a test pins the counters this screen hands over and
 * not only the sentence they are handed to. For as long as the sentence
 * alone was tested, these rows called round trips to Telegram "hits" and
 * reads that raised "misses", and printed the second of those twice on one
 * screen under two names, with nothing able to see it.
 */
internal fun cacheRows(state: SystemUiState): List<Pair<String, String?>> =
    listOf(
        "Held" to heldOfBudget(state.heldBytes, state.budgetBytes),
        "Where" to cacheWhereLine(state.volumeLabel, state.fellBack),
        "Reads" to cacheReadsLine(state.fromCacheBytes, state.fromUpstreamBytes, state.fetches),
    )

@Composable
private fun UpstreamBlock(state: SystemUiState) = Block(heading = "Upstream", rows = upstreamRows(state))

/** What the Upstream block says. Pure for the reason [cacheRows] is, and pinned by the same test. */
internal fun upstreamRows(state: SystemUiState): List<Pair<String, String?>> =
    listOf(
        "Since starting" to humanSize(state.fromUpstreamBytes),
        "Failed reads" to if (state.failedReads > 0) "${state.failedReads}" else "none",
    )

@Composable
private fun ThisAppBlock(state: SystemUiState) =
    Block(
        heading = "This app",
        rows =
            listOf(
                "Version" to state.versionName,
                "Telegram" to telegramLine(state.connected),
                "Uptime" to uptimeLine(state.uptimeSeconds),
            ),
    )
