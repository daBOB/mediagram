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

/** How wide a person's own portrait sits at the head of their page. */
private val PortraitWidth = 180.dp

/**
 * A person's own page — the television twin of the web's
 * `cast.js#renderPerson`: their portrait and name, then the titles in *this*
 * profile's own library they appear in, films and shows on the one wall.
 *
 * [page] is `null` both for nobody by that id and for somebody nobody in
 * this profile can see; either way this says only the fixed sentence, never
 * a name, so a kids profile never learns who was in a title it cannot open
 * — the same rule the web's own page follows.
 */
@Composable
internal fun TvPersonPage(
    page: PersonPage?,
    portrait: String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
) {
    if (page == null) {
        TvCenteredMessage("Nobody by that number is credited on anything in your library.")
        return
    }
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
                TvEntryPlate(entry, emptyMap(), emptySet(), onOpen, modifier)
            },
        )
    }
}
