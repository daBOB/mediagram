package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import stats.HISTORY_HEADING
import stats.LAST_30_DAYS_HEADING
import stats.NOTHING_WATCHED
import stats.STATS_HEADING
import stats.StatsUiState
import ui.common.StatsBars
import ui.tv.TvFocus

private val ChartHeight = 120.dp

/**
 * The chosen profile's own watch time on a television: the phone's
 * `StatsScreen` at a television's sizes, with the same strings
 * `feature:stats` hands both. Nothing on the page opens anything, but the
 * remote still needs somewhere to rest and stepping is what scrolls: the
 * totals, the chart and each history line are stops of their own, and the
 * totals take the remote on arrival. The history is a lazy list, so a
 * profile with years of it composes only what is on screen.
 */
@Composable
internal fun TvStatsPage(state: StatsUiState) {
    val arrival = remember { FocusRequester() }
    val ready = state is StatsUiState.Ready
    LaunchedEffect(ready) { if (ready) runCatching { arrival.requestFocus() } }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "heading") { Text(text = STATS_HEADING, style = TvTypeScale.title) }
        when (state) {
            StatsUiState.Loading -> Unit
            is StatsUiState.Failed -> item(key = "failed") { TvQuietLine(state.text) }
            StatsUiState.Empty -> item(key = "empty") { TvQuietLine(NOTHING_WATCHED) }
            is StatsUiState.Ready -> {
                item(key = "totals") {
                    TvStatsStop(Modifier.focusRequester(arrival)) { focused ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
                                for ((label, value) in state.totals) {
                                    Column {
                                        Text(text = label, style = TvFocus.textStyle(TvTypeScale.body, focused))
                                        Text(text = value, style = TvTypeScale.title)
                                    }
                                }
                            }
                            // Inside the totals' stop, so it is read with the figures it explains.
                            state.countingSince?.let { TvQuietLine(it) }
                        }
                    }
                }
                item(key = "chart") {
                    TvStatsStop { focused ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                            Text(text = LAST_30_DAYS_HEADING, style = TvFocus.textStyle(TvTypeScale.body, focused))
                            StatsBars(
                                bars = state.bars,
                                color = MaterialTheme.colorScheme.primary,
                                labelStyle = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, color = quiet),
                                height = ChartHeight,
                            )
                        }
                    }
                }
                tvAchievementItems(state.achievements)
                item(key = "history") { Text(text = HISTORY_HEADING, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
                items(items = state.history, key = { it.key }) { line ->
                    TvStatsStop { focused -> Text(text = line.text, style = TvFocus.textStyle(TvTypeScale.body, focused)) }
                }
            }
        }
    }
}

/** One stop for the remote on a page with nothing to press: focusable, read as one, and told whether it holds focus so it can wear the focus treatment. */
@Composable
internal fun TvStatsStop(
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier = modifier.onFocusChanged { focused = it.isFocused }.focusable().semantics(mergeDescendants = true) {}) {
        content(focused)
    }
}
