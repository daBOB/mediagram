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
import catalog.HomeRow
import designsystem.Overscan
import model.WatchSnapshot

/**
 * Films, shows and courses, newest arrival first — the television twin of
 * the web's `utility-pages.js` Latest page: exactly the three "Latest …"
 * rows `homeRowsOf` already builds for Home, the caller's own `rows` with
 * everything but those three filtered out (Home already shows the same
 * function; this page differs only in which of its rows it keeps and at
 * what limit, matching phase 2's own "no new function" decision).
 *
 * Arrival focus lands on the first row's first stop, or on [restoreKey]'s
 * plate when it names one still on the page — [TvHome]'s own rule,
 * simplified to a single fixed target rather than that page's own
 * row-arrives-above tracking: this page has no Continue row that can appear
 * above what a viewer is already browsing the way Home's can.
 */
@Composable
internal fun TvLatestPage(
    rows: List<HomeRow>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val first = remember { FocusRequester() }
    val target =
        remember(rows, restoreKey) {
            restoreKey?.let { wanted ->
                rows.withIndex().firstNotNullOfOrNull { (row, content) ->
                    keysOf(content).indexOf(wanted).takeIf { it >= 0 }?.let { row to it }
                }
            } ?: (0 to 0)
        }
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) first.requestFocus() }

    TvPage {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Overscan.horizontal)
                    .padding(bottom = Overscan.vertical),
        ) {
            rows.forEachIndexed { index, row ->
                key(row.title) {
                    TvHomeRow(
                        row = row,
                        positions = positions,
                        watchedIds = watchedIds,
                        onOpenTitle = onOpenTitle,
                        onOpenCollection = onOpenCollection,
                        onSeeAll = {},
                        focusAt = target.second.takeIf { index == target.first },
                        focus = Modifier.focusRequester(first),
                    )
                }
            }
        }
    }
}
