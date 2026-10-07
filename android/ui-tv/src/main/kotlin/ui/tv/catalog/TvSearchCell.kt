package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import catalog.SearchDestination
import catalog.factsLine
import catalog.spelledCountOf
import catalog.watchedFractionOf
import data.PortraitRequestLog
import java.io.File
import ui.catalog.DESTINATION_ASPECT

/**
 * One search result, drawn the way `search-view.js` draws its kind: a film
 * as its poster, opening the film's own page (the web's `openFilm`, the
 * phone's poster); a matched show as the show's poster; a person as the
 * web's round `personCard`; a franchise or a list as the destination card
 * Collections draws, a franchise counting its films and a list its titles;
 * and an episode, a documentary or a lesson as a row — which plays at once,
 * as on the phone and the web.
 *
 * [requester] is always given and always attached, whichever entry the
 * results want the remote on right now, so a card's modifier chain never
 * changes shape under it. [modifier] sizes a poster or a card within its
 * line; a row spans the width by itself.
 */
@Composable
internal fun TvSearchCell(
    entry: SearchEntry,
    layout: SearchLayout,
    requester: FocusRequester,
    marks: WatchMarks,
    onPlay: (setId: String) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenPerson: (personId: Long) -> Unit,
    onOpenDestination: (SearchDestination) -> Unit,
    portraits: PortraitRequestLog,
    fetchPortrait: suspend (Long) -> String?,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        is SearchEntry.Title -> {
            val set = entry.row.set
            val progress = watchedFractionOf(marks.positions[set.setId])
            val watched = set.setId in marks.watchedIds
            if (layout == SearchLayout.POSTERS) {
                TvPlate(
                    title = set.title,
                    posterPath = set.posterPath?.let(::File),
                    onOpen = { onOpenTitle(set.setId) },
                    modifier = modifier.focusRequester(requester),
                    // The line a shelf's plate carries, telling two versions of a title apart.
                    caption = factsLine(set.year, set.durationSecs),
                    progress = progress,
                    watched = watched,
                    held = entry.row.held,
                )
            } else {
                TvSearchRow(row = entry.row, progress = progress, watched = watched, onPlay = onPlay, focus = requester)
            }
        }

        is SearchEntry.Show ->
            TvEntryPlate(
                entry = entry.entry,
                positions = marks.positions,
                watchedIds = marks.watchedIds,
                onOpen = { onOpenCollection(entry.entry.key) },
                modifier = modifier.focusRequester(requester),
            )

        is SearchEntry.Person -> {
            val person = entry.person
            TvPersonCard(
                personId = person.personId,
                name = person.name,
                portraitPath = person.portraitPath,
                sub = spelledCountOf(person.titles, "title"),
                onOpenPerson = onOpenPerson,
                portraits = portraits,
                fetchPortrait = fetchPortrait,
                modifier = modifier.focusRequester(requester),
            )
        }

        is SearchEntry.Destination -> {
            val destination = entry.destination
            TvArtTile(
                name = destination.name,
                meta = spelledCountOf(destination.itemCount, if (destination.franchiseId != null) "film" else "title"),
                art = destination.art,
                aspectRatio = DESTINATION_ASPECT,
                onOpen = { onOpenDestination(destination) },
                modifier = modifier.focusRequester(requester),
                destination = true,
            )
        }
    }
}
