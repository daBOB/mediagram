package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.extentOf
import catalog.factsLine
import catalog.keyOf
import designsystem.Spacing
import ui.common.catalog.rememberRowState

private val DEPT_CARD_WIDTH = 140.dp

/**
 * One category row on the Documentaries department page — a folder card
 * opens like [ShowsDepartmentScreen]'s own `CollectionRow`, a standalone
 * documentary plays like [DocumentariesDepartmentScreen]'s own
 * `DocumentaryRow`. A category mixes the two side by side, since it files a
 * folder and a single the same way.
 */
@Composable
internal fun DocumentaryUnitRow(
    units: List<Entry>,
    watchedIds: Set<String>,
    onOpenCollection: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    LazyRow(
        state = rememberRowState(units.map(::keyOf)),
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        items(items = units, key = ::keyOf) { entry ->
            when (entry) {
                is Entry.Collection ->
                    PosterCard(
                        posterPath = entry.posterPath,
                        title = entry.name,
                        caption = extentOf(entry),
                        modifier = Modifier.width(DEPT_CARD_WIDTH),
                        onClick = { onOpenCollection(entry.key) },
                    )
                is Entry.Film ->
                    PosterCard(
                        posterPath = entry.set.posterPath,
                        title = entry.set.title,
                        caption = factsLine(entry.set.year, entry.set.durationSecs),
                        watched = entry.set.setId in watchedIds,
                        modifier = Modifier.width(DEPT_CARD_WIDTH),
                        onClick = { onPlay(entry.set.setId) },
                    )
            }
        }
    }
}
