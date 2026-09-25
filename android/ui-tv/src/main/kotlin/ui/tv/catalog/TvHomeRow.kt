package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CollectionKind
import catalog.Entry
import catalog.HOME_ROW_LIMIT
import catalog.HomeRow
import catalog.RowContent
import catalog.extentOf
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Progress
import ui.tv.TvTextRow

/**
 * One row of the start page: its heading, at most [HOME_ROW_LIMIT] plates
 * side by side (`homeRowsOf` already cuts every row to that), and "See all" after them as the row's last stop.
 *
 * The plates do not scroll sideways. The web start page rules rails out on
 * purpose — a rail hides how much is on a shelf and ranks whatever it shows
 * first — and six plates fit across a television without one. Six slots are
 * laid out even when fewer plates fill them, so a row of two keeps plates
 * the size of every other row's rather than stretching two across the width.
 *
 * "See all" sits where the remote runs out of plates, so it is reached by
 * the same Right that walked the row: the row admitting it is a window,
 * with the whole shelf one press away. [firstItem] carries the focus
 * requester Home hands its first row's first stop.
 */
@Composable
internal fun TvHomeRow(
    row: HomeRow,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
    firstItem: Modifier,
) {
    val seeAll: (() -> Unit)? = row.seeAll?.let { shelf -> { onSeeAll(shelf) } }
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.large)) {
        RowHeading(row)
        when (val content = row.content) {
            is RowContent.Entries -> {
                val entries = content.entries
                if (entries.all(::isCourse)) {
                    CourseIndex(entries.filterIsInstance<Entry.Collection>(), onOpenCollection, seeAll, firstItem)
                } else {
                    PlateRow(entries.size, seeAll) { index, modifier ->
                        val entry = entries[index]
                        TvEntryPlate(
                            entry = entry,
                            positions = positions,
                            watchedIds = watchedIds,
                            onOpen = { openEntry(entry, onOpenTitle, onOpenCollection) },
                            modifier = if (index == 0) modifier.then(firstItem) else modifier,
                        )
                    }
                }
            }

            is RowContent.Sets -> {
                val cards = content.cards
                PlateRow(cards.size, seeAll) { index, modifier ->
                    val card = cards[index]
                    TvSetPlate(
                        card = card,
                        onOpen = { onOpenTitle(card.set.setId) },
                        modifier = if (index == 0) modifier.then(firstItem) else modifier,
                    )
                }
            }
        }
    }
}

/** The same "Title · n" the web's and the phone's row headings carry, over a hairline. */
@Composable
private fun RowHeading(row: HomeRow) {
    Text(
        text =
            buildAnnotatedString {
                append(row.title)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · ${row.total}") }
            },
        style = TvTypeScale.title,
    )
    Box(
        modifier =
            Modifier
                .padding(top = Spacing.small, bottom = Spacing.medium)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.borderVariant),
    )
}

@Composable
private fun PlateRow(
    count: Int,
    onSeeAll: (() -> Unit)?,
    plate: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until HOME_ROW_LIMIT) {
            if (index < count) plate(index, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        }
        // A fixed width whether or not there is a "See all", so every row's
        // six plates line up under one another.
        Box(modifier = Modifier.width(SeeAllWidth)) {
            if (onSeeAll != null) TvTextRow(text = "See all", onClick = onSeeAll)
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
    onSeeAll: (() -> Unit)?,
    firstItem: Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        courses.forEachIndexed { index, course ->
            TvTextRow(
                text = "${course.name} · ${extentOf(course)}",
                onClick = { onOpenCollection(course.key) },
                modifier = if (index == 0) firstItem else Modifier,
            )
        }
        if (onSeeAll != null) TvTextRow(text = "See all", onClick = onSeeAll)
    }
}

private fun isCourse(entry: Entry): Boolean = entry is Entry.Collection && entry.kind == CollectionKind.COURSE

private val SeeAllWidth = 96.dp
