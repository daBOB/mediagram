package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.Entry
import catalog.SetCard
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
 * `collectionGrid` — it is not one title to finish.
 */
@Composable
internal fun TvEntryPlate(
    entry: Entry,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        is Entry.Film -> {
            TvPlate(
                title = entry.set.title,
                posterPath = entry.set.posterPath?.let(::File),
                onOpen = onOpen,
                modifier = modifier,
                progress = watchedFractionOf(positions[entry.set.setId]),
                watched = entry.set.setId in watchedIds,
            )
        }

        is Entry.Collection -> {
            TvPlate(
                title = entry.name,
                posterPath = entry.posterPath?.let(::File),
                onOpen = onOpen,
                modifier = modifier,
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
 * One set on Continue or Next up — the twin of the phone's `SetPlate`. Its
 * marks arrive already worked out on the [SetCard], by the same rule the
 * phone reads them from.
 */
@Composable
internal fun TvSetPlate(
    card: SetCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvPlate(
        title = card.set.title,
        posterPath = card.set.posterPath?.let(::File),
        onOpen = onOpen,
        modifier = modifier,
        progress = card.progress,
        watched = card.watched,
    )
}
