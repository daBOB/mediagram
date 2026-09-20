package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import system.SystemUiState
import system.SystemViewModel

/**
 * What the app is actually doing, in four blocks: the installed catalog,
 * the disk cache, what has crossed the wire to Telegram, and whether this
 * device's Telegram session is up.
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
    // Null only for the moment before the core resolves — this screen is
    // reached from inside an already-open library, so that moment does not
    // last long enough to be worth a loading state of its own.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        item { CatalogueBlock(current) }
        item { CacheBlock(current) }
        item { UpstreamBlock(current) }
        item { TelegramBlock(current) }
    }
}

@Composable
private fun CatalogueBlock(state: SystemUiState) = Block(
    heading = "Catalogue",
    rows = listOf(
        "Source" to if (state.origin == "package") "published package" else "this machine",
        "Holds" to "${state.sets} playable sets, ${state.posters} posters",
        // schema is this build's own compiled constant, not a value read
        // back out of the installed catalog — see CatalogFacts' own doc.
        "Schema" to "v${state.schema}, expected by this build",
    ),
)

@Composable
private fun CacheBlock(state: SystemUiState) = Block(
    heading = "Cache",
    rows = listOf(
        "Held" to heldOfBudget(state.fromCacheBytes, CACHE_BUDGET_BYTES),
        // "hits" is a round trip to Telegram that returned bytes, "misses"
        // one that raised instead — PlaybackCounters keeps no separate
        // count of cache-served reads, only the bytes the percentage ahead
        // of them is built from.
        "Reads" to cacheReadsLine(state.fromCacheBytes, state.fromUpstreamBytes, state.fetches, state.failedReads),
    ),
)

@Composable
private fun UpstreamBlock(state: SystemUiState) = Block(
    heading = "Upstream",
    rows = listOf(
        "Since starting" to humanSize(state.fromUpstreamBytes),
        "Failed reads" to if (state.failedReads > 0) "${state.failedReads}" else "none",
    ),
)

@Composable
private fun TelegramBlock(state: SystemUiState) = Block(
    heading = "Telegram",
    rows = listOf("Session" to telegramLine(state.connected)),
)

/** A heading and its label/value rows. A row whose value is null is left out entirely. */
@Composable
private fun Block(heading: String, rows: List<Pair<String, String?>>) {
    Column {
        Text(text = heading, style = MaterialTheme.typography.titleMedium)
        for ((label, value) in rows) {
            if (value.isNullOrEmpty()) continue
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The disk cache's own ceiling, mirrored from core:playback's
 * `CacheProvider` (a 2 GiB `LeastRecentlyUsedCacheEvictor`) rather than
 * imported: a feature module reaches the counters it is given, not the
 * cache instance itself.
 */
private const val CACHE_BUDGET_BYTES = 2L * 1024 * 1024 * 1024
