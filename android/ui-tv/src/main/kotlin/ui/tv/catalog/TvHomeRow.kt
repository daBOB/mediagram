package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import catalog.CollectionKind
import catalog.Entry
import catalog.HOME_ROW_LIMIT
import catalog.HomeRow
import catalog.RowContent
import catalog.extentOf
import catalog.keyOf
import designsystem.Spacing
import model.Progress
import ui.tv.TvTextRow

/**
 * One row of the start page: its heading, with "See all" at its far end as
 * the web's `rowHead` and the phone's `RowHeading` both set it, then at most
 * [HOME_ROW_LIMIT] plates side by side (`homeRowsOf` already cuts every row
 * to that).
 *
 * The plates do not scroll sideways. The web start page rules rails out on
 * purpose — a rail hides how much is on a shelf and ranks whatever it shows
 * first — and six plates fit across a television without one. Six slots are
 * laid out even when fewer plates fill them, so a row of two keeps plates
 * the size of every other row's rather than stretching two across the width.
 *
 * "See all" is still reached by the same Right that walked the row: the
 * row's last stop hands Right up to it, so the row admitting it is a window
 * leaves the whole shelf one press away. Up in the heading rather than as a
 * seventh slot beside the plates, it takes no width from them — six plates
 * are then as wide as a shelf wall's, wide enough for a caption's year and
 * runtime together. Only the row's last stop reaches it, though
 * ([SeeAllLink]): sitting just above the plates, it would otherwise be what
 * Up found from any of them, ahead of the row above or the masthead.
 *
 * [focusAt] is the stop, if any, that carries the focus requester Home
 * hands down in [focus] — the first row's first stop, or the one a viewer
 * opened and has come back to.
 *
 * [keysOf] is what each stop is keyed by, in order: the same key the
 * library records when the stop is opened, so Home can find it again.
 */
@Composable
internal fun TvHomeRow(
    row: HomeRow,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
    focusAt: Int?,
    focus: Modifier,
) {
    val link = remember { SeeAllLink() }
    val seeAll: (() -> Unit)? = row.seeAll?.let { shelf -> { onSeeAll(shelf) } }
    val at = { index: Int -> if (index == focusAt) focus else Modifier }
    val last = { index: Int, lastIndex: Int -> if (seeAll != null && index == lastIndex) link.lastStop else Modifier }
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.large)) {
        TvCountedHeading(row.title, row.total) {
            if (seeAll != null) TvTextRow(text = "See all", onClick = seeAll, modifier = link.seeAll, focusRequester = link.focus)
        }
        when (val content = row.content) {
            is RowContent.Entries -> {
                val entries = content.entries
                if (entries.all(::isCourse)) {
                    CourseIndex(entries.filterIsInstance<Entry.Collection>(), onOpenCollection) { index ->
                        at(index).then(last(index, entries.lastIndex))
                    }
                } else {
                    PlateRow(entries.size) { index, modifier ->
                        val entry = entries[index]
                        TvEntryPlate(
                            entry = entry,
                            positions = positions,
                            watchedIds = watchedIds,
                            onOpen = { openEntry(entry, onOpenTitle, onOpenCollection) },
                            modifier = modifier.then(at(index)).then(last(index, entries.lastIndex)),
                        )
                    }
                }
            }

            is RowContent.Sets -> {
                val cards = content.cards
                PlateRow(cards.size) { index, modifier ->
                    val card = cards[index]
                    TvSetPlate(
                        card = card,
                        onOpen = { onOpenTitle(card.set.setId) },
                        modifier = modifier.then(at(index)).then(last(index, cards.lastIndex)),
                    )
                }
            }
        }
    }
}

/**
 * The one way to "See all" and back: Right from the row's last stop, and
 * Left or Down from it to that stop again. It can take focus only while the
 * remote is on one or the other, so a search from anywhere else on the page
 * never lands on it.
 */
private class SeeAllLink {
    val focus = FocusRequester()
    private val lastFocus = FocusRequester()
    private var fromLast by mutableStateOf(false)
    private var held by mutableStateOf(false)

    val seeAll: Modifier =
        Modifier
            .onFocusChanged { held = it.isFocused }
            .focusProperties {
                canFocus = fromLast || held
                left = lastFocus
                down = lastFocus
            }

    val lastStop: Modifier =
        Modifier
            .focusRequester(lastFocus)
            .onFocusChanged { fromLast = it.isFocused }
            .focusProperties { right = focus }
}

/** Six slots whether or not six plates fill them, so a row of two keeps plates the size of every other row's. */
@Composable
private fun PlateRow(
    count: Int,
    plate: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (index in 0 until HOME_ROW_LIMIT) {
            if (index < count) plate(index, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Courses as a list of names rather than plates, as the web start page
 * sets them: a course has no artwork of its own, so a plate would be a
 * poster-shaped blank with initials in it, and six of those read worse
 * than six lines.
 */
@Composable
private fun CourseIndex(
    courses: List<Entry.Collection>,
    onOpenCollection: (key: String) -> Unit,
    at: (index: Int) -> Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        courses.forEachIndexed { index, course ->
            TvTextRow(
                text = "${course.name} · ${extentOf(course)}",
                onClick = { onOpenCollection(course.key) },
                modifier = at(index),
            )
        }
    }
}

/** Each stop of [row] by the key opening it records: a set's id, a film's, a show's or a course's own key. */
internal fun keysOf(row: HomeRow): List<String> =
    when (val content = row.content) {
        is RowContent.Entries -> content.entries.map(::keyOf)
        is RowContent.Sets -> content.cards.map { it.set.setId }
    }

private fun isCourse(entry: Entry): Boolean = entry is Entry.Collection && entry.kind == CollectionKind.COURSE
