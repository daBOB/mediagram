package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.Entry
import catalog.FranchisePage
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot

/**
 * One franchise's own page — the television twin of the web's
 * `collections-page.js#renderFranchise`: its films in release order, as a
 * plain wall, with TMDB's own introduction to it (when there is one) as the
 * wall's header.
 */
@Composable
internal fun TvFranchisePage(
    page: FranchisePage,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    TvPage {
        TvWall(
            items = page.franchise.films,
            key = MediaSet::setId,
            restoreKey = restoreKey,
            onOpen = { set -> onOpenTitle(set.setId) },
            header = {
                Column {
                    TvCountedHeading(page.franchise.name, page.franchise.films.size)
                    page.overview?.takeIf(String::isNotBlank)?.let { overview ->
                        TvReadableParagraph(overview, modifier = Modifier.padding(top = Spacing.small))
                    }
                }
            },
            plate = { set, modifier, onOpen ->
                TvEntryPlate(Entry.Film(set), positions, watchedIds, onOpen, modifier, heldIds)
            },
        )
    }
}
