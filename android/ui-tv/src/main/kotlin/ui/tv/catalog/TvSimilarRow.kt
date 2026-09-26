package ui.tv.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.extentOf
import catalog.factsLine
import designsystem.Spacing
import java.io.File
import model.MediaSet

/** A similar row's own plate width — a poster's width, the same every wall on this surface uses. */
private val SimilarPlateWidth = 140.dp

/**
 * "Similar" on a film's own page — a horizontal row of [catalog.similarTo]'s
 * own picks, the television twin of the phone's Similar tab and the web's
 * `similar.js`. Nothing is drawn for a title with nothing similar in the
 * library.
 */
@Composable
internal fun TvSimilarFilms(
    films: List<MediaSet>,
    onOpenTitle: (setId: String) -> Unit,
) {
    if (films.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (set in films) {
            TvPlate(
                title = set.title,
                posterPath = set.posterPath?.let(::File),
                onOpen = { onOpenTitle(set.setId) },
                modifier = Modifier.width(SimilarPlateWidth),
                caption = factsLine(set.year, set.durationSecs),
            )
        }
    }
}

/** "Similar" on a show's own page — [catalog.similarShows]'s own picks, one plate per show. */
@Composable
internal fun TvSimilarShows(
    shows: List<Entry.Collection>,
    onOpenCollection: (key: String) -> Unit,
) {
    if (shows.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (entry in shows) {
            TvPlate(
                title = entry.name,
                posterPath = entry.posterPath?.let(::File),
                onOpen = { onOpenCollection(entry.key) },
                modifier = Modifier.width(SimilarPlateWidth),
                caption = extentOf(entry),
            )
        }
    }
}
