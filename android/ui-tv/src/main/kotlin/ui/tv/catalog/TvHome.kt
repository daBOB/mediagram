package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import catalog.HomeRow
import designsystem.Overscan
import designsystem.Spacing
import model.WatchSnapshot

/**
 * The start page — the television twin of the phone's `HomeScreen`: what
 * was already underway, and what arrived recently, as [rows] from
 * `homeRowsOf` with the same arguments the phone passes it.
 *
 * The rows stack down the page and the page scrolls, never a row: see
 * [TvHomeRow]. The first row's first stop takes focus the moment the page
 * appears, as every wall on this surface does, so the remote is never left
 * resting on nothing.
 *
 * The overscan inset is padding inside the scroll rather than around it, so
 * a focused plate at the edge grows into space the page reserves for it
 * instead of being clipped — the reason `TvWall` passes it as
 * `contentPadding`.
 */
@Composable
internal fun TvHome(
    rows: List<HomeRow>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (rows.isNotEmpty()) first.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // Coming down from the masthead lands where the viewer last
                // was on this page, or on its first stop, rather than on
                // whichever plate happens to sit under the tab the remote
                // left from.
                .focusRestorer(first)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.horizontal)
                .padding(bottom = Overscan.vertical, top = Spacing.small),
    ) {
        rows.forEachIndexed { index, row ->
            // Keyed by title, so a row that appears above — Continue, the
            // moment something is started — does not hand this row's
            // plates, and the focus on one of them, to a different row.
            key(row.title) {
                TvHomeRow(
                    row = row,
                    positions = positions,
                    watchedIds = watchedIds,
                    onOpenTitle = onOpenTitle,
                    onOpenCollection = onOpenCollection,
                    onSeeAll = onSeeAll,
                    firstItem = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}
