package ui.catalog

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.Entry
import catalog.rowsOf
import model.MediaSet
import model.WatchSnapshot
import ui.common.catalog.TitlePreloadUi

/**
 * The Episodes tab on a show's own page: a row of season cards
 * ([SeasonCards]) over a readable list of that season's episodes — a Compose
 * port of `series-page.js`'s own `episodes()`. No row at all for a
 * single-season show: there is nothing to pick from.
 *
 * A [LazyListScope] extension rather than its own scrollable composable: a
 * show's episode list can run to a few hundred rows, and nesting a second
 * lazily-scrolled list inside the series page's own would either crash on
 * unbounded height or clip to whichever bound won. Its rows join the page's
 * own [androidx.compose.foundation.lazy.LazyColumn] instead, the same way
 * [items] already does for a course.
 *
 * [season]/[onSelectSeason] are the caller's own [androidx.compose.runtime.saveable.rememberSaveable]
 * state rather than this function's own: the same season must still be
 * shown once a watch-state update recomposes the whole series page, as
 * the chosen tab is ([rememberChosenTab]).
 *
 * [modifier] goes on every item this adds: the Episodes panel's rise.
 * [preloadOf] gives each episode row its own Preload control.
 */
internal fun LazyListScope.seriesEpisodes(
    collection: Entry.Collection,
    season: String?,
    onSelectSeason: (String) -> Unit,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
    modifier: Modifier = Modifier,
    preloadOf: (@Composable (MediaSet) -> TitlePreloadUi?)? = null,
) {
    val shown = collection.divisions.find { it.title == season } ?: collection.divisions.firstOrNull() ?: return
    val positions = watch.progress.associateBy { it.setId }
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
    if (collection.divisions.size > 1) {
        item(key = "season-picker") { SeasonCards(collection.divisions, shown, collection.posterPath, watchedIds, onSelectSeason, modifier) }
    }
    val rows = rowsOf(listOf(shown))
    items(rows, positions, watchedIds, heldIds, onPlay, modifier, preloadOf)
}
