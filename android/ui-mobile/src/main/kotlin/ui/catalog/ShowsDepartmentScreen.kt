package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Department
import catalog.Entry
import catalog.ShowsDepartment
import catalog.extentOf
import catalog.firstItemOf
import catalog.resumeCardsOf
import catalog.showsLineOf
import designsystem.Spacing
import model.WatchSnapshot
import ui.catalog.home.CourseList

private val DEPT_CARD_WIDTH = 140.dp

/**
 * The Series or Tutorials department's opening page — a Compose port of
 * `department-pages.js#renderShowsDept`: a hero, the resume strip for what
 * is underway (a card there plays, as `home-resume.js`'s do), one row per
 * hand-set category, Popular/New rows when [ShowsDepartment] offers them (a
 * big enough Series shelf only — a course is never categorised and never
 * gets these rows either), then every show or course.
 *
 * @param dept [Department.SERIES] or [Department.TUTORIALS]: the hero's
 *   title and count line, and what the Continue row and the foot section
 *   call it.
 */
@Composable
internal fun ShowsDepartmentScreen(
    dept: Department,
    department: ShowsDepartment,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onPlay: (String) -> Unit,
    state: LazyGridState = rememberLazyGridState(),
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val resumeCards = remember(department.underway, watch, heldIds) {
        resumeCardsOf(department.underway.continues, department.underway.nextUp, watch, heldIds)
    }
    val leadTitle = department.lead?.let { firstItemOf(it.divisions) }
    val leadKey = department.lead?.key
    val series = dept == Department.SERIES

    LazyVerticalGrid(
        columns = GutteredCells(columns, Spacing.medium),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            DepartmentHero(
                title = dept.label,
                line = showsLineOf(department, dept),
                lead = leadTitle,
                // The show's own name, not whichever episode happened to
                // lead — the web's own `lead?.show` (`department-pages.js`).
                leadName = leadTitle?.show,
                // `null`, not a lambda that quietly no-ops, once there is no
                // collection to open — the quote's credit is the hero's only
                // tap target now, and one with nowhere to go should not draw
                // as a link at all.
                onOpenTitle = leadKey?.let { key -> { onOpenCollection(key) } },
            )
        }
        if (resumeCards.isNotEmpty()) {
            item(key = "continue-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = if (series) "Continue your series" else "Continue your courses")
            }
            item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                ResumeStrip(cards = resumeCards, onOpenTitle = onPlay)
            }
        }
        for (row in department.categories) {
            item(key = "category-heading-${row.title}", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = row.title)
            }
            item(key = "category-${row.title}", span = { GridItemSpan(maxLineSpan) }) {
                CollectionRow(row.units, onOpenCollection)
            }
        }
        if (department.popular.isNotEmpty()) {
            item(key = "popular-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = "Popular series")
            }
            item(key = "popular", span = { GridItemSpan(maxLineSpan) }) {
                CollectionRow(department.popular, onOpenCollection)
            }
        }
        if (department.newEpisodes.isNotEmpty()) {
            item(key = "new-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = "New episodes")
            }
            item(key = "new", span = { GridItemSpan(maxLineSpan) }) {
                CollectionRow(department.newEpisodes, onOpenCollection)
            }
        }
        item(key = "all-heading", span = { GridItemSpan(maxLineSpan) }) {
            DeptRowHeading(title = if (series) "All shows" else "All courses")
        }
        if (series) {
            itemsIndexed(items = department.all, key = { _, entry -> "all/${entry.key}" }) { index, entry ->
                Box(Modifier.gutteredCell(index, columns, Spacing.medium)) {
                    EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
                }
            }
        } else {
            // Courses as a list, the web's `{mode: LIST}` here: a course
            // carries no artwork to fill a plate with. One item rather than
            // one per course, so the grid's gap does not open between rows
            // the list draws edge to edge.
            item(key = "all-courses", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(horizontal = Spacing.medium)) { CourseList(department.all, onOpenCollection) }
            }
        }
    }
}

@Composable
private fun CollectionRow(shows: List<Entry.Collection>, onOpenCollection: (String) -> Unit) {
    LazyRow(
        state = rememberRowState(shows.map { it.key }),
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        lazyRowItems(items = shows, key = { it.key }) { entry ->
            PosterCard(
                posterPath = entry.posterPath,
                title = entry.name,
                caption = extentOf(entry),
                modifier = Modifier.width(DEPT_CARD_WIDTH),
                onClick = { onOpenCollection(entry.key) },
            )
        }
    }
}
