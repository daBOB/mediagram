package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowWidthSizeClass
import designsystem.Spacing
import stats.HISTORY_HEADING
import stats.LAST_30_DAYS_HEADING
import stats.NOTHING_WATCHED
import stats.STATS_HEADING
import stats.StatsUiState
import ui.StatsBars

/**
 * The chosen profile's own watch time, the web's Stats page: this week,
 * this month and all time, a bar per day for the last thirty, then every
 * start, finish and rewatch, newest first. Every string arrives finished
 * from `feature:stats`, so the television's page cannot word a line
 * differently. The weekday letters under the bars need an EXPANDED width.
 */
@Composable
internal fun StatsScreen(state: StatsUiState) {
    val wide = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "heading") { Text(text = STATS_HEADING, style = MaterialTheme.typography.headlineSmall) }
        when (state) {
            StatsUiState.Loading -> Unit
            is StatsUiState.Failed -> item(key = "failed") { Text(text = state.text, color = quiet) }
            StatsUiState.Empty -> item(key = "empty") { Text(text = NOTHING_WATCHED, color = quiet) }
            is StatsUiState.Ready -> {
                item(key = "totals") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                        for ((label, value) in state.totals) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = label, style = MaterialTheme.typography.labelMedium, color = quiet)
                                Text(text = value, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
                state.countingSince?.let { since -> item(key = "since") { Text(text = since, style = MaterialTheme.typography.bodyMedium, color = quiet) } }
                item(key = "chart") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Text(text = LAST_30_DAYS_HEADING, style = MaterialTheme.typography.titleMedium)
                        StatsBars(
                            bars = state.bars,
                            color = MaterialTheme.colorScheme.primary,
                            labelStyle = if (wide) MaterialTheme.typography.labelSmall.copy(color = quiet) else null,
                        )
                    }
                }
                achievementItems(state.achievements)
                item(key = "history") { Text(text = HISTORY_HEADING, style = MaterialTheme.typography.titleMedium) }
                items(items = state.history, key = { it.key }) { line ->
                    Text(text = line.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
