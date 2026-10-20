package ui.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.CollectionKind
import catalog.Entry
import catalog.Shelf
import data.PortraitRequestLog
import model.MediaSet
import model.TitleCredits
import model.WatchSnapshot
import ui.common.catalog.TitlePreloadUi
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course: a show's own feature-article page
 * ([SeriesPage]) when [collection] is one, else a course's index
 * ([CoursePage]) — its chapters and lessons, flattened once and shown as
 * one indented list.
 *
 * Parameters past [onOpenGenre] default to inert values so a test composes
 * the page with only what it checks; `LibraryTitleBranches` wires every
 * one — the same rule as [TitleDetailScreen]'s on the film side.
 * [state] is the page's list, hoisted as a department page's is.
 * [episodePreload] gives a show's episode rows their Preload control; a
 * course's lessons have none.
 */
@Composable
fun CollectionScreen(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    shelves: List<Shelf> = emptyList(),
    onOpenCollection: (key: String) -> Unit = {},
    onOpenPerson: (Long) -> Unit = {},
    onPlay: (setId: String) -> Unit = {},
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
    onToggleWatchlist: () -> Unit = {},
    titleCredits: suspend (String) -> TitleCredits = { TitleCredits.Empty },
    fetchPortrait: suspend (Long) -> String? = { null },
    portraits: PortraitRequestLog = PortraitRequestLog(),
    season: String? = null,
    onSelectSeason: (String) -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    episodePreload: (@Composable (MediaSet) -> TitlePreloadUi?)? = null,
    playOnTv: PlayOnTv? = null,
) {
    if (collection.kind == CollectionKind.SHOW) {
        SeriesPage(
            collection, info, watch, heldIds, onOpenTitle, onOpenGenre, shelves, onOpenCollection,
            onOpenPerson, onPlay, editorsChoice, onToggleEditorsChoice, onToggleWatchlist,
            titleCredits, fetchPortrait, portraits, season, onSelectSeason, state, episodePreload, playOnTv,
        )
        return
    }
    CoursePage(collection, watch, heldIds, onPlay, state)
}
