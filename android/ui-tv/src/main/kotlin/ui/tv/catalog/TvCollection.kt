package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import catalog.Division
import catalog.Entry
import catalog.SeasonPlate
import catalog.SeriesResumePick
import catalog.firstItemOf
import catalog.ratingLabel
import catalog.rowsOf
import catalog.seasonPlatesOf
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.TitleCredits
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course — the television twin of the phone's
 * `CollectionScreen` and the web's series page: Episodes (a season wall,
 * [seasonPlatesOf]'s rule, or [TvCollectionRows]' flat list — a course, and
 * a show of just one season), About, Cast (only once [credits] names
 * somebody) and Similar (only once [similar] holds a show), the same split
 * [TvTitlePage] makes for a film.
 *
 * The header — name, art, facts, genre links, overview, and the
 * [SeriesResumePick] pill — sits on the Episodes tab, above the wall or the
 * rows, the same place the film page's own header sits on its Overview tab:
 * neither repeats it on the tabs beside it, which show only their own body.
 *
 * [restoreKey] names what was opened from here — a season's title on the
 * wall, a set's id in the list, a genre from the header's links, a person
 * from Cast, or a similar show from Similar — so coming back lands on it,
 * on whichever tab it belongs to: [TvTitlePage]'s own rule, recomputed fresh
 * each time this page is composed rather than trusted to survive in a plain
 * `rememberSaveable`, since this page is torn down and rebuilt on the way
 * back from a person's or a similar show's own page.
 */
@Composable
fun TvCollection(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    posterPath: suspend (key: String) -> String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenSeason: (Division) -> Unit,
    onOpenGenre: (String) -> Unit = {},
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
    credits: TitleCredits = TitleCredits.Empty,
    onOpenPerson: (personId: Long) -> Unit = {},
    shouldRequestPortrait: (Long) -> Boolean = { false },
    fetchPortrait: suspend (Long) -> String? = { null },
    similar: List<Entry.Collection> = emptyList(),
    onOpenCollection: (key: String) -> Unit = {},
    resume: SeriesResumePick? = null,
    onResume: (setId: String) -> Unit = {},
) {
    val watchedIds = rememberWatchMarks(watch).watchedIds
    val seasons = remember(collection, watchedIds) { seasonPlatesOf(collection, watchedIds) }
    val genres = remember(collection) { firstItemOf(collection.divisions)?.genres.orEmpty() }
    val genreFocus = restoreKey?.takeIf { it in genres }
    val tabs =
        remember(credits, similar) {
            buildList {
                add("Episodes")
                add("About")
                if (credits.cast.isNotEmpty()) add("Cast")
                if (similar.isNotEmpty()) add("Similar")
            }
        }
    val initialTab =
        remember(tabs, credits, similar, restoreKey) {
            when {
                restoreKey == null -> 0
                credits.cast.any { it.personId.toString() == restoreKey } -> tabs.indexOf("Cast")
                similar.any { it.key == restoreKey } -> tabs.indexOf("Similar")
                else -> 0
            }
        }
    var selected by rememberSaveable(collection.key) { mutableIntStateOf(initialTab) }
    if (selected >= tabs.size) selected = 0

    val header: @Composable () -> Unit = {
        Column {
            CollectionHeader(collection, info, onOpenGenre, genreFocus)
            resume?.let { pick -> SeriesResumeRow(pick, onResume) }
        }
    }
    TvPage(takesArrivalFocus = selected == 0 && genreFocus == null) {
        // No overscan padding of its own on this outer Column: `TvWall` and
        // `TvCollectionRows` already carry their own top/bottom overscan as
        // `contentPadding`, unchanged from before — adding it here too would
        // double the gap above the Episodes tab's own content. Only the tab
        // row, which sits above that content rather than inside it, needs
        // its own top inset.
        Column(modifier = Modifier.fillMaxSize()) {
            TvSectionTabs(
                titles = tabs,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Overscan.vertical),
            )
            when (tabs[selected]) {
                "About" -> TvTabBody { TvSeriesAbout(info) }

                "Cast" -> TvTabBody { TvCastRow(credits, onOpenPerson, shouldRequestPortrait, fetchPortrait, restoreKey) }

                "Similar" -> TvTabBody { TvSimilarShows(similar, onOpenCollection, restoreKey) }

                else ->
                    if (seasons != null) {
                        TvWall(
                            items = seasons,
                            key = SeasonPlate::title,
                            restoreKey = restoreKey,
                            onOpen = { plate -> onOpenSeason(plate.division) },
                            header = header,
                            plate = { plate, modifier, onOpen -> TvSeasonPlate(collection, plate, posterPath, onOpen, modifier) },
                        )
                    } else {
                        val rows = remember(collection) { rowsOf(collection.divisions) }
                        TvCollectionRows(rows, watch, onOpenTitle, restoreKey, header, heldIds)
                    }
            }
        }
    }
}

/** About: the provider's rating, network and status — a show's counterpart to the film page's Details tab, minus the editor's-choice toggle, which is a film's own pin. */
@Composable
private fun TvSeriesAbout(info: TitleInfo?) {
    Column {
        ratingLabel(info?.rating)?.let { Text(text = it, style = TvTypeScale.body) }
        info?.network?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        info?.status?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
    }
}

/**
 * One season's episodes — the television twin of the phone's
 * `SeasonScreen`, in the same rows [TvCollectionRows] draws for a whole
 * show or course: a season is just the one division the wall's plate stood
 * for, so it is shown the same way.
 *
 * Headed with the season's title at the size every other page's name
 * takes, as the phone's bar names it. The rows' own heading for the season
 * is left out under it: the same words twice, one above the other, with
 * nothing between them.
 */
@Composable
fun TvSeason(
    division: Division,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val rows = remember(division) { rowsOf(listOf(division)).drop(1) }
    TvPage {
        TvCollectionRows(rows, watch, onOpenTitle, restoreKey, header = { Text(text = division.title, style = TvTypeScale.title) }, heldIds)
    }
}
