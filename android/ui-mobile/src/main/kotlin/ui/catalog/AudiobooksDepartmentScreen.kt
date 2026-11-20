package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import catalog.AudiobooksDepartment
import catalog.CatalogUiState
import catalog.Department
import catalog.Entry
import catalog.audiobooksLineOf
import catalog.factsLine
import designsystem.Spacing
import model.WatchSnapshot

/** For a test to scroll a short window to a row further down the page. */
internal const val AUDIOBOOKS_DEPT_TEST_TAG = "audiobooks-department"

/**
 * The Audiobooks shelf, as its own tab. The shelf is only offered while it
 * holds a book, so the empty line is the web's own words for a page that is
 * never reached here rather than a state a viewer meets.
 */
@Composable
internal fun AudiobooksDepartmentTab(
    state: CatalogUiState.Ready,
    columns: Int,
    listState: LazyListState = rememberLazyListState(),
    onPlay: (String) -> Unit,
) {
    state.pages.audiobooks?.let {
        AudiobooksDepartmentScreen(department = it, watch = state.watch, columns = columns, onPlay = onPlay, state = listState)
    } ?: CenteredMessage("No audiobooks yet. Upload one with mediagram add-audiobook <file or folder>.")
}

/**
 * The Audiobooks department's opening page — a Compose port of
 * `audiobooks-department.js`: a hero with no art (a book has no backdrop),
 * what is being listened to, one row per genre, what arrived, then every
 * book by title as a grid. Every card plays on tap, as the web wires each
 * to `cx.play`: a book has no page worth a stop before listening.
 */
@Composable
internal fun AudiobooksDepartmentScreen(
    department: AudiobooksDepartment,
    watch: WatchSnapshot,
    columns: Int,
    onPlay: (String) -> Unit,
    state: LazyListState = rememberLazyListState(),
) {
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val reveals = rememberReveals()
    val wall = remember(department.books, columns) { department.books.chunked(columns) }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag(AUDIOBOOKS_DEPT_TEST_TAG),
        contentPadding = PaddingValues(bottom = Spacing.large),
    ) {
        item {
            DepartmentHero(
                title = Department.AUDIOBOOKS.label,
                line = audiobooksLineOf(department),
                lead = null,
                onOpenTitle = null,
                reveals = reveals,
            )
        }
        if (department.resumeCards.isNotEmpty()) {
            revealItem(reveals, "continue-heading") { DeptRowHeading(title = "Continue listening") }
            revealItem(reveals, "continue") { ResumeStrip(cards = department.resumeCards, onOpenTitle = onPlay) }
        }
        for (row in department.categories) {
            revealItem(reveals, "category-heading-${row.title}") { DeptRowHeading(title = row.title) }
            revealItem(reveals, "category-row-${row.title}") { DocumentaryUnitRow(row.units, watchedIds, onOpenCollection = {}, onPlay = onPlay) }
        }
        revealItem(reveals, "recent-heading") { DeptRowHeading(title = "Recently added") }
        revealItem(reveals, "recent") { DocumentaryUnitRow(department.recentlyAdded.map(Entry::Film), watchedIds, onOpenCollection = {}, onPlay = onPlay) }
        revealItem(reveals, "all-heading") { DeptRowHeading(title = "All audiobooks") }
        // The web's "All audiobooks" is a wrapping grid, not a strip: every book, a line of [columns] at a time.
        items(wall, key = { line -> line.first().setId }) { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                for (book in line) {
                    PosterCard(
                        posterPath = book.posterPath,
                        title = book.title,
                        caption = factsLine(book.year, book.durationSecs),
                        watched = book.setId in watchedIds,
                        whole = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onPlay(book.setId) },
                    )
                }
                // A short last line keeps its cards the width of the ones above.
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
