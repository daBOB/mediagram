package player

import data.CatalogRepository
import data.orDefault
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
 * A hand-built list ([runIsList]: My List, the Kids wall) gets none: it
 * mixes shows, and a row picked from a show's sidebar would open with the
 * list as its queue — the web passes no collection for a list either.
 *
 * Only the run is read off [upNext], and only when it changes: the rest of
 * that state moves every second of a countdown.
 *
 * The catalogue is read whole, once per run that holds a title the last
 * read lacked, and only the run's own titles are kept — a course runs to
 * 162 lessons, the library to ten thousand titles. There is no list until
 * that read has landed, so no row reads "Unknown title" for want of an
 * answer, and a film never shows one. A failed read keeps what was loaded
 * before it; with nothing loaded there is no list, as for a film.
 */
internal fun CoroutineScope.episodeListFlow(
    catalogRepository: CatalogRepository,
    openSetId: StateFlow<String?>,
    upNext: StateFlow<UpNextUiState>,
    watch: StateFlow<WatchSnapshot>,
    runIsList: StateFlow<Boolean>,
): StateFlow<EpisodeList?> {
    val run = upNext.map { it.run }.distinctUntilChanged()
    // The sets of one specific run: a list is only built for the run its sets were read for.
    val loaded = MutableStateFlow<Pair<List<String>, Map<String, MediaSet>>?>(null)
    launch {
        run.collectLatest { ids ->
            val before = loaded.value?.second
            loaded.value = when {
                ids.isEmpty() -> null
                before != null && ids.all { it in before } -> ids to before
                else -> orDefault(null) { catalogRepository.sets() }?.let { ids to runSetsOf(it, ids) } ?: before?.let { ids to it }
            }
        }
    }
    return combine(openSetId, run, loaded, watch, runIsList) { open, ids, read, snapshot, isList ->
        val known = read?.takeIf { it.first == ids }?.second
        if (open == null || isList || known == null) null else episodeListOf(open, ids, known, snapshot)
    }.stateIn(this, SharingStarted.Eagerly, null)
}

/** Just [ids]' own titles out of the whole [catalogue]. */
internal fun runSetsOf(catalogue: List<MediaSet>, ids: List<String>): Map<String, MediaSet> {
    val wanted = ids.toHashSet()
    return catalogue.filter { it.setId in wanted }.associateBy(MediaSet::setId)
}
