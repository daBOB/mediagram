package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Franchise
import catalog.filmOverviewFacts
import designsystem.Spacing
import java.io.File
import model.MediaSet
import uniffi.mediagram_core.TitleInfo

/**
 * A film's Overview tab — `film-page.js#overview`: the poster beside the
 * facts a reader looks up first, its genres and franchise as links. The
 * poster is left out for a film with none, as the web leaves out its image.
 */
@Composable
internal fun TvFilmOverview(
    set: MediaSet,
    info: TitleInfo?,
    franchise: Franchise?,
    onOpenGenre: (String) -> Unit,
    onOpenFranchise: (Long) -> Unit,
    restoreKey: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
        set.posterPath?.let { poster ->
            TvPlateArt(posterPath = File(poster), title = set.title, progress = null, watched = false, modifier = Modifier.width(PosterWidth))
        }
        TvFactSheet(filmOverviewFacts(set, info, franchise), onOpenGenre = onOpenGenre, onOpenFranchise = onOpenFranchise, restoreKey = restoreKey)
    }
}

/** `.overview-panel`'s 200px poster, a little narrower so its 2:3 height still fits under the tab row. */
private val PosterWidth = 160.dp
