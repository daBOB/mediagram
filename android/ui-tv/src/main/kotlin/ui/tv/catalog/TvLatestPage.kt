package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.RowContent
import catalog.Shelf
import catalog.homeRowsOf
import catalog.keyOf
import model.WatchSnapshot

/** How many of each kind — films, shows, courses — the Latest page holds, matching the web's own `renderLatest`. */
private const val LatestLimit = 48

/**
 * Films, shows and courses, newest arrival first — the television twin of
 * the web's `utility-pages.js#renderLatest`: one wall, not the home page's
 * six-wide rail, so nothing here is cut past a rail's first six the way
 * [PlateRow] on Home cuts a longer row. Headed "Latest", with each kind's
 * own heading before its plates — "Movies"/"Series"/"Tutorials", the same
 * label [catalog.HomeRow.seeAll] carries for that shelf and the web's own
 * `SECTIONS` table gives it, rather than the "Latest films" wording Home's
 * own row heading reads (a shelf name said once here is enough; Home repeats
 * "Latest" on each of its three rows because they sit apart, this page
 * because they don't).
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
    val rows =
        remember(shelves, watch, heldIds) {
            homeRowsOf(shelves, watch, heldIds, limit = LatestLimit).filter { it.content is RowContent.Entries }
        }
    val entries = remember(rows) { rows.flatMap { (it.content as RowContent.Entries).entries } }
    val headings =
        remember(rows) {
            var offset = 0
            buildMap {
                for (row in rows) {
                    val count = (row.content as RowContent.Entries).entries.size
                    if (count > 0) put(offset, row.seeAll ?: row.title)
                    offset += count
                }
            }
        }

    TvPage {
        TvWall(
            items = entries,
            key = ::keyOf,
            restoreKey = restoreKey,
            onOpen = { entry -> openEntry(entry, onOpenTitle, onOpenCollection) },
            header = { TvCountedHeading("Latest", entries.size) },
            headings = headings,
            plate = { entry, modifier, onOpen -> TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds) },
        )
    }
}
