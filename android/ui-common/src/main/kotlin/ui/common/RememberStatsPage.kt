package ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import stats.StatsUiState
import stats.StatsViewModel
import stats.statsUiStateOf

/** The Stats page both surfaces draw, from the chosen profile's read. Marks what it shows as seen on this device. */
@Composable
fun rememberStatsPage(viewModel: StatsViewModel = hiltViewModel()): StatsUiState {
    val read by viewModel.state.collectAsStateWithLifecycle()
    // Composed only while a Stats frame is on screen, phone or television: what the page holds
    // is no longer news on this device, and the rail's dot goes out.
    LaunchedEffect(read) { viewModel.markAchievementsSeen() }
    return remember(read) { statsUiStateOf(read) }
}
