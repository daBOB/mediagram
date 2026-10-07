package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.Department
import catalog.Entry
import catalog.Shelf
import catalog.keyOf
import catalog.latestOf
import model.WatchSnapshot

/** How many of each kind — films, shows, courses — the Latest page holds, matching the web's own `renderLatest`. */
private const val LatestLimit = 48

/**
 * Films, shows and courses, newest arrival first — the television twin of
 * the web's `utility-pages.js#renderLatest`: one wall, not the home page's
 * six-wide rail, so nothing here is cut past a rail's first six the way
 * [PlateRow] on Home cuts a longer row. Headed "Latest" over the web's own
 * "Newest arrivals first", with each kind's
 * own heading before its plates — "Movies"/"Series"/"Tutorials", its
 * department's own label, as the web's own `SECTIONS` table gives it,
 * rather than the "Latest series" wording Home's own band headings read (a
 * department's name said once here is enough; Home repeats "Latest" on each
 * band because they sit apart, this page because they don't).
 *
 * There is nowhere further this page's own plates lead than what opening one
 * already does, so none of them carries a "See all" — unlike Home's rows,
 * which are a window onto a shelf this page already shows in full.
 *
 * [restoreKey] finds its plate across the whole wall, not row by row, the
 * same as every other [TvWall] page; [heldIds] carries the offline badge the
 * same as any other wall of entries.
 */
@Composable
internal fun TvLatestPage(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val sections =
        remember(shelves) {
            val latest = latestOf(shelves, posterLimit = LatestLimit, limit = LatestLimit)
            listOf<Pair<Department, List<Entry>>>(
                Department.MOVIES to latest.movies,
                Department.SERIES to latest.series,
                Department.TUTORIALS to latest.courses,
            )
        }
    val entries = remember(sections) { sections.flatMap { it.second } }
    val headings =
        remember(sections) {
            var offset = 0
            buildMap {
                for ((department, items) in sections) {
                    if (items.isNotEmpty()) put(offset, department.label)
                    offset += items.size
                }
            }
        }

    TvPage {
        TvWall(
            items = entries,
            key = ::keyOf,
            restoreKey = restoreKey,
            onOpen = { entry -> openEntry(entry, onOpenTitle, onOpenCollection) },
            header = { TvShelfHead("Latest", "Newest arrivals first") },
            headings = headings,
            plate = { entry, modifier, onOpen -> TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds) },
        )
    }
}
