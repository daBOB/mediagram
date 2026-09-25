package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.Entry
import catalog.SetCard
import model.episodeLabel
import catalog.extentOf
import catalog.factsLine
import catalog.watchedFractionOf
import java.io.File
import model.Progress

/**
 * One shelf entry as a plate: a film's poster, or a show's or a course's —
 * the television twin of the phone's `EntryCard`, shared by Home and every
 * shelf wall the same way that one is.
 *
 * A film carries the phone's two marks, worked out the way `EntryCard`
 * works them out: the rule from its position, the tick from whether it was
 * finished. A show or a course carries neither, same as the web's
 * `collectionGrid` — it is not one title to finish. [heldIds] adds the
 * offline badge to a film, on the walls the phone's `EntryCard` carries it
 * on; Home's Latest rows pass none, as the phone's do.
 */
@Composable
internal fun TvEntryPlate(
    entry: Entry,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    heldIds: Set<String> = emptySet(),
) {
    when (entry) {
        is Entry.Film -> {
            TvPlate(
                title = entry.set.title,
                posterPath = entry.set.posterPath?.let(::File),
                onOpen = onOpen,
                modifier = modifier,
                // The year and the runtime, as the phone's shelf plate sets
                // them: the line that tells two versions of a title apart.
                caption = factsLine(entry.set.year, entry.set.durationSecs),
                progress = watchedFractionOf(positions[entry.set.setId]),
                watched = entry.set.setId in watchedIds,
                held = entry.set.setId in heldIds,
            )
        }

        is Entry.Collection -> {
            TvPlate(
                title = entry.name,
                posterPath = entry.posterPath?.let(::File),
                onOpen = onOpen,
                modifier = modifier,
                caption = extentOf(entry),
            )
        }
    }
}

/**
 * Where an entry leads: a film to the page that describes it, a show or a
 * course to what is inside it — there is no one thing a plate standing for
 * all of those could sensibly start.
 */
internal fun openEntry(
    entry: Entry,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    when (entry) {
        is Entry.Film -> onOpenTitle(entry.set.setId)
        is Entry.Collection -> onOpenCollection(entry.key)
    }
}

/**
 * One set on Continue or Next up — the twin of the phone's `SetPlate`, with
 * its same two lines: the show and episode number, then the card's own
 * caption. Its marks arrive already worked out on the [SetCard], by the same
 * rule the phone reads them from.
 */
@Composable
internal fun TvSetPlate(
    card: SetCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val set = card.set
    TvPlate(
        title = set.title,
        posterPath = set.posterPath?.let(::File),
        onOpen = onOpen,
        modifier = modifier,
        meta = listOfNotNull(set.show, episodeLabel(set).ifEmpty { null }).joinToString(" · ").ifEmpty { null },
        // Where this viewer stopped, or "Next up" — the difference between
        // the two is what the row is for.
        caption = card.caption.ifEmpty { null },
        progress = card.progress,
        watched = card.watched,
        held = card.held,
    )
}
