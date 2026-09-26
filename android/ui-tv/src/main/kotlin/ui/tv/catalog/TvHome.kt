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
import catalog.MagazineHome
import designsystem.Overscan
import designsystem.Spacing
import model.WatchSnapshot

/**
 * The start page — the television twin of the phone's `HomeScreen`: what
 * was already underway, and what arrived recently, as [rows] from
 * `homeRowsOf` with the same arguments the phone passes it.
 *
 * [restoreKey] names the stop a viewer opened, so coming back lands the
 * remote on it rather than at the top; with none, or one no row holds any
 * more, the first row's first stop takes it. A title on two rows at once —
 * underway, and also among the latest — is found on the upper one.
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
 *
 * [magazine] is Home's own editorial header — the cover story, the three
 * feature cards and the magazine's own "Recently added" row (in place of the
 * plain grid's "Latest films", which the caller drops from [rows] once it
 * passes a [magazine] — the same film shelf shown once rather than twice).
 * `null` draws the plain rows alone. The features are reached by Up from the
 * first row, outside the arrival-focus/restore-key mechanism below, but the
 * cover and "Recently added" both take part in it: arriving fresh (no
 * restore key naming a row's own stop) lands the remote on the cover's own
 * Watch now, so the cover is what a viewer sees first rather than scrolled
 * off above whatever row used to open the page — and "Recently added" joins
 * the same row list [rows] does, so a film opened from it and left again is
 * found the same way any other row's plate is.
 *
 * Unlike the phone's own magazine header, Continue and Next up stay in
 * [rows] rather than folding into a merged resume strip: TV's plain rows
 * already draw them (`homeRowsOf`'s own output), and building a second,
 * TV-only resume-strip component to match the phone's merged one was not
 * worth its own risk for this pass — a deliberate, documented difference,
 * not a silent gap.
 */
@Composable
internal fun TvHome(
    rows: List<HomeRow>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
    restoreKey: String? = null,
    magazine: MagazineHome? = null,
    // Playing the cover's own film straight away — the web's `play(set)` on
    // its "Watch now" (`home-cover.js:137`) — rather than opening its title
    // page the way every plate on this row otherwise does.
    onPlay: (setId: String) -> Unit = onOpenTitle,
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val first = remember { FocusRequester() }
    val coverFocus = remember { FocusRequester() }
    val allRows =
        remember(rows, magazine) {
            listOfNotNull(magazine?.recentlyAddedRow?.takeIf { it.total > 0 }) + rows
        }
    val hasCover = magazine?.editorial?.cover?.isNotEmpty() == true
    // Which row, and which stop along it, takes the remote — `null` when no
    // restore key names one still on the page, which the cover then claims
    // instead of the row list's own first stop.
    val rowTarget =
        remember(allRows, restoreKey) {
            restoreKey?.let { wanted ->
                allRows.withIndex().firstNotNullOfOrNull { (row, content) ->
                    keysOf(content).indexOf(wanted).takeIf { it >= 0 }?.let { row to it }
                }
            }
        }
    val landOnCover = hasCover && rowTarget == null
    val target = rowTarget ?: (0 to 0)

    // On arrival, on a new restore key, and when the first rows arrive —
    // never merely because the rows moved: a fetch finishing or Continue
    // appearing reorders them while the viewer is browsing, and the remote
    // must stay where the viewer put it.
    val takesFocus = LocalTakesArrivalFocus.current
    LaunchedEffect(restoreKey, allRows.isNotEmpty(), landOnCover) {
        if (!takesFocus) return@LaunchedEffect
        if (landOnCover) coverFocus.requestFocus() else if (allRows.isNotEmpty()) first.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // Coming down from the masthead lands where the viewer last
                // was on this page, or on its first stop, rather than on
                // whichever plate happens to sit under the tab the remote
                // left from.
                .focusRestorer(if (hasCover) coverFocus else first)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.horizontal)
                .padding(bottom = Overscan.vertical, top = Spacing.small),
    ) {
        magazine?.editorial?.let { editorial ->
            if (editorial.cover.isNotEmpty()) {
                TvCoverStory(films = editorial.cover, onPlay = onPlay, onOpenTitle = onOpenTitle, arrivalFocus = coverFocus)
            }
            if (editorial.features.isNotEmpty()) TvFeatureStrip(features = editorial.features, onOpenTitle = onOpenTitle)
        }
        allRows.forEachIndexed { index, row ->
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
                    focusAt = target.second.takeIf { index == target.first },
                    focus = Modifier.focusRequester(first),
                )
            }
        }
    }
}
