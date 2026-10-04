package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.tv.material3.Text
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.SeriesResumePick
import catalog.courseExtentOf
import catalog.rowsOf
import designsystem.TvTypeScale
import model.TitleCredits
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course — the television twin of the phone's
 * `CollectionScreen`: a show's own page ([TvSeriesPage], `series-page.js`)
 * when [collection] is one, else a course's own page ([TvCoursePage],
 * `course-view.js`). Everything past [heldIds] but [resume] and [onResume]
 * is a show's alone.
 *
 * [season]/[onSelectSeason] are the caller's own state, so a show opened
 * again from a title it led to still shows the season that was picked —
 * the phone's own rule.
 *
 * [restoreKey] names what was opened from here — a set's id in the list, or
 * on a show a genre from About, a person from Cast or a similar show from
 * Similar — so coming back lands on it, on whichever tab it belongs to,
 * recomputed fresh each time this page is composed rather than trusted to
 * survive in a plain `rememberSaveable`, since this page is torn down and
 * rebuilt on the way back from a person's or a similar show's own page.
 */
@Composable
fun TvCollection(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    onPlay: (setId: String) -> Unit,
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
    season: String? = null,
    onSelectSeason: (String) -> Unit = {},
    onToggleWatchlist: () -> Unit = {},
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
) {
    if (collection.kind == CollectionKind.SHOW) {
        TvSeriesPage(
            collection, info, watch, onPlay, onOpenGenre, restoreKey, heldIds, credits, onOpenPerson, shouldRequestPortrait,
            fetchPortrait, similar, onOpenCollection, resume, onResume, season, onSelectSeason, onToggleWatchlist,
            editorsChoice, onToggleEditorsChoice,
        )
        return
    }
    TvCoursePage(collection, watch, onPlay, restoreKey, heldIds, resume, onResume)
}

/**
 * A course's own page, as the web's `course-view.js` draws one: the page's
 * shelf head — the course's name, how many lessons and documents it holds,
 * a rule — over its lessons, each folder headed and indented as deep as it
 * sits, the phone's own index.
 *
 * The web's course page has no tabs, art or facts, nor has the phone's
 * tabs: a course carries no provider entry, so an About tab over it stood
 * empty and Cast never appeared. The one thing kept beyond the web's page
 * is the resume line under the head — see `docs/system-architecture.md`,
 * "Television differs from the web player".
 */
@Composable
private fun TvCoursePage(
    collection: Entry.Collection,
    watch: WatchSnapshot,
    onPlay: (setId: String) -> Unit,
    restoreKey: String?,
    heldIds: Set<String>,
    resume: SeriesResumePick?,
    onResume: (setId: String) -> Unit,
) {
    val rows = remember(collection) { rowsOf(collection.divisions) }
    val extent = remember(collection) { courseExtentOf(collection.divisions) }
    TvPage {
        TvCollectionRows(
            rows = rows,
            watch = watch,
            onPlay = onPlay,
            restoreKey = restoreKey,
            header = {
                Column {
                    TvShelfHead(collection.name, extent)
                    resume?.let { pick -> SeriesResumeRow(pick, onResume) }
                }
            },
            heldIds = heldIds,
        )
    }
}

/**
 * One season's episodes — the television twin of the phone's
 * `SeasonScreen`, in the same rows [TvCollectionRows] draws for a whole
 * course: a season is just the one division a search result or another
 * direct link may still open on its own, so it is shown the same way.
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
    onPlay: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val rows = remember(division) { rowsOf(listOf(division)).drop(1) }
    TvPage {
        TvCollectionRows(rows, watch, onPlay, restoreKey, header = { Text(text = division.title, style = TvTypeScale.title) }, heldIds)
    }
}
