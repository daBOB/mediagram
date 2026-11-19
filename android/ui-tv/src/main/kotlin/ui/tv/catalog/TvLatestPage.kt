package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import catalog.Department
import catalog.Entry
import catalog.Latest
import catalog.keyOf
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import model.WatchSnapshot

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
 *
 * Documentaries come third, plates that play ([onPlay]) as on the
 * Documentaries page, since a documentary has no page of its own. Under the
 * heading, a pill per part — the web's `jumpNav` — scrolls the wall to that
 * part's heading and hands the remote to its first plate, so a long wall of
 * films is one press from the documentaries below it.
 */
@Composable
internal fun TvLatestPage(
    latest: Latest,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
    onPlay: (setId: String) -> Unit = onOpenTitle,
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val sections =
        remember(latest) {
            listOf<Pair<Department, List<Entry>>>(
                Department.MOVIES to latest.movies,
                Department.SERIES to latest.series,
                Department.DOCUMENTARIES to latest.documentaries.map(Entry::Film),
                Department.TUTORIALS to latest.courses,
            )
        }
    val entries = remember(sections) { sections.flatMap { it.second } }
    val documentaries = remember(latest) { latest.documentaries.mapTo(HashSet()) { it.setId } }
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
    // Each part's first plate, by key, and the requester the jump hands the remote to.
    val firsts = remember(sections) { sections.filter { it.second.isNotEmpty() }.associate { (department, items) -> department.label to keyOf(items.first()) } }
    val jumpTargets = remember(firsts) { firsts.values.associateWith { FocusRequester() } }
    val cells = remember(entries, headings) { cellsOf(entries, hasHeader = true, headings) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val jump: (String) -> Unit = { label ->
        scope.launch {
            val heading = cells.indexOf(WallCell.Heading(label)).coerceAtLeast(0)
            grid.scrollToItem(heading)
            // The plate under the heading has a node to focus only once the grid has laid it out.
            snapshotFlow { grid.layoutInfo.visibleItemsInfo }.first { shown -> shown.any { it.index == heading + 1 } }
            jumpTargets.getValue(firsts.getValue(label)).requestFocus()
        }
    }

    TvPage {
        TvWall(
            items = entries,
            key = ::keyOf,
            restoreKey = restoreKey,
            onOpen = { entry ->
                if (entry is Entry.Film && entry.set.setId in documentaries) onPlay(entry.set.setId) else openEntry(entry, onOpenTitle, onOpenCollection)
            },
            header = {
                Column {
                    TvShelfHead("Latest", "Newest arrivals first")
                    if (firsts.size > 1) {
                        Row(modifier = Modifier.padding(top = Spacing.medium), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                            for (label in firsts.keys) TvPagePill(label, onClick = { jump(label) })
                        }
                    }
                }
            },
            headings = headings,
            gridState = grid,
            plate = { entry, modifier, onOpen ->
                val target = jumpTargets[keyOf(entry)]
                TvEntryPlate(entry, positions, watchedIds, onOpen, if (target != null) modifier.focusRequester(target) else modifier, heldIds)
            },
        )
    }
}
