package ui.tv.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import catalog.Entry
import catalog.ResumeVerb
import catalog.SeasonPlate
import catalog.SeriesResumePick
import catalog.episodeShort
import catalog.firstItemOf
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import model.ageLabel
import ui.catalog.rememberPosterPath
import ui.tv.TvTextRow
import uniffi.mediagram_core.TitleInfo

/**
 * The show's or course's name, then its art and facts. A show is rated as
 * a show, so its first episode speaks for all of it, as `series-header.js`
 * asks; it has no one year and no one runtime, so its rating is the only
 * fact. A course has no rating.
 *
 * Art and an overview stand taller than the screen leaves above the first
 * season, so arriving scrolls the name away. The name and the overview are
 * both stops the remote can rest on, then — as on the title page, where a
 * stop is the only way a remote scrolls — so Up from the first season reads
 * the overview and Up again brings the name back. A course with nothing to
 * show but its name stays short enough that nothing scrolls it away.
 */
@Composable
internal fun CollectionHeader(
    collection: Entry.Collection,
    info: TitleInfo?,
    onOpenGenre: (String) -> Unit,
    genreFocus: String?,
) {
    // A show is rated and tagged as a show, so its first episode speaks for
    // all of it, as the phone's header asks.
    val firstEpisode = remember(collection) { firstItemOf(collection.divisions) }
    val age = firstEpisode?.ageLabel()
    val genres = firstEpisode?.genres.orEmpty()
    if (info != null || collection.posterPath != null || age != null || genres.isNotEmpty()) {
        TvTitleHeader(
            posterPath = collection.posterPath,
            title = collection.name,
            facts = age,
            info = info,
            genres = genres,
            onOpenGenre = onOpenGenre,
            genreFocus = genreFocus,
            readableOverview = true,
            readableTitle = true,
        )
    } else {
        Text(text = collection.name, style = TvTypeScale.title)
    }
}

/**
 * "Resume"/"Continue"/"Play" on a show's own page — [catalog.seriesResume]'s
 * own pick, worded the way the phone's Continue wall words its own resume
 * caption, with [ResumeVerb] naming which of the three it is.
 */
@Composable
internal fun SeriesResumeRow(
    pick: SeriesResumePick,
    onResume: (setId: String) -> Unit,
) {
    val verb =
        when (pick.verb) {
            ResumeVerb.RESUME -> "Resume"
            ResumeVerb.CONTINUE -> "Continue"
            ResumeVerb.PLAY -> "Play"
        }
    TvTextRow(
        text = "▶ $verb · ${episodeShort(pick.set)}",
        onClick = { onResume(pick.set.setId) },
        modifier = Modifier.padding(top = Spacing.small),
    )
}

/**
 * A season's plate, whose artwork falls back in three steps — its own
 * poster, then the show's, then the initials [TvPlate] draws on its own —
 * so the season's is the only one looked up here.
 */
@Composable
internal fun TvSeasonPlate(
    collection: Entry.Collection,
    plate: SeasonPlate,
    posterPath: suspend (key: String) -> String?,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val seasonPoster = rememberPosterPath(plate.posterKey, posterPath)
    TvPlate(
        title = plate.title,
        posterPath = (seasonPoster ?: collection.posterPath)?.let(::File),
        onOpen = onOpen,
        modifier = modifier,
        caption = plate.caption,
        watched = plate.watched,
    )
}
