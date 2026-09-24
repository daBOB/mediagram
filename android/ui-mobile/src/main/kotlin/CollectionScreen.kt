package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.Division
import catalog.Entry
import catalog.firstItemOf
import catalog.seasonPlatesOf
import designsystem.Spacing
import model.WatchSnapshot
import model.ageLabel
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course: its seasons or chapters, and the
 * episodes or lessons under them.
 *
 * A show with more than one season is a wall of season plates instead —
 * [SeasonWall] — because a course drills into chapters and a show into
 * seasons, and only the second has artwork of its own to put on a plate.
 * Everything else — a course, and a show with just one season — falls
 * through to the flat, indented list below, the same list a season screen
 * shows for the one season it was opened from ([SeasonScreen]).
 *
 * The nesting is kept rather than flattened. A course runs from one folder
 * deep to four, and flattening it gives a row of headings that each repeat
 * their parents; a viewer reads the indent instead.
 *
 * [info] describes the show or the course itself, not an episode of it —
 * the whole series shares one row in the index, which is why it can be
 * asked for with the key every episode under it carries. A course has
 * neither a provider entry nor artwork, so the block that would describe it
 * is left out entirely and the screen is the name and the tree, as it has
 * always been. An empty block would claim the library looked and found
 * nothing, when the truth is that nobody recorded it.
 */
@Composable
fun CollectionScreen(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    posterPath: suspend (key: String) -> String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenSeason: (Division) -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val seasons = remember(collection, watchedIds) { seasonPlatesOf(collection, watchedIds) }
    if (seasons != null) {
        SeasonWall(collection, info, seasons, posterPath, onOpenSeason, onOpenGenre)
        return
    }

    // Flattened once per collection, not on every recomposition: the depth
    // becomes an indent here because a lazy list cannot nest, and a viewer
    // still has to see which folder holds what.
    val rows = remember(collection) { rowsOf(collection.divisions) }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item {
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
            item(key = "header") {
                TitleHeader(
                    posterPath = collection.posterPath,
                    title = collection.name,
                    // A show is not a file: it has no one year and no one
                    // runtime, and the seasons below already say how much
                    // of it there is. Its age rating is the show's own.
                    facts = age,
                    info = info,
                    genres = genres,
                    onOpenGenre = onOpenGenre,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        items(rows, positions, watchedIds, onOpenTitle)
    }
}
