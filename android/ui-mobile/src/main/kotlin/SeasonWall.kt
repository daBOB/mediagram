package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import catalog.Division
import catalog.Entry
import catalog.SeasonPlate
import catalog.firstItemOf
import designsystem.Spacing
import model.WatchSnapshot
import model.ageLabel
import uniffi.mediagram_core.TitleInfo

/**
 * A show's seasons, one plate each, above the collection's own header.
 *
 * A plate's artwork falls back in three steps — its season's poster, then
 * the show's, then the initials [PosterCard] draws on its own — which is
 * why [seasonPoster] only ever supplies the first of those and leaves the
 * rest to the `?:` and to the card.
 */
@Composable
internal fun SeasonWall(
    collection: Entry.Collection,
    info: TitleInfo?,
    plates: List<SeasonPlate>,
    posterPath: suspend (key: String) -> String?,
    onOpenSeason: (Division) -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.medium),
            )
        }
        // A show is rated as a show, so any episode speaks for it — the
        // first, as `series-header.js` asks. A course has no rating.
        val firstEpisode = firstItemOf(collection.divisions)
        val age = firstEpisode?.ageLabel()
        val genres = firstEpisode?.genres ?: emptyList()
        if (info != null || collection.posterPath != null || age != null || genres.isNotEmpty()) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                TitleHeader(
                    posterPath = collection.posterPath,
                    title = collection.name,
                    facts = age,
                    info = info,
                    genres = genres,
                    onOpenGenre = onOpenGenre,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        items(items = plates, key = { it.title }) { plate ->
            val seasonPoster = rememberPosterPath(plate.posterKey, posterPath)
            PosterCard(
                posterPath = seasonPoster ?: collection.posterPath,
                title = plate.title,
                caption = plate.caption,
                watched = plate.watched,
                modifier = Modifier,
                onClick = { onOpenSeason(plate.division) },
            )
        }
    }
}

/**
 * One season's episodes, in the same rows [CollectionScreen] draws for a
 * whole show or course — a season is just the one division the wall's
 * plate stood for, so it is shown the same way.
 */
@Composable
internal fun SeasonScreen(division: Division, watch: WatchSnapshot, onOpenTitle: (setId: String) -> Unit) {
    val rows = remember(division) { rowsOf(listOf(division)) }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        items(rows, positions, watchedIds, onOpenTitle)
    }
}

/**
 * The local file for a poster key, looked up once per key.
 *
 * The same shape as [rememberTitleInfo], generalised to artwork: a wall
 * opens several of these at once, one per plate, where a title screen only
 * ever asks for one synopsis.
 */
@Composable
private fun rememberPosterPath(key: String?, lookup: suspend (String) -> String?): String? {
    var path by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) { path = key?.let { lookup(it) } }
    return path
}
