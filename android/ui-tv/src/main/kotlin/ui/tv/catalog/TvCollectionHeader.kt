package ui.tv.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import catalog.Entry
import catalog.ResumeVerb
import catalog.SeriesResumePick
import catalog.episodeShort
import catalog.firstItemOf
import designsystem.Spacing
import designsystem.TvTypeScale
import model.ageLabel
import ui.tv.TvTextRow
import uniffi.mediagram_core.TitleInfo

/**
 * A course's name, then its art and facts — a show has its own spread
 * ([TvSeriesPage]). A course is rated as a course, so its first lesson
 * speaks for all of it, as `series-header.js` asks; it has no one year and
 * no one runtime, so its rating is the only fact.
 *
 * Art and an overview stand taller than the screen leaves above the first
 * lesson, so arriving scrolls the name away. The name and the overview are
 * both stops the remote can rest on, then — a stop is the only way a remote
 * scrolls — so Up from the first lesson reads the overview and Up again
 * brings the name back. A course with nothing to show but its name stays
 * short enough that nothing scrolls it away.
 */
@Composable
internal fun CollectionHeader(
    collection: Entry.Collection,
    info: TitleInfo?,
    onOpenGenre: (String) -> Unit,
    genreFocus: String?,
) {
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
 * "Resume"/"Continue"/"Play" — [catalog.seriesResume]'s own pick, as a row
 * under a course's header.
 */
@Composable
internal fun SeriesResumeRow(
    pick: SeriesResumePick,
    onResume: (setId: String) -> Unit,
) {
    TvTextRow(
        text = resumeLabel(pick),
        onClick = { onResume(pick.set.setId) },
        modifier = Modifier.padding(top = Spacing.small),
    )
}

/** `▶ Resume S1 E3` — `series-page.js`'s own pill words, [ResumeVerb] naming which of the three it is. */
internal fun resumeLabel(pick: SeriesResumePick): String {
    val verb =
        when (pick.verb) {
            ResumeVerb.RESUME -> "Resume"
            ResumeVerb.CONTINUE -> "Continue"
            ResumeVerb.PLAY -> "Play"
        }
    return "▶ $verb ${episodeShort(pick.set)}"
}
