package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.allSetsById
import stats.StatsUiState
import stats.StatsViewModel
import stats.statsUiStateOf

/**
 * The Stats page both surfaces draw: the chosen profile's read, every set
 * named from [catalogState] — this profile's own catalogue, Kids filter
 * applied — so a title this profile cannot see is never named, as on the
 * web. Joined here because the read and the catalogue live in two feature
 * modules that may not depend on each other. Marks what it shows as seen
 * on this device.
 */
@Composable
fun rememberStatsPage(
    catalogState: CatalogUiState,
    viewModel: StatsViewModel = hiltViewModel(),
): StatsUiState {
    val read by viewModel.state.collectAsStateWithLifecycle()
    // Composed only while a Stats frame is on screen, phone or television: what the page holds
    // is no longer news on this device, and the rail's dot goes out.
    LaunchedEffect(read) { viewModel.markAchievementsSeen() }
    val sets = remember(catalogState) { (catalogState as? CatalogUiState.Ready)?.let { allSetsById(it.shelves) } }
    return remember(read, sets) { statsUiStateOf(read, sets) }
}
