package player

import data.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.MediaSet
import model.WatchSnapshot

/**
 * [episodeListOf], kept current: the open title, its run as the up-next
 * state carries it, the catalogue's sets and the watch snapshot — any of
 * them moving re-derives the list, so a title finished here or synced in
 * from another device is ticked without the sidebar being reopened.
 *
 * Only the run is read off [upNext], and only when it changes: the rest of
 * that state moves every second of a countdown, and the list has nothing
 * to redo for any of it.
 *
 * The catalogue is read whole, once, rather than a set at a time: a course
 * runs to 162 lessons, and one listing is one crossing into the core where
 * per-row lookups would be one each. It is read again only for a run
 * holding an id the last listing lacked — a library refreshed while the
 * player was open. A failed read lists every row as unknown rather than
 * breaking the player.
 */
internal fun CoroutineScope.episodeListFlow(
    catalogRepository: CatalogRepository,
    openSetId: StateFlow<String?>,
    upNext: StateFlow<UpNextUiState>,
    watch: StateFlow<WatchSnapshot>,
): StateFlow<EpisodeList?> {
    val run = upNext.map { it.run }.distinctUntilChanged()
    val sets = MutableStateFlow<Map<String, MediaSet>>(emptyMap())
    launch {
        run.collectLatest { ids ->
            if (ids.any { it !in sets.value }) {
                sets.value = safely(emptyList()) { catalogRepository.sets() }.associateBy(MediaSet::setId)
            }
        }
    }
    return combine(openSetId, run, sets, watch) { open, ids, known, snapshot ->
        open?.let { episodeListOf(it, ids, known, snapshot) }
    }.stateIn(this, SharingStarted.Eagerly, null)
}
