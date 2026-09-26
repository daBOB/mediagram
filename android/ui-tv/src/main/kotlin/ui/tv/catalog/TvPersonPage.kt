package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.PersonPage
import catalog.keyOf
import designsystem.Spacing
import java.io.File
import model.WatchSnapshot
import ui.tv.setup.TvLoadingIndicator

/**
 * How wide a person's own portrait sits at the head of their page — small
 * enough that the 2:3 portrait and the first line of plates fit one 540dp
 * screen together, so arriving on the first film keeps the name in view.
 */
private val PortraitWidth = 120.dp

/**
 * A person's own page — the television twin of the web's
 * `cast.js#renderPerson`: their portrait and name, then the titles in *this*
 * profile's own library they appear in, films and shows on the one wall,
 * each carrying the same watched/offline marks every other wall's plates do.
 *
 * [page] is `null` for three different reasons a viewer cannot tell apart by
 * looking: still asking, nobody by that id, or somebody nobody in this
 * profile can see. [loading] tells the first from the other two — a fixed
 * "loading" mark rather than the empty sentence flashing up before an answer
 * has even arrived — and once it clears, `null` says only the fixed
 * sentence, never a name, so a kids profile never learns who was in a title
 * it cannot open, the same rule the web's own page follows.
 */
@Composable
internal fun TvPersonPage(
    page: PersonPage?,
    loading: Boolean,
    portrait: String?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
) {
    if (page == null) {
        if (loading) {
            TvLoadingIndicator()
        } else {
            TvCenteredMessage("Nobody by that number is credited on anything in your library.")
        }
        return
    }
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val entries = remember(page) { page.films.map(Entry::Film) + page.shows }
    TvPage {
        TvWall(
            items = entries,
            key = ::keyOf,
            restoreKey = restoreKey,
            onOpen = { entry -> openEntry(entry, onOpenTitle, onOpenCollection) },
            header = {
                Row {
                    TvPlateArt(
                        posterPath = portrait?.let(::File),
                        title = page.person.name,
                        progress = null,
                        watched = false,
                        modifier = Modifier.width(PortraitWidth),
                    )
                    Column(modifier = Modifier.padding(start = Spacing.large)) {
                        TvCountedHeading(page.person.name, entries.size)
                    }
                }
            },
            plate = { entry, modifier, onOpen ->
                TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds)
            },
        )
    }
}
