package ui.tv.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 *
 * [restoreKey] names the film whose page was just left, if any, so the
 * remote comes back to its own plate; with none, or none still in [films],
 * the first plate takes focus instead, so this row is never left with
 * nothing focused.
 */
@Composable
internal fun TvSimilarFilms(
    films: List<MediaSet>,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
) {
    if (films.isEmpty()) return
    val focusRequester = remember { FocusRequester() }
    val focusIndex =
        remember(films, restoreKey) {
            restoreKey?.let { wanted -> films.indexOfFirst { it.setId == wanted } }?.takeIf { it >= 0 } ?: 0
        }
    // Not gated on `LocalTakesArrivalFocus` — see the same note on [TvCastRow].
    LaunchedEffect(focusIndex, restoreKey) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        films.forEachIndexed { index, set ->
            TvPlate(
                title = set.title,
                posterPath = set.posterPath?.let(::File),
                onOpen = { onOpenTitle(set.setId) },
                modifier = plateModifier(index, focusIndex, focusRequester),
                caption = factsLine(set.year, set.durationSecs),
            )
        }
    }
}

/** "Similar" on a show's own page — [catalog.similarShows]'s own picks, one plate per show, with the same restore rule as [TvSimilarFilms]. */
@Composable
internal fun TvSimilarShows(
    shows: List<Entry.Collection>,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
) {
    if (shows.isEmpty()) return
    val focusRequester = remember { FocusRequester() }
    val focusIndex =
        remember(shows, restoreKey) {
            restoreKey?.let { wanted -> shows.indexOfFirst { it.key == wanted } }?.takeIf { it >= 0 } ?: 0
        }
    // Not gated on `LocalTakesArrivalFocus` — see the same note on [TvCastRow].
    LaunchedEffect(focusIndex, restoreKey) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        shows.forEachIndexed { index, entry ->
            TvPlate(
                title = entry.name,
                posterPath = entry.posterPath?.let(::File),
                onOpen = { onOpenCollection(entry.key) },
                modifier = plateModifier(index, focusIndex, focusRequester),
                caption = extentOf(entry),
            )
        }
    }
}

/** A similar plate's width, plus the arrival/restore [focusRequester] on whichever one [focusIndex] names. */
private fun plateModifier(
    index: Int,
    focusIndex: Int,
    focusRequester: FocusRequester,
): Modifier =
    Modifier.width(SimilarPlateWidth).let { if (index == focusIndex) it.focusRequester(focusRequester) else it }
